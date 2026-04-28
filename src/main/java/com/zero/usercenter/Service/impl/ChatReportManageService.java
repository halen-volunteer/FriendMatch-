package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.AiCheckService;
import com.zero.usercenter.Service.PunishService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.zero.usercenter.utils.Number.USER_PUNISH_KEY;

@Service
public class ChatReportManageService {
    @Resource private ChatSupportService chatSupportService;
    @Resource private ChatMessageMapper chatMessageMapper;
    @Resource private MessageReportMapper messageReportMapper;
    @Resource private AiCheckService aiCheckService;
    @Resource private PunishService punishService;
    @Resource private AdminAuthService adminAuthService;
    @Resource private UserPunishLogMapper userPunishLogMapper;
    @Resource private UserMapper userMapper;
    @Resource private UserViolationCountMapper userViolationCountMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public Result reportMsg(MessageReportDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getMessageId() == null) return Result.fail("消息ID不能为空");
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 6) return Result.fail("举报原因无效");
        ChatMessage msg = chatMessageMapper.selectById(dto.getMessageId());
        if (msg == null || msg.getIsDelete() == 1) return Result.fail("消息不存在或已被撤回，无法举报");
        if (msg.getSenderId().equals(userId)) return Result.fail("不能举报自己的消息");
        MessageReport report = new MessageReport();
        report.setMessageId(dto.getMessageId());
        report.setReporterId(userId);
        report.setReportReason(dto.getReportReason());
        report.setReportContent(dto.getReportContent() != null ? dto.getReportContent().trim() : "");
        report.setAiCheckResult(0);
        report.setAiConfidence(0);
        report.setAdminStatus(0);
        report.setAppealCount(0);
        report.setIsDelete(0);
        messageReportMapper.insert(report);
        Long reportId = report.getId();
        Long reporterId = report.getReporterId();
        String msgContent = msg.getMsgContent();
        Long msgSenderId = msg.getSenderId();
        Thread.ofVirtual().start(() -> handleAiAudit(reportId, reporterId, msg, msgContent, msgSenderId));
        return Result.ok(report.getId());
    }

    public Result getMsgReportStatus(Long reportId) {
        Long userId = chatSupportService.requireLogin();
        if (reportId == null) return Result.fail("举报ID不能为空");
        MessageReport report = messageReportMapper.selectById(reportId);
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (!userId.equals(report.getReporterId())) return Result.fail("无权查看该举报");
        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getId());
        data.put("reportReason", report.getReportReason());
        data.put("reportContent", report.getReportContent());
        data.put("aiCheckResult", report.getAiCheckResult());
        data.put("aiConfidence", report.getAiConfidence());
        data.put("adminStatus", report.getAdminStatus());
        data.put("adminAction", report.getAdminAction());
        data.put("adminNote", report.getAdminNote());
        data.put("reportTime", report.getCreateTime());
        return Result.ok(data);
    }

    public Result adminGetReportContext(Long reportId) {
        Long adminId = chatSupportService.requireLogin();
        adminAuthService.assertAdmin(adminId);
        if (reportId == null) return Result.fail("举报ID不能为空");
        MessageReport report = messageReportMapper.selectById(reportId);
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        ChatMessage targetMsg = chatMessageMapper.selectById(report.getMessageId());
        if (targetMsg == null) return Result.fail("被举报消息不存在");
        LambdaQueryWrapper<ChatMessage> beforeQw = new LambdaQueryWrapper<>();
        beforeQw.eq(ChatMessage::getConversationId, targetMsg.getConversationId()).eq(ChatMessage::getIsDelete, 0).lt(ChatMessage::getCreateTime, targetMsg.getCreateTime()).orderByDesc(ChatMessage::getCreateTime).last("LIMIT 50");
        List<ChatMessage> beforeMsgs = chatMessageMapper.selectList(beforeQw);
        Collections.reverse(beforeMsgs);
        LambdaQueryWrapper<ChatMessage> afterQw = new LambdaQueryWrapper<>();
        afterQw.eq(ChatMessage::getConversationId, targetMsg.getConversationId()).eq(ChatMessage::getIsDelete, 0).gt(ChatMessage::getCreateTime, targetMsg.getCreateTime()).orderByAsc(ChatMessage::getCreateTime).last("LIMIT 50");
        List<ChatMessage> afterMsgs = chatMessageMapper.selectList(afterQw);
        Map<String, Object> data = new HashMap<>();
        data.put("report", report);
        data.put("targetMsg", targetMsg);
        data.put("beforeMsgs", beforeMsgs);
        data.put("afterMsgs", afterMsgs);
        return Result.ok(data);
    }

    public Result adminGetMsgReportList(Integer adminStatus, int page, int pageSize) {
        Long adminId = chatSupportService.requireLogin();
        adminAuthService.assertAdmin(adminId);
        Page<MessageReport> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<MessageReport> qw = new LambdaQueryWrapper<>();
        qw.eq(MessageReport::getIsDelete, 0);
        if (adminStatus != null) qw.eq(MessageReport::getAdminStatus, adminStatus);
        qw.orderByAsc(MessageReport::getAdminStatus).orderByDesc(MessageReport::getCreateTime);
        Page<MessageReport> result = messageReportMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @Transactional
    public Result appealMsgReport(Long reportId) {
        Long userId = chatSupportService.requireLogin();
        if (reportId == null) return Result.fail("举报ID不能为空");
        MessageReport report = messageReportMapper.selectById(reportId);
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        ChatMessage msg = chatMessageMapper.selectById(report.getMessageId());
        boolean isReporter = report.getReporterId().equals(userId);
        boolean isSender = msg != null && msg.getSenderId().equals(userId);
        if (!isReporter && !isSender) return Result.fail("您无权对此举报提交申诉");
        int currentCount = report.getAppealCount() == null ? 0 : report.getAppealCount();
        if (currentCount >= 3) return Result.fail("申诉次数已达上限（最多3次），无法继续申诉");
        if (report.getAdminStatus() == 0) return Result.fail("举报正在处理中，请等待处理结果后再申诉");
        int newCount = currentCount + 1;
        LambdaUpdateWrapper<MessageReport> uw = new LambdaUpdateWrapper<>();
        uw.eq(MessageReport::getId, reportId).set(MessageReport::getAppealCount, newCount).set(MessageReport::getAppealRound, newCount).set(MessageReport::getAppealer, isReporter ? "reporter" : "sender").set(MessageReport::getAdminStatus, 0);
        messageReportMapper.update(null, uw);
        return Result.ok("申诉已提交，管理员将尽快处理（第" + newCount + "/3次申诉）");
    }

    @Transactional
    public Result adminHandleMsgReport(MessageReportHandleDTO dto) {
        Long adminId = chatSupportService.requireLogin();
        adminAuthService.assertAdmin(adminId);
        if (dto.getReportId() == null) return Result.fail("举报ID不能为空");
        if (dto.getAdminDecision() == null || dto.getAdminDecision() < 1 || dto.getAdminDecision() > 3) return Result.fail("处理决定无效（1-维持处罚，2-撤销处罚，3-确认违规执行处罚）");
        MessageReport report = messageReportMapper.selectById(dto.getReportId());
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (report.getAdminStatus() != 0) return Result.fail("该举报当前无需处理");
        ChatMessage msg = chatMessageMapper.selectById(report.getMessageId());
        Long senderId = msg != null ? msg.getSenderId() : null;
        Long reporterId = report.getReporterId();
        int appealCount = report.getAppealCount() == null ? 0 : report.getAppealCount();
        int confidence = report.getAiConfidence() == null ? 0 : report.getAiConfidence();
        String appealer = report.getAppealer();
        String note = dto.getAdminNote() != null ? dto.getAdminNote().trim() : "";
        int adminLevel = (appealCount == 0 && confidence == 50) ? 1 : Math.max(appealCount, 1);
        String adminDesc = "管理员" + adminLevel;
        int remaining = 3 - appealCount;
        LambdaUpdateWrapper<MessageReport> uw = new LambdaUpdateWrapper<>();
        uw.eq(MessageReport::getId, dto.getReportId()).set(MessageReport::getAdminStatus, 1).set(MessageReport::getAdminNote, note);
        if (confidence == 50) uw.set(MessageReport::getAiConfidence, 75);
        if (dto.getAdminDecision() == 1) {
            uw.set(MessageReport::getAdminAction, "维持处罚");
            messageReportMapper.update(null, uw);
            Long notifyId = "reporter".equals(appealer) ? reporterId : senderId;
            if (notifyId != null) {
                String suffix = remaining > 0 ? "如有异议，可继续申诉（剩余" + remaining + "次）。" : "申诉次数已用完，无法继续申诉。";
                chatSupportService.sendReportNotice(notifyId, report.getId(), adminDesc + "审核后维持原处罚决定" + (note.isBlank() ? "" : "（" + note + "）") + "。" + suffix);
            }
        } else if (dto.getAdminDecision() == 2) {
            uw.set(MessageReport::getAdminAction, "撤销处罚");
            messageReportMapper.update(null, uw);
            if (senderId != null) rollbackPunish(adminId, senderId);
            if (senderId != null) chatSupportService.sendReportNotice(senderId, report.getId(), adminDesc + "审核后认定您的消息不违规，处罚已撤销，违规记录已扣除" + (note.isBlank() ? "" : "（" + note + "）") + "。");
            String suffix = remaining > 0 ? "如有异议，可继续申诉（剩余" + remaining + "次）。" : "申诉次数已用完。";
            chatSupportService.sendReportNotice(reporterId, report.getId(), adminDesc + "审核后认定被举报消息不违规，举报已驳回" + (note.isBlank() ? "" : "（" + note + "）") + "。" + suffix);
        } else {
            uw.set(MessageReport::getAdminAction, "确认违规");
            messageReportMapper.update(null, uw);
            if (senderId != null && msg.getIsDelete() == 0) {
                PunishDTO punishDTO = new PunishDTO();
                punishDTO.setPunishUserId(senderId);
                punishDTO.setPunishReason(adminDesc + "确认违规" + (note.isBlank() ? "" : "：" + note));
                punishDTO.setMsgId(msg.getId());
                punishDTO.setAiAuditResult(adminDesc + "确认违规，执行处罚");
                punishDTO.setOperateType(2);
                try { punishService.punishUser(punishDTO); } catch (Exception ignored) {}
                String suffix = remaining > 0 ? "如有异议，可继续申诉（剩余" + remaining + "次）。" : "申诉次数已用完。";
                chatSupportService.sendReportNotice(senderId, report.getId(), adminDesc + "审核确认您的消息违规，系统已对您执行处罚" + (note.isBlank() ? "" : "（" + note + "）") + "。" + suffix);
                chatSupportService.sendReportNotice(reporterId, report.getId(), adminDesc + "审核确认被举报消息违规，已对违规用户执行处罚" + (note.isBlank() ? "" : "（" + note + "）") + "。");
            }
        }
        return Result.ok("处理成功");
    }

    private void handleAiAudit(Long reportId, Long reporterId, ChatMessage msg, String msgContent, Long msgSenderId) {
        int aiResult = aiCheckService.checkContent(msgContent);
        boolean aiViolation;
        int confidence;
        boolean aiUncertain = false;
        if (aiResult == 1) {
            aiViolation = true;
            confidence = 90;
        } else if (aiResult == 0) {
            aiViolation = false;
            confidence = 20;
        } else {
            aiViolation = false;
            confidence = 50;
            aiUncertain = true;
        }
        LambdaUpdateWrapper<MessageReport> reportUw = new LambdaUpdateWrapper<>();
        reportUw.eq(MessageReport::getId, reportId).set(MessageReport::getAiCheckResult, aiUncertain ? 0 : (aiViolation ? 1 : 2)).set(MessageReport::getAiCheckTime, LocalDateTime.now()).set(MessageReport::getAiConfidence, confidence);
        if (aiUncertain || aiViolation) reportUw.set(MessageReport::getAdminStatus, 0);
        messageReportMapper.update(null, reportUw);
        if (aiViolation) {
            PunishDTO punishDTO = new PunishDTO();
            punishDTO.setPunishUserId(msgSenderId);
            punishDTO.setPunishReason("AI自动审核：消息内容违规（置信度" + confidence + "%）");
            punishDTO.setMsgId(msg.getId());
            punishDTO.setAiAuditResult("AI自动处罚，ai_check_result=1，confidence=" + confidence);
            punishDTO.setOperateType(1);
            try { punishService.punishUser(punishDTO); } catch (Exception ignored) {}
            chatSupportService.sendReportNotice(reporterId, reportId, "您举报的消息经AI审核认定违规（置信度" + confidence + "%），系统已对违规用户执行处罚。");
            chatSupportService.sendReportNotice(msgSenderId, reportId, "您发送的消息经AI审核认定违规（置信度" + confidence + "%），系统已对您执行处罚。如有异议，可提交申诉。");
        } else if (aiUncertain) {
            chatSupportService.sendReportNotice(reporterId, reportId, "您举报的消息AI无法自动判断，已转交管理员人工审核，请耐心等待。");
        } else {
            chatSupportService.sendReportNotice(reporterId, reportId, "您举报的消息经AI审核未发现违规（置信度" + confidence + "%）。如有异议，可提交申诉，将由管理员人工复核。");
        }
    }

    private void rollbackPunish(Long adminId, Long senderId) {
        LambdaQueryWrapper<UserPunishLog> plQw = new LambdaQueryWrapper<>();
        plQw.eq(UserPunishLog::getPunishUserId, senderId).eq(UserPunishLog::getIsCancel, 0).orderByDesc(UserPunishLog::getId).last("LIMIT 1");
        UserPunishLog punishLog = userPunishLogMapper.selectOne(plQw);
        if (punishLog == null) return;
        LambdaUpdateWrapper<UserPunishLog> plUw = new LambdaUpdateWrapper<>();
        plUw.eq(UserPunishLog::getId, punishLog.getId()).set(UserPunishLog::getIsCancel, 1).set(UserPunishLog::getCancelTime, LocalDateTime.now()).set(UserPunishLog::getCancelUserId, adminId);
        userPunishLogMapper.update(null, plUw);
        LambdaUpdateWrapper<User> userUw = new LambdaUpdateWrapper<>();
        userUw.eq(User::getId, senderId).set(User::getGlobalPunishType, 0).set(User::getGlobalUnpunishTime, null);
        userMapper.update(null, userUw);
        stringRedisTemplate.delete(USER_PUNISH_KEY + senderId);
        LambdaQueryWrapper<UserViolationCount> vcQw = new LambdaQueryWrapper<>();
        vcQw.eq(UserViolationCount::getUserId, senderId);
        UserViolationCount vc = userViolationCountMapper.selectOne(vcQw);
        if (vc != null && vc.getTotalViolationNum() > 0) {
            LambdaUpdateWrapper<UserViolationCount> vcUw = new LambdaUpdateWrapper<>();
            vcUw.eq(UserViolationCount::getUserId, senderId).set(UserViolationCount::getTotalViolationNum, vc.getTotalViolationNum() - 1);
            userViolationCountMapper.update(null, vcUw);
        }
    }
}
