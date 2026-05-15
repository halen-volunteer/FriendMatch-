package com.zero.usercenter.mq;

import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.EmailMessage;
import com.zero.usercenter.mq.message.LoginLogMessage;
import com.zero.usercenter.mq.message.SystemNoticeMessage;
import com.zero.usercenter.utils.RabbitMQProducer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 异步消息分发门面。
 * 业务层只需要调用这里暴露的“发邮件、记登录日志、发系统通知、投递聊天消息”等方法，
 * 无需关心交换机、路由键、消息体序列化等 MQ 细节。
 */
@Component
public class AsyncMessageService {

    @Resource
    private RabbitMQProducer rabbitMQProducer;

    /**
     * 发送 HTML 邮件异步任务。
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content HTML 格式邮件正文
     */
    public void sendHtmlEmail(String to, String subject, String content) {
        // 1. 业务层只传最小必要参数，消息体封装和路由细节统一由门面完成。
        rabbitMQProducer.sendEmailMessage(new EmailMessage(to, subject, content, true));
    }

    /**
     * 投递登录日志异步落库任务。
     *
     * @param userId      登录用户 ID
     * @param loginIp     登录 IP
     * @param loginType   登录方式：1-账号密码，2-验证码
     * @param loginResult 登录结果：0-失败，1-成功
     */
    public void sendLoginLog(Long userId, String loginIp, Integer loginType, Integer loginResult) {
        // 1. 登录主流程只关心鉴权结果，审计日志通过 MQ 下沉到异步链路，避免阻塞接口响应。
        rabbitMQProducer.sendLoginLogMessage(new LoginLogMessage(userId, loginIp, loginType, loginResult));
    }

    /**
     * 投递聊天消息异步发送任务。
     *
     * @param message 待消费聊天消息体
     */
    public void sendChatMessage(ChatSendMessage message) {
        // 1. 聊天发送接口只做到“快速校验 + 入队”，真正的落库和推送由消费者继续完成。
        rabbitMQProducer.sendChatMessage(message);
    }

    /**
     * 投递系统通知异步任务。
     *
     * @param userId        接收通知的用户 ID
     * @param noticeType    通知类型
     * @param noticeContent 通知文案
     * @param relatedId     关联业务 ID
     */
    public void sendSystemNotice(Long userId, Integer noticeType, String noticeContent, Long relatedId) {
        // 1. 系统通知统一走“MQ 入队 -> 消费者落库 -> 在线推送”的标准链路。
        rabbitMQProducer.sendSystemNotificationMessage(
                new SystemNoticeMessage(userId, noticeType, noticeContent, relatedId)
        );
    }
}
