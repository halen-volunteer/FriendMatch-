package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * <p>同时支持账号登录和邮箱登录，后端会根据 {@code userAccount} 的格式判断登录方式。</p>
 */
@Data
public class LoginDTO {

    /**
     * 图形验证码内容。
     */
    private String checkNumber;

    /**
     * 登录密码（明文入参，后端会进行加密比对）。
     */
    private String userPassword;

    /**
     * 登录标识（账号或邮箱）。
     */
    private String userAccount;

    /**
     * 图形验证码在 Redis 中对应的唯一标识。
     */
    private String captchaID;
}
