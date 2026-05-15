package com.zero.usercenter.utils;

import com.zero.usercenter.DTO.UserFormat;

/**
 * 用户上下文 ThreadLocal 工具类。
 * 用于在一次请求链路内传递当前登录用户信息。
 */
public class UserHolder {

    /** 当前线程绑定的登录用户信息。 */
    private static final ThreadLocal<UserFormat> userThreadLocal = new ThreadLocal<>();

    /**
     * 保存当前用户。
     *
     * @param user 当前请求对应的登录用户
     */
    public static void saveUser(UserFormat user) {
        userThreadLocal.set(user);
    }

    /**
     * 获取当前用户信息。
     *
     * @return 当前线程绑定的用户；未登录时返回 null
     */
    public static UserFormat getUser() {
        return userThreadLocal.get();
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 当前用户 ID；未登录时返回 null
     */
    public static Long getUserId() {
        UserFormat user = userThreadLocal.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户账号。
     *
     * @return 当前用户账号；未登录时返回 null
     */
    public static String getUserAccount() {
        UserFormat user = userThreadLocal.get();
        return user != null ? user.getUserAccount() : null;
    }

    /**
     * 清理 ThreadLocal，防止线程复用时串到下一次请求。
     */
    public static void removeUser() {
        userThreadLocal.remove();
    }
}
