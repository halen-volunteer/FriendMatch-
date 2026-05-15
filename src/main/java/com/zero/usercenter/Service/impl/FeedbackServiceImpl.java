package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.FeedbackService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.mq.AsyncMessageService;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户反馈服务实现。
 * 当前反馈中心主要承载普通反馈，同时兼容历史处罚申诉型反馈数据。
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Resource private UserFeedbackMapper userFeedbackMapper;
    @Resource private UserMapper userMapper;
    @Resource private UserPunishLogMapper userPunishLogMapper;
    @Resource private AdminAuthService adminAuthService;
    @Resource private AsyncMessageService asyncMessageService;

    // ======================== 用户提交反馈 ========================

    /**
     * 提交用户反馈。
     * 其中 `feedbackType=3` 仍兼容历史处罚申诉型反馈，会附带处罚记录校验和次数限制。
     */
    @Override
    public Result submitFeedback(FeedbackSubmitDTO dto) {
        // 1. 先拿登录用户并校验基础参数，避免脏数据进入反馈中心。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        if (dto.getFeedbackType() == null || dto.getFeedbackType() < 1 || dto.getFeedbackType() > 4)
            return Result.fail("反馈类型无效");
        if (dto.getFeedbackContent() == null || dto.getFeedbackContent().isBlank())
            return Result.fail("反馈内容不能为空");
        if (dto.getFeedbackContent().length() > 2000)
            return Result.fail("反馈内容不能超过2000字符");

        // 2. 处罚申诉专属校验（feedbackType=3）。
        // 这类反馈不是普通建议，而是和具体处罚记录绑定的申诉入口。
        if (dto.getFeedbackType() == 3) {
            if (dto.getPunishLogId() == null) return Result.fail("申诉必须关联处罚记录ID（punishLogId）");
            UserPunishLog punishLog = userPunishLogMapper.selectById(dto.getPunishLogId());
            if (punishLog == null) return Result.fail("关联的处罚记录不存在");
            if (!punishLog.getPunishUserId().equals(userId)) return Result.fail("只能申诉自己的处罚记录");
            if (punishLog.getIsCancel() == 1) return Result.fail("该处罚已撤销，无需申诉");
            // 同一处罚只允许有限次数申诉，防止重复刷单式提交。
            LambdaQueryWrapper<UserFeedback> existQw = new LambdaQueryWrapper<>();
            existQw.eq(UserFeedback::getUserId, userId)
                   .eq(UserFeedback::getFeedbackType, 3)
                   .eq(UserFeedback::getIsDelete, 0)
                   .likeRight(UserFeedback::getFeedbackTitle, "申诉处罚#" + dto.getPunishLogId());
            long appealCount = userFeedbackMapper.selectCount(existQw);
            if (appealCount >= 3) return Result.fail("同一处罚最多申诉3次，已达上限");
        }

        // 3. 防刷：同一用户每天最多提交 10 条反馈。
        LambdaQueryWrapper<UserFeedback> countQw = new LambdaQueryWrapper<>();
        countQw.eq(UserFeedback::getUserId, userId)
               .eq(UserFeedback::getIsDelete, 0)
               .ge(UserFeedback::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay());
        long todayCount = userFeedbackMapper.selectCount(countQw);
        if (todayCount >= 10) return Result.fail("今日反馈提交次数已达上限");

        // 4. 组装反馈实体并落库，后续后台处理和通知回推都围绕这条记录展开。
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setFeedbackType(dto.getFeedbackType());
        // 处罚申诉自动生成标题，便于后续按 punishLogId 查询申诉次数。
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
     * 查询当前用户的反馈列表。
     */
    @Override
    public Result getMyFeedbackList(int page, int pageSize) {
        // 1. 当前用户只能看自己的反馈历史，所以先取登录态，再按用户维度分页查询。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 2. 反馈列表统一按创建时间倒序，方便优先查看最近提交内容。
        LambdaQueryWrapper<UserFeedback> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFeedback::getUserId, userId)
          .eq(UserFeedback::getIsDelete, 0)
          .orderByDesc(UserFeedback::getCreateTime);
        Page<UserFeedback> pageObj = new Page<>(page, pageSize);
        Page<UserFeedback> result = userFeedbackMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 查询反馈详情。
     * 普通用户只能查看自己提交的反馈，避免越权读取。
     */
    @Override
    public Result getFeedbackDetail(Long feedbackId) {
        // 1. 详情页必须做“本人可见”控制，避免普通用户越权查看别人的反馈内容。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (feedbackId == null) return Result.fail("反馈ID不能为空");

        UserFeedback feedback = userFeedbackMapper.selectById(feedbackId);
        if (feedback == null || feedback.getIsDelete() == 1) return Result.fail("反馈不存在");
        // 2. 普通用户只能查自己的反馈记录。
        if (!feedback.getUserId().equals(userId)) return Result.fail("无权限查看该反馈");
        return Result.ok(feedback);
    }

    // ======================== 管理员处理反馈 ========================

    /**
     * 管理员处理反馈。
     * 处理完成后会异步发送系统通知给反馈提交人。
     */
    @Override
    @Transactional
    @RequireAdmin
    public Result handleFeedback(FeedbackHandleDTO dto) {
        // 1. 管理员处理前先校验入参，确保目标反馈、处理状态和回复内容都齐全。
        Long operatorId = UserHolder.getUserId();

        if (dto.getFeedbackId() == null) return Result.fail("反馈ID不能为空");
        if (dto.getHandleStatus() == null || dto.getHandleStatus() < 1 || dto.getHandleStatus() > 3)
            return Result.fail("处理状态无效");
        if (dto.getHandleContent() == null || dto.getHandleContent().isBlank())
            return Result.fail("处理内容不能为空");

        UserFeedback feedback = userFeedbackMapper.selectById(dto.getFeedbackId());
        if (feedback == null || feedback.getIsDelete() == 1) return Result.fail("反馈不存在");
        if (feedback.getHandleStatus() == 2 || feedback.getHandleStatus() == 3)
            return Result.fail("该反馈已处理完毕");

        // 2. 更新处理状态、处理人和处理时间，这些字段决定后台展示和用户端结果。
        LambdaUpdateWrapper<UserFeedback> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserFeedback::getId, dto.getFeedbackId())
          .set(UserFeedback::getHandleStatus, dto.getHandleStatus())
          .set(UserFeedback::getHandleContent, dto.getHandleContent())
          .set(UserFeedback::getHandleUserId, operatorId)
          .set(UserFeedback::getHandleTime, LocalDateTime.now());
        userFeedbackMapper.update(null, uw);

        // 3. 处理完成后异步发送系统通知给反馈用户，不阻塞主流程。
        if (dto.getHandleStatus() == 2 || dto.getHandleStatus() == 3) {
            final Long feedbackUserId = feedback.getUserId();
            final Long feedbackRelatedId = feedback.getId();
            final String statusDesc = dto.getHandleStatus() == 2 ? "已解决" : "已驳回";
            final String handleContent = dto.getHandleContent();
            Thread.ofVirtual().start(() -> {
                asyncMessageService.sendSystemNotice(
                        feedbackUserId,
                        8,
                        "您的反馈已被处理，状态：" + statusDesc + "。回复：" + handleContent,
                        feedbackRelatedId);
            });
        }

        return Result.ok("处理成功");
    }

    /**
     * 管理员分页查询反馈列表。
     * 支持按处理状态筛选，并优先展示待处理数据。
     */
    @Override
    @RequireAdmin
    public Result adminGetFeedbackList(Integer handleStatus, int page, int pageSize) {
        // 1. 管理端列表优先按状态排序，再按创建时间倒序，方便先处理待办。
        LambdaQueryWrapper<UserFeedback> qw = new LambdaQueryWrapper<>();
        qw.eq(UserFeedback::getIsDelete, 0);
        if (handleStatus != null) qw.eq(UserFeedback::getHandleStatus, handleStatus);
        qw.orderByAsc(UserFeedback::getHandleStatus).orderByDesc(UserFeedback::getCreateTime);
        Page<UserFeedback> pageObj = new Page<>(page, pageSize);
        Page<UserFeedback> result = userFeedbackMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

}
