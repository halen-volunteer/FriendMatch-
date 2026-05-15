package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.TeamReportHandleDTO;
import com.zero.usercenter.DTO.TeamReportSubmitDTO;
import com.zero.usercenter.DTO.UserReportHandleDTO;
import com.zero.usercenter.DTO.UserReportSubmitDTO;
import com.zero.usercenter.Service.ReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报中心接口入口。
 * 统一暴露用户举报、团队举报，以及管理端审核上下文查询与处理能力。
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Resource
    private ReportService reportService;

    /**
     * 提交用户举报。
     */
    @PostMapping("/user")
    public Result reportUser(@RequestBody UserReportSubmitDTO dto) {
        // 用户举报会下沉到举报 service，后续可能进入审核、处罚与通知链路。
        return reportService.reportUser(dto);
    }

    /**
     * 提交团队举报。
     */
    @PostMapping("/team")
    public Result reportTeam(@RequestBody TeamReportSubmitDTO dto) {
        // 团队举报与用户举报共用举报中心，只是目标实体和校验规则不同。
        return reportService.reportTeam(dto);
    }

    /**
     * 查询用户举报状态。
     */
    @GetMapping("/user/{reportId}")
    public Result getUserReportStatus(@PathVariable Long reportId) {
        // 举报状态查询由 service 校验归属关系，避免越权查看他人举报单。
        return reportService.getUserReportStatus(reportId);
    }

    /**
     * 查询团队举报状态。
     */
    @GetMapping("/team/{reportId}")
    public Result getTeamReportStatus(@PathVariable Long reportId) {
        // 团队举报状态查询同样走 service 内统一的权限校验。
        return reportService.getTeamReportStatus(reportId);
    }

    /**
     * 举报状态兼容查询入口。
     * 通过 type 参数区分用户举报或团队举报。
     */
    @GetMapping("/{reportId}")
    public Result getReportStatus(@PathVariable Long reportId,
                                  @RequestParam(defaultValue = "user") String type) {
        // 这是兼容入口，通过 type 参数分流到用户举报或团队举报查询。
        if ("team".equalsIgnoreCase(type)) {
            return reportService.getTeamReportStatus(reportId);
        }
        return reportService.getUserReportStatus(reportId);
    }

    /**
     * 获取当前用户关联的举报记录汇总列表。
     */
    @GetMapping("/my-list")
    public Result getMyReports(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        // 我的举报列表会汇总当前用户相关的多类举报单，具体聚合逻辑在 service 中。
        return reportService.getMyReports(page, pageSize);
    }

    /**
     * 管理员分页查询用户举报列表。
     */
    @GetMapping("/admin/user/list")
    public Result adminGetUserReportList(@RequestParam(required = false) Integer reportStatus,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        // 管理员用户举报列表支持状态筛选，查询规则都封装在 service 层。
        return reportService.adminGetUserReportList(reportStatus, page, pageSize);
    }

    /**
     * 管理员查看用户举报详情与取证上下文。
     */
    @GetMapping("/admin/user/{reportId}/context")
    public Result adminGetUserReportContext(@PathVariable Long reportId) {
        // 这里会返回审核所需上下文，具体取证拼装逻辑在 service 中完成。
        return reportService.adminGetUserReportContext(reportId);
    }

    /**
     * 管理员处理用户举报。
     */
    @PostMapping("/admin/user/handle")
    public Result adminHandleUserReport(@RequestBody UserReportHandleDTO dto) {
        // 审核处理可能触发处罚和通知，因此 controller 不介入业务细节。
        return reportService.adminHandleUserReport(dto);
    }

    /**
     * 管理员分页查询团队举报列表。
     */
    @GetMapping("/admin/team/list")
    public Result adminGetTeamReportList(@RequestParam(required = false) Integer reportStatus,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        // 团队举报管理列表与用户举报列表共用同一审核域，只是查询维度不同。
        return reportService.adminGetTeamReportList(reportStatus, page, pageSize);
    }

    /**
     * 管理员查看团队举报详情与取证上下文。
     */
    @GetMapping("/admin/team/{reportId}/context")
    public Result adminGetTeamReportContext(@PathVariable Long reportId) {
        // 团队举报上下文会补齐团队信息和相关取证数据，controller 只做转发。
        return reportService.adminGetTeamReportContext(reportId);
    }

    /**
     * 管理员处理团队举报。
     */
    @PostMapping("/admin/team/handle")
    public Result adminHandleTeamReport(@RequestBody TeamReportHandleDTO dto) {
        // 团队举报处理同样可能触发惩罚与通知，统一交给 service 链路。
        return reportService.adminHandleTeamReport(dto);
    }
}
