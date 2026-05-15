package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.TeamReportHandleDTO;
import com.zero.usercenter.DTO.TeamReportSubmitDTO;
import com.zero.usercenter.DTO.UserReportHandleDTO;
import com.zero.usercenter.DTO.UserReportSubmitDTO;

public interface ReportService {

    /**
     * 提交用户举报。
     *
     * @param dto 用户举报参数，包含被举报用户、举报原因和证据说明等信息
     * @return 统一响应结果，成功时表示举报单已创建
     */
    Result reportUser(UserReportSubmitDTO dto);

    /**
     * 提交团队举报。
     *
     * @param dto 团队举报参数，包含被举报团队、举报原因和补充说明等信息
     * @return 统一响应结果，成功时表示举报单已创建
     */
    Result reportTeam(TeamReportSubmitDTO dto);

    /**
     * 查询用户举报状态。
     *
     * @param reportId 举报单 ID
     * @return 统一响应结果，成功时包含举报处理状态
     */
    Result getUserReportStatus(Long reportId);

    /**
     * 查询团队举报状态。
     *
     * @param reportId 举报单 ID
     * @return 统一响应结果，成功时包含举报处理状态
     */
    Result getTeamReportStatus(Long reportId);

    /**
     * 查询当前登录用户发起的举报记录。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含举报记录分页数据
     */
    Result getMyReports(int page, int pageSize);

    /**
     * 管理员分页查询用户举报列表。
     *
     * @param reportStatus 举报状态筛选条件
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含用户举报分页数据
     */
    Result adminGetUserReportList(Integer reportStatus, int page, int pageSize);

    /**
     * 管理员查看用户举报详情和上下文。
     *
     * @param reportId 举报单 ID
     * @return 统一响应结果，成功时包含举报详情及相关上下文
     */
    Result adminGetUserReportContext(Long reportId);

    /**
     * 管理员处理用户举报。
     *
     * @param dto 用户举报处理参数，包含举报单 ID、处理结果和处理说明
     * @return 统一响应结果，成功时表示用户举报已处理
     */
    Result adminHandleUserReport(UserReportHandleDTO dto);

    /**
     * 管理员分页查询团队举报列表。
     *
     * @param reportStatus 举报状态筛选条件
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含团队举报分页数据
     */
    Result adminGetTeamReportList(Integer reportStatus, int page, int pageSize);

    /**
     * 管理员查看团队举报详情和上下文。
     *
     * @param reportId 举报单 ID
     * @return 统一响应结果，成功时包含团队举报详情及相关上下文
     */
    Result adminGetTeamReportContext(Long reportId);

    /**
     * 管理员处理团队举报。
     *
     * @param dto 团队举报处理参数，包含举报单 ID、处理结果和处理说明
     * @return 统一响应结果，成功时表示团队举报已处理
     */
    Result adminHandleTeamReport(TeamReportHandleDTO dto);
}
