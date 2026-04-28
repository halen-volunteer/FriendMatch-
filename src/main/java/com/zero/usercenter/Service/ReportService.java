package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.*;

/**
 * 举报服务接口。
 */
public interface ReportService {

    /**
     * 提交用户举报。
     *
     * @param dto 用户举报参数
     * @return 提交结果
     */
    Result reportUser(UserReportSubmitDTO dto);

    /**
     * 提交团队举报。
     *
     * @param dto 团队举报参数
     * @return 提交结果
     */
    Result reportTeam(TeamReportSubmitDTO dto);

    /**
     * 查询用户举报状态。
     *
     * @param reportId 举报 ID
     * @return 举报状态
     */
    Result getUserReportStatus(Long reportId);

    /**
     * 查询团队举报状态。
     *
     * @param reportId 举报 ID
     * @return 举报状态
     */
    Result getTeamReportStatus(Long reportId);

    /**
     * 查询当前登录用户的举报列表（用户举报 + 团队举报统一视图）。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    Result getMyReports(int page, int pageSize);

    /**
     * 管理员查询用户举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    Result adminGetUserReportList(Integer reportStatus, int page, int pageSize);

    /**
     * 管理员处理用户举报。
     *
     * @param dto 举报处理参数
     * @return 处理结果
     */
    Result adminHandleUserReport(UserReportHandleDTO dto);

    /**
     * 管理员查询团队举报列表。
     *
     * @param reportStatus 举报状态（可选）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 举报分页列表
     */
    Result adminGetTeamReportList(Integer reportStatus, int page, int pageSize);

    /**
     * 管理员处理团队举报。
     *
     * @param dto 举报处理参数
     * @return 处理结果
     */
    Result adminHandleTeamReport(TeamReportHandleDTO dto);
}

