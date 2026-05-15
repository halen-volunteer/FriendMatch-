package com.zero.usercenter.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 邮件发送工具。
 * 对上层屏蔽纯文本和 HTML 邮件的底层发送细节。
 */
@Component
@Slf4j
public class EmailApi {
    
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String username;
    
    /**
     * 发送纯文本邮件。
     *
     * @param subject 邮件主题
     * @param content 纯文本正文
     * @param to 收件人邮箱
     */
    public void sendSimpleEmail(String subject, String content, String to) {
        try {
            // 1. 构建纯文本邮件对象。
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            // 2. 交给 Spring Mail 发送。
            mailSender.send(message);
            log.info("邮件发送成功：{}", to);
        } catch (Exception e) {
            log.error("邮件发送失败：{}", to, e);
            throw new RuntimeException("邮件发送失败");
        }
    }
    
    /**
     * 发送 HTML 邮件。
     *
     * @param subject 邮件主题
     * @param content HTML 正文
     * @param to 收件人邮箱
     */
    public void sendHtmlEmail(String subject, String content, String to) {
        try {
            // 1. 创建 MIME 邮件，支持富文本和更复杂的邮件结构。
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            // 2. 发送 HTML 邮件给目标用户。
            mailSender.send(message);
            log.info("HTML 邮件发送成功：{}", to);
        } catch (MessagingException e) {
            log.error("HTML 邮件发送失败：{}", to, e);
            throw new RuntimeException("邮件发送失败");
        }
    }
}
