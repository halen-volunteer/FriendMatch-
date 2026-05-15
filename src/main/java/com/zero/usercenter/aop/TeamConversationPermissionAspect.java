package com.zero.usercenter.aop;

import com.zero.usercenter.Service.TeamPermissionService;
import com.zero.usercenter.aop.annotation.RequireTeamConversationRole;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 团队会话角色权限切面。
 * 用于处理“只有 conversationId，没有直接 teamId”的群聊前置权限校验。
 */
@Aspect
@Component
@Order(21)
public class TeamConversationPermissionAspect {

    /**
     * 团队会话 ID 固定前缀。
     * 例如 `team_1001` 会被解析为团队 ID `1001`。
     */
    private static final String TEAM_CONVERSATION_PREFIX = "team_";

    /**
     * SpEL 表达式解析器。
     * 负责从注解表达式和方法参数中提取 conversationId。
     */
    @Resource
    private PermissionExpressionEvaluator expressionEvaluator;

    /**
     * 团队权限服务。
     * 负责校验当前用户在目标团队中的角色是否满足要求。
     */
    @Resource
    private TeamPermissionService teamPermissionService;

    /**
     * 环绕增强：拦截所有标记了团队会话角色注解的方法。
     *
     * @param joinPoint 当前切点，可获取目标方法和入参
     * @param requireTeamConversationRole 注解配置，包含 conversationId 表达式和角色要求
     * @return 目标方法执行结果
     * @throws Throwable 原方法执行异常或权限校验异常
     */
    @Around("@annotation(requireTeamConversationRole)")
    public Object around(ProceedingJoinPoint joinPoint,
                         RequireTeamConversationRole requireTeamConversationRole) throws Throwable {
        // 第一步：先确认用户已经登录，避免匿名请求进入后续团队权限判断。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 第二步：根据注解上的 SpEL 表达式，从目标方法参数里解析出 conversationId。
        String conversationId = expressionEvaluator.getStringValue(
                requireTeamConversationRole.conversationId(),
                joinPoint.getArgs(),
                requireTeamConversationRole.invalidConversationMessage());

        // 第三步：先判断当前会话是不是团队会话。
        // 如果不是 team_ 前缀的团队会话，就根据注解配置决定直接放行还是拦截。
        if (!conversationId.startsWith(TEAM_CONVERSATION_PREFIX)) {
            if (requireTeamConversationRole.requiredTeamConversation()) {
                throw new BusinessException(requireTeamConversationRole.invalidConversationMessage());
            }
            return joinPoint.proceed();
        }

        // 第四步：把 team_123 这种会话标识解析成真正的团队 ID。
        Long teamId = parseTeamId(conversationId, requireTeamConversationRole.invalidTeamIdMessage());

        // 第五步：委托团队权限服务，统一校验当前用户在该团队中的角色范围。
        teamPermissionService.assertRole(
                teamId,
                userId,
                requireTeamConversationRole.value(),
                requireTeamConversationRole.forbiddenMessage());

        // 第六步：所有前置校验通过后，才真正执行目标业务方法。
        return joinPoint.proceed();
    }

    /**
     * 从团队会话 ID 中解析出团队 ID。
     *
     * @param conversationId 会话 ID，例如 team_1001
     * @param invalidTeamIdMessage 团队 ID 非法时抛出的错误提示
     * @return 解析得到的团队 ID
     */
    private Long parseTeamId(String conversationId, String invalidTeamIdMessage) {
        try {
            // 去掉固定前缀后，剩余部分应当是可转为 Long 的纯数字团队 ID。
            return Long.parseLong(conversationId.substring(TEAM_CONVERSATION_PREFIX.length()));
        } catch (Exception e) {
            throw new BusinessException(invalidTeamIdMessage);
        }
    }
}
