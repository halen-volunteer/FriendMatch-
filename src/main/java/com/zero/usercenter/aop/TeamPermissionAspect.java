package com.zero.usercenter.aop;

import com.zero.usercenter.Service.TeamPermissionService;
import com.zero.usercenter.aop.annotation.RequireTeamRole;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/**
 * 团队角色权限切面。
 * 统一拦截“仅队长”与“仅队长/管理员”类前置判断，减少 TeamServiceImpl 中的重复分支。
 */
@Aspect
@Component
@Order(20)
public class TeamPermissionAspect {

    /**
     * SpEL 表达式解析器。
     * 用于从注解配置和方法参数中解析出真实 teamId。
     */
    @Resource
    private PermissionExpressionEvaluator expressionEvaluator;

    /**
     * 团队权限服务。
     * 统一封装团队成员角色校验逻辑。
     */
    @Resource
    private TeamPermissionService teamPermissionService;

    /**
     * 环绕增强：拦截标记团队角色要求的方法。
     *
     * @param joinPoint 当前切点，可获取方法和参数
     * @param requireTeamRole 注解配置，包含 teamId 表达式和所需角色范围
     * @return 目标方法执行结果
     * @throws Throwable 原方法执行异常或权限校验异常
     */
    @Around("@annotation(requireTeamRole)")
    public Object around(ProceedingJoinPoint joinPoint, RequireTeamRole requireTeamRole) throws Throwable {
        // 第一步：先检查当前请求是否已有登录用户。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 第二步：把注解里写的 SpEL 表达式（如 #p0）解析成真实的团队 ID。
        Long teamId = expressionEvaluator.getLongValue(
                requireTeamRole.teamId(),
                joinPoint.getArgs(),
                requireTeamRole.invalidTeamIdMessage());

        // 第三步：对解析结果做一次基础兜底，避免非法 teamId 继续向下传播。
        if (teamId <= 0) {
            throw new BusinessException(requireTeamRole.invalidTeamIdMessage());
        }

        // 第四步：统一走团队权限服务，校验当前用户角色是否满足注解要求。
        teamPermissionService.assertRole(
                teamId,
                userId,
                requireTeamRole.value(),
                requireTeamRole.forbiddenMessage());

        // 第五步：前置权限通过后，再放行业务方法。
        return joinPoint.proceed();
    }
}
