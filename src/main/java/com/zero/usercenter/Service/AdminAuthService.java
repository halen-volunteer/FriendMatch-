package com.zero.usercenter.Service;

public interface AdminAuthService {

    /**
     * 判断用户是否为启用状态的管理员。
     */
    boolean isAdmin(Long userId);

    /**
     * 断言当前用户为管理员；否则抛出业务异常。
     */
    void assertAdmin(Long userId);
}
