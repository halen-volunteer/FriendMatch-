package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamReportMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Mapper.UserReportMapper;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamReport;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Model.UserReport;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.AiCheckService;
import com.zero.usercenter.Service.ReportService;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报服务实现类。
 * <p>
 * 提供用户举报、团队举报、举报状态查询以及管理员处理能力。
 */
@Service
public class ReportServiceImpl implements ReportService {

    @Resource
    private UserReportMapper userReportMapper;
    @Resource
    private TeamReportMapper teamReportMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private TeamMapper teamMapper;
    @Resource
    private AiCheckService aiCheckService;
    @Resource
    private AdminAuthService adminAuthService;

    /**
     * 提交用户举报并异步触发 AI 初审。
     *
     * @param dto 用户举报参数
     * @return 举报提交结果，含 reportId 与 reportTime
     */
    @Override
    public Result reportUser(UserReportSubmitDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto == null || dto.getReportedUserId() == null) return Result.fail("被举报用户不能为空");
        if (userId.equals(dto.getReportedUserId())) return Result.fail("不能举报自己");
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 6) return Result.fail("举报原因不合法");
        if (dto.getReportContent() == null || dto.getReportContent().isBlank()) return Result.fail("举报内容不能为空");

        User reported = userMapper.selectById(dto.getReportedUserId());
        if (reported == null || reported.getIsDelete() == 1) return Result.fail("被举报用户不存在");

        UserReport report = new UserReport();
        report.setReporterId(userId);
        report.setReportedUserId(dto.getReportedUserId());
        report.setReportReason(dto.getReportReason());
        report.setReportContent(dto.getReportContent().trim());
        report.setReportEvidence(dto.getReportEvidence() == null ? "" : dto.getReportEvidence().trim());
        report.setAiCheckResult(0);
        report.setReportStatus(0);
        report.setAppealCount(0);
        report.setIsDelete(0);
        userReportMapper.insert(report);

        Thread.ofVirtual().start(() -> doAiCheck(report.getId(), report.getReportContent()));

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getId());
        data.put("reportTime", report.getCreateTime());
        data.put("message", "举报已提交，感谢您的反馈");
        return Result.ok(data);
    }

    /**
     * 提交团队举报。
     *
     * @param dto 团队举报参数
     * @return 举报提交结果，含 reportId 与 reportTime
     */
    @Override
    public Result reportTeam(TeamReportSubmitDTO dto) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (dto == null || dto.getReportedTeamId() == null) return Result.fail("被举报团队不能为空");
        if (dto.getReportReason() == null || dto.getReportReason() < 1 || dto.getReportReason() > 5) return Result.fail("举报原因不合法");
        if (dto.getReportContent() == null || dto.getReportContent().isBlank()) return Result.fail("举报内容不能为空");

        Team team = teamMapper.selectById(dto.getReportedTeamId());
        if (team == null || team.getIsDelete() == 1) return Result.fail("被举报团队不存在");

        TeamReport report = new TeamReport();
        report.setReporterId(userId);
        report.setReportedTeamId(dto.getReportedTeamId());
        report.setReportReason(dto.getReportReason());
        report.setReportContent(dto.getReportContent().trim());
        report.setReportEvidence(dto.getReportEvidence() == null ? "" : dto.getReportEvidence().trim());
        report.setReportStatus(0);
        report.setIsDelete(0);
        teamReportMapper.insert(report);

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getId());
        data.put("reportTime", report.getCreateTime());
        return Result.ok(data);
    }

    /**
     * 查询用户举报状态。
     *
     * @param reportId 举报ID
     * @return 举报详情与处理状态
     */
    @Override
    public Result getUserReportStatus(Long reportId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (reportId == null) return Result.fail("举报ID不能为空");

        UserReport report = userReportMapper.selectById(reportId);
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (!userId.equals(report.getReporterId()) && !userId.equals(report.getReportedUserId())) return Result.fail("无权查看该举报");

        Map<String, Object> data = new HashMap<>();
        data.put("reportId", report.getId());
        data.put("reportReason", report.getReportReason());
        data.put("reportContent", report.getReportContent());
        data.put("aiCheckResult", report.getAiCheckResult());
        data.put("aiConfidence", report.getAiConfidence());
        data.put("reportStatus", report.getReportStatus());
        data.put("adminAction", report.getAdminAction());
        data.put("adminNote", report.getAdminNote());
        data.put("appealCount", report.getAppealCount());
        data.put("canAppeal", report.getAppealCount() != null && report.getAppealCount() < 3);
        data.put("processTime", report.getProcessTime());
        return Result.ok(data);
    }

    /**
     * 查询团队举报状态。
     *
     * @param reportId 举报ID
     * @return 团队举报详情
     */
    @Override
    public Result getTeamReportStatus(Long reportId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (reportId == null) return Result.fail("举报ID不能为空");

        TeamReport report = teamReportMapper.selectById(reportId);
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (!userId.equals(report.getReporterId())) return Result.fail("无权查看该举报");

        return Result.ok(report);
    }

    @Override
    public Result getMyReports(int page, int pageSize) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        List<Map<String, Object>> merged = new ArrayList<>();

        LambdaQueryWrapper<UserReport> userQw = new LambdaQueryWrapper<>();
        userQw.eq(UserReport::getReporterId, userId)
                .eq(UserReport::getIsDelete, 0)
                .orderByDesc(UserReport::getCreateTime);
        userReportMapper.selectList(userQw).forEach(reportItem -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", reportItem.getId());
            item.put("reportType", "user");
            item.put("reportReason", reportItem.getReportReason());
            item.put("reportContent", reportItem.getReportContent());
            item.put("handleStatus", reportItem.getReportStatus());
            item.put("handleStatusText", mapReportStatus(reportItem.getReportStatus()));
            item.put("createTime", reportItem.getCreateTime());
            merged.add(item);
        });

        LambdaQueryWrapper<TeamReport> teamQw = new LambdaQueryWrapper<>();
        teamQw.eq(TeamReport::getReporterId, userId)
                .eq(TeamReport::getIsDelete, 0)
                .orderByDesc(TeamReport::getCreateTime);
        teamReportMapper.selectList(teamQw).forEach(reportItem -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", reportItem.getId());
            item.put("reportType", "team");
            item.put("reportReason", reportItem.getReportReason());
            item.put("reportContent", reportItem.getReportContent());
            item.put("handleStatus", reportItem.getReportStatus());
            item.put("handleStatusText", mapReportStatus(reportItem.getReportStatus()));
            item.put("createTime", reportItem.getCreateTime());
            merged.add(item);
        });

        merged.sort(Comparator.comparing((Map<String, Object> item) -> (LocalDateTime) item.get("createTime"), Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        int fromIndex = Math.max((page - 1) * pageSize, 0);
        if (fromIndex >= merged.size()) return Result.ok(List.of(), (long) merged.size());
        int toIndex = Math.min(fromIndex + pageSize, merged.size());
        return Result.ok(merged.subList(fromIndex, toIndex), (long) merged.size());
    }

    /**
     * 管理员分页查询用户举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 用户举报分页数据
     */
    @Override
    public Result adminGetUserReportList(Integer reportStatus, int page, int pageSize) {
        Long adminId = UserHolder.getUserId();
        if (adminId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(adminId);

        Page<UserReport> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<UserReport> qw = new LambdaQueryWrapper<>();
        qw.eq(UserReport::getIsDelete, 0);
        if (reportStatus != null) qw.eq(UserReport::getReportStatus, reportStatus);
        qw.orderByAsc(UserReport::getReportStatus).orderByDesc(UserReport::getCreateTime);
        Page<UserReport> result = userReportMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 管理员处理用户举报。
     *
     * @param dto 用户举报处理参数
     * @return 处理结果
     */
    @Override
    public Result adminHandleUserReport(UserReportHandleDTO dto) {
        Long adminId = UserHolder.getUserId();
        if (adminId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(adminId);
        if (dto == null || dto.getReportId() == null) return Result.fail("举报ID不能为空");
        if (dto.getReportStatus() == null || dto.getReportStatus() < 1 || dto.getReportStatus() > 3) return Result.fail("处理状态不合法");

        UserReport report = userReportMapper.selectById(dto.getReportId());
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (report.getReportStatus() != 0) return Result.fail("该举报已处理");

        report.setReportStatus(dto.getReportStatus());
        report.setAdminAction(dto.getAdminAction());
        report.setAdminNote(dto.getAdminNote() == null ? "" : dto.getAdminNote().trim());
        report.setAdminId(adminId);
        report.setProcessTime(LocalDateTime.now());
        userReportMapper.updateById(report);
        return Result.ok("处理成功");
    }

    /**
     * 管理员分页查询团队举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 团队举报分页数据
     */
    @Override
    public Result adminGetTeamReportList(Integer reportStatus, int page, int pageSize) {
        Long adminId = UserHolder.getUserId();
        if (adminId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(adminId);

        Page<TeamReport> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeamReport> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamReport::getIsDelete, 0);
        if (reportStatus != null) qw.eq(TeamReport::getReportStatus, reportStatus);
        qw.orderByAsc(TeamReport::getReportStatus).orderByDesc(TeamReport::getCreateTime);
        Page<TeamReport> result = teamReportMapper.selectPage(pageObj, qw);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 管理员处理团队举报。
     *
     * @param dto 团队举报处理参数
     * @return 处理结果
     */
    @Override
    public Result adminHandleTeamReport(TeamReportHandleDTO dto) {
        Long adminId = UserHolder.getUserId();
        if (adminId == null) throw new BusinessException("用户未登录");
        adminAuthService.assertAdmin(adminId);
        if (dto == null || dto.getReportId() == null) return Result.fail("举报ID不能为空");
        if (dto.getReportStatus() == null || dto.getReportStatus() < 1 || dto.getReportStatus() > 3) return Result.fail("处理状态不合法");

        TeamReport report = teamReportMapper.selectById(dto.getReportId());
        if (report == null || report.getIsDelete() == 1) return Result.fail("举报记录不存在");
        if (report.getReportStatus() != 0) return Result.fail("该举报已处理");

        report.setReportStatus(dto.getReportStatus());
        report.setAdminAction(dto.getAdminAction());
        report.setAdminNote(dto.getAdminNote() == null ? "" : dto.getAdminNote().trim());
        report.setAdminId(adminId);
        report.setProcessTime(LocalDateTime.now());
        teamReportMapper.updateById(report);
        return Result.ok("处理成功");
    }

    /**
     * 执行 AI 内容审核并回写结果。
     *
     * @param reportId 用户举报ID
     * @param content 待审核文本
     */
    private void doAiCheck(Long reportId, String content) {
        int aiResult = aiCheckService.checkContent(content);
        LambdaQueryWrapper<UserReport> qw = new LambdaQueryWrapper<>();
        qw.eq(UserReport::getId, reportId).eq(UserReport::getIsDelete, 0);
        UserReport report = userReportMapper.selectOne(qw);
        if (report == null) return;

        report.setAiCheckResult(aiResult == 1 ? 1 : 2);
        report.setAiConfidence(aiResult == 1 ? 90 : 70);
        report.setAiCheckTime(LocalDateTime.now());
        userReportMapper.updateById(report);
    }

    private String mapReportStatus(Integer status) {
        if (status == null || status == 0) return "待处理";
        if (status == 1) return "已通过";
        if (status == 2) return "已驳回";
        if (status == 3) return "已关闭";
        return "处理中";
    }
}
