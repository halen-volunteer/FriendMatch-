package com.zero.usercenter.aop;

import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.aop.annotation.RequireAdmin;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/**
 * 管理员权限切面。
 * 统一拦截后台治理类方法，避免在每个 Service 方法开头重复写管理员校验。
 */
@Aspect
@Component
@Order(10)
public class AdminPermissionAspect {

    /**
     * 管理员权限服务。
     * 用于校验当前登录用户是否具备后台管理员身份。
     */
    @Resource
    private AdminAuthService adminAuthService;

    /**
     * 环绕增强：拦截所有要求管理员权限的方法。
     *
     * @param joinPoint 当前切点，可获取被拦截方法及其参数
     * @param requireAdmin 管理员权限注解
     * @return 目标方法执行结果
     * @throws Throwable 原方法执行异常或管理员校验异常
     */
    @Around("@annotation(requireAdmin)")
    public Object around(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        // 第一步：先确认用户已经登录，未登录请求不允许进入后台权限校验。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 第二步：委托管理员权限服务，统一校验该用户是否为有效管理员。
        adminAuthService.assertAdmin(userId);

        // 第三步：管理员身份成立后，继续执行原始业务方法。
        return joinPoint.proceed();
    }
}
