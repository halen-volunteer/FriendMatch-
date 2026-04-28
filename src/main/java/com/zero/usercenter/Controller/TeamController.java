package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 团队管理模块 Controller
 *
 * 基础路径：/api/team
 * 所有接口需在请求头携带 Authorization: {token}
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    @Resource
    private TeamService teamService;

    // ==================== 团队创建与查询 ====================

    /**
     * 创建团队
     * POST /api/team/create
     *
     * @param dto 团队创建数据传输对象，包含名称、类型、加入规则、密码等
     * @return 创建结果，包含新团队 ID
     */
    @PostMapping("/create")
    public Result createTeam(@RequestBody TeamCreateDTO dto) {
        return teamService.createTeam(dto);
    }

    /**
     * 获取团队列表
     * GET /api/team/list?page=1&pageSize=20&teamType=1&sort=createTime
     * teamType: 1-竞技，2-休闲，不传查全部
     * sort: createTime-按创建时间，不传-按ID降序
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param teamType 团队类型（1-竞技，2-休闲，null-全部）
     * @param sort     排序字段
     * @return 团队分页列表
     */
    @GetMapping("/list")
    public Result getTeamList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer teamType,
            @RequestParam(defaultValue = "id") String sort) {
        return teamService.getTeamList(page, pageSize, teamType, sort);
    }

    /**
     * 搜索团队
     * GET /api/team/search?keyword=xxx&type=name&page=1&pageSize=20
     * type: name-按名称，tag-按标签
     *
     * @param keyword  搜索关键词
     * @param type     搜索类型（name-按名称，tag-按标签）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 搜索结果列表
     */
    @GetMapping("/search")
    public Result searchTeam(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "name") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return teamService.searchTeam(keyword, type, page, pageSize);
    }

    /**
     * 获取团队详情
     * GET /api/team/{teamId}
     * 返回团队信息及当前用户角色（-1 表示非成员）
     *
     * @param teamId 团队 ID
     * @return 团队详细信息及当前用户角色
     */
    @GetMapping("/{teamId}")
    public Result getTeamDetail(@PathVariable Long teamId) {
        return teamService.getTeamDetail(teamId);
    }

    /**
     * 编辑团队信息（仅队长）
     * POST /api/team/update
     *
     * @param dto 团队更新数据传输对象，包含可修改的团队字段
     * @return 更新结果
     */
    @PostMapping("/update")
    public Result updateTeam(@RequestBody TeamUpdateDTO dto) {
        return teamService.updateTeam(dto);
    }

    /**
     * 解散团队（仅队长）
     * POST /api/team/dissolve?teamId=1001
     * 软删除团队，异步通知所有成员
     *
     * @param teamId 要解散的团队 ID
     * @return 解散结果
     */
    @PostMapping("/dissolve")
    public Result dissolveTeam(@RequestParam Long teamId) {
        return teamService.dissolveTeam(teamId);
    }

    // ==================== 加入团队 ====================

    /**
     * 申请加入团队（joinRule=1）
     * POST /api/team/apply
     * 异步通知团队管理员审核
     *
     * @param dto 入队申请数据传输对象，包含团队 ID 和申请备注
     * @return 申请结果
     */
    @PostMapping("/apply")
    public Result applyTeam(@RequestBody TeamApplyDTO dto) {
        return teamService.applyTeam(dto);
    }

    /**
     * 密码加入团队（joinRule=3）
     * POST /api/team/join-by-password
     * BCrypt 校验密码，通过后直接入队
     *
     * @param dto 密码入队数据传输对象，包含团队 ID 和密码
     * @return 加入结果
     */
    @PostMapping("/join-by-password")
    public Result joinByPassword(@RequestBody TeamJoinByPasswordDTO dto) {
        return teamService.joinByPassword(dto);
    }

    /**
     * 邀请用户加入团队（仅队长/管理员）
     * POST /api/team/invite
     * 直接加入，无需审批，异步发送邀请通知
     *
     * @param dto 邀请数据传输对象，包含团队 ID 和被邀请用户 ID
     * @return 邀请结果
     */
    @PostMapping("/invite")
    public Result inviteMember(@RequestBody TeamInviteDTO dto) {
        return teamService.inviteMember(dto);
    }

    /**
     * 主动退出团队
     * POST /api/team/quit?teamId=1001
     * 队长不可直接退出，需先转让权限或解散团队
     *
     * @param teamId 要退出的团队 ID
     * @return 退出结果
     */
    @PostMapping("/quit")
    public Result quitTeam(@RequestParam Long teamId) {
        return teamService.quitTeam(teamId);
    }

    // ==================== 审批 ====================

    /**
     * 获取待审核申请列表（仅队长/管理员）
     * GET /api/team/apply/pending?teamId=1001&page=1&pageSize=20
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 待审核申请分页列表
     */
    @GetMapping("/apply/pending")
    public Result getPendingApplyList(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return teamService.getPendingApplyList(teamId, page, pageSize);
    }

    /**
     * 审批加入申请（仅队长/管理员）
     * POST /api/team/apply/audit
     * auditStatus: 1-通过（直接入队），2-拒绝（发送拒绝通知）
     *
     * @param dto 审批数据传输对象，包含申请 ID 和审批状态
     * @return 审批结果
     */
    @PostMapping("/apply/audit")
    public Result auditApply(@RequestBody TeamAuditDTO dto) {
        return teamService.auditApply(dto);
    }

    // ==================== 成员管理 ====================

    /**
     * 获取团队成员列表
     * GET /api/team/{teamId}/members?page=1&pageSize=20
     * 按角色升序（队长→管理员→普通成员），含禁言状态
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 成员分页列表
     */
    @GetMapping("/{teamId}/members")
    public Result getMemberList(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return teamService.getMemberList(teamId, page, pageSize);
    }

    /**
     * 获取团队成员列表（按角色）
     * GET /api/team/members?teamId=1001&roleType=2&page=1&pageSize=20
     *
     * @param teamId   团队 ID
     * @param roleType 角色类型（1-队长，2-管理员，3-普通成员，不传查全部）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 成员分页列表
     */
    @GetMapping("/members")
    public Result getMemberListByRole(
            @RequestParam Long teamId,
            @RequestParam(required = false) Integer roleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return teamService.getMemberListByRole(teamId, roleType, page, pageSize);
    }

    /**
     * 移除成员（仅队长/管理员）
     * POST /api/team/member/remove
     * 不能移除队长；管理员不能移除其他管理员
     *
     * @param dto 成员操作数据传输对象，包含团队 ID 和目标用户 ID
     * @return 移除结果
     */
    @PostMapping("/member/remove")
    public Result removeMember(@RequestBody TeamMemberOperationDTO dto) {
        return teamService.removeMember(dto);
    }

    /**
     * 修改成员角色（仅队长）
     * POST /api/team/member/role/update
     * roleType: 2-管理员，3-普通成员；不可修改队长角色
     *
     * @param dto 成员操作数据传输对象，包含团队 ID、目标用户 ID 和新角色
     * @return 修改结果
     */
    @PostMapping("/member/role/update")
    public Result updateMemberRole(@RequestBody TeamMemberOperationDTO dto) {
        return teamService.updateMemberRole(dto);
    }

    /**
     * 修改成员角色（兼容 Part7 路径）
     * POST /api/team/member/role
     */
    @PostMapping("/member/role")
    public Result updateMemberRoleAlias(@RequestBody TeamMemberOperationDTO dto) {
        return teamService.updateMemberRole(dto);
    }

    /**
     * 转让队长权限（仅队长）
     * POST /api/team/transfer-leader
     * 原队长降为管理员，目标成员升为队长
     *
     * @param dto 成员操作数据传输对象，包含团队 ID 和目标用户 ID
     * @return 转让结果
     */
    @PostMapping("/transfer-leader")
    public Result transferLeader(@RequestBody TeamMemberOperationDTO dto) {
        return teamService.transferLeader(dto);
    }

    /**
     * 转让队长权限（兼容别名）
     * POST /api/team/transfer-captain
     */
    @PostMapping("/transfer-captain")
    public Result transferCaptain(@RequestBody TeamMemberOperationDTO dto) {
        return teamService.transferLeader(dto);
    }

    // ==================== 禁言管理 ====================

    /**
     * 全员禁言/解除全员禁言（仅队长/管理员）
     * POST /api/team/mute-all
     * isMute: true-开启全员禁言，false-解除
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和禁言开关
     * @return 操作结果
     */
    @PostMapping("/mute-all")
    public Result muteAll(@RequestBody TeamMuteDTO dto) {
        return teamService.muteAll(dto);
    }

    /**
     * 禁言指定成员（仅队长/管理员）
     * POST /api/team/member/mute
     * muteDuration 单位：分钟；不能禁言队长；管理员不能禁言其他管理员
     *
     * @param dto 禁言数据传输对象，包含团队 ID、目标用户 ID 和禁言时长
     * @return 禁言结果
     */
    @PostMapping("/member/mute")
    public Result muteMember(@RequestBody TeamMuteDTO dto) {
        return teamService.muteMember(dto);
    }

    /**
     * 解除指定成员禁言（仅队长/管理员）
     * POST /api/team/member/unmute
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和目标用户 ID
     * @return 解除结果
     */
    @PostMapping("/member/unmute")
    public Result unmuteMember(@RequestBody TeamMuteDTO dto) {
        return teamService.unmuteMember(dto);
    }
}

