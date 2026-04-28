package com.zero.usercenter.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RabbitMQ 配置类
 * 定义交换机、队列、绑定关系以及消息转换器
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机名称 ====================
    /** 好友申请交换机 */
    public static final String FRIEND_REQUEST_EXCHANGE = "friend.request.exchange";
    /** 好友操作交换机 */
    public static final String FRIEND_OPERATION_EXCHANGE = "friend.operation.exchange";
    /** 系统通知交换机 */
    public static final String SYSTEM_NOTIFICATION_EXCHANGE = "system.notification.exchange";

    // ==================== 队列名称 ====================
    /** 好友申请队列 */
    public static final String FRIEND_REQUEST_QUEUE = "friend.request.queue";
    /** 好友同意队列 */
    public static final String FRIEND_AGREE_QUEUE = "friend.agree.queue";
    /** 好友拒绝队列 */
    public static final String FRIEND_REJECT_QUEUE = "friend.reject.queue";
    /** 好友删除队列 */
    public static final String FRIEND_DELETE_QUEUE = "friend.delete.queue";
    /** 拉黑用户队列 */
    public static final String BLACKLIST_QUEUE = "blacklist.queue";
    /** 系统通知队列 */
    public static final String SYSTEM_NOTIFICATION_QUEUE = "system.notification.queue";

    // ==================== 路由键 ====================
    /** 好友申请路由键 */
    public static final String FRIEND_REQUEST_ROUTING_KEY = "friend.request";
    /** 好友同意路由键 */
    public static final String FRIEND_AGREE_ROUTING_KEY = "friend.agree";
    /** 好友拒绝路由键 */
    public static final String FRIEND_REJECT_ROUTING_KEY = "friend.reject";
    /** 好友删除路由键 */
    public static final String FRIEND_DELETE_ROUTING_KEY = "friend.delete";
    /** 拉黑用户路由键 */
    public static final String BLACKLIST_ROUTING_KEY = "blacklist";
    /** 系统通知路由键 */
    public static final String SYSTEM_NOTIFICATION_ROUTING_KEY = "system.notification";

    // ==================== 交换机定义 ====================

    /**
     * 好友请求交换机（Topic 类型）
     * 用于处理好友申请、同意、拒绝等操作
     */
    @Bean
    public TopicExchange friendRequestExchange() {
        return new TopicExchange(FRIEND_REQUEST_EXCHANGE, true, false);
    }

    /**
     * 好友操作交换机（Topic 类型）
     * 用于处理好友删除、拉黑等操作
     */
    @Bean
    public TopicExchange friendOperationExchange() {
        return new TopicExchange(FRIEND_OPERATION_EXCHANGE, true, false);
    }

    /**
     * 系统通知交换机（Fanout 类型）
     * 用于广播系统通知
     */
    @Bean
    public FanoutExchange systemNotificationExchange() {
        return new FanoutExchange(SYSTEM_NOTIFICATION_EXCHANGE, true, false);
    }

    // ==================== 队列定义 ====================

    /**
     * 好友申请队列
     * 用于处理好友申请消息
     */
    @Bean
    public Queue friendRequestQueue() {
        return new Queue(FRIEND_REQUEST_QUEUE, true, false, false);
    }

    /**
     * 好友同意队列
     * 用于处理好友同意消息
     */
    @Bean
    public Queue friendAgreeQueue() {
        return new Queue(FRIEND_AGREE_QUEUE, true, false, false);
    }

    /**
     * 好友拒绝队列
     * 用于处理好友拒绝消息
     */
    @Bean
    public Queue friendRejectQueue() {
        return new Queue(FRIEND_REJECT_QUEUE, true, false, false);
    }

    /**
     * 好友删除队列
     * 用于处理好友删除消息
     */
    @Bean
    public Queue friendDeleteQueue() {
        return new Queue(FRIEND_DELETE_QUEUE, true, false, false);
    }

    /**
     * 拉黑用户队列
     * 用于处理拉黑用户消息
     */
    @Bean
    public Queue blacklistQueue() {
        return new Queue(BLACKLIST_QUEUE, true, false, false);
    }

    /**
     * 系统通知队列
     * 用于处理系统通知消息
     */
    @Bean
    public Queue systemNotificationQueue() {
        return new Queue(SYSTEM_NOTIFICATION_QUEUE, true, false, false);
    }

    // ==================== 绑定关系定义 ====================

    /**
     * 好友申请队列绑定到好友请求交换机
     */
    @Bean
    public Binding friendRequestBinding() {
        return BindingBuilder.bind(friendRequestQueue())
                .to(friendRequestExchange())
                .with(FRIEND_REQUEST_ROUTING_KEY);
    }

    /**
     * 好友同意队列绑定到好友请求交换机
     */
    @Bean
    public Binding friendAgreeBinding() {
        return BindingBuilder.bind(friendAgreeQueue())
                .to(friendRequestExchange())
                .with(FRIEND_AGREE_ROUTING_KEY);
    }

    /**
     * 好友拒绝队列绑定到好友请求交换机
     */
    @Bean
    public Binding friendRejectBinding() {
        return BindingBuilder.bind(friendRejectQueue())
                .to(friendRequestExchange())
                .with(FRIEND_REJECT_ROUTING_KEY);
    }

    /**
     * 好友删除队列绑定到好友操作交换机
     */
    @Bean
    public Binding friendDeleteBinding() {
        return BindingBuilder.bind(friendDeleteQueue())
                .to(friendOperationExchange())
                .with(FRIEND_DELETE_ROUTING_KEY);
    }

    /**
     * 拉黑用户队列绑定到好友操作交换机
     */
    @Bean
    public Binding blacklistBinding() {
        return BindingBuilder.bind(blacklistQueue())
                .to(friendOperationExchange())
                .with(BLACKLIST_ROUTING_KEY);
    }

    /**
     * 系统通知队列绑定到系统通知交换机
     */
    @Bean
    public Binding systemNotificationBinding() {
        return BindingBuilder.bind(systemNotificationQueue())
                .to(systemNotificationExchange());
    }

    // ==================== 消息转换器 ====================

    /**
     * 消息转换器（JSON 格式）
     * Spring Boot 4.0+ 推荐使用 SimpleMessageConverter
     * 配合 ObjectMapper 实现 JSON 序列化/反序列化
     */
    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    /**
     * ObjectMapper Bean
     * 用于 JSON 序列化/反序列化
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * RabbitTemplate 配置
     * 用于发送消息
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 设置消息转换器
        rabbitTemplate.setMessageConverter(messageConverter());
        // 启用发布者确认
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功，correlationData: " + correlationData);
            } else {
                System.out.println("消息发送失败，cause: " + cause);
            }
        });
        // 启用发布者返回
        rabbitTemplate.setReturnsCallback(returned -> {
            System.out.println("消息被返回，message: " + returned.getMessage());
        });
        return rabbitTemplate;
    }
}
