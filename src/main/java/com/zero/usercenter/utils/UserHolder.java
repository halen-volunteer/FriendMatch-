package com.zero.usercenter.utils;

import com.zero.usercenter.DTO.UserFormat;

/**
 * 用户上下文 ThreadLocal 工具类
 * 用于在请求链路中传递当前用户信息
 */
public class UserHolder {
    
    private static final ThreadLocal<UserFormat> userThreadLocal = new ThreadLocal<>();
    
    /**
     * 保存用户信息到 ThreadLocal
     *
     * @param user 当前登录用户信息
     */
    public static void saveUser(UserFormat user) {
        userThreadLocal.set(user);
    }
    
    /**
     * 获取当前用户信息
     */
    public static UserFormat getUser() {
        return userThreadLocal.get();
    }
    
    /**
     * 获取当前用户 ID
     */
    public static Long getUserId() {
        UserFormat user = userThreadLocal.get();
        return user != null ? user.getId() : null;
    }
    
    /**
     * 获取当前用户账号
     */
    public static String getUserAccount() {
        UserFormat user = userThreadLocal.get();
        return user != null ? user.getUserAccount() : null;
    }
    
    /**
     * 清理 ThreadLocal（防止内存泄漏）
     * 必须在请求结束后调用
     */
    public static void removeUser() {
        userThreadLocal.remove();
    }
}
