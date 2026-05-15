package com.zero.usercenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 基础配置。
 * 统一声明交换机、队列、绑定关系，以及生产端发送确认能力。
 */
@Configuration
public class RabbitMQConfig {

    /** 好友申请事件交换机。 */
    public static final String FRIEND_REQUEST_EXCHANGE = "friend.request.exchange";
    /** 好友关系操作交换机，例如删除好友、拉黑。 */
    public static final String FRIEND_OPERATION_EXCHANGE = "friend.operation.exchange";
    /** 系统通知交换机。 */
    public static final String SYSTEM_NOTIFICATION_EXCHANGE = "system.notification.exchange";
    /** 邮件发送交换机。 */
    public static final String EMAIL_EXCHANGE = "email.exchange";
    /** 登录日志交换机。 */
    public static final String LOGIN_LOG_EXCHANGE = "login.log.exchange";
    public static final String CHAT_MESSAGE_EXCHANGE = "chat.message.exchange";

    /** 好友申请队列。 */
    public static final String FRIEND_REQUEST_QUEUE = "friend.request.queue";
    /** 好友申请通过队列。 */
    public static final String FRIEND_AGREE_QUEUE = "friend.agree.queue";
    /** 好友申请拒绝队列。 */
    public static final String FRIEND_REJECT_QUEUE = "friend.reject.queue";
    /** 删除好友队列。 */
    public static final String FRIEND_DELETE_QUEUE = "friend.delete.queue";
    /** 黑名单操作队列。 */
    public static final String BLACKLIST_QUEUE = "blacklist.queue";
    /** 系统通知队列。 */
    public static final String SYSTEM_NOTIFICATION_QUEUE = "system.notification.queue";
    /** 邮件发送队列。 */
    public static final String EMAIL_QUEUE = "email.queue";
    /** 登录日志队列。 */
    public static final String LOGIN_LOG_QUEUE = "login.log.queue";
    public static final String CHAT_MESSAGE_QUEUE = "chat.message.queue";

    /** 好友申请路由键。 */
    public static final String FRIEND_REQUEST_ROUTING_KEY = "friend.request";
    /** 好友申请通过路由键。 */
    public static final String FRIEND_AGREE_ROUTING_KEY = "friend.agree";
    /** 好友申请拒绝路由键。 */
    public static final String FRIEND_REJECT_ROUTING_KEY = "friend.reject";
    /** 删除好友路由键。 */
    public static final String FRIEND_DELETE_ROUTING_KEY = "friend.delete";
    /** 黑名单路由键。 */
    public static final String BLACKLIST_ROUTING_KEY = "blacklist";
    /** 系统通知路由键。当前 fanout 模式下实际不会使用该值。 */
    public static final String SYSTEM_NOTIFICATION_ROUTING_KEY = "system.notification";
    /** 邮件路由键。 */
    public static final String EMAIL_ROUTING_KEY = "email.send";
    /** 登录日志路由键。 */
    public static final String LOGIN_LOG_ROUTING_KEY = "login.log";
    public static final String CHAT_MESSAGE_ROUTING_KEY = "chat.message";

    /**
     * 好友申请相关交换机。
     * 保留多种 routing key，便于后续把好友链路逐步事件化。
     */
    @Bean
    public TopicExchange friendRequestExchange() {
        return new TopicExchange(FRIEND_REQUEST_EXCHANGE, true, false);
    }

    /**
     * 好友删除、拉黑等关系操作交换机。
     */
    @Bean
    public TopicExchange friendOperationExchange() {
        return new TopicExchange(FRIEND_OPERATION_EXCHANGE, true, false);
    }

    /**
     * 系统通知交换机。
     * 这里只需要广播到统一通知队列，因此采用 fanout。
     */
    @Bean
    public FanoutExchange systemNotificationExchange() {
        return new FanoutExchange(SYSTEM_NOTIFICATION_EXCHANGE, true, false);
    }

