package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Service.TeamPermissionService;
import com.zero.usercenter.aop.annotation.TeamRoleScope;
import com.zero.usercenter.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 团队权限服务实现。
 * 当前优先承接“成员 / 队长或管理员 / 队长”这三类稳定前置权限。
 */
@Service
public class TeamPermissionServiceImpl implements TeamPermissionService {

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    /**
     * 查询用户在团队中的有效成员记录。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return 未退出的成员记录；不存在时返回 null
     */
    @Override
    public TeamMember getActiveMember(Long teamId, Long userId) {
        // 1. 这里只返回当前仍在团队内的有效成员关系，已退出成员不参与任何权限判断。
        if (teamId == null || userId == null) {
            return null;
        }
        LambdaQueryWrapper<TeamMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectOne(queryWrapper);
    }

    /**
     * 断言用户满足指定团队角色要求。
     *
     * @param teamId           团队 ID
     * @param userId           用户 ID
     * @param scope            角色范围
     * @param forbiddenMessage 自定义无权限提示
     */
    @Override
    public void assertRole(Long teamId, Long userId, TeamRoleScope scope, String forbiddenMessage) {
        // 1. 先确认团队存在，避免后续把“不存在的团队”误判成“没有权限”。
        Team team = teamMapper.selectById(teamId);
        if (team == null || Integer.valueOf(1).equals(team.getIsDelete())) {
            throw new BusinessException("团队不存在");
        }

        // 2. 再查当前用户在团队内的有效成员关系，后续所有权限判断都以它为基准。
        TeamMember member = getActiveMember(teamId, userId);
        // 3. 根据注解传入的 scope 决定权限边界：
        // MEMBER 只要求在队内；ADMIN_OR_LEADER 要求管理员或队长；LEADER 只允许队长。
        boolean passed = switch (scope) {
            // 是队员就可以操作。
            case MEMBER -> member != null;
            // 你的角色是队长或管理员，才可以操作。
            case ADMIN_OR_LEADER -> member != null && member.getRoleType() != null && member.getRoleType() <= 2;
            // 只有队长可以操作。
            case LEADER -> member != null && Integer.valueOf(1).equals(member.getRoleType());
        };
        // 4. 权限不足时统一抛出业务异常，便于接口层得到稳定错误提示。
        if (!passed) {
            throw new BusinessException(resolveMessage(scope, forbiddenMessage));
        }
    }

    /**
     * 解析最终要抛出的权限提示文案。
     *
     * @param scope            角色范围
     * @param forbiddenMessage 自定义提示
     * @return 最终提示文案
     */
    private String resolveMessage(TeamRoleScope scope, String forbiddenMessage) {
        // 1. 优先使用注解自定义提示，没有配置时再回退到角色对应的默认文案。
        if (forbiddenMessage != null && !forbiddenMessage.isBlank()) {
            return forbiddenMessage;
        }
        return switch (scope) {
            case MEMBER -> "仅团队成员可操作";
            case ADMIN_OR_LEADER -> "仅队长/管理员可操作";
            case LEADER -> "仅队长可操作";
        };
    }
}
