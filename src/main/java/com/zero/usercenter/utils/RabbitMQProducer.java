package com.zero.usercenter.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zero.usercenter.config.RabbitMQConfig;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.EmailMessage;
import com.zero.usercenter.mq.message.LoginLogMessage;
import com.zero.usercenter.mq.message.SystemNoticeMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 生产者。
 * 统一负责把业务消息序列化为 JSON 并投递到对应交换机。
 */
@Slf4j
@Component
public class RabbitMQProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 好友申请事件发送入口。
     * 当前好友主链路仍以同步事务为主，这里保留给后续事件化扩展。
     *
     * @param message 好友申请事件载荷
     */
    public void sendFriendRequestMessage(Object message) {
        sendJsonMessage(
                RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                RabbitMQConfig.FRIEND_REQUEST_ROUTING_KEY,
                message,
                "friend request");
    }

    /**
     * 好友通过事件发送入口。
     * 当前实际通知统一走 system notice，这里仍是预留方法。
     *
     * @param message 好友通过事件载荷
     */
    public void sendFriendAgreeMessage(Object message) {
        sendJsonMessage(
                RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                RabbitMQConfig.FRIEND_AGREE_ROUTING_KEY,
                message,
                "friend agree");
    }

    /**
     * 好友拒绝事件发送入口。
     * 当前尚未接入真实消费逻辑，仅保留交换机和路由能力。
     *
     * @param message 好友拒绝事件载荷
     */
    public void sendFriendRejectMessage(Object message) {
        sendJsonMessage(
                RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                RabbitMQConfig.FRIEND_REJECT_ROUTING_KEY,
                message,
                "friend reject");
    }

    /**
     * 删除好友事件发送入口。
     * 当前业务仍以同步删关系加异步系统通知为主，这里暂未启用。
     *
     * @param message 删除好友事件载荷
     */
    public void sendFriendDeleteMessage(Object message) {
        sendJsonMessage(
                RabbitMQConfig.FRIEND_OPERATION_EXCHANGE,
                RabbitMQConfig.FRIEND_DELETE_ROUTING_KEY,
                message,
                "friend delete");
    }

    /**
     * 拉黑事件发送入口。
     * 当前只保留发送能力，黑名单主链路还没有改造成 MQ 消费。
     *
     * @param message 拉黑事件载荷
     */
    public void sendBlacklistMessage(Object message) {
        sendJsonMessage(
                RabbitMQConfig.FRIEND_OPERATION_EXCHANGE,
                RabbitMQConfig.BLACKLIST_ROUTING_KEY,
                message,
                "blacklist");
    }

    /**
     * 验证码邮件异步投递入口。
     *
     * @param message 邮件消息体
     */
    public void sendEmailMessage(EmailMessage message) {
        sendJsonMessage(
                RabbitMQConfig.EMAIL_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                message,
                "email");
    }

    /**
     * 登录日志异步落库入口。
     *
     * @param message 登录日志消息体
     */
    public void sendLoginLogMessage(LoginLogMessage message) {
        sendJsonMessage(
                RabbitMQConfig.LOGIN_LOG_EXCHANGE,
                RabbitMQConfig.LOGIN_LOG_ROUTING_KEY,
                message,
                "login log");
    }

    public void sendChatMessage(ChatSendMessage message) {
        sendJsonMessage(
                RabbitMQConfig.CHAT_MESSAGE_EXCHANGE,
                RabbitMQConfig.CHAT_MESSAGE_ROUTING_KEY,
                message,
                "chat message");
    }

    /**
     * 系统通知异步入库和在线推送入口。
     *
     * @param message 系统通知消息体
     */
    public void sendSystemNotificationMessage(SystemNoticeMessage message) {
        sendJsonMessage(
                RabbitMQConfig.SYSTEM_NOTIFICATION_EXCHANGE,
                "",
                message,
                "system notice");
    }

    /**
     * 统一 JSON 序列化发送逻辑。
     * 如果发送失败直接抛异常，由上层决定重试、降级或记录补偿任务。
     *
     * @param exchange 目标交换机
     * @param routingKey 路由键
     * @param message 原始业务消息对象
     * @param messageType 日志打印用的消息类型标识
     */
    private void sendJsonMessage(String exchange, String routingKey, Object message, String messageType) {
        try {
            // 1. 先把业务对象序列化为 JSON，统一队列中的消息格式。
            String jsonMessage = objectMapper.writeValueAsString(message);
            // 2. 再投递到指定交换机和路由键，由 RabbitMQ 决定后续路由。
            rabbitTemplate.convertAndSend(exchange, routingKey, jsonMessage);
            log.info("RabbitMQ {} message sent: {}", messageType, jsonMessage);
        } catch (Exception e) {
            log.error("RabbitMQ {} message send failed", messageType, e);
            throw new RuntimeException("RabbitMQ message send failed: " + messageType, e);
        }
    }
}
