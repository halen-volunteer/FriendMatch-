package com.zero.usercenter.Enums;

import lombok.Getter;

/**
 * 邮件模板枚举。
 * 统一维护邮件主题和正文模板，避免业务代码散落拼接 HTML。
 */
@Getter
public enum EmailTemplateEnum {
    
    VERIFICATION_CODE_EMAIL_HTML(
        "FriendMatch 验证码",
        "<html>" +
        "<head><meta charset='UTF-8'></head>" +
        "<body style='font-family: Arial, sans-serif; background-color: #f5f5f5;'>" +
        "<div style='max-width: 600px; margin: 0 auto; padding: 20px; background-color: white; border-radius: 8px;'>" +
        "<h2 style='color: #333; text-align: center;'>FriendMatch 验证码</h2>" +
        "<hr style='border: none; border-top: 1px solid #e0e0e0;'>" +
        "<p style='font-size: 16px; color: #666;'>您的验证码是：</p>" +
        "<div style='background-color: #f0f0f0; padding: 20px; border-radius: 5px; text-align: center; margin: 20px 0;'>" +
        "<span style='font-size: 36px; font-weight: bold; color: #007bff; letter-spacing: 5px;'>{code}</span>" +
        "</div>" +
        "<p style='font-size: 14px; color: #999;'>验证码有效期为 5 分钟，请勿泄露给他人。</p>" +
        "<p style='font-size: 12px; color: #ccc; margin-top: 30px; text-align: center;'>© 2026 FriendMatch. All rights reserved.</p>" +
        "</div>" +
        "</body>" +
        "</html>"
    );
    
    private final String subject;
    private final String template;
    
    EmailTemplateEnum(String subject, String template) {
        this.subject = subject;
        this.template = template;
    }
    
    /**
     * 用业务参数替换模板占位符。
     */
    public String set(String code) {
        return template.replace("{code}", code);
    }
}
