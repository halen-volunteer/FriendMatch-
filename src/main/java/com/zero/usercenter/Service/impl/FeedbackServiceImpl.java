package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.FeedbackService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户反馈服务实现
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Resource private UserFeedbackMapper userFeedbackMapper;
    @Resource private SystemNoticeMapper systemNoticeMapper;
    @Resource private UserMapper userMapper;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;
    @Resource private UserPunishLogMapper userPunishLogMapper;
    @Resource private AdminAuthService adminAuthService;

    // ======================== 用户提交反馈 ========================

    /**
     * 提交用户反馈/申诉
     * feedbackType=3（处罚申诉）时需携带 punishLogId，最多申诉 3 次
     * 每日每用户最多提交 10 条（防刷）
     *
     * @param dto 反馈数据传输对象
     * @return feedbackId
     */
    @Override
    public Result submitFeedback(FeedbackSubmitDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (dto.getFeedbackType() == null || dto.getFeedbackType() < 1 || dto.getFeedbackType() > 4)
            return Result.fail("反馈类型无效");
        if (dto.getFeedbackContent() == null || dto.getFeedbackContent().isBlank())
            return Result.fail("反馈内容不能为空");
        if (dto.getFeedbackContent().length() > 2000)
            return Result.fail("反馈内容不能超过2000字符");

        // 处罚申诉专属校验（feedbackType=3）
        if (dto.getFeedbackType() == 3) {
            if (dto.getPunishLogId() == null) return Result.fail("申诉必须关联处罚记录ID（punishLogId）");
            UserPunishLog punishLog = userPunishLogMapper.selectById(dto.getPunishLogId());
            if (punishLog == null) return Result.fail("关联的处罚记录不存在");
            if (!punishLog.getPunishUserId().equals(userId)) return Result.fail("只能申诉自己的处罚记录");
            if (punishLog.getIsCancel() == 1) return Result.fail("该处罚已撤销，无需申诉");
            // 查该用户针对同一处罚记录的历史申诉次数
            LambdaQueryWrapper<UserFeedback> existQw = new LambdaQueryWrapper<>();
            existQw.eq(UserFeedback::getUserId, userId)
                   .eq(UserFeedback::getFeedbackType, 3)
                   .eq(UserFeedback::getIsDelete, 0)
                   .likeRight(UserFeedback::getFeedbackTitle, "申诉处罚#" + dto.getPunishLogId());
            long appealCount = userFeedbackMapper.selectCount(existQw);
            if (appealCount >= 3) return Result.fail("同一处罚最多申诉3次，已达上限");
        }

        // 防刷：同一用户每天最多提交 10 条反馈
        LambdaQueryWrapper<UserFeedback> countQw = new LambdaQueryWrapper<>();
        countQw.eq(UserFeedback::getUserId, userId)
               .eq(UserFeedback::getIsDelete, 0)
               .ge(UserFeedback::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        long todayCount = userFeedbackMapper.selectCount(countQw);
        if (todayCount >= 10) return Result.fail("今日反馈提交次数已达上限");

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setFeedbackType(dto.getFeedbackType());
        // 处罚申诉自动生成标题，便于后续按 punishLogId 查询申诉次数
        if (dto.getFeedbackType() == 3 && dto.getPunishLogId() != null) {
            feedback.setFeedbackTitle("申诉处罚#" + dto.getPunishLogId());
        } else {
            feedback.setFeedbackTitle(dto.getFeedbackTitle() != null ? dto.getFeedbackTitle().trim() : "");
        }
        feedback.setFeedbackContent(dto.getFeedbackContent().trim());
        feedback.setFeedbackAttachment(dto.getFeedbackAttachment() != null ? dto.getFeedbackAttachment() : "");
        feedback.setFeedbackImg(dto.getFeedbackImg() != null ? dto.getFeedbackImg() : "");
        feedback.setHandleStatus(0); // 待处理
        feedback.setIsDelete(0);
        userFeedbackMapper.insert(feedback);

        return Result.ok(feedback.getId());
    }

    // ======================== 用户查询自己的反馈 ========================

    /**
     * 查询当前用户的反馈列表（分页）
     * 过滤 is_delete=0，按创建时间降序
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @return 反馈列表及总数
     */
    @Override
    public Result getMyFeedbackList(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        LambdaQueryWrapper<UserFeedback> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFeedback::getUserId, userId)
          .eq(UserFeedback::getIsDelete, 0)
          .orderByDesc(UserFeedback::getCreateTime);
        Page<UserFeedback> pageObj = new Page<>(page, pageSize);
        Page<UserFeedback> result = userFeedbackMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 查询反馈详情
     * 普通用户只能查看自己提交的反馈，防止越权
     *
     * @param feedbackId 反馈记录ID
     * @return 反馈详情（含管理员回复 handleContent 和 handleTime）
     */
    @Override
    public Result getFeedbackDetail(Long feedbackId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (feedbackId == null) return Result.fail("反馈ID不能为空");

        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null || feedback.getIsDelete() == 1) return Result.fail("反馈不存在");
        // 普通用户只能查自己的
        if (!feedback.getUserId().equals(userId)) return Result.fail("无权限查看该反馈");
        return Result.ok(feedback);
    }

    // ======================== 管理员处理反馈 ========================

    /**
     * 管理员处理反馈
     * 更新处理状态（1-处理中，2-已解决，3-已驳回）和回复内容
     * 状态为 2/3 时异步发送 noticeType=8 系统通知给反馈用户
     *
     * @param dto 处理数据传输对象（feedbackId、handleStatus、handleContent）
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result handleFeedback(FeedbackHandleDTO dto) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);

        if (dto.getFeedbackId() == null) return Result.fail("反馈ID不能为空");
        if (dto.getHandleStatus() == null || dto.getHandleStatus() < 1 || dto.getHandleStatus() > 3)
            return Result.fail("处理状态无效");
        if (dto.getHandleContent() == null || dto.getHandleContent().isBlank())
            return Result.fail("处理内容不能为空");

        UserFeedback feedback = userFeedbackMapper.selectById(dto.getFeedbackId());
        if (feedback == null || feedback.getIsDelete() == 1) return Result.fail("反馈不存在");
        if (feedback.getHandleStatus() == 2 || feedback.getHandleStatus() == 3)
            return Result.fail("该反馈已处理完毕");

        LambdaUpdateWrapper<UserFeedback> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserFeedback::getId, dto.getFeedbackId())
          .set(UserFeedback::getHandleStatus, dto.getHandleStatus())
          .set(UserFeedback::getHandleContent, dto.getHandleContent())
          .set(UserFeedback::getHandleUserId, operatorId)
          .set(UserFeedback::getHandleTime, LocalDateTime.now());
        userFeedbackMapper.update(null, uw);

        // 异步发送系统通知给反馈用户，不阻塞主流程
        if (dto.getHandleStatus() == 2 || dto.getHandleStatus() == 3) {
            final Long feedbackUserId = feedback.getUserId();
            final Long feedbackRelatedId = feedback.getId();
            final String statusDesc = dto.getHandleStatus() == 2 ? "已解决" : "已驳回";
            final String handleContent = dto.getHandleContent();
            Thread.ofVirtual().start(() -> {
                SystemNotice notice = new SystemNotice();
                notice.setUserId(feedbackUserId);
                notice.setNoticeType(8);
                notice.setNoticeContent("您的反馈已被处理，状态：" + statusDesc + "。回复：" + handleContent);
                notice.setRelatedId(feedbackRelatedId);
                notice.setIsRead(0);
                notice.setIsDelete(0);
                sendRealtimeSystemNotice(notice);
            });
        }

        return Result.ok("处理成功");
    }

    /**
     * 管理员分页查询所有反馈
     * 支持按 handleStatus 过滤，优先展示待处理，同状态按时间降序
     *
     * @param handleStatus 处理状态（0-待处理，1-处理中，2-已解决，3-已驳回），null 查全部
     * @param page         页码
     * @param pageSize     每页数量
     * @return 反馈列表及总数
     */
    @Override
    public Result adminGetFeedbackList(Integer handleStatus, int page, int pageSize) {
        Long operatorId = UserHolder.getUserId();
        if (operatorId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(operatorId);
        LambdaQueryWrapper<UserFeedback> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFeedback::getIsDelete, 0);
        if (handleStatus != null) qw.eq(UserFeedback::getHandleStatus, handleStatus);
        qw.orderByAsc(UserFeedback::getHandleStatus).orderByDesc(UserFeedback::getCreateTime);
        Page<UserFeedback> pageObj = new Page<>(page, pageSize);
        Page<UserFeedback> result = userFeedbackMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 落库并实时推送系统通知
     * 先写入 t_system_notice，再通过 WebSocket 推送；用户离线时推送静默跳过，通知不丢失
     *
     * @param notice 已填充好字段的系统通知实体
     */
    private void sendRealtimeSystemNotice(SystemNotice notice) {
        systemNoticeMapper.insert(notice);

        java.util.Map<String, Object> push = new java.util.HashMap<>();
        push.put("type", "system_notice");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("relatedId", notice.getRelatedId());
        push.put("data", data);
        chatWebSocketHandler.sendToUser(notice.getUserId(), com.alibaba.fastjson2.JSON.toJSONString(push));
    }
}
