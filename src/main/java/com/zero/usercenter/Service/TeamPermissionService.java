package com.zero.usercenter.Service;

import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.aop.annotation.TeamRoleScope;

/**
 * 团队权限服务接口。
 * 统一封装团队成员身份与角色层级校验，供 AOP 和业务服务共同复用。
 */
public interface TeamPermissionService {

    /**
     * 获取指定团队中仍处于有效状态的成员记录。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return 成员记录；若用户不在团队内或已退出，则返回 null
     */
    TeamMember getActiveMember(Long teamId, Long userId);

    /**
     * 断言指定用户满足团队角色要求。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @param scope 允许通过的角色范围
     * @param forbiddenMessage 校验失败时抛出的提示信息
     */
    void assertRole(Long teamId, Long userId, TeamRoleScope scope, String forbiddenMessage);
}
