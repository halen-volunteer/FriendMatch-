package com.zero.usercenter.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志异步消息体。
 * 用于承载登录审计落库所需的最小字段，避免主流程直接同步写日志表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogMessage {

    /** 登录用户 ID。 */
    private Long userId;

    /** 登录 IP。 */
    private String loginIp;

    /** 登录方式：1-账号密码，2-验证码。 */
    private Integer loginType;

    /** 登录结果：0-失败，1-成功。 */
    private Integer loginResult;
}
