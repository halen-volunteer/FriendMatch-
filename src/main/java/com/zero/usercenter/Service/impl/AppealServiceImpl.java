package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.AppealHandleDTO;
import com.zero.usercenter.DTO.AppealSubmitDTO;
import com.zero.usercenter.DTO.PunishCancelDTO;
import com.zero.usercenter.DTO.PunishDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.AppealAdminHistoryMapper;
import com.zero.usercenter.Mapper.AppealMapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.PunishMsgRelationMapper;
import com.zero.usercenter.Mapper.ReportCaseMapper;
import com.zero.usercenter.Mapper.ReportDetailMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserPunishLogMapper;
import com.zero.usercenter.Model.Appeal;
import com.zero.usercenter.Model.AppealAdminHistory;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.PunishMsgRelation;
import com.zero.usercenter.Model.ReportCase;
import com.zero.usercenter.Model.ReportDetail;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.UserPunishLog;
import com.zero.usercenter.Service.AppealService;
import com.zero.usercenter.Service.PunishService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.GRADIENT_MUTE_DURATIONS;
import static com.zero.usercenter.utils.Number.TEAM_ALL_MUTE_KEY;

@Service
@Slf4j
public class AppealServiceImpl implements AppealService {

    private static final int REPORT_TYPE_USER = 1;
    private static final int REPORT_TYPE_MESSAGE = 2;
    private static final int REPORT_TYPE_TEAM = 3;

    private static final int ADMIN_STATUS_PENDING = 0;
    private static final int ADMIN_STATUS_APPROVED = 1;
    private static final int ADMIN_STATUS_REJECTED = 2;

    private static final int ADMIN_ACTION_CONFIRM_VIOLATION = 1;
    private static final int ADMIN_ACTION_CONFIRM_NORMAL = 2;

    private static final int APPELLANT_TYPE_REPORTER = 1;
    private static final int APPELLANT_TYPE_REPORTED = 2;

    private static final int APPEAL_STATUS_PENDING = 0;
    private static final int APPEAL_STATUS_APPROVED = 1;
    private static final int APPEAL_STATUS_REJECTED = 2;

    private static final int CASE_STATUS_FINISHED = 1;

    private static final int MAX_APPEAL_COUNT_AI = 3;
    private static final int MAX_APPEAL_COUNT_MANUAL = 2;

    private static final long SUBMIT_LOCK_SECONDS = 10L;
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end";

    @Resource
    private AppealMapper appealMapper;

    @Resource
    private ReportCaseMapper reportCaseMapper;

    @Resource
    private ReportDetailMapper reportDetailMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private AppealAdminHistoryMapper appealAdminHistoryMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private PunishMsgRelationMapper punishMsgRelationMapper;

    @Resource
    private UserPunishLogMapper userPunishLogMapper;

    @Resource
    private PunishService punishService;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ReportAdminAssignSupport reportAdminAssignSupport;

    @Override
    @Transactional
    public Result submitAppeal(AppealSubmitDTO dto) {
        // 1. 先确认登录和申诉基本参数，确保后续逻辑都围绕有效举报单展开。
        Long userId = UserHolder.getUserId();
        log.info("[appeal-submit] start userId={}, reportType={}, relatedReportId={}",
                userId,
                dto == null ? null : dto.getRelatedReportType(),
                dto == null ? null : dto.getRelatedReportId());
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (dto == null || dto.getRelatedReportId() == null || dto.getRelatedReportType() == null) {
            log.warn("[appeal-submit] reject because related report info missing, userId={}", userId);
            return Result.fail("关联举报信息不能为空");
        }
        if (dto.getAppealReason() == null || dto.getAppealReason().isBlank()) {
            log.warn("[appeal-submit] reject because appealReason blank, userId={}, reportType={}, relatedReportId={}",
                    userId, dto.getRelatedReportType(), dto.getRelatedReportId());
            return Result.fail("申诉理由不能为空");
        }
        if (dto.getAppealReason().trim().length() > 1000) {
            log.warn("[appeal-submit] reject because appealReason too long, userId={}, reportType={}, relatedReportId={}",
                    userId, dto.getRelatedReportType(), dto.getRelatedReportId());
            return Result.fail("申诉理由不能超过 1000 个字符");
        }

        Long relatedReportId = normalizeRelatedReportId(dto.getRelatedReportType(), dto.getRelatedReportId());
        int appellantType = assertAppealPermission(userId, dto.getRelatedReportType(), relatedReportId);

        // 2. 以“举报类型 + 举报单”加短锁，防止同一举报被并发重复发起申诉。
        String lockKey = buildSubmitLockKey(dto.getRelatedReportType(), relatedReportId);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                SUBMIT_LOCK_SECONDS,
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("[appeal-submit] reject because submit lock not acquired, userId={}, reportType={}, relatedReportId={}",
                    userId, dto.getRelatedReportType(), relatedReportId);
            return Result.fail("当前举报正在提交申诉，请稍后再试");
        }

