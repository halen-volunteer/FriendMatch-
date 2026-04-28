package com.zero.usercenter.utils;

import com.alibaba.fastjson2.JSON;
import com.zero.usercenter.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;

/**
 * RabbitMQ 消息消费者
 * 负责消费各类业务消息
 */
@Slf4j
@Component
public class RabbitMQConsumer {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 消费好友申请消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_REQUEST_QUEUE)
    public void consumeFriendRequestMessage(String message) {
        try {
            log.info("收到好友申请消息: {}", message);
            // TODO: 处理好友申请消息
            // 1. 解析消息
            // 2. 保存到数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理好友申请消息失败", e);
        }
    }

    /**
     * 消费好友同意消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_AGREE_QUEUE)
    public void consumeFriendAgreeMessage(String message) {
        try {
            log.info("收到好友同意消息: {}", message);
            // TODO: 处理好友同意消息
            // 1. 解析消息
            // 2. 更新数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理好友同意消息失败", e);
        }
    }

    /**
     * 消费好友拒绝消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_REJECT_QUEUE)
    public void consumeFriendRejectMessage(String message) {
        try {
            log.info("收到好友拒绝消息: {}", message);
            // TODO: 处理好友拒绝消息
            // 1. 解析消息
            // 2. 更新数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理好友拒绝消息失败", e);
        }
    }

    /**
     * 消费好友删除消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_DELETE_QUEUE)
    public void consumeFriendDeleteMessage(String message) {
        try {
            log.info("收到好友删除消息: {}", message);
            // TODO: 处理好友删除消息
            // 1. 解析消息
            // 2. 更新数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理好友删除消息失败", e);
        }
    }

    /**
     * 消费拉黑用户消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.BLACKLIST_QUEUE)
    public void consumeBlacklistMessage(String message) {
        try {
            log.info("收到拉黑用户消息: {}", message);
            // TODO: 处理拉黑用户消息
            // 1. 解析消息
            // 2. 更新数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理拉黑用户消息失败", e);
        }
    }

    /**
     * 消费系统通知消息
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMQConfig.SYSTEM_NOTIFICATION_QUEUE)
    public void consumeSystemNotificationMessage(String message) {
        try {
            log.info("收到系统通知消息: {}", message);
            // TODO: 处理系统通知消息
            // 1. 解析消息
            // 2. 保存到数据库
            // 3. 发送通知
        } catch (Exception e) {
            log.error("处理系统通知消息失败", e);
        }
    }
}
