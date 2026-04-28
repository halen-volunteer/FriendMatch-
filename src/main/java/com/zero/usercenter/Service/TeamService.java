package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.*;

/**
 * 团队管理模块 Service 接口
 */
public interface TeamService {

    /**
     * 创建团队
     *
     * @param dto 团队创建数据传输对象，包含名称、类型、加入规则、密码等
     * @return 创建结果，包含新团队 ID
     */
    Result createTeam(TeamCreateDTO dto);

    /**
     * 获取团队列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param teamType 团队类型（1-竞技，2-休闲，null-全部）
     * @param sort     排序字段（createTime-按创建时间，默认按 ID 降序）
     * @return 团队分页列表
     */
    Result getTeamList(int page, int pageSize, Integer teamType, String sort);

    /**
     * 搜索团队
     *
     * @param keyword  搜索关键词
     * @param type     搜索类型（name-按名称，tag-按标签）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 搜索结果列表
     */
    Result searchTeam(String keyword, String type, int page, int pageSize);

    /**
     * 获取团队详情
     *
     * @param teamId 团队 ID
     * @return 团队详细信息及当前用户角色
     */
    Result getTeamDetail(Long teamId);

    /**
     * 编辑团队信息（队长）
     *
     * @param dto 团队更新数据传输对象，包含可修改的团队字段
     * @return 更新结果
     */
    Result updateTeam(TeamUpdateDTO dto);

    /**
     * 解散团队（队长）
     *
     * @param teamId 要解散的团队 ID
     * @return 解散结果
     */
    Result dissolveTeam(Long teamId);

    /**
     * 申请加入团队
     *
     * @param dto 入队申请数据传输对象，包含团队 ID 和申请备注
     * @return 申请结果
     */
    Result applyTeam(TeamApplyDTO dto);

    /**
     * 密码加入团队
     *
     * @param dto 密码入队数据传输对象，包含团队 ID 和密码
     * @return 加入结果
     */
    Result joinByPassword(TeamJoinByPasswordDTO dto);

    /**
     * 邀请用户加入团队（队长/管理员）
     *
     * @param dto 邀请数据传输对象，包含团队 ID 和被邀请用户 ID
     * @return 邀请结果
     */
    Result inviteMember(TeamInviteDTO dto);

    /**
     * 获取待审核申请列表（队长/管理员）
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 待审核申请分页列表
     */
    Result getPendingApplyList(Long teamId, int page, int pageSize);

    /**
     * 审批加入申请（队长/管理员）
     *
     * @param dto 审批数据传输对象，包含申请 ID 和审批状态
     * @return 审批结果
     */
    Result auditApply(TeamAuditDTO dto);

    /**
     * 获取团队成员列表
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 成员分页列表
     */
    Result getMemberList(Long teamId, int page, int pageSize);

    /**
     * 获取团队成员列表（按角色过滤）
     *
     * @param teamId   团队 ID
     * @param roleType 角色类型（1-队长，2-管理员，3-普通成员，null-全部）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 成员分页列表
     */
    Result getMemberListByRole(Long teamId, Integer roleType, int page, int pageSize);

    /**
     * 移除成员（队长/管理员）
     *
     * @param dto 成员操作数据传输对象，包含团队 ID 和目标用户 ID
     * @return 移除结果
     */
    Result removeMember(TeamMemberOperationDTO dto);

    /**
     * 修改成员角色（队长）
     *
     * @param dto 成员操作数据传输对象，包含团队 ID、目标用户 ID 和新角色
     * @return 修改结果
     */
    Result updateMemberRole(TeamMemberOperationDTO dto);

    /**
     * 转让队长权限（队长）
     *
     * @param dto 成员操作数据传输对象，包含团队 ID 和目标用户 ID
     * @return 转让结果
     */
    Result transferLeader(TeamMemberOperationDTO dto);

    /**
     * 主动退出团队
     *
     * @param teamId 要退出的团队 ID
     * @return 退出结果
     */
    Result quitTeam(Long teamId);

    /**
     * 全员禁言/解除禁言（队长/管理员）
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和禁言开关
     * @return 操作结果
     */
    Result muteAll(TeamMuteDTO dto);

    /**
     * 禁言指定成员（队长/管理员）
     *
     * @param dto 禁言数据传输对象，包含团队 ID、目标用户 ID 和禁言时长
     * @return 禁言结果
     */
    Result muteMember(TeamMuteDTO dto);

    /**
     * 解除指定成员禁言（队长/管理员）
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和目标用户 ID
     * @return 解除结果
     */
    Result unmuteMember(TeamMuteDTO dto);
}

