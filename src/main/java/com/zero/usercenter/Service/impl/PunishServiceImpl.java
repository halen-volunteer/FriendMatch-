package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.AdminAuditService;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.*;

/**
 * 处罚管理服务实现
 *
 * 梯度处罚规则（基于 total_violation_num）：
 *   1次 → 全局禁言 60 分钟
 *   2次 → 全局禁言 1440 分钟（1天）
 *   3次 → 全局禁言 10080 分钟（7天）
 *   4次及以上 → 永久封号
 */
@Service
public class PunishServiceImpl implements PunishService {

    @Resource private UserPunishLogMapper userPunishLogMapper;//处罚表
    @Resource private UserViolationCountMapper userViolationCountMapper;
    @Resource private PunishMsgRelationMapper punishMsgRelationMapper;
    @Resource private UserMapper userMapper;
    @Resource private SystemNoticeMapper systemNoticeMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;
    @Resource private AdminAuthService adminAuthService;
    @Resource private AdminAuditService adminAuditService;

    // ======================== 执行处罚 ========================

    /**
     * 执行处罚
     * 按违规次数梯度升级处罚力度，更新 DB + Redis，异步发送处罚通知
     * 梯度规则：1次→禁言60min，2次→1440min，3次→10080min，4次+→永久封号
     *
     * @param dto 处罚数据传输对象（punishUserId、punishType、punishReason 等）
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result punishUser(PunishDTO dto) {
        boolean isSystemAuto = dto.getOperateType() != null && dto.getOperateType() == 1;
        Long operatorId = isSystemAuto ? null : UserHolder.getUserId();
        if (!isSystemAuto && operatorId == null) throw new BusinessException("用户未登录");
        if (!isSystemAuto) adminAuthService.assertAdmin(operatorId);

        // 参数校验
        if (dto.getPunishUserId() == null) return Result.fail("被处罚用户ID不能为空");
        if (dto.getPunishReason() == null || dto.getPunishReason().isBlank())
            return Result.fail("处罚原因不能为空");
        if (dto.getPunishReason().length() > 512) return Result.fail("处罚原因不能超过512字符");

        User target = userMapper.selectById(dto.getPunishUserId());
        if (target == null || target.getIsDelete() == 1) return Result.fail("被处罚用户不存在");

        LocalDateTime now = LocalDateTime.now();

        // 先更新违规统计，获取最新违规次数
        int violationNum = updateViolationCount(dto.getPunishUserId(), now);

        // 按梯度自动决定处罚类型和时长
        int punishType;
        int punishDuration;
        LocalDateTime endTime;
        if (violationNum >= VIOLATION_BAN_THRESHOLD) {
            // 违规次数 >= 4，永久封号
            punishType = 2;
            punishDuration = -1;
            endTime = null;
        } else {
            // 按梯度禁言
            punishType = 1;
            punishDuration = GRADIENT_MUTE_DURATIONS[violationNum - 1];
            endTime = now.plusMinutes(punishDuration);
        }

        // 写处罚记录
        UserPunishLog log = new UserPunishLog();
        log.setPunishUserId(dto.getPunishUserId());
        log.setPunishType(punishType);
        log.setPunishReason(dto.getPunishReason());
        log.setPunishDuration(punishDuration);
        log.setPunishStartTime(now);
        log.setPunishEndTime(endTime);
        log.setOperateType(dto.getOperateType() != null ? dto.getOperateType() : 2); // 1-系统自动，2-管理员手动（默认）
        log.setOperateUserId(dto.getOperateType() != null && dto.getOperateType() == 1 ? null : operatorId);
        log.setIsCancel(0);
        userPunishLogMapper.insert(log);

        // 同步更新 User 表字段
        applyPunishToUser(dto.getPunishUserId(), punishType, endTime);

        // 更新 Redis 缓存
        syncPunishCache(dto.getPunishUserId(), punishType, endTime);

        // 异步发送系统通知给被处罚用户，不阻塞主流程
        final Long noticeUserId = dto.getPunishUserId();
        final String noticeReason = dto.getPunishReason();
        final int noticePunishType = punishType;
        final Long noticePunishLogId = log.getId();
        Thread.ofVirtual().start(() ->
            sendPunishNotice(noticeUserId, noticeReason, noticePunishType, noticePunishLogId)
        );

        // 写违规消息关联（若有关联消息ID）
        if (dto.getMsgId() != null) {
            PunishMsgRelation relation = new PunishMsgRelation();
            relation.setPunishLogId(log.getId());
            relation.setMsgId(dto.getMsgId());
            relation.setAiAuditResult(dto.getAiAuditResult() != null ? dto.getAiAuditResult() : "管理员手动处罚");
            punishMsgRelationMapper.insert(relation);
        }
        adminAuditService.log("punish_execute", String.valueOf(dto.getPunishUserId()), dto.getPunishReason());

        return Result.ok("处罚已执行");
    }

    // ======================== 撤销处罚 ========================

    /**
     * 撤销处罚
     * 清除 t_user/t_team_member 禁言状态，删除 Redis 缓存，异步发送撤销通知
     *
     * @param dto 撤销数据传输对象（punishLogId）
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result cancelPunish(PunishCancelDTO dto) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);
        if (dto.getPunishLogId() == null) return Result.fail("处罚记录ID不能为空");

        UserPunishLog log = userPunishLogMapper.selectById(dto.getPunishLogId());
        if (log == null) return Result.fail("处罚记录不存在");
        if (log.getIsCancel() == 1) return Result.fail("该处罚已撤销");

        // 更新处罚记录
        LambdaUpdateWrapper<UserPunishLog> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserPunishLog::getId, dto.getPunishLogId())
          .set(UserPunishLog::getIsCancel, 1)
          .set(UserPunishLog::getCancelTime, LocalDateTime.now())
          .set(UserPunishLog::getCancelUserId, operatorId);
        userPunishLogMapper.update(null, uw);

        // 清除 User 表禁言/封号状态
        LambdaUpdateWrapper<User> userUw = new LambdaUpdateWrapper<>();
        userUw.eq(User::getId, log.getPunishUserId());
        if (log.getPunishType() == 1 || log.getPunishType() == 2) {
            userUw.set(User::getGlobalPunishType, 0)
                  .set(User::getGlobalUnpunishTime, null);
            userMapper.update(null, userUw);
        }

        // 违规次数 -1（撤销申诉成功，该次违规不计入梯度）
        LambdaQueryWrapper<UserViolationCount> vcQw = new LambdaQueryWrapper<>();
        vcQw.eq(UserViolationCount::getUserId, log.getPunishUserId());
        UserViolationCount vc = userViolationCountMapper.selectOne(vcQw);
        if (vc != null && vc.getTotalViolationNum() > 0) {
            LambdaUpdateWrapper<UserViolationCount> vcUw = new LambdaUpdateWrapper<>();
            vcUw.eq(UserViolationCount::getUserId, log.getPunishUserId())
                .set(UserViolationCount::getTotalViolationNum, vc.getTotalViolationNum() - 1);
            userViolationCountMapper.update(null, vcUw);
        }

        // 清除 Redis 缓存
        stringRedisTemplate.delete(USER_PUNISH_KEY + log.getPunishUserId());

        // 异步发送撤销通知给被处罚用户，不阻塞主流程
        final Long cancelNoticeUserId = log.getPunishUserId();
        final int cancelPunishType = log.getPunishType();
        final Long cancelPunishLogId = log.getId();
        Thread.ofVirtual().start(() -> {
            String typeDesc = cancelPunishType == 1 ? "全局禁言" : "永久封号";
            SystemNotice cancelNotice = new SystemNotice();
            cancelNotice.setUserId(cancelNoticeUserId);
            cancelNotice.setNoticeType(7);
            cancelNotice.setNoticeContent("您的" + typeDesc + "处罚（记录ID：" + cancelPunishLogId + "）已被管理员撤销，恢复正常使用。");
            cancelNotice.setRelatedId(cancelPunishLogId);
            cancelNotice.setIsRead(0);
            cancelNotice.setIsDelete(0);
            sendRealtimeSystemNotice(cancelNotice);
        });
        adminAuditService.log("punish_cancel", String.valueOf(log.getPunishUserId()), dto.getCancelReason());

        return Result.ok("处罚已撤销");
    }

    // ======================== 查询处罚记录 ========================

    /**
     * 查询指定用户的处罚记录（管理员）
     * 分页查 t_user_punish_log，按创建时间降序
     *
     * @param userId   被查询用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 处罚记录列表及总数
     */
    @Override
    public Result getPunishLogs(Long userId, int page, int pageSize) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);
        if (userId == null) return Result.fail("用户ID不能为空");

        LambdaQueryWrapper<UserPunishLog> qw = new LambdaQueryWrapper<>();
        qw.eq(UserPunishLog::getPunishUserId, userId).orderByDesc(UserPunishLog::getCreateTime);
        Page<UserPunishLog> pageObj = new Page<>(page, pageSize);
        Page<UserPunishLog> result = userPunishLogMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 查询当前用户自己的处罚记录
     * 复用 getPunishLogs，userId 取当前登录用户
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @return 处罚记录列表及总数
     */
    @Override
    public Result getMyPunishLogs(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        return getPunishLogs(userId, page, pageSize);
    }

    /**
     * 查询指定用户的违规次数
     * 返回 totalViolationNum 和 latestViolationTime
     *
     * @param userId 用户ID
     * @return 违规统计信息
     */
    @Override
    public Result getViolationCount(Long userId) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);
        if (userId == null) return Result.fail("用户ID不能为空");
        LambdaQueryWrapper<UserViolationCount> qw = new LambdaQueryWrapper<>();
        qw.eq(UserViolationCount::getUserId, userId);
        UserViolationCount vc = userViolationCountMapper.selectOne(qw);
        Map<String, Object> data = new HashMap<>();
        data.put("totalViolationNum", vc != null ? vc.getTotalViolationNum() : 0);
        data.put("latestViolationTime", vc != null ? vc.getLatestViolationTime() : null);
        return Result.ok(data);
    }

    // ======================== 辅助方法 ========================

    /**
     * 更新违规统计（insert or update）
     * 首次违规插入记录，已有则累加并更新最新违规时间
     *
     * @param userId 被处罚用户ID
     * @param now    处罚时间
     * @return 更新后的累计违规次数
     */
    private int updateViolationCount(Long userId, LocalDateTime now) {
        LambdaQueryWrapper<UserViolationCount> qw = new LambdaQueryWrapper<>();
        qw.eq(UserViolationCount::getUserId, userId);
        UserViolationCount vc = userViolationCountMapper.selectOne(qw);
        int newCount;
        if (vc == null) {
            newCount = 1;
            vc = new UserViolationCount();
            vc.setUserId(userId);
            vc.setTotalViolationNum(newCount);
            vc.setLatestViolationTime(now);
            userViolationCountMapper.insert(vc);
        } else {
            newCount = vc.getTotalViolationNum() + 1;
            LambdaUpdateWrapper<UserViolationCount> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserViolationCount::getUserId, userId)
              .set(UserViolationCount::getTotalViolationNum, newCount)
              .set(UserViolationCount::getLatestViolationTime, now);
            userViolationCountMapper.update(null, uw);
        }
        return newCount;
    }

    /**
     * 将处罚状态同步到 t_user
     * punishType=1：全局禁言（更新 User.globalPunishType=1）
     * punishType=2：永久封号（更新 User.globalPunishType=2）
     *
     * @param punishUserId 被处罚用户ID
     * @param punishType   处罚类型（1-全局禁言，2-永久封号）
     * @param endTime      解禁时间（永久封号为 null）
     */
    private void applyPunishToUser(Long punishUserId, int punishType, LocalDateTime endTime) {
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
        uw.eq(User::getId, punishUserId);
        if (punishType == 1) {
            // 全局禁言
            uw.set(User::getGlobalPunishType, 1).set(User::getGlobalUnpunishTime, endTime);
            userMapper.update(null, uw);
        } else if (punishType == 2) {
            // 永久封号
            uw.set(User::getGlobalPunishType, 2).set(User::getGlobalUnpunishTime, null);
            userMapper.update(null, uw);
        }
    }

    /**
     * 同步处罚状态到 Redis 缓存
     * 全局禁言：TTL = 禁言剩余时长 + 1分钟（缓存精确跟随禁言到期，避免提前失效）
     * 永久封号：TTL = 24小时（长期缓存，减少查库频率）
     *
     * @param userId      被处罚用户ID
     * @param punishType  处罚类型（1-全局禁言，2-永久封号）
     * @param endTime     解禁时间（永久封号为 null）
     */
    private void syncPunishCache(Long userId, int punishType, LocalDateTime endTime) {
        String val = punishType + (endTime != null ? "|" + endTime : "");
        long ttlMinutes;
        if (punishType == 2 || endTime == null) {
            // 永久封号：缓存 24 小时
            ttlMinutes = 24 * 60L;
        } else {
            // 全局禁言：TTL = 剩余禁言时长 + 1分钟缓冲
            long remaining = java.time.Duration.between(LocalDateTime.now(), endTime).toMinutes();
            ttlMinutes = Math.max(remaining + 1, 1);
        }
        stringRedisTemplate.opsForValue().set(
            USER_PUNISH_KEY + userId, val,
            ttlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 异步发送处罚系统通知（noticeType=7）
     * 内容包含处罚类型描述和申诉提示
     *
     * @param userId        被处罚用户ID
     * @param reason        处罚原因
     * @param punishType    处罚类型（1/2/3）
     * @param punishLogId   处罚记录ID
     */
    private void sendPunishNotice(Long userId, String reason, int punishType, Long punishLogId) {
        String typeDesc = punishType == 1 ? "全局禁言" : "永久封号";
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setNoticeType(7);
        notice.setNoticeContent("您因【" + reason + "】被执行" + typeDesc + "处罚，如有异议请提交申诉。");
        notice.setRelatedId(punishLogId);
        notice.setIsRead(0);
        notice.setIsDelete(0);
        sendRealtimeSystemNotice(notice);
    }

    /**
     * 落库并实时推送系统通知
     * 先写入 t_system_notice，再通过 WebSocket 推送；用户离线时推送静默跳过，通知不丢失
     *
     * @param notice 已填充好字段的系统通知实体
     */
    private void sendRealtimeSystemNotice(SystemNotice notice) {
        systemNoticeMapper.insert(notice);

        Map<String, Object> push = new HashMap<>();
        push.put("type", "system_notice");
        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("relatedId", notice.getRelatedId());
        push.put("data", data);
        chatWebSocketHandler.sendToUser(notice.getUserId(), JSON.toJSONString(push));
    }
}
