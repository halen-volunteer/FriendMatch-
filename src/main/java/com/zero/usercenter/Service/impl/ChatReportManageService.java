package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.MessageReportDTO;
import com.zero.usercenter.DTO.MessageReportHandleDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.AppealMapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.PunishMsgRelationMapper;
import com.zero.usercenter.Mapper.ReportCaseMapper;
import com.zero.usercenter.Mapper.ReportDetailMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.Appeal;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.PunishMsgRelation;
import com.zero.usercenter.Model.ReportCase;
import com.zero.usercenter.Model.ReportDetail;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Service.AiCheckService;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ChatReportManageService {

    private static final int REPORT_TYPE_MESSAGE = 2;
    private static final int CASE_STATUS_PROCESSING = 0;
    private static final int CASE_STATUS_FINISHED = 1;
    private static final int ADMIN_STATUS_PENDING = 0;
    private static final int ADMIN_STATUS_APPROVED = 1;
    private static final int ADMIN_STATUS_REJECTED = 2;
    private static final int ADMIN_ACTION_CONFIRM_VIOLATION = 1;
    private static final int ADMIN_ACTION_CONFIRM_NORMAL = 2;
    private static final int MAX_APPEAL_COUNT_AI = 3;
    private static final int MAX_APPEAL_COUNT_MANUAL = 2;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ReportCaseMapper reportCaseMapper;

    @Resource
    private ReportDetailMapper reportDetailMapper;

    @Resource
    private AppealMapper appealMapper;

    @Resource
    private PunishMsgRelationMapper punishMsgRelationMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private AiCheckService aiCheckService;

    @Resource
    private PunishService punishService;

    @Resource
    private ReportAdminAssignSupport reportAdminAssignSupport;

    @Transactional
    public Result reportMsg(MessageReportDTO dto) {
        // 1. 先确认登录、消息存在以及举报参数合法。
        Long userId = chatSupportService.requireLogin();
        log.info("[report-message] start reporterId={}, messageId={}, reason={}",
                userId,
                dto == null ? null : dto.getMessageId(),
                dto == null ? null : dto.getReportReason());
        if (dto == null || dto.getMessageId() == null) {
            log.warn("[report-message] reject because messageId is null, reporterId={}", userId);
            return Result.fail("消息 ID 不能为空");
        }
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 6) {
            log.warn("[report-message] reject because reason invalid, reporterId={}, messageId={}, reason={}",
                    userId, dto.getMessageId(), dto.getReportReason());
            return Result.fail("举报原因无效");
        }

        ChatMessage message = chatMessageMapper.selectById(dto.getMessageId());
        if (message == null || Integer.valueOf(1).equals(message.getIsDelete())) {
            log.warn("[report-message] reject because target message missing, reporterId={}, messageId={}",
                    userId, dto.getMessageId());
            return Result.fail("被举报消息不存在");
        }
        if (userId.equals(message.getSenderId())) {
            log.warn("[report-message] reject because reporter equals sender, reporterId={}, messageId={}",
                    userId, dto.getMessageId());
            return Result.fail("不能举报自己发送的消息");
        }

        String reportContent = trimToEmpty(dto.getReportContent());
        ReportCase reportCase = findActiveCase(REPORT_TYPE_MESSAGE, dto.getMessageId());
        if (reportCase != null && existsReporterDetail(reportCase.getId(), userId)) {
            log.warn("[report-message] reject because duplicate detail exists, reporterId={}, caseId={}, messageId={}",
                    userId, reportCase.getId(), dto.getMessageId());
            return Result.fail("您已经举报过这条消息，请勿重复提交");
        }

        // 2. 消息举报也复用案卷：首次创建案卷，后续举报只追加明细。
        // 首轮统一先走 AI 审核，不在这里提前分配管理员，避免 AI 自动结案的工单占用人工处理池。
        boolean created = false;
        if (reportCase == null) {
            reportCase = createCase(REPORT_TYPE_MESSAGE, dto.getMessageId(), reportContent);
            created = true;
        } else {
            refreshCaseOnNewDetail(reportCase, reportContent);
        }

        ReportDetail detail = new ReportDetail();
        detail.setCaseId(reportCase.getId());
        detail.setReportType(REPORT_TYPE_MESSAGE);
        detail.setTargetId(dto.getMessageId());
        detail.setReporterId(userId);
        detail.setReportReason(dto.getReportReason());
        detail.setReportContent(reportContent);
        detail.setReportEvidence("");
        detail.setIsDelete(0);
        reportDetailMapper.insert(detail);
        log.info("[report-message] detail persisted reporterId={}, caseId={}, detailId={}, messageId={}",
                userId, reportCase.getId(), detail.getId(), dto.getMessageId());

        // 3. 仅首次创建案卷时异步触发 AI 初审，避免对同一消息重复跑模型。
        if (created) {
            Long caseId = reportCase.getId();
            Long messageId = dto.getMessageId();
            runAfterTransactionCommit(() -> {
                log.info("Message report created, start AI audit asynchronously. caseId={}, messageId={}", caseId, messageId);
                Thread.ofVirtual().start(() -> handleAiAudit(caseId, messageId));
            });
        }

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("detailId", chatSupportService.stringifyId(detail.getId()));
        data.put("message", "举报提交成功，已进入处理流程");
        return Result.ok(data);
    }

    public Result getMsgReportStatus(Long reportId) {
        // 1. 只有举报人和消息发送者本人可以查看这条消息举报状态。
        Long userId = chatSupportService.requireLogin();
        if (reportId == null) {
            return Result.fail("举报 ID 不能为空");
        }

        ReportCase reportCase = loadMessageCase(reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }

        ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
        boolean reporter = existsReporterDetail(reportCase.getId(), userId);
        boolean sender = message != null && userId.equals(message.getSenderId());
        if (!reporter && !sender) {
            return Result.fail("无权查看该举报记录");
        }

        // 2. 返回状态时顺带附上 AI 初审结果、管理员结论和可申诉信息。
        ReportDetail latestDetail = findLatestDetail(reportCase.getId());
        int appealCount = getAppealCount(REPORT_TYPE_MESSAGE, reportCase.getId());
        boolean pendingAppeal = hasPendingAppeal(REPORT_TYPE_MESSAGE, reportCase.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("messageId", chatSupportService.stringifyId(reportCase.getTargetId()));
        data.put("reportReason", latestDetail == null ? null : latestDetail.getReportReason());
        data.put("reportContent", latestDetail == null ? "" : latestDetail.getReportContent());
        data.put("aiCheckResult", reportCase.getAiCheckResult());
        data.put("aiConfidence", reportCase.getAiConfidence());
        data.put("adminStatus", reportCase.getAdminStatus());
        data.put("handleStatus", reportCase.getAdminStatus());
        data.put("handleStatusText", buildDisplayStatus(reportCase, pendingAppeal));
        data.put("adminAction", reportCase.getAdminAction());
        data.put("adminNote", reportCase.getAdminNote());
        data.put("appealCount", appealCount);
        data.put("hasPendingAppeal", pendingAppeal);
        data.put("canAppeal", canAppeal(reportCase, appealCount, pendingAppeal, reporter ? "reporter" : "reported"));
        data.put("viewRole", reporter ? "reporter" : "reported");
        data.put("viewRoleText", reporter ? "我发起的举报" : "我作为被举报方");
        data.put("createTime", latestDetail == null ? reportCase.getCreateTime() : latestDetail.getCreateTime());
        data.put("updateTime", reportCase.getUpdateTime());
        data.put("reportCount", reportCase.getReportCount());
        return Result.ok(data);
    }

    @RequireAdmin
    public Result adminGetReportContext(Long reportId) {
        // 1. 后台查看上下文前，先确认案卷归当前管理员处理。
        Long adminId = chatSupportService.requireLogin();
        if (reportId == null) {
            return Result.fail("举报 ID 不能为空");
        }

        ReportCase reportCase = loadMessageCase(reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }
        if (!canAccessMessageReportContext(adminId, reportCase)) {
            return Result.fail("当前举报不属于您处理");
        }

        ChatMessage targetMsg = chatMessageMapper.selectById(reportCase.getTargetId());
        if (targetMsg == null) {
            return Result.fail("被举报消息不存在");
        }

        Map<String, Object> report = buildAdminCaseView(reportCase);
        List<ReportDetail> details = listCaseDetails(reportCase.getId());

        // 2. 上下文里同时返回举报详情、目标消息和前后消息片段，帮助管理员判断是否违规。
        Map<String, Object> data = new HashMap<>();
        data.put("report", report);
        data.put("reportCase", reportCase);
        data.put("reportDetails", details);
        data.put("targetMsg", buildMessageContextItem(targetMsg));
        data.put("beforeMsgs", queryContextMessages(targetMsg, true));
        data.put("afterMsgs", queryContextMessages(targetMsg, false));
        return Result.ok(data);
    }

    @RequireAdmin
    public Result adminGetMsgReportList(Integer adminStatus, int page, int pageSize) {
        // 管理员列表只看分配给自己的消息举报案卷。
        Long adminId = chatSupportService.requireLogin();

        Page<ReportCase> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, REPORT_TYPE_MESSAGE)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getAdminId, adminId);
        if (adminStatus != null) {
            query.eq(ReportCase::getAdminStatus, adminStatus);
        }
        query.orderByAsc(ReportCase::getAdminStatus)
                .orderByDesc(ReportCase::getLatestReportTime)
                .orderByDesc(ReportCase::getId);

        Page<ReportCase> result = reportCaseMapper.selectPage(pageObj, query);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ReportCase reportCase : result.getRecords()) {
            list.add(buildAdminCaseView(reportCase));
        }
        return Result.ok(list, result.getTotal());
    }

    @Transactional
    @RequireAdmin
    public Result adminHandleMsgReport(MessageReportHandleDTO dto) {
        // 1. 处理前先确认案卷存在、仍待处理，且归当前管理员负责。
        Long adminId = chatSupportService.requireLogin();
        log.info("[report-message-admin] start adminId={}, reportId={}, decision={}",
                adminId,
                dto == null ? null : dto.getReportId(),
                dto == null ? null : dto.getAdminDecision());
        if (dto == null || dto.getReportId() == null) {
            log.warn("[report-message-admin] reject because reportId is null, adminId={}", adminId);
            return Result.fail("举报 ID 不能为空");
        }
        if (dto.getAdminDecision() == null || (dto.getAdminDecision() != 1 && dto.getAdminDecision() != 2)) {
            log.warn("[report-message-admin] reject because decision invalid, adminId={}, reportId={}, decision={}",
                    adminId, dto.getReportId(), dto.getAdminDecision());
            return Result.fail("处理动作无效");
        }

        ReportCase reportCase = loadMessageCase(dto.getReportId());
        if (reportCase == null) {
            log.warn("[report-message-admin] reject because report missing, adminId={}, reportId={}", adminId, dto.getReportId());
            return Result.fail("举报记录不存在");
        }
        if (!reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            log.warn("[report-message-admin] reject because report not assigned, adminId={}, reportId={}, assignedAdminId={}",
                    adminId, dto.getReportId(), reportCase.getAdminId());
            return Result.fail("当前举报不属于您处理");
        }
        if (!Integer.valueOf(ADMIN_STATUS_PENDING).equals(reportCase.getAdminStatus())) {
            log.warn("[report-message-admin] reject because report already handled, adminId={}, reportId={}, adminStatus={}",
                    adminId, dto.getReportId(), reportCase.getAdminStatus());
            return Result.fail("该举报已处理");
        }

        ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
        if (message == null) {
            log.warn("[report-message-admin] reject because target message missing, adminId={}, reportId={}, messageId={}",
                    adminId, dto.getReportId(), reportCase.getTargetId());
            return Result.fail("被举报消息不存在");
        }

        String note = trimToEmpty(dto.getAdminNote());
        reportCase.setCaseStatus(CASE_STATUS_FINISHED);
        reportCase.setProcessTime(LocalDateTime.now());
        reportCase.setAdminNote(note);
        reportCase.setAdminId(adminId);

        // 2. 举报成立时处罚消息发送者；不成立时仅更新状态并通知相关参与方。
        if (dto.getAdminDecision() == 1) {
            reportCase.setAdminStatus(ADMIN_STATUS_APPROVED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            log.info("[report-message-admin] approve report, adminId={}, reportId={}, messageId={}, senderId={}",
                    adminId, dto.getReportId(), message.getId(), message.getSenderId());
            punishSenderByMessageReport(message, reportCase, note);
            notifyMessageReportParticipants(reportCase, true, note);
        } else {
            reportCase.setAdminStatus(ADMIN_STATUS_REJECTED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            log.info("[report-message-admin] reject report, adminId={}, reportId={}, messageId={}",
                    adminId, dto.getReportId(), message.getId());
            notifyMessageReportParticipants(reportCase, false, note);
        }

        reportCaseMapper.updateById(reportCase);
        log.info("[report-message-admin] completed adminId={}, reportId={}, finalStatus={}, finalAction={}",
                adminId, dto.getReportId(), reportCase.getAdminStatus(), reportCase.getAdminAction());
        return Result.ok("处理成功");
    }

    private void handleAiAudit(Long caseId, Long messageId) {
        log.info("[report-message-ai] start caseId={}, messageId={}", caseId, messageId);
        ReportCase reportCase = reportCaseMapper.selectById(caseId);
        if (reportCase == null
                || Integer.valueOf(1).equals(reportCase.getIsDelete())
                || !Integer.valueOf(ADMIN_STATUS_PENDING).equals(reportCase.getAdminStatus())) {
            log.warn("Skip AI audit because report case is unavailable or already finished. caseId={}, messageId={}", caseId, messageId);
            return;
        }

        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || Integer.valueOf(1).equals(message.getIsDelete())) {
            log.warn("Skip AI audit because message is unavailable. caseId={}, messageId={}", caseId, messageId);
            return;
        }

        String auditText = chatSupportService.extractAuditText(message.getMsgType(), message.getMsgContent());
        if (!chatSupportService.supportsTextAudit(message.getMsgType(), message.getMsgContent())) {
            log.info("AI audit fallback because message does not support text audit. caseId={}, messageId={}, msgType={}",
                    caseId, messageId, message.getMsgType());
            notifyMessageReportAiFallback(reportCase);
            return;
        }

        // AI 只负责给出“可自动确认违规 / 可自动确认正常 / 不确定”三类结论。
        // 一旦工单在 AI 判定期间已经被人工处理，这里会在后续再次读取主单状态后直接退出，避免重复结案。
        int aiResult = aiCheckService.checkContent(auditText);
        log.info("[report-message-ai] audit result caseId={}, messageId={}, aiResult={}", caseId, messageId, aiResult);
        if (aiResult == 1) {
            ReportCase latest = reportCaseMapper.selectById(caseId);
            if (latest == null || !Integer.valueOf(ADMIN_STATUS_PENDING).equals(latest.getAdminStatus())) {
                return;
            }
            latest.setAiCheckResult(1);
            latest.setAiConfidence(90);
            latest.setAdminStatus(ADMIN_STATUS_APPROVED);
            latest.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            latest.setCaseStatus(CASE_STATUS_FINISHED);
            latest.setAdminNote("AI 初筛判定违规，系统已自动处罚");
            latest.setProcessTime(LocalDateTime.now());
            reportCaseMapper.updateById(latest);

            punishSenderByMessageReport(message, latest, "AI 初筛判定违规");
            notifyMessageReportParticipants(latest, true, "AI 初筛判定违规，系统已自动处罚");
            log.info("Message report AI audit finished as violation. caseId={}, messageId={}", caseId, messageId);
            return;
        }

        if (aiResult == 0) {
            ReportCase latest = reportCaseMapper.selectById(caseId);
            if (latest == null || !Integer.valueOf(ADMIN_STATUS_PENDING).equals(latest.getAdminStatus())) {
                return;
            }
            latest.setAiCheckResult(2);
            latest.setAiConfidence(90);
            latest.setAdminStatus(ADMIN_STATUS_REJECTED);
            latest.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            latest.setCaseStatus(CASE_STATUS_FINISHED);
            latest.setAdminNote("AI 初筛判定未违规");
            latest.setProcessTime(LocalDateTime.now());
            reportCaseMapper.updateById(latest);

            notifyMessageReportParticipants(latest, false, "AI 初筛判定未违规");
            log.info("Message report AI audit finished as normal. caseId={}, messageId={}", caseId, messageId);
            return;
        }

        log.info("Message report AI audit returned uncertain result, fallback to manual review. caseId={}, messageId={}, aiResult={}",
                caseId, messageId, aiResult);
        notifyMessageReportAiFallback(reportCase);
    }

    private void notifyMessageReportAiFallback(ReportCase reportCase) {
        ReportCase latest = reportCaseMapper.selectById(reportCase.getId());
        if (latest == null) {
            return;
        }
        // 回退到人工时，不直接改 adminStatus，保持主单继续处于待处理。
        // 这里额外补上管理员分配，让真正需要人工介入的消息举报才进入管理员工作台。
        latest.setAiCheckResult(0);
        latest.setAiConfidence(0);
        latest.setAdminNote("AI 无法自动判断，已转人工审核");
        Long assignedAdminId = latest.getAdminId();
        if (assignedAdminId == null) {
            assignedAdminId = reportAdminAssignSupport.allocateAdmin(latest.getId(), REPORT_TYPE_MESSAGE);
            latest.setAdminId(assignedAdminId);
        }
        reportCaseMapper.updateById(latest);

        String noticeText = assignedAdminId == null
                ? "您举报的消息 AI 无法自动判断，已进入人工审核队列，请稍后查看处理进度。"
                : "您举报的消息 AI 无法自动判断，已转交管理员人工审核，请耐心等待。";
        for (Long reporterId : loadReporterIds(latest.getId())) {
            chatSupportService.sendReportNotice(
                    reporterId,
                    latest.getId(),
                    noticeText
            );
        }
        log.info("Message report fallback notice sent to reporters. caseId={}, adminId={}", latest.getId(), assignedAdminId);
    }

    private void runAfterTransactionCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 举报明细和主单需要先提交成功，后面的 AI 审核或异步通知才有稳定的数据基础。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void punishSenderByMessageReport(ChatMessage message, ReportCase reportCase, String reason) {
        log.info("[report-message-punish] punish sender by message report, messageId={}, senderId={}, caseId={}, aiResult={}, reason={}",
                message == null ? null : message.getId(),
                message == null ? null : message.getSenderId(),
                reportCase == null ? null : reportCase.getId(),
                reportCase == null ? null : reportCase.getAiCheckResult(),
                reason);
        PunishDTO punishDTO = new PunishDTO();
        punishDTO.setPunishUserId(message.getSenderId());
        punishDTO.setPunishReason(reason == null || reason.isBlank() ? "消息举报确认违规" : reason);
        punishDTO.setMsgId(message.getId());
        punishDTO.setAiAuditResult(reportCase.getAiCheckResult() != null && reportCase.getAiCheckResult() == 1 ? "AI 判定违规" : "管理员确认违规");
        punishDTO.setOperateType(reportCase.getAiCheckResult() != null && reportCase.getAiCheckResult() == 1 ? 1 : 2);
        punishService.punishUser(punishDTO);
    }

    private void notifyMessageReportParticipants(ReportCase reportCase, boolean violation, String note) {
        log.info("[report-message-notify] notify participants caseId={}, messageId={}, violation={}, note={}",
                reportCase == null ? null : reportCase.getId(),
                reportCase == null ? null : reportCase.getTargetId(),
                violation,
                note);
        String resultText = violation ? "举报成立，已确认违规" : "举报不成立，已确认未违规";
        String suffix = note == null || note.isBlank() ? "" : "。处理说明：" + note;
        Set<Long> notified = new HashSet<>();

        for (Long reporterId : loadReporterIds(reportCase.getId())) {
            if (reporterId != null && notified.add(reporterId)) {
                chatSupportService.sendReportNotice(
                        reporterId,
                        reportCase.getId(),
                        "您发起的消息举报已处理：" + resultText + suffix
                );
            }
        }

        ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
        if (message != null && message.getSenderId() != null && notified.add(message.getSenderId())) {
            chatSupportService.sendReportNotice(
                    message.getSenderId(),
                    reportCase.getId(),
                    "与您相关的消息举报已处理：" + resultText + suffix
            );
        }
    }

    private ReportCase loadMessageCase(Long reportId) {
        ReportCase reportCase = reportCaseMapper.selectById(reportId);
        if (reportCase == null || Integer.valueOf(1).equals(reportCase.getIsDelete())) {
            return null;
        }
        if (!Integer.valueOf(REPORT_TYPE_MESSAGE).equals(reportCase.getReportType())) {
            return null;
        }
        return reportCase;
    }

    private ReportCase findActiveCase(Integer reportType, Long targetId) {
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, reportType)
                .eq(ReportCase::getTargetId, targetId)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getCaseStatus, CASE_STATUS_PROCESSING)
                .last("LIMIT 1");
        return reportCaseMapper.selectOne(query);
    }

    private boolean existsReporterDetail(Long caseId, Long reporterId) {
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getReporterId, reporterId)
                .eq(ReportDetail::getIsDelete, 0)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query) != null;
    }

    private ReportCase createCase(Integer reportType, Long targetId, String reportContent) {
        ReportCase reportCase = new ReportCase();
        reportCase.setReportType(reportType);
        reportCase.setTargetId(targetId);
        reportCase.setCaseStatus(CASE_STATUS_PROCESSING);
        reportCase.setReportCount(0);
        reportCase.setLatestReportTime(LocalDateTime.now());
        reportCase.setPriorityLevel(reportContent == null || reportContent.isBlank() ? 0 : 1);
        reportCase.setAiCheckResult(0);
        reportCase.setAiConfidence(0);
        reportCase.setAdminStatus(ADMIN_STATUS_PENDING);
        reportCase.setAdminAction(0);
        reportCase.setAdminNote("");
        reportCase.setAppealCount(0);
        reportCase.setIsDelete(0);
        reportCaseMapper.insert(reportCase);
        reportCase.setReportCount(1);
        reportCaseMapper.updateById(reportCase);
        return reportCase;
    }

    /**
     * 判断当前管理员是否可以查看消息举报上下文。
     *
     * @param adminId    当前管理员用户 ID
     * @param reportCase 消息举报案卷
     * @return true 表示当前管理员可以查看
     */
    private boolean canAccessMessageReportContext(Long adminId, ReportCase reportCase) {
        // 1. 常规消息举报人工审核场景下，仍然要求当前管理员就是案卷指派处理人。
        if (reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            return true;
        }

        // 2. 若该消息举报已进入申诉复核，则允许当前这轮申诉的处理管理员查看原举报上下文。
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, REPORT_TYPE_MESSAGE)
                .eq(Appeal::getRelatedReportId, reportCase.getId())
                .eq(Appeal::getAppealStatus, 0)
                .eq(Appeal::getAdminId, adminId)
                .eq(Appeal::getIsDelete, 0)
                .last("LIMIT 1");
        return appealMapper.selectOne(query) != null;
    }

    private void refreshCaseOnNewDetail(ReportCase reportCase, String reportContent) {
        reportCase.setLatestReportTime(LocalDateTime.now());
        reportCase.setReportCount((reportCase.getReportCount() == null ? 0 : reportCase.getReportCount()) + 1);
        if (reportCase.getPriorityLevel() == null || reportCase.getPriorityLevel() == 0) {
            reportCase.setPriorityLevel(reportContent == null || reportContent.isBlank() ? 0 : 1);
        }
        reportCaseMapper.updateById(reportCase);
    }

    private ReportDetail findLatestDetail(Long caseId) {
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getIsDelete, 0)
                .orderByDesc(ReportDetail::getCreateTime)
                .orderByDesc(ReportDetail::getId)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query);
    }

    private List<ReportDetail> listCaseDetails(Long caseId) {
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getIsDelete, 0)
                .orderByDesc(ReportDetail::getCreateTime)
                .orderByDesc(ReportDetail::getId);
        return reportDetailMapper.selectList(query);
    }

    private int getAppealCount(Integer reportType, Long reportId) {
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, reportId)
                .eq(Appeal::getIsDelete, 0);
        Long count = appealMapper.selectCount(query);
        return count == null ? 0 : count.intValue();
    }

    private boolean hasPendingAppeal(Integer reportType, Long reportId) {
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, reportId)
                .eq(Appeal::getAppealStatus, 0)
                .eq(Appeal::getIsDelete, 0)
                .last("LIMIT 1");
        return appealMapper.selectOne(query) != null;
    }

    private boolean canAppeal(ReportCase reportCase, int appealCount, boolean pendingAppeal, String viewRole) {
        int maxAppealCount = resolveMaxAppealCount(reportCase);
        if (reportCase == null || pendingAppeal || appealCount >= maxAppealCount) {
            return false;
        }
        if ("reporter".equals(viewRole)) {
            return Integer.valueOf(ADMIN_STATUS_REJECTED).equals(reportCase.getAdminStatus());
        }
        return Integer.valueOf(ADMIN_STATUS_APPROVED).equals(reportCase.getAdminStatus());
    }

    private int resolveMaxAppealCount(ReportCase reportCase) {
        if (reportCase != null
                && Integer.valueOf(1).equals(reportCase.getAiCheckResult())
                && Integer.valueOf(ADMIN_ACTION_CONFIRM_VIOLATION).equals(reportCase.getAdminAction())) {
            return MAX_APPEAL_COUNT_AI;
        }
        return MAX_APPEAL_COUNT_MANUAL;
    }

    private String buildDisplayStatus(ReportCase reportCase, boolean pendingAppeal) {
        if (pendingAppeal) {
            return "申诉处理中";
        }
        if (reportCase == null || reportCase.getAdminStatus() == null) {
            return "处理中";
        }
        return switch (reportCase.getAdminStatus()) {
            case ADMIN_STATUS_PENDING -> "处理中";
            case ADMIN_STATUS_APPROVED -> "已确认违规";
            case ADMIN_STATUS_REJECTED -> "已确认未违规";
            default -> "处理中";
        };
    }

    private Map<String, Object> buildAdminCaseView(ReportCase reportCase) {
        ReportDetail latestDetail = findLatestDetail(reportCase.getId());
        Map<String, Object> item = new HashMap<>();
        item.put("id", chatSupportService.stringifyId(reportCase.getId()));
        item.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("messageId", chatSupportService.stringifyId(reportCase.getTargetId()));
        item.put("reporterId", latestDetail == null ? null : latestDetail.getReporterId());
        item.put("reportReason", latestDetail == null ? null : latestDetail.getReportReason());
        item.put("reportContent", latestDetail == null ? "" : latestDetail.getReportContent());
        item.put("reportStatus", reportCase.getAdminStatus());
        item.put("adminStatus", reportCase.getAdminStatus());
        item.put("adminAction", reportCase.getAdminAction());
        item.put("adminNote", reportCase.getAdminNote());
        item.put("reportCount", reportCase.getReportCount());
        item.put("aiCheckResult", reportCase.getAiCheckResult());
        item.put("aiConfidence", reportCase.getAiConfidence());
        item.put("createTime", latestDetail == null ? reportCase.getCreateTime() : latestDetail.getCreateTime());
        item.put("updateTime", reportCase.getUpdateTime());
        return item;
    }

    private List<Map<String, Object>> queryContextMessages(ChatMessage targetMsg, boolean before) {
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<>();
        query.eq(ChatMessage::getConversationId, targetMsg.getConversationId())
                .eq(ChatMessage::getIsDelete, 0);
        if (before) {
            query.lt(ChatMessage::getCreateTime, targetMsg.getCreateTime())
                    .orderByDesc(ChatMessage::getCreateTime)
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 50");
        } else {
            query.gt(ChatMessage::getCreateTime, targetMsg.getCreateTime())
                    .orderByAsc(ChatMessage::getCreateTime)
                    .orderByAsc(ChatMessage::getId)
                    .last("LIMIT 50");
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(query);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage message : messages) {
            list.add(buildMessageContextItem(message));
        }
        return list;
    }

    private Map<String, Object> buildMessageContextItem(ChatMessage message) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", chatSupportService.stringifyId(message.getId()));
        item.put("msgId", chatSupportService.stringifyId(message.getId()));
        item.put("senderId", message.getSenderId());
        item.put("senderNickname", resolveUserNickname(message.getSenderId()));
        item.put("msgType", message.getMsgType());
        item.put("msgContent", message.getMsgContent());
        item.put("displayText", chatSupportService.extractSearchableText(message.getMsgType(), message.getMsgContent()));
        item.put("createTime", message.getCreateTime());
        return item;
    }

    private String resolveUserNickname(Long userId) {
        if (userId == null) {
            return "";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "";
        }
        if (user.getUserNickname() != null && !user.getUserNickname().isBlank()) {
            return user.getUserNickname().trim();
        }
        return user.getUserAccount() == null ? "" : user.getUserAccount().trim();
    }

    private List<Long> loadReporterIds(Long caseId) {
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getIsDelete, 0)
                .select(ReportDetail::getReporterId);
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ReportDetail detail : reportDetailMapper.selectList(query)) {
            if (detail.getReporterId() != null && seen.add(detail.getReporterId())) {
                ids.add(detail.getReporterId());
            }
        }
        return ids;
    }

    private Long findLatestActivePunishLogIdByMessage(Long messageId) {
        LambdaQueryWrapper<PunishMsgRelation> query = new LambdaQueryWrapper<>();
        query.eq(PunishMsgRelation::getMsgId, messageId)
                .orderByDesc(PunishMsgRelation::getId);
        List<PunishMsgRelation> relations = punishMsgRelationMapper.selectList(query);
        if (relations.isEmpty()) {
            return null;
        }
        return relations.get(0).getPunishLogId();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
