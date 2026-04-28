package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.ReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 举报模块 Controller。
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Resource
    private ReportService reportService;

    /**
     * 提交用户举报。
     *
     * @param dto 用户举报参数
     * @return 举报提交结果
     */
    @PostMapping("/user")
    public Result reportUser(@RequestBody UserReportSubmitDTO dto) {
        return reportService.reportUser(dto);
    }

    /**
     * 提交团队举报。
     *
     * @param dto 团队举报参数
     * @return 举报提交结果
     */
    @PostMapping("/team")
    public Result reportTeam(@RequestBody TeamReportSubmitDTO dto) {
        return reportService.reportTeam(dto);
    }

    /**
     * 查询用户举报状态。
     *
     * @param reportId 举报 ID
     * @return 举报状态
     */
    @GetMapping("/user/{reportId}")
    public Result getUserReportStatus(@PathVariable Long reportId) {
        return reportService.getUserReportStatus(reportId);
    }

    /**
     * 查询团队举报状态。
     *
     * @param reportId 举报 ID
     * @return 举报状态
     */
    @GetMapping("/team/{reportId}")
    public Result getTeamReportStatus(@PathVariable Long reportId) {
        return reportService.getTeamReportStatus(reportId);
    }

    /**
     * 统一举报状态查询入口。
     *
     * @param reportId 举报 ID
     * @param type 举报类型（user/team）
     * @return 举报状态
     */
    @GetMapping("/{reportId}")
    public Result getReportStatus(@PathVariable Long reportId,
                                  @RequestParam(defaultValue = "user") String type) {
        if ("team".equalsIgnoreCase(type)) {
            return reportService.getTeamReportStatus(reportId);
        }
        return reportService.getUserReportStatus(reportId);
    }

    /**
     * 查询当前登录用户的举报列表。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    @GetMapping("/my-list")
    public Result getMyReports(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return reportService.getMyReports(page, pageSize);
    }

    /**
     * 管理员查询用户举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    @GetMapping("/admin/user/list")
    public Result adminGetUserReportList(@RequestParam(required = false) Integer reportStatus,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return reportService.adminGetUserReportList(reportStatus, page, pageSize);
    }

    /**
     * 管理员处理用户举报。
     *
     * @param dto 举报处理参数
     * @return 处理结果
     */
    @PostMapping("/admin/user/handle")
    public Result adminHandleUserReport(@RequestBody UserReportHandleDTO dto) {
        return reportService.adminHandleUserReport(dto);
    }

    /**
     * 管理员查询团队举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    @GetMapping("/admin/team/list")
    public Result adminGetTeamReportList(@RequestParam(required = false) Integer reportStatus,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return reportService.adminGetTeamReportList(reportStatus, page, pageSize);
    }

    /**
     * 管理员处理团队举报。
     *
     * @param dto 举报处理参数
     * @return 处理结果
     */
    @PostMapping("/admin/team/handle")
    public Result adminHandleTeamReport(@RequestBody TeamReportHandleDTO dto) {
        return reportService.adminHandleTeamReport(dto);
    }
}

