package com.zero.usercenter.aop.annotation;

import java.lang.annotation.*;

/**
 * 团队角色权限注解。
 * 通过 SpEL 提取 teamId，并在业务执行前统一完成成员角色校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTeamRole {

    /**
     * 所需角色范围。
     */
    TeamRoleScope value();

    /**
     * teamId 提取表达式，支持 `#p0`、`#p0.teamId` 一类写法。
     */
    String teamId();

    /**
     * teamId 缺失或非法时的提示。
     */
    String invalidTeamIdMessage() default "团队ID无效";

    /**
     * 角色不满足时的提示。
     */
    String forbiddenMessage() default "";
}
