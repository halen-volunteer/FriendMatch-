package com.zero.usercenter.utils;

import com.alibaba.fastjson2.JSON;
import com.zero.usercenter.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RabbitMQ 消息生产者
 * 负责发送各类业务消息到 RabbitMQ
 */
@Slf4j
@Component
public class RabbitMQProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发送好友申请消息
     * 
     * @param message 消息内容（包含申请人ID、被申请人ID等）
     */
    public void sendFriendRequestMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                    RabbitMQConfig.FRIEND_REQUEST_ROUTING_KEY,
                    jsonMessage);
            log.info("好友申请消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("好友申请消息发送失败", e);
        }
    }

    /**
     * 发送好友同意消息
     * 
     * @param message 消息内容（包含同意人ID、申请人ID等）
     */
    public void sendFriendAgreeMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                    RabbitMQConfig.FRIEND_AGREE_ROUTING_KEY,
                    jsonMessage);
            log.info("好友同意消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("好友同意消息发送失败", e);
        }
    }

    /**
     * 发送好友拒绝消息
     * 
     * @param message 消息内容（包含拒绝人ID、申请人ID等）
     */
    public void sendFriendRejectMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                    RabbitMQConfig.FRIEND_REJECT_ROUTING_KEY,
                    jsonMessage);
            log.info("好友拒绝消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("好友拒绝消息发送失败", e);
        }
    }

    /**
     * 发送好友删除消息
     * 
     * @param message 消息内容（包含删除人ID、被删除人ID等）
     */
    public void sendFriendDeleteMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_OPERATION_EXCHANGE,
                    RabbitMQConfig.FRIEND_DELETE_ROUTING_KEY,
                    jsonMessage);
            log.info("好友删除消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("好友删除消息发送失败", e);
        }
    }

    /**
     * 发送拉黑用户消息
     * 
     * @param message 消息内容（包含拉黑人ID、被拉黑人ID等）
     */
    public void sendBlacklistMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_OPERATION_EXCHANGE,
                    RabbitMQConfig.BLACKLIST_ROUTING_KEY,
                    jsonMessage);
            log.info("拉黑用户消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("拉黑用户消息发送失败", e);
        }
    }

    /**
     * 发送系统通知消息
     * 
     * @param message 消息内容（包含通知类型、内容等）
     */
    public void sendSystemNotificationMessage(Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SYSTEM_NOTIFICATION_EXCHANGE,
                    "",
                    jsonMessage);
            log.info("系统通知消息发送成功: {}", jsonMessage);
        } catch (Exception e) {
            log.error("系统通知消息发送失败", e);
        }
    }
}
