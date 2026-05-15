package com.zero.usercenter.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件异步消息体。
 * 负责把发送邮件所需的目标地址、主题、正文和内容类型统一打包。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    /** 收件人邮箱。 */
    private String to;

    /** 邮件主题。 */
    private String subject;

    /** 邮件正文内容。 */
    private String content;

    /** 是否为 HTML 邮件：true-HTML，false-纯文本。 */
    private Boolean html;
}