        try {
            // 3. 申诉前要检查是否已有待处理申诉，以及本案卷还能否继续申诉。
            assertNoPendingAppeal(dto.getRelatedReportType(), relatedReportId);
            int usedCount = getAppealCount(dto.getRelatedReportType(), relatedReportId);
            int maxAppealCount = resolveMaxAppealCount(dto.getRelatedReportType(), relatedReportId);
            if (usedCount >= maxAppealCount) {
                log.warn("[appeal-submit] reject because appeal count exceeded, userId={}, reportType={}, relatedReportId={}, usedCount={}, maxCount={}",
                        userId, dto.getRelatedReportType(), relatedReportId, usedCount, maxAppealCount);
                return Result.fail("该举报最多只支持 " + maxAppealCount + " 次申诉");
            }

            Long adminId = reportAdminAssignSupport.allocateAdmin(relatedReportId, dto.getRelatedReportType(), true);
            if (adminId == null) {
                log.warn("[appeal-submit] reject because no admin available, userId={}, reportType={}, relatedReportId={}",
                        userId, dto.getRelatedReportType(), relatedReportId);
                return Result.fail("当前暂无可分配的管理员，请稍后重试");
            }

            // 4. 创建申诉单，并把处理管理员、轮次、关联处罚信息一起固化下来。
            Appeal appeal = new Appeal();
            appeal.setAppellantId(userId);
            appeal.setAppellantType(appellantType);
            appeal.setRelatedReportId(relatedReportId);
            appeal.setRelatedCaseId(resolveRelatedCaseId(dto.getRelatedReportType(), relatedReportId));
            appeal.setRelatedReportType(dto.getRelatedReportType());
            appeal.setRelatedPunishId(resolveRelatedPunishId(dto.getRelatedReportType(), relatedReportId));
            appeal.setAppealRound(usedCount + 1);
            appeal.setAppealReason(dto.getAppealReason().trim());
            appeal.setAppealEvidence(dto.getAppealEvidence() == null ? "" : dto.getAppealEvidence().trim());
            appeal.setAppealStatus(APPEAL_STATUS_PENDING);
            appeal.setAdminId(adminId);
            appeal.setAssignTime(LocalDateTime.now());
            appeal.setLastDispatchTime(LocalDateTime.now());
            appeal.setDispatchCount(1);
            appeal.setIsDelete(0);
            appealMapper.insert(appeal);
            log.info("[appeal-submit] appeal persisted appealId={}, userId={}, reportType={}, relatedReportId={}, appellantType={}, adminId={}, round={}",
                    appeal.getId(), userId, dto.getRelatedReportType(), relatedReportId, appellantType, adminId, appeal.getAppealRound());

            // 5. 申诉单创建后，同步案卷申诉次数并记录管理员分配历史。
            recordAppealAdminHistory(appeal, adminId, "assign", "系统已分配本轮申诉处理管理员");
            syncCaseOnAppealSubmit(appeal);
            return Result.ok(buildAppealView(appeal));
        } finally {
            // 6. 无论成功失败都要释放申诉提交锁，避免后续请求被一直阻塞。
            releaseSubmitLock(lockKey, lockValue);
            log.info("[appeal-submit] finish userId={}, reportType={}, relatedReportId={}",
                    userId, dto.getRelatedReportType(), relatedReportId);
        }
    }

    @Override
    public Result getMyAppeals(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        Page<Appeal> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getAppellantId, userId)
                .eq(Appeal::getIsDelete, 0)
                .orderByDesc(Appeal::getCreateTime);
        Page<Appeal> result = appealMapper.selectPage(pageObj, query);
        return Result.ok(buildAppealViews(result.getRecords()), result.getTotal());
    }

    @Override
    @RequireAdmin
    public Result getPendingAppeals(int page, int pageSize) {
        Long adminId = UserHolder.getUserId();
        Page<Appeal> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getAppealStatus, APPEAL_STATUS_PENDING)
                .eq(Appeal::getAdminId, adminId)
                .eq(Appeal::getIsDelete, 0)
                .orderByAsc(Appeal::getCreateTime);
        Page<Appeal> result = appealMapper.selectPage(pageObj, query);
        return Result.ok(buildAppealViews(result.getRecords()), result.getTotal());
    }

    @Override
    @Transactional
    @RequireAdmin
    public Result handleAppeal(AppealHandleDTO dto) {
        // 1. 先确认申诉单存在、状态仍待处理，并且归当前管理员负责。
        Long adminId = UserHolder.getUserId();
        log.info("[appeal-handle] start adminId={}, appealId={}, status={}",
                adminId,
                dto == null ? null : dto.getAppealId(),
                dto == null ? null : dto.getAppealStatus());
        if (dto == null || dto.getAppealId() == null) {
            log.warn("[appeal-handle] reject because appealId null, adminId={}", adminId);
            return Result.fail("申诉 ID 不能为空");
        }
        if (dto.getAppealStatus() == null
                || (dto.getAppealStatus() != APPEAL_STATUS_APPROVED && dto.getAppealStatus() != APPEAL_STATUS_REJECTED)) {
            log.warn("[appeal-handle] reject because status invalid, adminId={}, appealId={}, status={}",
                    adminId, dto.getAppealId(), dto.getAppealStatus());
            return Result.fail("申诉处理状态不合法");
        }

        Appeal appeal = appealMapper.selectById(dto.getAppealId());
        if (appeal == null || Integer.valueOf(1).equals(appeal.getIsDelete())) {
            log.warn("[appeal-handle] reject because appeal missing, adminId={}, appealId={}", adminId, dto.getAppealId());
            return Result.fail("申诉记录不存在");
        }
        if (!Integer.valueOf(APPEAL_STATUS_PENDING).equals(appeal.getAppealStatus())) {
            log.warn("[appeal-handle] reject because appeal already handled, adminId={}, appealId={}, currentStatus={}",
                    adminId, dto.getAppealId(), appeal.getAppealStatus());
            return Result.fail("该申诉已处理");
        }
        if (!adminId.equals(appeal.getAdminId())) {
            log.warn("[appeal-handle] reject because appeal not assigned, adminId={}, appealId={}, assignedAdminId={}",
                    adminId, dto.getAppealId(), appeal.getAdminId());
            return Result.fail("当前申诉不属于您处理");
        }

        String reply = dto.getAdminReply() == null ? "" : dto.getAdminReply().trim();
        appeal.setAppealStatus(dto.getAppealStatus());
        if (appeal.getAcceptTime() == null) {
            appeal.setAcceptTime(LocalDateTime.now());
        }
        appeal.setAdminReply(reply);
        appeal.setProcessTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        // 2. 无论通过还是驳回，都先记录管理员处理历史，保证后续可追溯。
        recordAppealAdminHistory(
                appeal,
                adminId,
                Integer.valueOf(APPEAL_STATUS_APPROVED).equals(dto.getAppealStatus()) ? "approve" : "reject",
                reply
        );

        // 3. 申诉通过时，根据申诉方身份决定是“撤销原处罚”还是“恢复处罚”。
        if (Integer.valueOf(APPEAL_STATUS_APPROVED).equals(dto.getAppealStatus())) {
            if (Integer.valueOf(APPELLANT_TYPE_REPORTED).equals(appeal.getAppellantType())) {
                log.info("[appeal-handle] approved for reported side, rollback punish, adminId={}, appealId={}",
                        adminId, appeal.getId());
                rollbackPunishByAppeal(appeal);
            } else {
                log.info("[appeal-handle] approved for reporter side, apply punish, adminId={}, appealId={}",
                        adminId, appeal.getId());
                applyPunishByAppeal(appeal);
            }
        }

        // 4. 最后把案卷状态反写回举报中心，并通知相关参与方。
        syncCaseAfterAppealHandled(appeal, dto.getAppealStatus(), reply);
        notifyAppealParticipants(appeal, dto.getAppealStatus(), reply);
        log.info("[appeal-handle] completed adminId={}, appealId={}, finalStatus={}",
                adminId, appeal.getId(), dto.getAppealStatus());
        return Result.ok("申诉处理成功");
    }

    /**
     * 校验当前用户是否有权对指定举报单发起申诉，并返回申诉方身份。
     *
     * @param userId          当前用户 ID
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 申诉方身份：举报方 / 被举报方
     */
    private int assertAppealPermission(Long userId, Integer reportType, Long relatedReportId) {
        // 1. 先加载举报案卷，后续所有权限判断都围绕该案卷结论展开。
        ReportCase reportCase = loadReportCase(reportType, relatedReportId);
        if (reportCase == null) {
            log.warn("[appeal-permission] report missing userId={}, reportType={}, relatedReportId={}",
                    userId, reportType, relatedReportId);
            throw new BusinessException("关联举报不存在");
        }

        // 2. 判断当前用户属于举报方还是被举报方，二者都不是则直接拒绝申诉。
        boolean reporterSide = isReporterSide(userId, relatedReportId);
        boolean reportedSide = isReportedSide(userId, reportType, reportCase);
        if (!reporterSide && !reportedSide) {
            log.warn("[appeal-permission] no permission userId={}, reportType={}, relatedReportId={}",
                    userId, reportType, relatedReportId);
            throw new BusinessException("当前用户无权对该举报发起申诉");
        }

        // 3. 举报方只能对“管理员判定未违规”的结果继续申诉。
        if (reporterSide) {
            if (!Integer.valueOf(ADMIN_STATUS_REJECTED).equals(reportCase.getAdminStatus())) {
                log.warn("[appeal-permission] reporter side not allowed userId={}, reportType={}, relatedReportId={}, adminStatus={}",
                        userId, reportType, relatedReportId, reportCase.getAdminStatus());
                throw new BusinessException("只有确认未违规的举报结果才允许举报人继续申诉");
            }
            return APPELLANT_TYPE_REPORTER;
        }

        // 4. 被举报方只能对“管理员判定违规并已处罚”的结果继续申诉。
        if (!Integer.valueOf(ADMIN_STATUS_APPROVED).equals(reportCase.getAdminStatus())) {
            log.warn("[appeal-permission] reported side not allowed userId={}, reportType={}, relatedReportId={}, adminStatus={}",
                    userId, reportType, relatedReportId, reportCase.getAdminStatus());
            throw new BusinessException("只有确认违规后的处罚结果才允许被举报方继续申诉");
        }
        return APPELLANT_TYPE_REPORTED;
    }

    /**
     * 断言当前举报单不存在待处理申诉。
     *
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     */
    private void assertNoPendingAppeal(Integer reportType, Long relatedReportId) {
        // 1. 同一举报在任一时刻只允许存在一条待处理申诉，避免管理员处理竞态。
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, relatedReportId)
                .eq(Appeal::getAppealStatus, APPEAL_STATUS_PENDING)
                .eq(Appeal::getIsDelete, 0)
                .last("LIMIT 1");
        if (appealMapper.selectOne(query) != null) {
            throw new BusinessException("当前举报已有待处理申诉，请等待管理员处理后再提交");
        }
    }

    /**
     * 统计指定举报单已提交的申诉次数。
     *
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 已有申诉次数
     */
    private int getAppealCount(Integer reportType, Long relatedReportId) {
        // 1. 申诉轮次按同一举报单下全部未删除申诉记录累计。
        LambdaQueryWrapper<Appeal> query = new LambdaQueryWrapper<>();
        query.eq(Appeal::getRelatedReportType, reportType)
                .eq(Appeal::getRelatedReportId, relatedReportId)
                .eq(Appeal::getIsDelete, 0);
        Long count = appealMapper.selectCount(query);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 解析当前举报单允许的最大申诉次数。
     *
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 最大申诉次数
     */
    private int resolveMaxAppealCount(Integer reportType, Long relatedReportId) {
        // 1. AI 初审且确认违规的消息举报给更高申诉次数，其余场景按人工复核上限处理。
        ReportCase reportCase = loadReportCase(reportType, relatedReportId);
        if (reportCase == null) {
            return MAX_APPEAL_COUNT_MANUAL;
        }
        if (Integer.valueOf(REPORT_TYPE_MESSAGE).equals(reportType)
                && Integer.valueOf(1).equals(reportCase.getAiCheckResult())
                && Integer.valueOf(ADMIN_ACTION_CONFIRM_VIOLATION).equals(reportCase.getAdminAction())) {
            return MAX_APPEAL_COUNT_AI;
        }
        return MAX_APPEAL_COUNT_MANUAL;
    }

    /**
     * 推断当前用户在指定申诉场景中的实际身份。
     *
     * @param userId          当前用户 ID
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 申诉方身份
     */
    private Integer resolveActualAppellantType(Long userId, Integer reportType, Long relatedReportId) {
        // 1. 优先根据举报案卷和双方关系判断当前用户到底属于举报方还是被举报方。
        ReportCase reportCase = loadReportCase(reportType, relatedReportId);
        if (reportCase == null) {
            return APPELLANT_TYPE_REPORTED;
        }
        if (isReporterSide(userId, relatedReportId)) {
            return APPELLANT_TYPE_REPORTER;
        }
        if (isReportedSide(userId, reportType, reportCase)) {
            return APPELLANT_TYPE_REPORTED;
        }
        return APPELLANT_TYPE_REPORTED;
    }

    /**
     * 解析申诉关联的举报案卷 ID。
     *
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 举报案卷 ID
     */
    private Long resolveRelatedCaseId(Integer reportType, Long relatedReportId) {
        return relatedReportId;
    }

    /**
     * 解析申诉关联的处罚记录 ID。
     *
     * @param reportType      举报类型
     * @param relatedReportId 关联举报单 ID
     * @return 处罚记录 ID；无处罚记录时返回 null
     */
    private Long resolveRelatedPunishId(Integer reportType, Long relatedReportId) {
        // 1. 只有用户举报和消息举报场景会落处罚记录，团队举报走团队治理链路。
        if (reportType != REPORT_TYPE_MESSAGE && reportType != REPORT_TYPE_USER) {
            return null;
        }
        ReportCase reportCase = loadReportCase(reportType, relatedReportId);
        if (reportCase == null) {
            return null;
        }
        if (reportType == REPORT_TYPE_MESSAGE) {
            // 2. 消息举报需要通过“消息-处罚关联表”回溯最近仍生效的处罚记录。
            return findLatestActivePunishLogIdByMessage(reportCase.getTargetId());
        }

        // 3. 用户举报直接找目标用户最近一条仍有效的处罚记录。
        LambdaQueryWrapper<UserPunishLog> query = new LambdaQueryWrapper<>();
        query.eq(UserPunishLog::getPunishUserId, reportCase.getTargetId())
                .eq(UserPunishLog::getIsCancel, 0)
                .orderByDesc(UserPunishLog::getId)
                .last("LIMIT 1");
        UserPunishLog punishLog = userPunishLogMapper.selectOne(query);
        return punishLog == null ? null : punishLog.getId();
    }

    /**
     * 申诉提交后同步更新举报案卷。
     *
     * @param appeal 新建的申诉单
     */
    private void syncCaseOnAppealSubmit(Appeal appeal) {
        // 1. 申诉提交后，把案卷上的 appealCount 同步到最新轮次，方便举报中心展示。
        ReportCase reportCase = loadReportCase(appeal.getRelatedReportType(), appeal.getRelatedReportId());
        if (reportCase == null) {
            return;
        }
        reportCase.setAppealCount(appeal.getAppealRound());
        reportCaseMapper.updateById(reportCase);
    }

    /**
     * 申诉处理完成后同步更新举报案卷终态。
     *
     * @param appeal       申诉单
     * @param appealStatus 申诉处理结果
     * @param reply        管理员处理说明
     */
    private void syncCaseAfterAppealHandled(Appeal appeal, Integer appealStatus, String reply) {
        // 1. 申诉处理结束后，举报案卷的最终结论要跟着申诉结果一起翻转或维持。
        ReportCase reportCase = loadReportCase(appeal.getRelatedReportType(), appeal.getRelatedReportId());
        if (reportCase == null) {
            log.warn("[appeal-case-sync] skip because report missing, appealId={}, reportType={}, relatedReportId={}",
                    appeal == null ? null : appeal.getId(),
                    appeal == null ? null : appeal.getRelatedReportType(),
                    appeal == null ? null : appeal.getRelatedReportId());
            return;
        }

        reportCase.setAppealCount(appeal.getAppealRound());
        reportCase.setCaseStatus(CASE_STATUS_FINISHED);
        reportCase.setAdminId(appeal.getAdminId());
        reportCase.setProcessTime(LocalDateTime.now());
        reportCase.setAdminNote(reply == null ? "" : reply);

        // 2. 举报方申诉通过意味着“原未违规结论被推翻”；被举报方申诉通过意味着“原处罚被撤销”。
        if (Integer.valueOf(APPELLANT_TYPE_REPORTER).equals(appeal.getAppellantType())) {
            if (Integer.valueOf(APPEAL_STATUS_APPROVED).equals(appealStatus)) {
                reportCase.setAdminStatus(ADMIN_STATUS_APPROVED);
                reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            } else {
                reportCase.setAdminStatus(ADMIN_STATUS_REJECTED);
                reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            }
        } else {
            if (Integer.valueOf(APPEAL_STATUS_APPROVED).equals(appealStatus)) {
                reportCase.setAdminStatus(ADMIN_STATUS_REJECTED);
                reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_NORMAL);
            } else {
                reportCase.setAdminStatus(ADMIN_STATUS_APPROVED);
                reportCase.setAdminAction(ADMIN_ACTION_CONFIRM_VIOLATION);
            }
        }

        // 3. 最终把翻转后的案卷结论写回举报中心。
        reportCaseMapper.updateById(reportCase);
        log.info("[appeal-case-sync] synced case after appeal handled, appealId={}, caseId={}, reportType={}, finalAdminStatus={}, finalAction={}",
                appeal.getId(), reportCase.getId(), appeal.getRelatedReportType(), reportCase.getAdminStatus(), reportCase.getAdminAction());
    }

    /**
     * 申诉通过后撤销原处罚。
     *
     * @param appeal 申诉单
     */
    private void rollbackPunishByAppeal(Appeal appeal) {
        // 1. 被举报方申诉通过后，需要找到原处罚记录并走统一撤销逻辑。
        Long punishLogId = appeal.getRelatedPunishId();
        if (punishLogId == null) {
            punishLogId = resolveRelatedPunishId(appeal.getRelatedReportType(), appeal.getRelatedReportId());
        }
        if (punishLogId == null) {
            log.warn("[appeal-rollback-punish] skip because punishLogId missing, appealId={}, reportType={}, relatedReportId={}",
                    appeal == null ? null : appeal.getId(),
                    appeal == null ? null : appeal.getRelatedReportType(),
                    appeal == null ? null : appeal.getRelatedReportId());
            return;
        }

        // 2. 构造统一撤销 DTO，复用处罚服务中的撤销链路。
        log.info("[appeal-rollback-punish] cancel punish by appeal, appealId={}, punishLogId={}",
                appeal == null ? null : appeal.getId(), punishLogId);
        PunishCancelDTO cancelDTO = new PunishCancelDTO();
        cancelDTO.setPunishLogId(punishLogId);
        cancelDTO.setCancelReason("申诉通过，系统自动撤销原处罚");
        punishService.cancelPunish(cancelDTO);
    }

    /**
     * 举报方申诉通过后补执行处罚。
     *
     * @param appeal 申诉单
     */
    private void applyPunishByAppeal(Appeal appeal) {
        // 1. 举报方申诉通过后，说明原“未违规”结论被推翻，需要补执行处罚。
        ReportCase reportCase = loadReportCase(appeal.getRelatedReportType(), appeal.getRelatedReportId());
        if (reportCase == null) {
            log.warn("[appeal-apply-punish] skip because report missing, appealId={}, reportType={}, relatedReportId={}",
                    appeal == null ? null : appeal.getId(),
                    appeal == null ? null : appeal.getRelatedReportType(),
                    appeal == null ? null : appeal.getRelatedReportId());
            return;
        }

        // 2. 不同举报类型走不同处罚链路：用户处罚、消息发送者处罚、团队治理。
        log.info("[appeal-apply-punish] apply punish by appeal, appealId={}, reportType={}, targetId={}",
                appeal.getId(), appeal.getRelatedReportType(), reportCase.getTargetId());
        if (Integer.valueOf(REPORT_TYPE_USER).equals(appeal.getRelatedReportType())) {
            punishTargetUser(reportCase, "申诉复核改判违规，已执行处罚");
            return;
        }
        if (Integer.valueOf(REPORT_TYPE_MESSAGE).equals(appeal.getRelatedReportType())) {
            punishMessageSender(reportCase, "申诉复核改判违规，已执行处罚");
            return;
        }
        if (Integer.valueOf(REPORT_TYPE_TEAM).equals(appeal.getRelatedReportType())) {
            applyTeamModeration(reportCase);
        }
    }

    /**
     * 对被举报用户执行处罚。
     *
     * @param reportCase 举报案卷
     * @param reason     处罚原因
     */
    private void punishTargetUser(ReportCase reportCase, String reason) {
        // 1. 目标用户不存在时直接跳过，避免生成无效处罚。
        if (reportCase.getTargetId() == null) {
            return;
        }
        // 2. 组装处罚 DTO，复用统一处罚服务落库和通知逻辑。
        PunishDTO punishDTO = new PunishDTO();
        punishDTO.setPunishUserId(reportCase.getTargetId());
        punishDTO.setPunishReason(reason);
        punishDTO.setOperateType(2);
        punishService.punishUser(punishDTO);
    }

    /**
     * 对被举报消息的发送者执行处罚。
     *
     * @param reportCase 举报案卷
     * @param reason     处罚原因
     */
    private void punishMessageSender(ReportCase reportCase, String reason) {
        // 1. 先回查消息和发送者，消息不存在时无法继续处罚。
        if (reportCase.getTargetId() == null) {
            return;
        }
        ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
        if (message == null || message.getSenderId() == null) {
            return;
        }

        // 2. 在处罚 DTO 中带上消息 ID 和申诉改判说明，方便后续审计追踪。
        PunishDTO punishDTO = new PunishDTO();
        punishDTO.setPunishUserId(message.getSenderId());
        punishDTO.setPunishReason(reason);
        punishDTO.setMsgId(message.getId());
        punishDTO.setAiAuditResult("管理员申诉复核改判违规");
        punishDTO.setOperateType(2);
        punishService.punishUser(punishDTO);
    }

    /**
     * 对违规团队执行梯度治理。
     *
     * @param reportCase 举报案卷
     */
    private void applyTeamModeration(ReportCase reportCase) {
        // 1. 团队申诉改判违规时，也沿用举报主流程中的梯度治理策略。
        int approvedCountBeforeCurrent = countApprovedTeamReports(reportCase.getTargetId());
        int currentRound = approvedCountBeforeCurrent + 1;
        if (currentRound >= 3) {
            // 2. 达到最高梯度后直接解散团队。
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
        // 1. 只统计已确认违规的团队举报，用于计算当前梯度治理轮次。
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

        // 2. 先把全员禁言状态和自动解禁时间写回数据库。
        LocalDateTime unpunishTime = LocalDateTime.now().plusMinutes(durationMinutes);
        team.setTeamAllMute(1);
        team.setTeamAllMuteUnpunishTime(unpunishTime);
        teamMapper.updateById(team);

        // 3. 再同步写 Redis TTL 键，供高频会话权限判断快速命中。
        long ttlMinutes = Math.max(Duration.between(LocalDateTime.now(), unpunishTime).toMinutes() + 1, 1);
        stringRedisTemplate.opsForValue().set(TEAM_ALL_MUTE_KEY + teamId, "1", ttlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 对团队执行解散治理。
     *
     * @param teamId 团队 ID
     */
    private void dissolveTeamByModeration(Long teamId) {
        // 1. 先软删除团队，并清空全员禁言状态。
        Team team = teamMapper.selectById(teamId);
        if (team == null || Integer.valueOf(1).equals(team.getIsDelete())) {
            return;
        }

        team.setIsDelete(1);
        team.setTeamAllMute(0);
        team.setTeamAllMuteUnpunishTime(null);
        teamMapper.updateById(team);
        stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + teamId);

        // 2. 再批量把团队成员标记为退出，保证后续不会继续访问该团队会话。
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
     * 通知申诉双方处理结果。
     *
     * @param appeal       申诉单
     * @param appealStatus 申诉处理结果
     * @param reply        管理员处理说明
     */
    private void notifyAppealParticipants(Appeal appeal, Integer appealStatus, String reply) {
        // 1. 先生成统一结果文案，并拼上可选的管理员处理说明。
        log.info("[appeal-notify] notify participants appealId={}, reportType={}, relatedReportId={}, appealStatus={}",
                appeal == null ? null : appeal.getId(),
                appeal == null ? null : appeal.getRelatedReportType(),
                appeal == null ? null : appeal.getRelatedReportId(),
                appealStatus);
        String resultText = buildAppealResultText(appeal.getAppellantType(), appealStatus);
        String replyText = reply == null || reply.isBlank() ? "" : "，处理说明：" + reply;

        // 2. 第一时间通知申诉发起人，让其看到最终处理结论。
        if (appeal.getAppellantId() != null) {
            chatSupportService.sendReportNotice(
                    appeal.getAppellantId(),
                    appeal.getRelatedReportId(),
                    "您发起的申诉已处理：" + resultText + replyText
            );
        }

        // 3. 再通知对侧参与人，保证举报双方对结果认知一致。
        for (Long userId : resolveAppealCounterpartUserIds(appeal)) {
            if (userId != null && !userId.equals(appeal.getAppellantId())) {
                chatSupportService.sendReportNotice(
                        userId,
                        appeal.getRelatedReportId(),
                        "与您相关的申诉已处理：" + resultText + replyText
                );
            }
        }
    }

    /**
     * 构建申诉处理结果文案。
     *
     * @param appellantType 申诉方身份
     * @param appealStatus  申诉处理结果
     * @return 结果文案
     */
    private String buildAppealResultText(Integer appellantType, Integer appealStatus) {
        // 1. 举报方和被举报方的申诉语义不同，因此文案需要分开生成。
        if (Integer.valueOf(APPELLANT_TYPE_REPORTER).equals(appellantType)) {
            if (Integer.valueOf(APPEAL_STATUS_APPROVED).equals(appealStatus)) {
                return "申诉通过，已改判违规并执行处罚";
            }
            return "申诉驳回，维持未违规结论";
        }

        if (Integer.valueOf(APPEAL_STATUS_APPROVED).equals(appealStatus)) {
            return "申诉通过，已撤销处罚";
        }
        return "申诉驳回，维持原处罚";
    }

    /**
     * 解析申诉对侧需要接收通知的用户集合。
     *
     * @param appeal 申诉单
     * @return 需要通知的对侧用户 ID 列表
     */
    private List<Long> resolveAppealCounterpartUserIds(Appeal appeal) {
        // 1. 举报方发起申诉时，需要通知被举报侧；反之亦然。
        if (Integer.valueOf(APPELLANT_TYPE_REPORTER).equals(appeal.getAppellantType())) {
            return resolveReportedSideUserIds(appeal);
        }
        return resolveReporterSideUserIds(appeal);
    }

    /**
     * 解析举报方一侧用户列表。
     *
     * @param appeal 申诉单
     * @return 举报方用户 ID 列表
     */
    private List<Long> resolveReporterSideUserIds(Appeal appeal) {
        return findReporterIds(appeal.getRelatedReportId());
    }

    /**
     * 解析被举报方一侧用户列表。
     *
     * @param appeal 申诉单
     * @return 被举报侧用户 ID 列表
     */
    private List<Long> resolveReportedSideUserIds(Appeal appeal) {
        // 1. 先加载举报案卷，再按举报类型反查被举报对象。
        ReportCase reportCase = loadReportCase(appeal.getRelatedReportType(), appeal.getRelatedReportId());
        if (reportCase == null) {
            return Collections.emptyList();
        }

        // 2. 用户举报直接通知目标用户；消息举报通知消息发送者；团队举报通知团队管理者。
        if (Integer.valueOf(REPORT_TYPE_USER).equals(appeal.getRelatedReportType())) {
            return reportCase.getTargetId() == null
                    ? Collections.emptyList()
                    : Collections.singletonList(reportCase.getTargetId());
        }
        if (Integer.valueOf(REPORT_TYPE_MESSAGE).equals(appeal.getRelatedReportType())) {
            ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
            if (message == null || message.getSenderId() == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(message.getSenderId());
        }
        if (Integer.valueOf(REPORT_TYPE_TEAM).equals(appeal.getRelatedReportType())) {
            return findTeamManagerIds(reportCase.getTargetId());
        }
        return Collections.emptyList();
    }

    /**
     * 查找举报方用户列表。
     *
     * @param reportId 举报单 ID
     * @return 举报人用户 ID 列表
     */
    private List<Long> findReporterIds(Long reportId) {
        // 1. 一个举报案卷可能聚合多条举报明细，因此这里需要去重收集举报人。
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, reportId)
                .eq(ReportDetail::getIsDelete, 0)
                .select(ReportDetail::getReporterId);
        Set<Long> ids = new HashSet<>();
        for (ReportDetail detail : reportDetailMapper.selectList(query)) {
            if (detail.getReporterId() != null) {
                ids.add(detail.getReporterId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 查找团队管理者列表。
     *
     * @param teamId 团队 ID
     * @return 队长和管理员用户 ID 列表
     */
    private List<Long> findTeamManagerIds(Long teamId) {
        // 1. 团队申诉结果需要通知队长和管理员，因此这里统一拉取管理层用户。
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
     * 判断当前用户是否属于举报方。
     *
     * @param userId   当前用户 ID
     * @param reportId 举报单 ID
     * @return true 表示当前用户是举报方
     */
    private boolean isReporterSide(Long userId, Long reportId) {
        // 1. 参数不完整时直接按不是举报方处理。
        if (userId == null || reportId == null) {
            return false;
        }

        // 2. 只要在该举报案卷明细中出现过当前用户的举报记录，就视为举报方。
        LambdaQueryWrapper<ReportDetail> query = new LambdaQueryWrapper<>();
        query.eq(ReportDetail::getCaseId, reportId)
                .eq(ReportDetail::getReporterId, userId)
                .eq(ReportDetail::getIsDelete, 0)
                .last("LIMIT 1");
        return reportDetailMapper.selectOne(query) != null;
    }

    /**
     * 判断当前用户是否属于被举报方。
     *
     * @param userId     当前用户 ID
     * @param reportType 举报类型
     * @param reportCase 举报案卷
     * @return true 表示当前用户是被举报方
     */
    private boolean isReportedSide(Long userId, Integer reportType, ReportCase reportCase) {
        // 1. 参数缺失时直接按不是被举报方处理。
        if (userId == null || reportType == null || reportCase == null) {
            return false;
        }

        // 2. 用户举报直接比较目标用户；消息举报回查发送者；团队举报校验是否属于团队管理者。
        if (Integer.valueOf(REPORT_TYPE_USER).equals(reportType)) {
            return userId.equals(reportCase.getTargetId());
        }
        if (Integer.valueOf(REPORT_TYPE_MESSAGE).equals(reportType)) {
            ChatMessage message = chatMessageMapper.selectById(reportCase.getTargetId());
            return message != null && userId.equals(message.getSenderId());
        }
        if (Integer.valueOf(REPORT_TYPE_TEAM).equals(reportType)) {
            return isTeamManager(reportCase.getTargetId(), userId);
        }
        return false;
    }

    /**
     * 加载并校验举报案卷。
     *
     * @param reportType 举报类型
     * @param reportId   举报单 ID
     * @return 合法案卷；不存在或类型不匹配时返回 null
     */
    private ReportCase loadReportCase(Integer reportType, Long reportId) {
        // 1. 参数不完整时无法定位案卷，直接返回 null。
        if (reportType == null || reportId == null) {
            return null;
        }

        // 2. 案卷不存在、已删除或类型和传入不一致时，统一视为无效案卷。
        ReportCase reportCase = reportCaseMapper.selectById(reportId);
        if (reportCase == null || Integer.valueOf(1).equals(reportCase.getIsDelete())) {
            return null;
        }
        if (!reportType.equals(reportCase.getReportType())) {
            return null;
        }
        return reportCase;
    }

    /**
     * 规范化关联举报单 ID。
     *
     * @param reportType      举报类型
     * @param relatedReportId 原始关联举报单 ID
     * @return 规范化后的举报单 ID
     */
    private Long normalizeRelatedReportId(Integer reportType, Long relatedReportId) {
        return relatedReportId;
    }

    /**
     * 判断用户是否为团队管理者。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return true 表示是队长或管理员
     */
    private boolean isTeamManager(Long teamId, Long userId) {
        // 1. 团队举报的被举报侧是团队管理层，因此这里统一识别队长和管理员。
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
     * 查找某条消息最近仍生效的处罚记录。
     *
     * @param messageId 消息 ID
     * @return 处罚记录 ID；没有生效处罚时返回 null
     */
    private Long findLatestActivePunishLogIdByMessage(Long messageId) {
        // 1. 空消息 ID 无法继续回溯处罚记录。
        if (messageId == null) {
            return null;
        }

        // 2. 先按关联表倒序遍历，优先命中最近一次处罚关系。
        LambdaQueryWrapper<PunishMsgRelation> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(PunishMsgRelation::getMsgId, messageId)
                .orderByDesc(PunishMsgRelation::getId);
        for (PunishMsgRelation relation : punishMsgRelationMapper.selectList(relationQuery)) {
            if (relation.getPunishLogId() == null) {
                continue;
            }

            // 3. 只返回当前仍未被撤销的处罚记录，已取消处罚不应再被申诉链路复用。
            UserPunishLog punishLog = userPunishLogMapper.selectById(relation.getPunishLogId());
            if (punishLog != null && !Integer.valueOf(1).equals(punishLog.getIsCancel())) {
                return punishLog.getId();
            }
        }
        return null;
    }

    /**
     * 记录申诉管理员处理历史。
     *
     * @param appeal      申诉单
     * @param adminId     管理员用户 ID
     * @param actionType  动作类型
     * @param note        处理说明
     */
    private void recordAppealAdminHistory(Appeal appeal, Long adminId, String actionType, String note) {
        // 1. 申诉管理员的分配、通过、驳回都记成独立历史，方便后续审计。
        AppealAdminHistory history = new AppealAdminHistory();
        history.setAppealId(appeal.getId());
        history.setRelatedReportId(appeal.getRelatedReportId());
        history.setRelatedReportType(appeal.getRelatedReportType());
        history.setAppealRound(appeal.getAppealRound());
        history.setAdminId(adminId);
        history.setActionType(actionType);
        history.setActionNote(note == null ? "" : note);
        history.setIsDelete(0);
        appealAdminHistoryMapper.insert(history);
    }

    /**
     * 构建申诉提交锁键。
     *
     * @param reportType 举报类型
     * @param reportId   举报单 ID
     * @return Redis 锁键
     */
    private Map<String, Object> buildAppealView(Appeal appeal) {
        Map<String, Object> item = new HashMap<>();
        if (appeal == null) {
            return item;
        }

        item.put("id", chatSupportService.stringifyId(appeal.getId()));
        item.put("appellantId", chatSupportService.stringifyId(appeal.getAppellantId()));
        item.put("appellantType", appeal.getAppellantType());
        item.put("relatedReportId", chatSupportService.stringifyId(appeal.getRelatedReportId()));
        item.put("relatedCaseId", chatSupportService.stringifyId(appeal.getRelatedCaseId()));
        item.put("relatedReportType", appeal.getRelatedReportType());
        item.put("relatedPunishId", chatSupportService.stringifyId(appeal.getRelatedPunishId()));
        item.put("appealRound", appeal.getAppealRound());
        item.put("appealReason", appeal.getAppealReason());
        item.put("appealEvidence", appeal.getAppealEvidence());
        item.put("appealStatus", appeal.getAppealStatus());
        item.put("adminId", chatSupportService.stringifyId(appeal.getAdminId()));
        item.put("assignTime", appeal.getAssignTime());
        item.put("acceptTime", appeal.getAcceptTime());
        item.put("lastDispatchTime", appeal.getLastDispatchTime());
        item.put("dispatchCount", appeal.getDispatchCount());
        item.put("adminReply", appeal.getAdminReply());
        item.put("processTime", appeal.getProcessTime());
        item.put("isDelete", appeal.getIsDelete());
        item.put("createTime", appeal.getCreateTime());
        item.put("updateTime", appeal.getUpdateTime());
        return item;
    }

    private List<Map<String, Object>> buildAppealViews(List<Appeal> appeals) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (appeals == null || appeals.isEmpty()) {
            return list;
        }
        for (Appeal appeal : appeals) {
            list.add(buildAppealView(appeal));
        }
        return list;
    }

    private String buildSubmitLockKey(Integer reportType, Long reportId) {
        return "appeal:submit:lock:" + reportType + ":" + reportId;
    }

    /**
     * 释放申诉提交分布式锁。
     *
     * @param lockKey   锁键
     * @param lockValue 锁值
     */
    private void releaseSubmitLock(String lockKey, String lockValue) {
        if (lockKey == null || lockValue == null) {
            return;
        }
        // 1. 用 Lua 保证“校验锁值 + 删除锁”原子执行，避免误删别人的锁。
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }
}
