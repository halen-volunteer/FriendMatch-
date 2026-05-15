package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.PunishMsgRelationMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Mapper.UserPunishLogMapper;
import com.zero.usercenter.Mapper.UserViolationCountMapper;
import com.zero.usercenter.Model.PunishMsgRelation;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Model.UserPunishLog;
import com.zero.usercenter.Model.UserViolationCount;
import com.zero.usercenter.Service.AdminAuditService;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.mq.AsyncMessageService;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.GRADIENT_MUTE_DURATIONS;
import static com.zero.usercenter.utils.Number.USER_PUNISH_KEY;
import static com.zero.usercenter.utils.Number.VIOLATION_BAN_THRESHOLD;

@Service
@Slf4j
public class PunishServiceImpl implements PunishService {

    @Resource
    private UserPunishLogMapper userPunishLogMapper;

    @Resource
    private UserViolationCountMapper userViolationCountMapper;

    @Resource
    private PunishMsgRelationMapper punishMsgRelationMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AdminAuthService adminAuthService;

    @Resource
    private AdminAuditService adminAuditService;

    @Resource
    private AsyncMessageService asyncMessageService;

    /**
     * 执行处罚。
     * 支持管理员手动处罚和系统自动处罚两种来源，最终都会统一沉淀到处罚日志和用户处罚状态。
     */
    @Override
    @Transactional
    public Result punishUser(PunishDTO dto) {
        // 1. 先判断当前请求到底是“管理员手动处罚”还是“系统自动处罚”。
        // 系统自动处罚通常不会带登录态，但仍然要求走同一套处罚落库和状态同步流程。
        Long operatorId = UserHolder.getUserId();
        boolean systemAuto = operatorId == null && dto.getOperateType() != null && dto.getOperateType() == 1;
        log.info("[punish-execute] start operatorId={}, systemAuto={}, targetUserId={}, msgId={}, operateType={}",
                operatorId,
                systemAuto,
                dto == null ? null : dto.getPunishUserId(),
                dto == null ? null : dto.getMsgId(),
                dto == null ? null : dto.getOperateType());
        if (!systemAuto && operatorId == null) {
            throw new BusinessException("用户未登录");
        }
        if (systemAuto) {
            if (operatorId != null) {
                adminAuthService.assertAdmin(operatorId);
            }
        } else {
            adminAuthService.assertAdmin(operatorId);
        }

        // 2. 先做基础参数校验，避免无效处罚数据进入后续写库和缓存同步链路。
        if (dto.getPunishUserId() == null) {
            return Result.fail("被处罚用户ID不能为空");
        }
        if (dto.getPunishReason() == null || dto.getPunishReason().isBlank()) {
            return Result.fail("处罚原因不能为空");
        }
        if (dto.getPunishReason().length() > 512) {
            return Result.fail("处罚原因不能超过512字符");
        }

        User targetUser = userMapper.selectById(dto.getPunishUserId());
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getIsDelete())) {
            return Result.fail("被处罚用户不存在");
        }

        // 3. 处罚采用“违规次数 -> 梯度禁言/封号”的统一口径。
        // 这样无论来源是 AI 自动处罚、举报复核还是管理员手动处罚，最终都沉淀到同一套处罚日志与缓存状态中。
        LocalDateTime now = LocalDateTime.now();
        int violationNum = updateViolationCount(dto.getPunishUserId(), now);

        // 4. 根据违规次数计算本次处罚类型：前几次走阶梯禁言，超过阈值后直接封号。
        int punishType;
        int punishDuration;
        LocalDateTime punishEndTime;
        if (violationNum >= VIOLATION_BAN_THRESHOLD) {
            punishType = 2;
            punishDuration = -1;
            punishEndTime = null;
        } else {
            punishType = 1;
            punishDuration = GRADIENT_MUTE_DURATIONS[violationNum - 1];
            punishEndTime = now.plusMinutes(punishDuration);
        }
        log.info("[punish-execute] resolved punish strategy targetUserId={}, violationNum={}, punishType={}, punishDuration={}, punishEndTime={}",
                dto.getPunishUserId(), violationNum, punishType, punishDuration, punishEndTime);

        // 5. 写入处罚日志，日志是后续申诉、撤销和追责的唯一事实来源。
        UserPunishLog punishLog = new UserPunishLog();
        punishLog.setPunishUserId(dto.getPunishUserId());
        punishLog.setPunishType(punishType);
        punishLog.setPunishReason(dto.getPunishReason());
        punishLog.setPunishDuration(punishDuration);
        punishLog.setPunishStartTime(now);
        punishLog.setPunishEndTime(punishEndTime);
        punishLog.setOperateType(systemAuto ? 1 : 2);
        punishLog.setOperateUserId(systemAuto ? null : operatorId);
        punishLog.setIsCancel(0);
        userPunishLogMapper.insert(punishLog);
        log.info("[punish-execute] punish log persisted punishLogId={}, targetUserId={}, punishType={}",
                punishLog.getId(), dto.getPunishUserId(), punishType);

        // 6. 同步更新用户表中的全局处罚状态，并把当前生效处罚写入 Redis，供登录链快速判断。
        applyPunishToUser(dto.getPunishUserId(), punishType, punishEndTime);
        syncPunishCache(dto.getPunishUserId(), punishType, punishEndTime);

        // 7. 处罚完成后异步通知用户，避免通知发送阻塞主事务。
        asyncMessageService.sendSystemNotice(
                dto.getPunishUserId(),
                7,
                buildPunishNoticeContent(dto.getPunishReason(), punishType),
                punishLog.getId());

        // 8. 如果这次处罚和具体消息有关，就补一条消息-处罚关联，方便后续审核追踪。
        if (dto.getMsgId() != null) {
            PunishMsgRelation relation = new PunishMsgRelation();
            relation.setPunishLogId(punishLog.getId());
            relation.setMsgId(dto.getMsgId());
            relation.setAiAuditResult(dto.getAiAuditResult() != null ? dto.getAiAuditResult() : "管理员手动处罚");
            punishMsgRelationMapper.insert(relation);
            log.info("[punish-execute] punish-msg relation persisted punishLogId={}, msgId={}", punishLog.getId(), dto.getMsgId());
        }

        // 9. 最后写审计日志，便于管理后台追踪谁在什么理由下执行了处罚。
        adminAuditService.log("punish_execute", String.valueOf(dto.getPunishUserId()), dto.getPunishReason());
        log.info("[punish-execute] completed punishLogId={}, targetUserId={}", punishLog.getId(), dto.getPunishUserId());
        return Result.ok("处罚已执行");
    }

    /**
     * 撤销处罚。
     * 撤销后会同步回滚违规次数，并重算当前用户仍然生效的处罚状态。
     */
    @Override
    @Transactional
    @RequireAdmin
    public Result cancelPunish(PunishCancelDTO dto) {
        // 1. 先锁定要撤销的处罚记录，避免空 ID 或重复撤销。
        Long operatorId = UserHolder.getUserId();
        log.info("[punish-cancel] start operatorId={}, punishLogId={}",
                operatorId,
                dto == null ? null : dto.getPunishLogId());
        if (dto.getPunishLogId() == null) {
            log.warn("[punish-cancel] reject because punishLogId null, operatorId={}", operatorId);
            return Result.fail("处罚记录ID不能为空");
        }

        UserPunishLog punishLog = userPunishLogMapper.selectById(dto.getPunishLogId());
        if (punishLog == null) {
            log.warn("[punish-cancel] reject because punish log missing, operatorId={}, punishLogId={}",
                    operatorId, dto.getPunishLogId());
            return Result.fail("处罚记录不存在");
        }
        if (Integer.valueOf(1).equals(punishLog.getIsCancel())) {
            log.warn("[punish-cancel] reject because punish log already canceled, operatorId={}, punishLogId={}",
                    operatorId, dto.getPunishLogId());
            return Result.fail("该处罚已撤销");
        }

        // 2. 撤销处罚后不能只改日志状态，还要同步回滚违规次数并重新计算用户当前生效处罚，
        // 否则用户表 / Redis 中可能仍残留旧的禁言或封号状态。
        LambdaUpdateWrapper<UserPunishLog> logUpdate = new LambdaUpdateWrapper<>();
        logUpdate.eq(UserPunishLog::getId, dto.getPunishLogId())
                .set(UserPunishLog::getIsCancel, 1)
                .set(UserPunishLog::getCancelTime, LocalDateTime.now())
                .set(UserPunishLog::getCancelUserId, operatorId)
                .set(UserPunishLog::getCancelReason, dto.getCancelReason() == null ? "" : dto.getCancelReason().trim());
        userPunishLogMapper.update(null, logUpdate);
        log.info("[punish-cancel] punish log marked canceled punishLogId={}, targetUserId={}",
                punishLog.getId(), punishLog.getPunishUserId());

        // 3. 回滚累计违规次数，再重新扫描用户当前仍然有效的处罚，避免误清空更晚生效的记录。
        rollbackViolationCount(punishLog.getPunishUserId());
        refreshUserPunishState(punishLog.getPunishUserId());

        // 4. 撤销完成后异步通知用户，告知其处罚已恢复。
        String typeDesc = punishLog.getPunishType() != null && punishLog.getPunishType() == 1 ? "全局禁言" : "永久封号";
        asyncMessageService.sendSystemNotice(
                punishLog.getPunishUserId(),
                7,
                "您的" + typeDesc + "处罚（记录ID：" + punishLog.getId() + "）已被管理员撤销，恢复正常使用。",
                punishLog.getId());

        // 5. 写撤销审计，保留人工操作痕迹。
        adminAuditService.log("punish_cancel", String.valueOf(punishLog.getPunishUserId()), dto.getCancelReason());
        log.info("[punish-cancel] completed punishLogId={}, targetUserId={}",
                punishLog.getId(), punishLog.getPunishUserId());
        return Result.ok("处罚已撤销");
    }

    /**
     * 管理员分页查询指定用户的处罚记录。
     */
    @Override
    @RequireAdmin
    public Result getPunishLogs(Long userId, int page, int pageSize) {
        if (userId == null) {
            return Result.fail("用户ID不能为空");
        }

        // 先分页查处罚日志，再把实体转成前端更容易理解的视图对象。
        Page<UserPunishLog> result = queryPunishLogPage(userId, page, pageSize);
        return Result.ok(toPunishLogViewList(result.getRecords()), result.getTotal());
    }

    /**
     * 查询当前用户自己的处罚记录。
     */
    @Override
    public Result getMyPunishLogs(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 当前用户只能查看自己的处罚历史，因此直接复用同一套分页查询逻辑。
        Page<UserPunishLog> result = queryPunishLogPage(userId, page, pageSize);
        return Result.ok(toPunishLogViewList(result.getRecords()), result.getTotal());
    }

    /**
     * 查询指定用户的违规统计信息。
     */
    @Override
    @RequireAdmin
    public Result getViolationCount(Long userId) {
        if (userId == null) {
            return Result.fail("用户ID不能为空");
        }

        // 违规统计表只保存累计次数和最近时间，用于处罚梯度判断和后台展示。
        LambdaQueryWrapper<UserViolationCount> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserViolationCount::getUserId, userId);
        UserViolationCount violationCount = userViolationCountMapper.selectOne(queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("totalViolationNum", violationCount != null ? violationCount.getTotalViolationNum() : 0);
        result.put("latestViolationTime", violationCount != null ? violationCount.getLatestViolationTime() : null);
        return Result.ok(result);
    }

    /**
     * 按用户分页查询处罚日志。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 处罚日志分页结果
     */
    private Page<UserPunishLog> queryPunishLogPage(Long userId, int page, int pageSize) {
        // 1. 处罚记录统一按创建时间倒序，方便优先查看最近处罚。
        LambdaQueryWrapper<UserPunishLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserPunishLog::getPunishUserId, userId)
                .orderByDesc(UserPunishLog::getCreateTime);
        Page<UserPunishLog> pageObj = new Page<>(page, pageSize);
        return userPunishLogMapper.selectPage(pageObj, queryWrapper);
    }

    /**
     * 批量把处罚日志转换成前端展示结构。
     *
     * @param records 处罚日志列表
     * @return 展示数据列表
     */
    private List<Map<String, Object>> toPunishLogViewList(List<UserPunishLog> records) {
        return records.stream().map(this::toPunishLogView).collect(Collectors.toList());
    }

    /**
     * 将单条处罚日志转换成前端展示结构。
     *
     * @param record 处罚日志
     * @return 展示数据
     */
    private Map<String, Object> toPunishLogView(UserPunishLog record) {
        // 1. 统一把处罚日志核心字段、撤销信息和当前状态组装给前端。
        Map<String, Object> item = new HashMap<>();
        item.put("id", record.getId());
        item.put("punishUserId", record.getPunishUserId());
        item.put("punishType", record.getPunishType());
        item.put("punishReason", record.getPunishReason());
        item.put("punishDuration", record.getPunishDuration());
        item.put("startTime", record.getPunishStartTime());
        item.put("endTime", record.getPunishEndTime());
        item.put("punishStartTime", record.getPunishStartTime());
        item.put("punishEndTime", record.getPunishEndTime());
        item.put("status", buildPunishStatus(record));
        item.put("isCancel", record.getIsCancel());
        item.put("operateType", record.getOperateType());
        item.put("cancelTime", record.getCancelTime());
        item.put("cancelUserId", record.getCancelUserId());
        item.put("cancelReason", record.getCancelReason());
        item.put("createTime", record.getCreateTime());
        return item;
    }

    /**
     * 计算处罚日志当前展示状态。
     *
     * @param record 处罚日志
     * @return active / ended
     */
    private String buildPunishStatus(UserPunishLog record) {
        // 1. 被撤销处罚无论原类型是什么，都统一按 ended 展示。
        if (record == null) {
            return "ended";
        }
        if (Integer.valueOf(1).equals(record.getIsCancel())) {
            return "ended";
        }
        // 2. 永久封号只要没撤销就始终是 active。
        if (Integer.valueOf(2).equals(record.getPunishType())) {
            return "active";
        }
        // 3. 临时处罚则按结束时间判断是否仍在生效。
        LocalDateTime endTime = record.getPunishEndTime();
        if (endTime == null) {
            return "active";
        }
        return endTime.isAfter(LocalDateTime.now()) ? "active" : "ended";
    }

    /**
     * 累加用户违规次数。
     *
     * @param userId 用户 ID
     * @param now    当前时间
     * @return 更新后的违规次数
     */
    private int updateViolationCount(Long userId, LocalDateTime now) {
        // 1. 每次处罚都会先更新这张累计表，它决定后续处罚是否升级为封号。
        LambdaQueryWrapper<UserViolationCount> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserViolationCount::getUserId, userId);
        UserViolationCount violationCount = userViolationCountMapper.selectOne(queryWrapper);

        if (violationCount == null) {
            // 2. 首次违规时直接建档，后面所有处罚梯度都围绕这张累计表计算。
            violationCount = new UserViolationCount();
            violationCount.setUserId(userId);
            violationCount.setTotalViolationNum(1);
            violationCount.setLatestViolationTime(now);
            userViolationCountMapper.insert(violationCount);
            log.info("[punish-violation] init violation count userId={}, totalViolationNum=1", userId);
            return 1;
        }

        // 3. 非首次违规则更新累计次数和最近违规时间。
        int newCount = violationCount.getTotalViolationNum() + 1;
        LambdaUpdateWrapper<UserViolationCount> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserViolationCount::getUserId, userId)
                .set(UserViolationCount::getTotalViolationNum, newCount)
                .set(UserViolationCount::getLatestViolationTime, now);
        userViolationCountMapper.update(null, updateWrapper);
        log.info("[punish-violation] update violation count userId={}, totalViolationNum={}", userId, newCount);
        return newCount;
    }

    /**
     * 回滚用户违规次数。
     *
     * @param userId 用户 ID
     */
    private void rollbackViolationCount(Long userId) {
        // 1. 撤销处罚时只需要回滚累计次数，不会清空历史处罚日志，便于审计和申诉。
        LambdaQueryWrapper<UserViolationCount> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserViolationCount::getUserId, userId);
        UserViolationCount violationCount = userViolationCountMapper.selectOne(queryWrapper);
        if (violationCount == null || violationCount.getTotalViolationNum() == null || violationCount.getTotalViolationNum() <= 0) {
            return;
        }

        // 2. 只有当前统计值大于 0 时才执行回滚，避免出现负数。
        LambdaUpdateWrapper<UserViolationCount> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserViolationCount::getUserId, userId)
                .set(UserViolationCount::getTotalViolationNum, violationCount.getTotalViolationNum() - 1);
        userViolationCountMapper.update(null, updateWrapper);
    }

    /**
     * 重新计算用户当前生效处罚状态。
     *
     * @param userId 用户 ID
     */
    private void refreshUserPunishState(Long userId) {
        // 1. 空用户 ID 直接返回，避免发起无意义查询。
        if (userId == null) {
            return;
        }
        log.info("[punish-refresh] start refresh userId={}", userId);

        // 2. 用户可能同时存在多条历史处罚记录，这里总是重新扫描“最近仍生效的一条”回写用户状态，
        // 避免撤销旧处罚时误清空更晚生效的禁言/封号。
        LambdaQueryWrapper<UserPunishLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserPunishLog::getPunishUserId, userId)
                .eq(UserPunishLog::getIsCancel, 0)
                .orderByDesc(UserPunishLog::getCreateTime)
                .orderByDesc(UserPunishLog::getId);
        List<UserPunishLog> punishLogs = userPunishLogMapper.selectList(queryWrapper);

        LocalDateTime now = LocalDateTime.now();
        UserPunishLog activePunishLog = null;
        for (UserPunishLog punishLog : punishLogs) {
            if (isPunishStillActive(punishLog, now)) {
                activePunishLog = punishLog;
                break;
            }
        }

        // 3. 找不到生效处罚时，清空用户表处罚状态和 Redis 缓存。
        LambdaUpdateWrapper<User> userUpdate = new LambdaUpdateWrapper<>();
        userUpdate.eq(User::getId, userId);
        if (activePunishLog == null) {
            userUpdate.set(User::getGlobalPunishType, 0)
                    .set(User::getGlobalUnpunishTime, null);
            userMapper.update(null, userUpdate);
            stringRedisTemplate.delete(USER_PUNISH_KEY + userId);
            log.info("[punish-refresh] cleared punish state userId={}", userId);
            return;
        }

        // 4. 仍存在生效处罚时，以最新生效处罚为准刷新用户表和缓存。
        userUpdate.set(User::getGlobalPunishType, activePunishLog.getPunishType())
                .set(User::getGlobalUnpunishTime, activePunishLog.getPunishEndTime());
        userMapper.update(null, userUpdate);
        syncPunishCache(userId, activePunishLog.getPunishType(), activePunishLog.getPunishEndTime());
        log.info("[punish-refresh] refreshed active punish userId={}, punishLogId={}, punishType={}, punishEndTime={}",
                userId, activePunishLog.getId(), activePunishLog.getPunishType(), activePunishLog.getPunishEndTime());
    }

    /**
     * 判断一条处罚日志当前是否仍然生效。
     *
     * @param punishLog 处罚日志
     * @param now       当前时间
     * @return true 表示仍生效
     */
    private boolean isPunishStillActive(UserPunishLog punishLog, LocalDateTime now) {
        // 1. 已撤销处罚直接视为失效。
        if (punishLog == null || Integer.valueOf(1).equals(punishLog.getIsCancel())) {
            return false;
        }
        // 2. 永久封号不依赖结束时间，只要没撤销就持续生效。
        if (Integer.valueOf(2).equals(punishLog.getPunishType())) {
            return true;
        }
        // 3. 临时禁言则按结束时间判断是否还在有效期内。
        LocalDateTime endTime = punishLog.getPunishEndTime();
        return endTime != null && endTime.isAfter(now);
    }

    /**
     * 把处罚结果写回用户主表。
     *
     * @param punishUserId 被处罚用户 ID
     * @param punishType   处罚类型
     * @param endTime      处罚结束时间
     */
    private void applyPunishToUser(Long punishUserId, int punishType, LocalDateTime endTime) {
        // 1. 用户表保存的是全局处罚状态，供登录、资料等主链路快速判断。
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, punishUserId);
        if (punishType == 1) {
            updateWrapper.set(User::getGlobalPunishType, 1)
                    .set(User::getGlobalUnpunishTime, endTime);
        } else {
            // 2. 永久封号不依赖结束时间，后续登录链只要看到 globalPunishType=2 就会直接拒绝登录。
            updateWrapper.set(User::getGlobalPunishType, 2)
                    .set(User::getGlobalUnpunishTime, null);
        }
        userMapper.update(null, updateWrapper);
        log.info("[punish-user-state] applied to user table userId={}, punishType={}, endTime={}",
                punishUserId, punishType, endTime);
    }

    /**
     * 同步当前生效处罚到 Redis。
     *
     * @param userId     用户 ID
     * @param punishType 处罚类型
     * @param endTime    结束时间
     */
    private void syncPunishCache(Long userId, int punishType, LocalDateTime endTime) {
        // 1. Redis 只缓存当前生效处罚，永久封号给固定兜底 TTL，临时禁言按结束时间动态过期。
        String value = punishType + (endTime != null ? "|" + endTime : "");
        long ttlMinutes;
        if (punishType == 2 || endTime == null) {
            ttlMinutes = 24 * 60L;
        } else {
            // 2. 临时处罚按结束时间动态过期，避免缓存比真实处罚持续更久。
            ttlMinutes = Math.max(Duration.between(LocalDateTime.now(), endTime).toMinutes() + 1, 1);
        }
        stringRedisTemplate.opsForValue().set(USER_PUNISH_KEY + userId, value, ttlMinutes, TimeUnit.MINUTES);
        log.info("[punish-cache] synced cache userId={}, punishType={}, endTime={}, ttlMinutes={}",
                userId, punishType, endTime, ttlMinutes);
    }

    /**
     * 生成处罚通知文案。
     *
     * @param reason     处罚原因
     * @param punishType 处罚类型
     * @return 通知文案
     */
    private String buildPunishNoticeContent(String reason, int punishType) {
        String typeDesc = punishType == 1 ? "全局禁言" : "永久封号";
        return "您因【" + reason + "】被执行" + typeDesc + "处罚，如有异议请提交申诉。";
    }
}
