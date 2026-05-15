package com.zero.usercenter.aop.annotation;

import java.lang.annotation.*;

/**
 * 管理员权限注解。
 * 用于声明当前方法仅允许启用状态的管理员访问。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