    /**
     * 邮件交换机。
     */
    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(EMAIL_EXCHANGE, true, false);
    }

    /**
     * 登录日志交换机。
     */
    @Bean
    public DirectExchange loginLogExchange() {
        return new DirectExchange(LOGIN_LOG_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange chatMessageExchange() {
        return new DirectExchange(CHAT_MESSAGE_EXCHANGE, true, false);
    }

    /**
     * 好友申请队列。
     * durable=true 代表 RabbitMQ 重启后队列仍会保留。
     */
    @Bean
    public Queue friendRequestQueue() {
        return new Queue(FRIEND_REQUEST_QUEUE, true, false, false);
    }

    /**
     * 好友申请通过队列。
     */
    @Bean
    public Queue friendAgreeQueue() {
        return new Queue(FRIEND_AGREE_QUEUE, true, false, false);
    }

    /**
     * 好友申请拒绝队列。
     */
    @Bean
    public Queue friendRejectQueue() {
        return new Queue(FRIEND_REJECT_QUEUE, true, false, false);
    }

    /**
     * 删除好友队列。
     */
    @Bean
    public Queue friendDeleteQueue() {
        return new Queue(FRIEND_DELETE_QUEUE, true, false, false);
    }

    /**
     * 黑名单操作队列。
     */
    @Bean
    public Queue blacklistQueue() {
        return new Queue(BLACKLIST_QUEUE, true, false, false);
    }

    /**
     * 系统通知队列。
     */
    @Bean
    public Queue systemNotificationQueue() {
        return new Queue(SYSTEM_NOTIFICATION_QUEUE, true, false, false);
    }

    /**
     * 邮件发送队列。
     */
    @Bean
    public Queue emailQueue() {
        return new Queue(EMAIL_QUEUE, true, false, false);
    }

    /**
     * 登录日志队列。
     */
    @Bean
    public Queue loginLogQueue() {
        return new Queue(LOGIN_LOG_QUEUE, true, false, false);
    }

    @Bean
    public Queue chatMessageQueue() {
        return new Queue(CHAT_MESSAGE_QUEUE, true, false, false);
    }

    /**
     * 绑定好友申请队列到好友申请交换机。
     */
    @Bean
    public Binding friendRequestBinding() {
        return BindingBuilder.bind(friendRequestQueue())
                .to(friendRequestExchange())
                .with(FRIEND_REQUEST_ROUTING_KEY);
    }

    /**
     * 绑定好友通过队列。
     */
    @Bean
    public Binding friendAgreeBinding() {
        return BindingBuilder.bind(friendAgreeQueue())
                .to(friendRequestExchange())
                .with(FRIEND_AGREE_ROUTING_KEY);
    }

    /**
     * 绑定好友拒绝队列。
     */
    @Bean
    public Binding friendRejectBinding() {
        return BindingBuilder.bind(friendRejectQueue())
                .to(friendRequestExchange())
                .with(FRIEND_REJECT_ROUTING_KEY);
    }

    /**
     * 绑定删除好友队列。
     */
    @Bean
    public Binding friendDeleteBinding() {
        return BindingBuilder.bind(friendDeleteQueue())
                .to(friendOperationExchange())
                .with(FRIEND_DELETE_ROUTING_KEY);
    }

    /**
     * 绑定黑名单队列。
     */
    @Bean
    public Binding blacklistBinding() {
        return BindingBuilder.bind(blacklistQueue())
                .to(friendOperationExchange())
                .with(BLACKLIST_ROUTING_KEY);
    }

    /**
     * 绑定系统通知队列。
     * fanout 模式下不依赖路由键，发送到交换机的消息会广播到所有绑定队列。
     */
    @Bean
    public Binding systemNotificationBinding() {
        return BindingBuilder.bind(systemNotificationQueue())
                .to(systemNotificationExchange());
    }

    /**
     * 绑定邮件队列。
     */
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue())
                .to(emailExchange())
                .with(EMAIL_ROUTING_KEY);
    }

    /**
     * 绑定登录日志队列。
     */
    @Bean
    public Binding loginLogBinding() {
        return BindingBuilder.bind(loginLogQueue())
                .to(loginLogExchange())
                .with(LOGIN_LOG_ROUTING_KEY);
    }

    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue())
                .to(chatMessageExchange())
                .with(CHAT_MESSAGE_ROUTING_KEY);
    }

    /**
     * RabbitMQ 消息转换器。
     * 这里创建消息 ID，便于排查重复投递、路由失败等问题。
     */
    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    /**
     * JSON 序列化工具。
     * 供生产者和消费者统一序列化/反序列化消息体。
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    /**
     * RabbitTemplate 配置。
     * 统一打开 mandatory、发布确认和 returned 回调，帮助感知消息是否真正进入队列。
     *
     * @param connectionFactory RabbitMQ 连接工厂
     * @return 配置完成的 RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // 1. 创建 RabbitTemplate，并注入底层连接工厂。
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 2. 统一使用当前项目的消息转换器。
        rabbitTemplate.setMessageConverter(messageConverter());
        // 3. mandatory=true 时，消息到达交换机但路由不到队列会触发 returned callback。
        rabbitTemplate.setMandatory(true);
        // 4. 发布确认用于感知消息是否成功到达交换机。
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("RabbitMQ message published successfully");
            } else {
                System.out.println("RabbitMQ message publish failed: " + cause);
            }
        });
        // 5. returned callback 用于感知消息到达交换机后是否成功路由到队列。
        rabbitTemplate.setReturnsCallback(returned ->
                System.out.println("RabbitMQ message returned: " + returned.getMessage()));
        return rabbitTemplate;
    }
}
