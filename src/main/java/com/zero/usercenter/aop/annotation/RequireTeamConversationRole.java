package com.zero.usercenter.aop.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基于团队会话 ID 的角色权限注解。
 * 适用于参数里没有直接传 teamId，而是通过 team_{teamId} 这类会话标识进行鉴权的场景。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTeamConversationRole {

    /**
     * 所需角色范围。
     */
    TeamRoleScope value();

    /**
     * conversationId 提取表达式，支持 #p0、#p0.conversationId 这类写法。
     */
    String conversationId();

    /**
     * 是否要求当前会话必须为团队会话。
     * 为 false 时，非 team_ 会话会直接跳过该注解校验。
     */
    boolean requiredTeamConversation() default true;

    /**
     * 会话标识非法时的提示。
     */
    String invalidConversationMessage() default "会话ID无效";

    /**
     * 团队 ID 非法时的提示。
     */
    String invalidTeamIdMessage() default "团队ID无效";

    /**
     * 角色不满足时的提示。
     */
    String forbiddenMessage() default "";
}
