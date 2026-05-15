package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.*;

/**
 * 团队管理服务接口。
 * 覆盖团队创建、入队审批、成员管理和禁言管理等能力。
 */
public interface TeamService {

    /**
     * 创建团队。
     *
     * @param dto 团队创建参数，包含团队名称、简介、类型、标签和人数上限等信息
     * @return 统一响应结果，成功时表示团队已创建
     */
    Result createTeam(TeamCreateDTO dto);

    /**
     * 获取团队列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param teamType 团队类型筛选条件
     * @param sort 排序方式
     * @return 统一响应结果，成功时包含团队分页列表
     */
    Result getTeamList(int page, int pageSize, Integer teamType, String sort);

    /**
     * 搜索团队。
     *
     * @param keyword 搜索关键词
     * @param type 搜索类型或筛选方式
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含团队搜索结果
     */
    Result searchTeam(String keyword, String type, int page, int pageSize);

    /**
     * 获取团队详情。
     *
     * @param teamId 团队 ID
     * @return 统一响应结果，成功时包含团队详情及成员状态等信息
     */
    Result getTeamDetail(Long teamId);

    /**
     * 编辑团队信息。
     *
     * @param dto 团队更新参数，包含团队 ID 及需要修改的字段
     * @return 统一响应结果，成功时表示团队信息已更新
     */
    Result updateTeam(TeamUpdateDTO dto);

    /**
     * 解散团队。
     *
     * @param teamId 团队 ID
     * @return 统一响应结果，成功时表示团队已解散
     */
    Result dissolveTeam(Long teamId);

    /**
     * 申请加入团队。
     *
     * @param dto 入队申请参数，包含团队 ID 和申请说明
     * @return 统一响应结果，成功时表示入队申请已提交
     */
    Result applyTeam(TeamApplyDTO dto);

    /**
     * 密码加入团队。
     *
     * @param dto 密码入队参数，包含团队 ID 和入队密码
     * @return 统一响应结果，成功时表示已加入团队
     */
    Result joinByPassword(TeamJoinByPasswordDTO dto);

    /**
     * 邀请用户加入团队。
     *
     * @param dto 邀请参数，包含团队 ID、目标用户 ID 和邀请说明
     * @return 统一响应结果，成功时表示邀请已发送或成员已加入
     */
    Result inviteMember(TeamInviteDTO dto);

    /**
     * 获取待审核申请列表。
     *
     * @param teamId 团队 ID
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含待审核申请分页列表
     */
    Result getPendingApplyList(Long teamId, int page, int pageSize);

    /**
     * 审批加入申请。
     *
     * @param dto 审批参数，包含申请记录 ID、审批结果和备注
     * @return 统一响应结果，成功时表示审批已完成
     */
    Result auditApply(TeamAuditDTO dto);

    /**
     * 获取团队成员列表。
     *
     * @param teamId 团队 ID
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含成员分页列表
     */
    Result getMemberList(Long teamId, int page, int pageSize);

    /**
     * 按角色过滤获取团队成员列表。
     *
     * @param teamId 团队 ID
     * @param roleType 角色类型筛选条件
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含角色过滤后的成员分页列表
     */
    Result getMemberListByRole(Long teamId, Integer roleType, int page, int pageSize);

    /**
     * 移除成员。
     *
     * @param dto 成员操作参数，包含团队 ID、目标成员 ID 和操作说明
     * @return 统一响应结果，成功时表示成员已移除
     */
    Result removeMember(TeamMemberOperationDTO dto);

    /**
     * 修改成员角色。
     *
     * @param dto 成员操作参数，包含团队 ID、目标成员 ID 和目标角色
     * @return 统一响应结果，成功时表示成员角色已更新
     */
    Result updateMemberRole(TeamMemberOperationDTO dto);

    /**
     * 转让队长权限。
     *
     * @param dto 成员操作参数，包含团队 ID 和新的队长用户 ID
     * @return 统一响应结果，成功时表示队长权限已转让
     */
    Result transferLeader(TeamMemberOperationDTO dto);

    /**
     * 主动退出团队。
     *
     * @param teamId 团队 ID
     * @return 统一响应结果，成功时表示已退出团队
     */
    Result quitTeam(Long teamId);

    /**
     * 全员禁言或解除禁言。
     *
     * @param dto 团队禁言参数，包含团队 ID 和禁言开关状态
     * @return 统一响应结果，成功时表示全员禁言状态已更新
     */
    Result muteAll(TeamMuteDTO dto);

    /**
     * 禁言指定成员。
     *
     * @param dto 团队禁言参数，包含团队 ID、目标用户 ID 和禁言时长等信息
     * @return 统一响应结果，成功时表示成员已被禁言
     */
    Result muteMember(TeamMuteDTO dto);

    /**
     * 解除指定成员禁言。
     *
     * @param dto 团队禁言参数，包含团队 ID 和目标用户 ID
     * @return 统一响应结果，成功时表示成员禁言已解除
     */
    Result unmuteMember(TeamMuteDTO dto);
}

