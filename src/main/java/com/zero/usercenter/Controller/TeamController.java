package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 团队相关接口入口。
 * 统一暴露团队创建、加入、审批、成员管理和禁言控制接口。
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    @Resource
    private TeamService teamService;

    // ==================== 团队创建与查询 ====================

    /**
     * 创建团队。
     */
    @PostMapping("/create")
    public Result createTeam(@RequestBody TeamCreateDTO dto) {
        // Controller 只负责接收创建请求，真正的参数校验、建团、默认成员写入都在 service 层完成。
        return teamService.createTeam(dto);
    }

    /**
     * 获取团队列表。
     */
    @GetMapping("/list")
    public Result getTeamList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer teamType,
            @RequestParam(defaultValue = "id") String sort) {
        // 列表查询统一转发给 service，由 service 决定筛选条件、排序规则和分页结果。
        return teamService.getTeamList(page, pageSize, teamType, sort);
    }

    /**
     * 搜索团队。
     */
    @GetMapping("/search")
    public Result searchTeam(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "name") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 搜索入口保持轻量，具体按名称/标签/简介怎么搜由 service 层封装。
        return teamService.searchTeam(keyword, type, page, pageSize);
    }

    /**
     * 获取团队详情。
     */
    @GetMapping("/{teamId}")
    public Result getTeamDetail(@PathVariable Long teamId) {
        // 团队详情会在 service 层补齐成员数、加入状态、权限等附加信息。
        return teamService.getTeamDetail(teamId);
    }

    /**
     * 编辑团队信息。
     */
    @PostMapping("/update")
    public Result updateTeam(@RequestBody TeamUpdateDTO dto) {
        // 编辑团队涉及权限校验和字段白名单控制，统一下沉到 service。
        return teamService.updateTeam(dto);
    }

    /**
     * 解散团队。
     */
    @PostMapping("/dissolve")
    public Result dissolveTeam(@RequestParam Long teamId) {
        // 解散团队会连带处理成员关系、会话状态等副作用，因此由 service 层统一编排。
        return teamService.dissolveTeam(teamId);
    }

    // ==================== 加入团队 ====================

    /**
     * 申请加入团队。
     */
    @PostMapping("/apply")
    public Result applyTeam(@RequestBody TeamApplyDTO dto) {
        // 申请入队会在 service 层判断团队类型、重复申请和审批流。
        return teamService.applyTeam(dto);
    }

    /**
     * 通过密码加入团队。
     */
    @PostMapping("/join-by-password")
    public Result joinByPassword(@RequestBody TeamJoinByPasswordDTO dto) {
        // 密码入队和申请入队共用团队加入链路，只是入口参数不同。
        return teamService.joinByPassword(dto);
    }

    /**
     * 邀请用户加入团队。
     */
    @PostMapping("/invite")
    public Result inviteMember(@RequestBody TeamInviteDTO dto) {
        // 邀请成员需要做团队角色权限、目标用户状态等校验，统一交给 service。
        return teamService.inviteMember(dto);
    }

    /**
     * 主动退出团队。
     */
    @PostMapping("/quit")
    public Result quitTeam(@RequestParam Long teamId) {
        // 退出团队会影响成员关系和可能的队长转移规则，由 service 统一处理。
        return teamService.quitTeam(teamId);
    }

    // ==================== 审批 ====================

    /**
     * 获取待审核申请列表。
     */
    @GetMapping("/apply/pending")
    public Result getPendingApplyList(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 待审核列表的权限校验和状态过滤都在 service 层完成。
        return teamService.getPendingApplyList(teamId, page, pageSize);
    }

    /**
     * 审批加入申请。
     */
    @PostMapping("/apply/audit")
    public Result auditApply(@RequestBody TeamAuditDTO dto) {
        // 审批通过后可能触发加群、发通知等副作用，因此保持 controller 只做转发。
        return teamService.auditApply(dto);
    }

    // ==================== 成员管理 ====================

    /**
     * 获取团队成员列表。
     */
    @GetMapping("/{teamId}/members")
    public Result getMemberList(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 成员列表的分页、角色拼装和可见性处理都在 service 层封装。
        return teamService.getMemberList(teamId, page, pageSize);
    }

    /**
     * 按角色筛选团队成员列表。
     */
    @GetMapping("/members")
    public Result getMemberListByRole(
            @RequestParam Long teamId,
            @RequestParam(required = false) Integer roleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 这是按角色筛选的成员列表入口，内部仍复用团队成员查询能力。
        return teamService.getMemberListByRole(teamId, roleType, page, pageSize);
    }

    /**
     * 移除成员。
     */
    @PostMapping("/member/remove")
    public Result removeMember(@RequestBody TeamMemberOperationDTO dto) {
        // 移除成员涉及权限校验、目标成员判断和会话关系清理，统一交给 service。
        return teamService.removeMember(dto);
    }

    /**
     * 修改成员角色。
     */
    @PostMapping("/member/role/update")
    public Result updateMemberRole(@RequestBody TeamMemberOperationDTO dto) {
        // 成员角色修改由 service 层处理，避免控制层知道具体角色规则。
        return teamService.updateMemberRole(dto);
    }

    /**
     * 修改成员角色兼容入口。
     */
    @PostMapping("/member/role")
    public Result updateMemberRoleAlias(@RequestBody TeamMemberOperationDTO dto) {
        // 兼容旧路径，内部仍然走同一套角色变更逻辑，避免两套实现分叉。
        return teamService.updateMemberRole(dto);
    }

    /**
     * 转让队长权限。
     */
    @PostMapping("/transfer-leader")
    public Result transferLeader(@RequestBody TeamMemberOperationDTO dto) {
        // 队长转让涉及权限移交和角色回写，由 service 层统一编排。
        return teamService.transferLeader(dto);
    }

    /**
     * 转让队长权限兼容入口。
     */
    @PostMapping("/transfer-captain")
    public Result transferCaptain(@RequestBody TeamMemberOperationDTO dto) {
        // 兼容旧命名入口，防止历史前端调用失效。
        return teamService.transferLeader(dto);
    }

    // ==================== 禁言管理 ====================

    /**
     * 设置或解除全员禁言。
     */
    @PostMapping("/mute-all")
    public Result muteAll(@RequestBody TeamMuteDTO dto) {
        // 全员禁言会落到团队权限和群聊状态更新链路中，controller 不持有业务细节。
        return teamService.muteAll(dto);
    }

    /**
     * 禁言指定成员。
     */
    @PostMapping("/member/mute")
    public Result muteMember(@RequestBody TeamMuteDTO dto) {
        // 指定成员禁言由 service 层处理成员权限和时长等业务规则。
        return teamService.muteMember(dto);
    }

    /**
     * 解除指定成员禁言。
     */
    @PostMapping("/member/unmute")
    public Result unmuteMember(@RequestBody TeamMuteDTO dto) {
        // 解除禁言和设置禁言共用同一套团队成员状态管理逻辑。
        return teamService.unmuteMember(dto);
    }
}

