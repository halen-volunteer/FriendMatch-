package com.zero.usercenter.Service;

/**
 * 管理员权限校验接口。
 */
public interface AdminAuthService {

    /**
     * 判断用户是否为启用状态的管理员。
     *
     * @param userId 待校验的用户 ID
     * @return true 表示用户具备管理员权限，false 表示不具备
     */
    boolean isAdmin(Long userId);

    /**
     * 断言当前用户为管理员；否则抛出业务异常。
     *
     * @param userId 待校验的用户 ID
     */
    void assertAdmin(Long userId);
}
