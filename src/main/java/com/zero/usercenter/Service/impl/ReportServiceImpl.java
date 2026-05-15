package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.TeamReportHandleDTO;
import com.zero.usercenter.DTO.TeamReportSubmitDTO;
import com.zero.usercenter.DTO.UserReportHandleDTO;
import com.zero.usercenter.DTO.UserReportSubmitDTO;
import com.zero.usercenter.Mapper.AppealMapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.ReportCaseMapper;
import com.zero.usercenter.Mapper.ReportDetailMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.Appeal;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.ReportCase;
import com.zero.usercenter.Model.ReportDetail;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.Service.ReportService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.GRADIENT_MUTE_DURATIONS;
import static com.zero.usercenter.utils.Number.TEAM_ALL_MUTE_KEY;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final int REPORT_TYPE_USER = 1;
    private static final int REPORT_TYPE_MESSAGE = 2;
    private static final int REPORT_TYPE_TEAM = 3;
    private static final int CASE_STATUS_PROCESSING = 0;
    private static final int CASE_STATUS_FINISHED = 1;
    private static final int ADMIN_STATUS_PENDING = 0;
    private static final int ADMIN_STATUS_APPROVED = 1;
    private static final int ADMIN_STATUS_REJECTED = 2;
    private static final int ADMIN_ACTION_CONFIRM_VIOLATION = 1;
    private static final int ADMIN_ACTION_CONFIRM_NORMAL = 2;
    private static final int MAX_APPEAL_COUNT_AI = 3;
    private static final int MAX_APPEAL_COUNT_MANUAL = 2;
    private static final int REPORT_CONTEXT_WINDOW_MINUTES = 5;
    private static final int REPORT_CONTEXT_MAX_MESSAGES = 200;

    @Resource
    private ReportCaseMapper reportCaseMapper;

    @Resource
    private ReportDetailMapper reportDetailMapper;

    @Resource
    private AppealMapper appealMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private PunishService punishService;

    @Resource
    private ReportAdminAssignSupport reportAdminAssignSupport;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result reportUser(UserReportSubmitDTO dto) {
        // 1. 先确认登录和基础参数有效，避免无效举报进入后续流程。
        Long userId = chatSupportService.requireLogin();
        log.info("[report-user] start reporterId={}, targetUserId={}, reason={}",
                userId,
                dto == null ? null : dto.getReportedUserId(),
                dto == null ? null : dto.getReportReason());
        if (dto == null || dto.getReportedUserId() == null) {
            log.warn("[report-user] reject because targetUserId is null, reporterId={}", userId);
            return Result.fail("被举报用户不能为空");
        }
        if (userId.equals(dto.getReportedUserId())) {
            log.warn("[report-user] reject because reporter equals target, reporterId={}", userId);
            return Result.fail("不能举报自己");
        }
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 6) {
            log.warn("[report-user] reject because reason invalid, reporterId={}, targetUserId={}, reason={}",
                    userId, dto.getReportedUserId(), dto.getReportReason());
            return Result.fail("举报原因无效");
        }
        String reportContent = trimToEmpty(dto.getReportContent());
        if (reportContent.isBlank()) {
            log.warn("[report-user] reject because reportContent blank, reporterId={}, targetUserId={}",
                    userId, dto.getReportedUserId());
            return Result.fail("举报说明不能为空");
        }

        User reportedUser = userMapper.selectById(dto.getReportedUserId());
        if (reportedUser == null || Integer.valueOf(1).equals(reportedUser.getIsDelete())) {
            log.warn("[report-user] reject because target user missing, reporterId={}, targetUserId={}",
                    userId, dto.getReportedUserId());
            return Result.fail("被举报用户不存在");
        }

        // 2. 尽量复用同一个举报案卷，避免同一目标被重复创建多个未结案 case。
        ReportCase reportCase = findActiveCase(REPORT_TYPE_USER, dto.getReportedUserId());
        if (reportCase != null && existsReporterDetail(reportCase.getId(), userId)) {
            log.warn("[report-user] reject because duplicate detail exists, reporterId={}, caseId={}, targetUserId={}",
                    userId, reportCase.getId(), dto.getReportedUserId());
            return Result.fail("您已经举报过该用户，请勿重复提交");
        }

        if (reportCase == null) {
            reportCase = createCase(REPORT_TYPE_USER, dto.getReportedUserId(), reportContent);
        } else {
            refreshCaseOnNewDetail(reportCase, reportContent);
        }

        // 3. 记录这一次举报明细，案卷负责汇总，明细负责保存每个举报人的证据和理由。
        ReportDetail detail = new ReportDetail();
        detail.setCaseId(reportCase.getId());
        detail.setReportType(REPORT_TYPE_USER);
        detail.setTargetId(dto.getReportedUserId());
        detail.setReporterId(userId);
        detail.setReportReason(dto.getReportReason());
        detail.setReportContent(reportContent);
        detail.setReportEvidence(trimToEmpty(dto.getReportEvidence()));
        detail.setIsDelete(0);
        reportDetailMapper.insert(detail);
        log.info("[report-user] detail persisted reporterId={}, caseId={}, detailId={}, targetUserId={}",
                userId, reportCase.getId(), detail.getId(), dto.getReportedUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("detailId", chatSupportService.stringifyId(detail.getId()));
        data.put("message", "举报提交成功，已进入审核流程");
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result reportTeam(TeamReportSubmitDTO dto) {
        // 1. 团队举报的整体流程和用户举报一致，先校验登录和参数。
        Long userId = chatSupportService.requireLogin();
        log.info("[report-team] start reporterId={}, targetTeamId={}, reason={}",
                userId,
                dto == null ? null : dto.getReportedTeamId(),
                dto == null ? null : dto.getReportReason());
        if (dto == null || dto.getReportedTeamId() == null) {
            log.warn("[report-team] reject because targetTeamId is null, reporterId={}", userId);
            return Result.fail("被举报团队不能为空");
        }
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 5) {
            log.warn("[report-team] reject because reason invalid, reporterId={}, targetTeamId={}, reason={}",
                    userId, dto.getReportedTeamId(), dto.getReportReason());
            return Result.fail("举报原因无效");
        }
        String reportContent = trimToEmpty(dto.getReportContent());
        if (reportContent.isBlank()) {
            log.warn("[report-team] reject because reportContent blank, reporterId={}, targetTeamId={}",
                    userId, dto.getReportedTeamId());
            return Result.fail("举报说明不能为空");
        }

        Team reportedTeam = teamMapper.selectById(dto.getReportedTeamId());
        if (reportedTeam == null || Integer.valueOf(1).equals(reportedTeam.getIsDelete())) {
            log.warn("[report-team] reject because target team missing, reporterId={}, targetTeamId={}",
                    userId, dto.getReportedTeamId());
            return Result.fail("被举报团队不存在");
        }

        // 2. 同一个团队举报维持一个处理中 case，减少重复案卷。
        ReportCase reportCase = findActiveCase(REPORT_TYPE_TEAM, dto.getReportedTeamId());
        if (reportCase != null && existsReporterDetail(reportCase.getId(), userId)) {
            log.warn("[report-team] reject because duplicate detail exists, reporterId={}, caseId={}, targetTeamId={}",
                    userId, reportCase.getId(), dto.getReportedTeamId());
            return Result.fail("您已经举报过该团队，请勿重复提交");
        }

        if (reportCase == null) {
            reportCase = createCase(REPORT_TYPE_TEAM, dto.getReportedTeamId(), reportContent);
        } else {
            refreshCaseOnNewDetail(reportCase, reportContent);
        }

        // 3. 写入举报明细，便于后续管理员查看上下文和多个举报人。
        ReportDetail detail = new ReportDetail();
        detail.setCaseId(reportCase.getId());
        detail.setReportType(REPORT_TYPE_TEAM);
        detail.setTargetId(dto.getReportedTeamId());
        detail.setReporterId(userId);
        detail.setReportReason(dto.getReportReason());
        detail.setReportContent(reportContent);
        detail.setReportEvidence(trimToEmpty(dto.getReportEvidence()));
        detail.setIsDelete(0);
        reportDetailMapper.insert(detail);
        log.info("[report-team] detail persisted reporterId={}, caseId={}, detailId={}, targetTeamId={}",
                userId, reportCase.getId(), detail.getId(), dto.getReportedTeamId());

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("detailId", chatSupportService.stringifyId(detail.getId()));
        data.put("message", "举报提交成功，已进入审核流程");
        return Result.ok(data);
    }

    @Override
    public Result getUserReportStatus(Long reportId) {
        // 1. 只有举报人和被举报人自己可以查看这条举报状态。
        Long userId = chatSupportService.requireLogin();
        if (reportId == null) {
            return Result.fail("举报 ID 不能为空");
        }

        ReportCase reportCase = loadCase(REPORT_TYPE_USER, reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }

        boolean reporter = existsReporterDetail(reportCase.getId(), userId);
        boolean reported = userId.equals(reportCase.getTargetId());
        if (!reporter && !reported) {
            return Result.fail("无权查看该举报记录");
        }

        // 2. 根据当前视角拼出状态展示字段和可申诉能力。
        ReportDetail latestDetail = reporter ? findLatestReporterDetail(reportCase.getId(), userId) : findLatestDetail(reportCase.getId());
        int appealCount = getAppealCount(REPORT_TYPE_USER, reportCase.getId());
        boolean pendingAppeal = hasPendingAppeal(REPORT_TYPE_USER, reportCase.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("reportReason", latestDetail == null ? null : latestDetail.getReportReason());
        data.put("reportContent", latestDetail == null ? "" : latestDetail.getReportContent());
        data.put("reportEvidence", latestDetail == null ? "" : latestDetail.getReportEvidence());
        data.put("reportStatus", reportCase.getAdminStatus());
        data.put("handleStatus", reportCase.getAdminStatus());
        data.put("handleStatusText", buildDisplayStatus(reportCase, pendingAppeal));
        data.put("adminAction", reportCase.getAdminAction());
        data.put("adminNote", reportCase.getAdminNote());
        data.put("appealCount", appealCount);
        data.put("hasPendingAppeal", pendingAppeal);
        data.put("canAppeal", canAppeal(reportCase, appealCount, pendingAppeal, reporter ? "reporter" : "reported"));
        data.put("viewRole", reporter ? "reporter" : "reported");
        data.put("viewRoleText", reporter ? "我发起的举报" : "我作为被举报方");
        data.put("processTime", reportCase.getProcessTime());
        data.put("createTime", latestDetail == null ? reportCase.getCreateTime() : latestDetail.getCreateTime());
        data.put("reportCount", reportCase.getReportCount());
        data.put("targetId", chatSupportService.stringifyId(reportCase.getTargetId()));
        return Result.ok(data);
    }

    @Override
    public Result getTeamReportStatus(Long reportId) {
        // 1. 团队举报状态的查看范围，限制在举报人和团队管理者侧。
        Long userId = chatSupportService.requireLogin();
        if (reportId == null) {
            return Result.fail("举报 ID 不能为空");
        }

        ReportCase reportCase = loadCase(REPORT_TYPE_TEAM, reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }

        boolean reporter = existsReporterDetail(reportCase.getId(), userId);
        boolean reportedSide = isTeamManager(reportCase.getTargetId(), userId);
        if (!reporter && !reportedSide) {
            return Result.fail("无权查看该举报记录");
        }

        // 2. 组装团队举报状态页需要的上下文字段。
        ReportDetail latestDetail = reporter ? findLatestReporterDetail(reportCase.getId(), userId) : findLatestDetail(reportCase.getId());
        int appealCount = getAppealCount(REPORT_TYPE_TEAM, reportCase.getId());
        boolean pendingAppeal = hasPendingAppeal(REPORT_TYPE_TEAM, reportCase.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        data.put("reportReason", latestDetail == null ? null : latestDetail.getReportReason());
        data.put("reportContent", latestDetail == null ? "" : latestDetail.getReportContent());
        data.put("reportEvidence", latestDetail == null ? "" : latestDetail.getReportEvidence());
        data.put("reportStatus", reportCase.getAdminStatus());
        data.put("handleStatus", reportCase.getAdminStatus());
        data.put("handleStatusText", buildDisplayStatus(reportCase, pendingAppeal));
        data.put("adminAction", reportCase.getAdminAction());
        data.put("adminNote", reportCase.getAdminNote());
        data.put("appealCount", appealCount);
        data.put("hasPendingAppeal", pendingAppeal);
        data.put("canAppeal", canAppeal(reportCase, appealCount, pendingAppeal, reporter ? "reporter" : "reported"));
        data.put("viewRole", reporter ? "reporter" : "reported");
        data.put("viewRoleText", reporter ? "我发起的举报" : "我作为被举报方");
        data.put("processTime", reportCase.getProcessTime());
        data.put("createTime", latestDetail == null ? reportCase.getCreateTime() : latestDetail.getCreateTime());
        data.put("reportCount", reportCase.getReportCount());
        data.put("targetId", chatSupportService.stringifyId(reportCase.getTargetId()));
        return Result.ok(data);
    }

    @Override
    public Result getMyReports(int page, int pageSize) {
        // 1. 把“我发起的举报”和“与我相关的被举报记录”合并成一个中心列表。
        Long userId = chatSupportService.requireLogin();
        List<Map<String, Object>> merged = new ArrayList<>();
        Set<Long> managedTeamIds = loadManagedTeamIds(userId);

        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getIsDelete, 0)
                .orderByDesc(ReportCase::getLatestReportTime)
                .orderByDesc(ReportCase::getId);
        List<ReportCase> cases = reportCaseMapper.selectList(query);

        for (ReportCase reportCase : cases) {
            ReportDetail selfDetail = findLatestReporterDetail(reportCase.getId(), userId);
            if (selfDetail != null) {
                merged.add(buildReportCenterItem(reportCase, selfDetail, "reporter"));
            }

            if (Integer.valueOf(REPORT_TYPE_USER).equals(reportCase.getReportType()) && userId.equals(reportCase.getTargetId())) {
                merged.add(buildReportCenterItem(reportCase, findLatestDetail(reportCase.getId()), "reported"));
                continue;
            }

            if (Integer.valueOf(REPORT_TYPE_TEAM).equals(reportCase.getReportType()) && managedTeamIds.contains(reportCase.getTargetId())) {
                merged.add(buildReportCenterItem(reportCase, findLatestDetail(reportCase.getId()), "reported"));
                continue;
            }

            if (Integer.valueOf(REPORT_TYPE_MESSAGE).equals(reportCase.getReportType())) {
                ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
                if (message != null && userId.equals(message.getSenderId())) {
                    merged.add(buildReportCenterItem(reportCase, findLatestDetail(reportCase.getId()), "reported"));
                }
            }
        }

        // 2. 结果在内存里统一分页，保证不同举报类型能被合并展示。
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, merged.size());
        int toIndex = Math.min(fromIndex + safePageSize, merged.size());
        return Result.ok(merged.subList(fromIndex, toIndex), (long) merged.size());
    }

    @Override
    @RequireAdmin
    public Result adminGetUserReportList(Integer reportStatus, int page, int pageSize) {
        // 1. 管理员侧只看分配给自己的用户举报。
        Long adminId = chatSupportService.requireLogin();
        Page<ReportCase> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, REPORT_TYPE_USER)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getAdminId, adminId);
        if (reportStatus != null) {
            query.eq(ReportCase::getAdminStatus, reportStatus);
        }
        query.orderByAsc(ReportCase::getAdminStatus)
                .orderByDesc(ReportCase::getLatestReportTime)
                .orderByDesc(ReportCase::getId);
        Page<ReportCase> result = reportCaseMapper.selectPage(pageObj, query);

        List<Map<String, Object>> list = new ArrayList<>();
        for (ReportCase reportCase : result.getRecords()) {
            list.add(buildAdminListItem(reportCase));
        }
        return Result.ok(list, result.getTotal());
    }

    @Override
    @RequireAdmin
    public Result adminGetUserReportContext(Long reportId) {
        // 1. 先确认案卷存在并且确实分配给当前管理员。
        Long adminId = chatSupportService.requireLogin();
        ReportCase reportCase = loadCase(REPORT_TYPE_USER, reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }
        if (!reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            return Result.fail("当前举报不属于您处理");
        }

        ReportDetail latestDetail = findLatestDetail(reportCase.getId());
        User reportedUser = userMapper.selectById(reportCase.getTargetId());

        // 2. 组装举报详情、被举报用户和上下文消息给后台查看。
        Map<String, Object> data = new HashMap<>();
        data.put("report", buildAdminListItem(reportCase));
        data.put("reportedUser", buildUserSimpleInfo(reportedUser));
        data.put("chatContext", buildUserReportContext(reportCase, latestDetail));
        return Result.ok(data);
    }

    @Override
    @Transactional
    @RequireAdmin
    public Result adminHandleUserReport(UserReportHandleDTO dto) {
        // 1. 审核处理先确认参数有效、案件存在且归属当前管理员。
        Long adminId = chatSupportService.requireLogin();
        log.info("[report-user-admin] start adminId={}, reportId={}, decision={}",
                adminId,
                dto == null ? null : dto.getReportId(),
                dto == null ? null : dto.getReportStatus());
        if (dto == null || dto.getReportId() == null) {
            log.warn("[report-user-admin] reject because reportId null, adminId={}", adminId);
            return Result.fail("举报 ID 不能为空");
        }
        if (dto.getReportStatus() == null || (dto.getReportStatus() != 1 && dto.getReportStatus() != 2)) {
            log.warn("[report-user-admin] reject because decision invalid, adminId={}, reportId={}, decision={}",
                    adminId, dto.getReportId(), dto.getReportStatus());
            return Result.fail("处理动作无效");
        }

        ReportCase reportCase = loadCase(REPORT_TYPE_USER, dto.getReportId());
        if (reportCase == null) {
            log.warn("[report-user-admin] reject because report missing, adminId={}, reportId={}", adminId, dto.getReportId());
            return Result.fail("举报记录不存在");
        }
        if (!reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            log.warn("[report-user-admin] reject because report not assigned, adminId={}, reportId={}, assignedAdminId={}",
                    adminId, dto.getReportId(), reportCase.getAdminId());
            return Result.fail("当前举报不属于您处理");
        }
        if (!Integer.valueOf(ADMIN_STATUS_PENDING).equals(reportCase.getAdminStatus())) {
            log.warn("[report-user-admin] reject because report already handled, adminId={}, reportId={}, adminStatus={}",
                    adminId, dto.getReportId(), reportCase.getAdminStatus());
            return Result.fail("该举报已处理");
        }

        String note = trimToEmpty(dto.getAdminNote());
        reportCase.setCaseStatus(CASE_STATUS_FINISHED);
        reportCase.setProcessTime(LocalDateTime.now());
        reportCase.setAdminNote(note);
        reportCase.setAdminId(adminId);

        // 2. 通过举报时执行处罚，不通过时只改状态并通知相关方。
        if (dto.getReportStatus() == 1) {
            reportCase.setAdminStatus(ADMIN_STATUS_APPROVED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            log.info("[report-user-admin] approve report, adminId={}, reportId={}, targetUserId={}",
                    adminId, dto.getReportId(), reportCase.getTargetId());
            punishUserByReport(reportCase, note);
            notifyNormalReportParticipants(reportCase, true, note);
        } else {
            reportCase.setAdminStatus(ADMIN_STATUS_REJECTED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            log.info("[report-user-admin] reject report, adminId={}, reportId={}, targetUserId={}",
                    adminId, dto.getReportId(), reportCase.getTargetId());
            notifyNormalReportParticipants(reportCase, false, note);
        }

        reportCaseMapper.updateById(reportCase);
        log.info("[report-user-admin] completed adminId={}, reportId={}, finalStatus={}, finalAction={}",
                adminId, dto.getReportId(), reportCase.getAdminStatus(), reportCase.getAdminAction());
        return Result.ok("处理成功");
    }

    @Override
    @RequireAdmin
    public Result adminGetTeamReportList(Integer reportStatus, int page, int pageSize) {
        // 团队举报列表的读取逻辑和用户举报一致，只是 reportType 不同。
        Long adminId = chatSupportService.requireLogin();
        Page<ReportCase> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, REPORT_TYPE_TEAM)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getAdminId, adminId);
        if (reportStatus != null) {
            query.eq(ReportCase::getAdminStatus, reportStatus);
        }
        query.orderByAsc(ReportCase::getAdminStatus)
                .orderByDesc(ReportCase::getLatestReportTime)
                .orderByDesc(ReportCase::getId);
        Page<ReportCase> result = reportCaseMapper.selectPage(pageObj, query);

        List<Map<String, Object>> list = new ArrayList<>();
        for (ReportCase reportCase : result.getRecords()) {
            list.add(buildAdminListItem(reportCase));
        }
        return Result.ok(list, result.getTotal());
    }

    @Override
    @RequireAdmin
    public Result adminGetTeamReportContext(Long reportId) {
        // 1. 先确认团队举报案卷属于当前管理员。
        Long adminId = chatSupportService.requireLogin();
        ReportCase reportCase = loadCase(REPORT_TYPE_TEAM, reportId);
        if (reportCase == null) {
            return Result.fail("举报记录不存在");
        }
        if (!reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            return Result.fail("当前举报不属于您处理");
        }

        ReportDetail latestDetail = findLatestDetail(reportCase.getId());
        Team reportedTeam = teamMapper.selectById(reportCase.getTargetId());

        // 2. 组装团队信息和聊天上下文，方便管理员判断举报是否成立。
        Map<String, Object> data = new HashMap<>();
        data.put("report", buildAdminListItem(reportCase));
        data.put("reportedTeam", buildTeamSimpleInfo(reportedTeam));
        data.put("chatContext", buildTeamReportContext(reportCase, latestDetail));
        return Result.ok(data);
    }

    @Override
    @Transactional
    @RequireAdmin
    public Result adminHandleTeamReport(TeamReportHandleDTO dto) {
        // 1. 处理前先确认参数、案卷状态和归属管理员都正确。
        Long adminId = chatSupportService.requireLogin();
        log.info("[report-team-admin] start adminId={}, reportId={}, decision={}",
                adminId,
                dto == null ? null : dto.getReportId(),
                dto == null ? null : dto.getReportStatus());
        if (dto == null || dto.getReportId() == null) {
            log.warn("[report-team-admin] reject because reportId null, adminId={}", adminId);
            return Result.fail("举报 ID 不能为空");
        }
        if (dto.getReportStatus() == null || (dto.getReportStatus() != 1 && dto.getReportStatus() != 2)) {
            log.warn("[report-team-admin] reject because decision invalid, adminId={}, reportId={}, decision={}",
                    adminId, dto.getReportId(), dto.getReportStatus());
            return Result.fail("处理动作无效");
        }

        ReportCase reportCase = loadCase(REPORT_TYPE_TEAM, dto.getReportId());
        if (reportCase == null) {
            log.warn("[report-team-admin] reject because report missing, adminId={}, reportId={}", adminId, dto.getReportId());
            return Result.fail("举报记录不存在");
        }
        if (!reportAdminAssignSupport.isAssignedAdmin(adminId, reportCase.getAdminId())) {
            log.warn("[report-team-admin] reject because report not assigned, adminId={}, reportId={}, assignedAdminId={}",
                    adminId, dto.getReportId(), reportCase.getAdminId());
            return Result.fail("当前举报不属于您处理");
        }
        if (!Integer.valueOf(ADMIN_STATUS_PENDING).equals(reportCase.getAdminStatus())) {
            log.warn("[report-team-admin] reject because report already handled, adminId={}, reportId={}, adminStatus={}",
                    adminId, dto.getReportId(), reportCase.getAdminStatus());
            return Result.fail("该举报已处理");
        }

        String note = trimToEmpty(dto.getAdminNote());
        reportCase.setCaseStatus(CASE_STATUS_FINISHED);
        reportCase.setProcessTime(LocalDateTime.now());
        reportCase.setAdminNote(note);
        reportCase.setAdminId(adminId);

        // 2. 团队举报成立时按梯度禁言或解散团队，不成立时只发通知。
        if (dto.getReportStatus() == 1) {
            reportCase.setAdminStatus(ADMIN_STATUS_APPROVED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            log.info("[report-team-admin] approve report, adminId={}, reportId={}, targetTeamId={}",
                    adminId, dto.getReportId(), reportCase.getTargetId());
            applyTeamModeration(reportCase, note);
            notifyNormalReportParticipants(reportCase, true, note);
        } else {
            reportCase.setAdminStatus(ADMIN_STATUS_REJECTED);
            reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            log.info("[report-team-admin] reject report, adminId={}, reportId={}, targetTeamId={}",
                    adminId, dto.getReportId(), reportCase.getTargetId());
            notifyNormalReportParticipants(reportCase, false, note);
        }

        reportCaseMapper.updateById(reportCase);
        log.info("[report-team-admin] completed adminId={}, reportId={}, finalStatus={}, finalAction={}",
                adminId, dto.getReportId(), reportCase.getAdminStatus(), reportCase.getAdminAction());
        return Result.ok("处理成功");
    }

    /**
     * 对用户举报成立后的目标用户执行处罚。
     *
     * @param reportCase 举报案卷
     * @param note       管理员处理说明
     */
    private void punishUserByReport(ReportCase reportCase, String note) {
        // 1. 用户举报成立后，统一转成处罚服务调用，处罚规则仍收口在 PunishService。
        log.info("[report-user-punish] punish target user, caseId={}, targetUserId={}, note={}",
                reportCase == null ? null : reportCase.getId(),
                reportCase == null ? null : reportCase.getTargetId(),
                note);
        PunishDTO punishDTO = new PunishDTO();
        punishDTO.setPunishUserId(reportCase.getTargetId());
        punishDTO.setPunishReason(note == null || note.isBlank() ? "用户举报确认违规" : note);
        punishDTO.setOperateType(2);
        punishService.punishUser(punishDTO);
    }

    /**
     * 对团队举报成立后的目标团队执行治理。
     *
     * @param reportCase 举报案卷
     * @param note       管理员处理说明
     */
    private void applyTeamModeration(ReportCase reportCase, String note) {
        // 1. 团队治理采用梯度策略：前几次先全员禁言，累计到阈值后再直接解散。
        int approvedCountBeforeCurrent = countApprovedTeamReports(reportCase.getTargetId());
        int currentRound = approvedCountBeforeCurrent + 1;
        log.info("[report-team-moderation] apply moderation caseId={}, targetTeamId={}, currentRound={}, note={}",
                reportCase == null ? null : reportCase.getId(),
                reportCase == null ? null : reportCase.getTargetId(),
                currentRound,
                note);
        if (currentRound >= 3) {
            // 2. 达到阈值后不再只是禁言，而是直接解散团队。
            dissolveTeamByModeration(reportCase.getTargetId());
            return;
        }
        // 3. 未达到解散阈值时，按梯度时长执行全员禁言。
        int durationMinutes = GRADIENT_MUTE_DURATIONS[Math.min(currentRound - 1, GRADIENT_MUTE_DURATIONS.length - 1)];
        muteTeamByModeration(reportCase.getTargetId(), durationMinutes);
    }

    /**
     * 统计团队历史上已确认违规的举报次数。
     *
     * @param teamId 团队 ID
     * @return 已确认违规次数
     */
    private int countApprovedTeamReports(Long teamId) {
        // 1. 只统计已确认违规的团队举报，用于判断当前治理梯度。
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, REPORT_TYPE_TEAM)
                .eq(ReportCase::getTargetId, teamId)
                .eq(ReportCase::getAdminStatus, ADMIN_STATUS_APPROVED)
                .eq(ReportCase::getIsDelete, 0);
        Long count = reportCaseMapper.selectCount(query);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 对团队执行全员禁言。
     *
     * @param teamId          团队 ID
     * @param durationMinutes 禁言时长（分钟）
     */
    private void muteTeamByModeration(Long teamId, int durationMinutes) {
        // 1. 团队不存在或已删除时直接跳过，避免更新脏数据。
        Team team = teamMapper.selectById(teamId);
        if (team == null || Integer.valueOf(1).equals(team.getIsDelete())) {
            return;
        }

        // 2. 数据库保存持久态，Redis 保存快速命中的全员禁言缓存。
        LocalDateTime unpunishTime = LocalDateTime.now().plusMinutes(durationMinutes);
        team.setTeamAllMute(1);
        team.setTeamAllMuteUnpunishTime(unpunishTime);
        teamMapper.updateById(team);

        // 3. Redis TTL 和解禁时间保持一致，供会话发送链快速判断。
        long ttlMinutes = Math.max(Duration.between(LocalDateTime.now(), unpunishTime).toMinutes() + 1, 1);
        stringRedisTemplate.opsForValue().set(TEAM_ALL_MUTE_KEY + teamId, "1", ttlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 对团队执行解散治理。
     *
     * @param teamId 团队 ID
     */
    private void dissolveTeamByModeration(Long teamId) {
        // 1. 先软删除团队本体并清理团队级禁言状态。
        Team team = teamMapper.selectById(teamId);
        if (team == null || Integer.valueOf(1).equals(team.getIsDelete())) {
            return;
        }

        // 2. 解散时既要软删除团队本体，也要同步结束所有成员关系并清理禁言缓存。
        team.setIsDelete(1);
        team.setTeamAllMute(0);
        team.setTeamAllMuteUnpunishTime(null);
        teamMapper.updateById(team);
        stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + teamId);

        // 3. 批量把所有未退出成员标记成退出，避免后续继续访问该团队。
        LambdaUpdateWrapper<TeamMember> update = new LambdaUpdateWrapper<>();
        update.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0)
                .set(TeamMember::getIsQuit, 1)
                .set(TeamMember::getQuitTime, LocalDateTime.now())
                .set(TeamMember::getTeamMuteType, 0)
                .set(TeamMember::getTeamMuteUnpunishTime, null);
        teamMemberMapper.update(null, update);
    }

    /**
     * 通知普通举报链路的参与方处理结果。
     *
     * @param reportCase 举报案卷
     * @param violation  是否确认违规
     * @param note       管理员处理说明
     */
    private void notifyNormalReportParticipants(ReportCase reportCase, boolean violation, String note) {
        // 1. 举报结果需要同时通知举报侧和被举报侧，避免双方对处理结果感知不一致。
        log.info("[report-notify] notify participants caseId={}, reportType={}, targetId={}, violation={}, note={}",
                reportCase == null ? null : reportCase.getId(),
                reportCase == null ? null : reportCase.getReportType(),
                reportCase == null ? null : reportCase.getTargetId(),
                violation,
                note);
        String resultText = violation ? "举报成立，已确认违规" : "举报不成立，已确认未违规";
        String suffix = note == null || note.isBlank() ? "" : "。处理说明：" + note;
        Set<Long> notified = new HashSet<>();

        // 2. 先通知所有举报人，支持一个案卷里聚合多个举报人。
        for (Long reporterId : loadReporterIds(reportCase.getId())) {
            if (reporterId != null && notified.add(reporterId)) {
                chatSupportService.sendReportNotice(
                        reporterId,
                        reportCase.getId(),
                        "您发起的举报已处理：" + resultText + suffix
                );
            }
        }

        // 3. 用户举报和团队举报的被通知对象不同，分别处理即可。
        if (Integer.valueOf(REPORT_TYPE_USER).equals(reportCase.getReportType())) {
            if (notified.add(reportCase.getTargetId())) {
                chatSupportService.sendReportNotice(
                        reportCase.getTargetId(),
                        reportCase.getId(),
                        "与您相关的举报已处理：" + resultText + suffix
                );
            }
            return;
        }

        if (Integer.valueOf(REPORT_TYPE_TEAM).equals(reportCase.getReportType())) {
            // 4. 团队举报统一通知队长和管理员。
            for (Long managerId : loadTeamManagerIds(reportCase.getTargetId())) {
                if (managerId != null && notified.add(managerId)) {
                    chatSupportService.sendReportNotice(
                        managerId,
                            reportCase.getId(),
                            "与您管理团队相关的举报已处理：" + resultText + suffix
                    );
                }
            }
        }
    }

    /**
     * 创建新的举报案卷。
     *
     * @param reportType    举报类型
     * @param targetId      被举报目标 ID
     * @param reportContent 举报说明
     * @return 新建的举报案卷
     */
    private ReportCase createCase(Integer reportType, Long targetId, String reportContent) {
        // 1. 新建案卷时把管理员分配、优先级、申诉次数等汇总态一并初始化。
        ReportCase reportCase = new ReportCase();
        reportCase.setReportType(reportType);
        reportCase.setTargetId(targetId);
        reportCase.setCaseStatus(CASE_STATUS_PROCESSING);
        reportCase.setReportCount(1);
        reportCase.setLatestReportTime(LocalDateTime.now());
        reportCase.setPriorityLevel(reportContent == null || reportContent.isBlank() ? 0 : 1);
        reportCase.setAiCheckResult(0);
        reportCase.setAiConfidence(0);
        reportCase.setAdminStatus(ADMIN_STATUS_PENDING);
        reportCase.setAdminAction(0);
        reportCase.setAdminNote("");
        reportCase.setAdminId(reportAdminAssignSupport.allocateAdmin(targetId, reportType));
        reportCase.setAppealCount(0);
        reportCase.setIsDelete(0);
        reportCaseMapper.insert(reportCase);
        return reportCase;
    }

    /**
     * 在已有案卷下追加举报明细时刷新案卷汇总状态。
     *
     * @param reportCase    已存在的举报案卷
     * @param reportContent 新提交的举报说明
     */
    private void refreshCaseOnNewDetail(ReportCase reportCase, String reportContent) {
        // 1. 复用旧案卷时，只刷新最近举报时间、举报次数和优先级，不重建 case。
        reportCase.setLatestReportTime(LocalDateTime.now());
        reportCase.setReportCount((reportCase.getReportCount() == null ? 0 : reportCase.getReportCount()) + 1);
        if (reportCase.getPriorityLevel() == null || reportCase.getPriorityLevel() == 0) {
            reportCase.setPriorityLevel(reportContent == null || reportContent.isBlank() ? 0 : 1);
        }
        reportCaseMapper.updateById(reportCase);
    }

    /**
     * 查询同一目标下仍在处理中的举报案卷。
     *
     * @param reportType 举报类型
     * @param targetId   被举报目标 ID
     * @return 处理中案卷；不存在时返回 null
     */
    private ReportCase findActiveCase(Integer reportType, Long targetId) {
        // 1. 同一目标只要已有处理中案卷，就优先复用，避免重复建 case。
        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, reportType)
                .eq(ReportCase::getTargetId, targetId)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getCaseStatus, CASE_STATUS_PROCESSING)
                .last("LIMIT 1");
        return reportCaseMapper.selectOne(query);
    }

    /**
     * 加载并校验举报案卷。
     *
     * @param reportType 举报类型
     * @param reportId   举报单 ID
     * @return 合法案卷；不存在或类型不匹配时返回 null
     */
    private ReportCase loadCase(Integer reportType, Long reportId) {
        // 1. 案卷不存在、已删除或类型不匹配时都统一视为无效。
        ReportCase reportCase = reportCaseMapper.selectById(reportId);
        if (reportCase == null || Integer.valueOf(1).equals(reportCase.getIsDelete())) {
            return null;
        }
        if (!Integer.valueOf(reportType).equals(reportCase.getReportType())) {
            return null;
        }
        return reportCase;
    }

    /**
     * 判断当前举报人是否已在该案卷下提交过举报明细。
     *
     * @param caseId      举报案卷 ID
     * @param reporterId  举报人用户 ID
     * @return true 表示已经提交过
     */
    private boolean existsReporterDetail(Long caseId, Long reporterId) {
        // 1. 用 caseId + reporterId 判断当前用户是否已在同一案卷里提交过举报。
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getReporterId, reporterId)
                .eq(ReportDetail::getIsDelete, 0)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query) != null;
    }

    /**
     * 查询某个案卷最新的一条举报明细。
     *
     * @param caseId 举报案卷 ID
     * @return 最新举报明细
     */
    private ReportDetail findLatestDetail(Long caseId) {
        // 1. 管理端和状态展示都优先看最新一条举报明细。
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getIsDelete, 0)
                .orderByDesc(ReportDetail::getCreateTime)
                .orderByDesc(ReportDetail::getId)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query);
    }

    /**
     * 查询某个举报人在指定案卷下最新的一条举报明细。
     *
     * @param caseId     举报案卷 ID
     * @param reporterId 举报人用户 ID
     * @return 最新举报明细
     */
    private ReportDetail findLatestReporterDetail(Long caseId, Long reporterId) {
        // 1. 举报中心展示“我发起的举报”时，需要按当前用户维度取最新明细。
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, caseId)
                .eq(ReportDetail::getReporterId, reporterId)
                .eq(ReportDetail::getIsDelete, 0)
                .orderByDesc(ReportDetail::getCreateTime)
                .orderByDesc(ReportDetail::getId)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query);
    }

    /**
     * 统计举报案卷的申诉次数。
     *
     * @param reportType 举报类型
     * @param reportId   举报单 ID
     * @return 申诉次数
     */
    private int getAppealCount(Integer reportType, Long reportId) {
        // 1. 一个案卷下累计有多少条未删除申诉记录，直接决定还能否继续申诉。
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, reportId)
                .eq(Appeal::getIsDelete, 0);
        Long count = appealMapper.selectCount(query);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 判断案卷下是否存在待处理申诉。
     *
     * @param reportType 举报类型
     * @param reportId   举报单 ID
     * @return true 表示存在待处理申诉
     */
    private boolean hasPendingAppeal(Integer reportType, Long reportId) {
        // 1. 只要存在一条待处理申诉，就不应再开放新的申诉入口。
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, reportId)
                .eq(Appeal::getAppealStatus, 0)
                .eq(Appeal::getIsDelete, 0)
                .last("LIMIT 1");
        return appealMapper.selectOne(query) != null;
    }

    /**
     * 判断当前视角下是否仍可继续申诉。
     *
     * @param reportCase     举报案卷
     * @param appealCount    已申诉次数
     * @param pendingAppeal  是否存在待处理申诉
     * @param viewRole       当前视角角色
     * @return true 表示可以继续申诉
     */
    private boolean canAppeal(ReportCase reportCase, int appealCount, boolean pendingAppeal, String viewRole) {
        // 1. 有待处理申诉或已达次数上限时，一律不可再申诉。
        int maxAppealCount = resolveMaxAppealCount(reportCase);
        if (reportCase == null || pendingAppeal || appealCount >= maxAppealCount) {
            return false;
        }
        // 2. 举报方只能对“确认未违规”结果申诉；被举报方只能对“确认违规”结果申诉。
        if ("reporter".equals(viewRole)) {
            return Integer.valueOf(ADMIN_STATUS_REJECTED).equals(reportCase.getAdminStatus());
        }
        return Integer.valueOf(ADMIN_STATUS_APPROVED).equals(reportCase.getAdminStatus());
    }

    /**
     * 解析当前案卷允许的最大申诉次数。
     *
     * @param reportCase 举报案卷
     * @return 最大申诉次数
     */
    private int resolveMaxAppealCount(ReportCase reportCase) {
        // 1. AI 初审且最终确认违规的消息举报，允许更高申诉次数。
        if (reportCase != null
                && Integer.valueOf(REPORT_TYPE_MESSAGE).equals(reportCase.getReportType())
                && Integer.valueOf(1).equals(reportCase.getAiCheckResult())
                && Integer.valueOf(ADMIN_ACTION_CONFIRM_VIOLATION).equals(reportCase.getAdminAction())) {
            return MAX_APPEAL_COUNT_AI;
        }
        return MAX_APPEAL_COUNT_MANUAL;
    }

    /**
     * 构建举报状态展示文案。
     *
     * @param reportCase    举报案卷
     * @param pendingAppeal 是否存在待处理申诉
     * @return 状态展示文案
     */
    private String buildDisplayStatus(ReportCase reportCase, boolean pendingAppeal) {
        // 1. 申诉处理中优先级最高，直接覆盖普通审核状态文案。
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

    /**
     * 构建举报中心列表项。
     *
     * @param reportCase 举报案卷
     * @param detail     当前视角对应的举报明细
     * @param viewRole   当前视角角色
     * @return 举报中心展示数据
     */
    private Map<String, Object> buildReportCenterItem(ReportCase reportCase, ReportDetail detail, String viewRole) {
        // 1. 先计算申诉次数和待处理申诉状态，后续列表页和详情页都依赖这些字段。
        int appealCount = getAppealCount(reportCase.getReportType(), reportCase.getId());
        boolean pendingAppeal = hasPendingAppeal(reportCase.getReportType(), reportCase.getId());

        // 2. 再组装举报中心通用展示字段。
        Map<String, Object> item = new HashMap<>();
        item.put("id", chatSupportService.stringifyId(reportCase.getId()));
        item.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("reportType", reportCase.getReportType());
        item.put("reportReason", detail == null ? null : detail.getReportReason());
        item.put("reportContent", detail == null ? "" : detail.getReportContent());
        item.put("reportEvidence", detail == null ? "" : detail.getReportEvidence());
        item.put("reportStatus", reportCase.getAdminStatus());
        item.put("handleStatus", reportCase.getAdminStatus());
        item.put("handleStatusText", buildDisplayStatus(reportCase, pendingAppeal));
        item.put("adminAction", reportCase.getAdminAction());
        item.put("adminNote", reportCase.getAdminNote());
        item.put("createTime", detail == null ? reportCase.getCreateTime() : detail.getCreateTime());
        item.put("viewRole", viewRole);
        item.put("viewRoleText", "reported".equals(viewRole) ? "我作为被举报方" : "我发起的举报");
        item.put("appealCount", appealCount);
        item.put("hasPendingAppeal", pendingAppeal);
        item.put("canAppeal", canAppeal(reportCase, appealCount, pendingAppeal, viewRole));
        item.put("targetId", chatSupportService.stringifyId(reportCase.getTargetId()));
        // 3. 最后按举报类型补充不同的目标字段名，方便前端直接读取。
        if (Integer.valueOf(REPORT_TYPE_USER).equals(reportCase.getReportType())) {
            item.put("reportedUserId", chatSupportService.stringifyId(reportCase.getTargetId()));
        } else if (Integer.valueOf(REPORT_TYPE_TEAM).equals(reportCase.getReportType())) {
            item.put("reportedTeamId", chatSupportService.stringifyId(reportCase.getTargetId()));
        } else {
            item.put("messageId", chatSupportService.stringifyId(reportCase.getTargetId()));
        }
        return item;
    }

    /**
     * 构建管理端举报列表项。
     *
     * @param reportCase 举报案卷
     * @return 管理端展示数据
     */
    private Map<String, Object> buildAdminListItem(ReportCase reportCase) {
        // 1. 管理端列表默认展示最新举报明细，方便管理员快速判断上下文。
        ReportDetail latestDetail = findLatestDetail(reportCase.getId());
        Map<String, Object> item = new HashMap<>();
        item.put("id", chatSupportService.stringifyId(reportCase.getId()));
        item.put("reportId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("caseId", chatSupportService.stringifyId(reportCase.getId()));
        item.put("reporterId", latestDetail == null ? null : latestDetail.getReporterId());
        item.put("reportReason", latestDetail == null ? null : latestDetail.getReportReason());
        item.put("reportContent", latestDetail == null ? "" : latestDetail.getReportContent());
        item.put("reportEvidence", latestDetail == null ? "" : latestDetail.getReportEvidence());
        item.put("reportStatus", reportCase.getAdminStatus());
        item.put("adminStatus", reportCase.getAdminStatus());
        item.put("adminAction", reportCase.getAdminAction());
        item.put("adminNote", reportCase.getAdminNote());
        item.put("createTime", latestDetail == null ? reportCase.getCreateTime() : latestDetail.getCreateTime());
        item.put("updateTime", reportCase.getUpdateTime());
        // 2. 根据举报类型补充不同的目标字段。
        if (Integer.valueOf(REPORT_TYPE_USER).equals(reportCase.getReportType())) {
            item.put("reportedUserId", chatSupportService.stringifyId(reportCase.getTargetId()));
        } else if (Integer.valueOf(REPORT_TYPE_TEAM).equals(reportCase.getReportType())) {
            item.put("reportedTeamId", chatSupportService.stringifyId(reportCase.getTargetId()));
        }
        return item;
    }

    /**
     * 构建用户举报上下文消息。
     *
     * @param reportCase 举报案卷
     * @param detail     举报明细
     * @return 聊天上下文数据
     */
    private Map<String, Object> buildUserReportContext(ReportCase reportCase, ReportDetail detail) {
        // 1. 默认先返回空上下文骨架，避免前端判空复杂。
        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        int totalCount = 0;

        if (detail != null && detail.getReporterId() != null && reportCase.getTargetId() != null) {
            // 2. 用户举报的上下文来自举报人与被举报用户的私聊会话，在举报时间前后窗口内截取。
            String conversationId = chatSupportService.buildConvId(detail.getReporterId(), reportCase.getTargetId());
            LocalDateTime endTime = detail.getCreateTime() == null ? LocalDateTime.now() : detail.getCreateTime();
            LocalDateTime startTime = endTime.minusMinutes(REPORT_CONTEXT_WINDOW_MINUTES);

            LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<>();
            query.eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getIsDelete, 0)
                    .between(ChatMessage::getCreateTime, startTime, endTime)
                    .orderByAsc(ChatMessage::getCreateTime)
                    .orderByAsc(ChatMessage::getId);
            List<ChatMessage> raw = chatMessageMapper.selectList(query);
            totalCount = raw.size();
            // 3. 最多只截取固定条数上下文，避免超长会话拖慢后台审核页。
            for (int i = 0; i < raw.size() && i < REPORT_CONTEXT_MAX_MESSAGES; i++) {
                messages.add(buildChatContextItem(raw.get(i)));
            }
        }

        // 4. 统一补齐窗口大小、总数和是否截断标记。
        context.put("windowMinutes", REPORT_CONTEXT_WINDOW_MINUTES);
        context.put("totalCount", totalCount);
        context.put("truncated", totalCount > messages.size());
        context.put("messages", messages);
        return context;
    }

    /**
     * 构建团队举报上下文消息。
     *
     * @param reportCase 举报案卷
     * @param detail     举报明细
     * @return 聊天上下文数据
     */
    private Map<String, Object> buildTeamReportContext(ReportCase reportCase, ReportDetail detail) {
        // 1. 默认先返回空上下文骨架，保持管理端接口返回结构稳定。
        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        int totalCount = 0;

        if (detail != null) {
            // 2. 团队举报上下文来自团队会话，在举报时间前后窗口内截取最近消息。
            String conversationId = "team_" + reportCase.getTargetId();
            LocalDateTime endTime = detail.getCreateTime() == null ? LocalDateTime.now() : detail.getCreateTime();
            LocalDateTime startTime = endTime.minusMinutes(REPORT_CONTEXT_WINDOW_MINUTES);

            LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<>();
            query.eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getIsDelete, 0)
                    .between(ChatMessage::getCreateTime, startTime, endTime)
                    .orderByAsc(ChatMessage::getCreateTime)
                    .orderByAsc(ChatMessage::getId);
            List<ChatMessage> raw = chatMessageMapper.selectList(query);
            totalCount = raw.size();
            // 3. 控制上下文返回条数，避免大群消息导致审核页过重。
            for (int i = 0; i < raw.size() && i < REPORT_CONTEXT_MAX_MESSAGES; i++) {
                messages.add(buildChatContextItem(raw.get(i)));
            }
        }

        // 4. 统一补齐窗口大小、总数和是否截断标记。
        context.put("windowMinutes", REPORT_CONTEXT_WINDOW_MINUTES);
        context.put("totalCount", totalCount);
        context.put("truncated", totalCount > messages.size());
        context.put("messages", messages);
        return context;
    }

    /**
     * 将消息实体转换为审核上下文展示项。
     *
     * @param message 聊天消息实体
     * @return 上下文展示数据
     */
    private Map<String, Object> buildChatContextItem(ChatMessage message) {
        // 1. 同时保留原始内容和可读展示文本，方便后台在不同消息类型下统一展示。
        Map<String, Object> item = new HashMap<>();
        item.put("id", chatSupportService.stringifyId(message.getId()));
        item.put("senderId", message.getSenderId());
        item.put("msgType", message.getMsgType());
        item.put("msgContent", message.getMsgContent());
        item.put("displayText", chatSupportService.extractSearchableText(message.getMsgType(), message.getMsgContent()));
        item.put("createTime", message.getCreateTime());
        return item;
    }

    /**
     * 构建用户简要信息。
     *
     * @param user 用户实体
     * @return 简要展示数据
     */
    private Map<String, Object> buildUserSimpleInfo(User user) {
        // 1. 举报上下文中只返回审核需要的关键字段，避免无关敏感信息暴露过多。
        if (user == null) {
            return null;
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("userAccount", user.getUserAccount());
        info.put("userNickname", user.getUserNickname());
        info.put("userAvatar", user.getUserAvatar());
        info.put("userIntro", user.getUserIntro());
        info.put("userTags", user.getUserTags());
        info.put("globalPunishType", user.getGlobalPunishType());
        return info;
    }

    /**
     * 构建团队简要信息。
     *
     * @param team 团队实体
     * @return 简要展示数据
     */
    private Map<String, Object> buildTeamSimpleInfo(Team team) {
        // 1. 团队上下文中补充团队基础资料和当前全员禁言状态。
        if (team == null) {
            return null;
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", team.getId());
        info.put("teamName", team.getTeamName());
        info.put("teamAvatar", team.getTeamAvatar());
        info.put("teamIntro", team.getTeamIntro());
        info.put("teamTags", team.getTeamTags());
        info.put("creatorId", team.getCreatorId());
        info.put("teamAllMute", team.getTeamAllMute());
        info.put("teamAllMuteUnpunishTime", team.getTeamAllMuteUnpunishTime());
        return info;
    }

    /**
     * 加载当前用户管理的团队集合。
     *
     * @param userId 当前用户 ID
     * @return 队长/管理员身份的团队 ID 集合
     */
    private Set<Long> loadManagedTeamIds(Long userId) {
        // 1. 举报中心需要识别“我作为团队管理者的被举报记录”，所以要先批量拉取可管理团队。
        Set<Long> teamIds = new HashSet<>();
        LambdaQueryWrapper<TeamMember> query = new LambdaQueryWrapper<>();
        query.eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsDelete, 0)
                .eq(TeamMember::getIsQuit, 0)
                .in(TeamMember::getRoleType, 1, 2);
        for (TeamMember member : teamMemberMapper.selectList(query)) {
            if (member.getTeamId() != null) {
                teamIds.add(member.getTeamId());
            }
        }
        return teamIds;
    }

    /**
     * 判断用户是否为团队管理者。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return true 表示队长或管理员
     */
    private boolean isTeamManager(Long teamId, Long userId) {
        // 1. 只有队长和管理员才算团队举报链路中的“被举报侧”。
        LambdaQueryWrapper<TeamMember> query = new LambdaQueryWrapper<>();
        query.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsDelete, 0)
                .eq(TeamMember::getIsQuit, 0)
                .in(TeamMember::getRoleType, 1, 2)
                .last("LIMIT 1");
        return teamMemberMapper.selectOne(query) != null;
    }

    /**
     * 加载案卷内全部举报人。
     *
     * @param caseId 举报案卷 ID
     * @return 举报人用户 ID 列表
     */
    private List<Long> loadReporterIds(Long caseId) {
        // 1. 一个案卷可能聚合多条举报明细，因此这里要做去重收集。
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

    /**
     * 加载团队管理者列表。
     *
     * @param teamId 团队 ID
     * @return 队长和管理员用户 ID 列表
     */
    private List<Long> loadTeamManagerIds(Long teamId) {
        // 1. 团队举报处理结果默认通知队长和管理员两类角色。
        LambdaQueryWrapper<TeamMember> query = new LambdaQueryWrapper<>();
        query.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsDelete, 0)
                .eq(TeamMember::getIsQuit, 0)
                .in(TeamMember::getRoleType, 1, 2);
        List<Long> ids = new ArrayList<>();
        for (TeamMember member : teamMemberMapper.selectList(query)) {
            if (member.getUserId() != null) {
                ids.add(member.getUserId());
            }
        }
        return ids;
    }

    /**
     * 把可能为 null 的字符串安全转为空串并去掉首尾空格。
     *
     * @param value 原始字符串
     * @return 处理后的字符串
     */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
