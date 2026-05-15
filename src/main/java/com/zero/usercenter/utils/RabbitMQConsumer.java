package com.zero.usercenter.utils;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.SystemNoticeMapper;
import com.zero.usercenter.Mapper.UserLoginLogMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.SystemNotice;
import com.zero.usercenter.Model.UserLoginLog;
import com.zero.usercenter.Service.impl.ChatPendingMessageStateService;
import com.zero.usercenter.Service.impl.ChatSupportService;
import com.zero.usercenter.config.RabbitMQConfig;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.EmailMessage;
import com.zero.usercenter.mq.message.LoginLogMessage;
import com.zero.usercenter.mq.message.PendingChatOperation;
import com.zero.usercenter.mq.message.SystemNoticeMessage;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 消费者。
 * 负责承接已经事件化的异步任务，并在消费者侧完成真正的业务落地。
 * 当前覆盖：
 * 1. 邮件发送；
 * 2. 登录日志落库；
 * 3. 系统通知落库并尽量实时推送；
 * 4. 聊天消息异步落库、未读数更新与 WebSocket 推送。
 */
@Slf4j
@Component
public class RabbitMQConsumer {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private EmailApi emailApi;

    @Resource
    private UserLoginLogMapper userLoginLogMapper;

    @Resource
    private SystemNoticeMapper systemNoticeMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private ChatPendingMessageStateService chatPendingMessageStateService;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 好友申请占位消费者。
     * 当前好友主链路尚未完全事件化，因此这里只做 ACK，避免队列积压。
     *
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_REQUEST_QUEUE)
    public void consumeFriendRequestMessage(String message, Channel channel,
                                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handlePlaceholderMessage("friend request", message, channel, deliveryTag);
    }

    /**
     * 好友通过占位消费者。
     *
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_AGREE_QUEUE)
    public void consumeFriendAgreeMessage(String message, Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handlePlaceholderMessage("friend agree", message, channel, deliveryTag);
    }

    /**
     * 好友拒绝占位消费者。
     *
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_REJECT_QUEUE)
    public void consumeFriendRejectMessage(String message, Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handlePlaceholderMessage("friend reject", message, channel, deliveryTag);
    }

    /**
     * 删除好友占位消费者。
     *
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_DELETE_QUEUE)
    public void consumeFriendDeleteMessage(String message, Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handlePlaceholderMessage("friend delete", message, channel, deliveryTag);
    }

    /**
     * 黑名单占位消费者。
     *
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.BLACKLIST_QUEUE)
    public void consumeBlacklistMessage(String message, Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handlePlaceholderMessage("blacklist", message, channel, deliveryTag);
    }

    /**
     * 邮件消费者。
     *
     * @param message     JSON 消息体
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void consumeEmailMessage(String message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 1. 先反序列化邮件载荷，还原真实发送参数。
            EmailMessage emailMessage = objectMapper.readValue(message, EmailMessage.class);

            // 2. 再根据 html 标记选择具体发送方式，保持业务侧发送入口简单统一。
            if (Boolean.TRUE.equals(emailMessage.getHtml())) {
                emailApi.sendHtmlEmail(emailMessage.getSubject(), emailMessage.getContent(), emailMessage.getTo());
            } else {
                emailApi.sendSimpleEmail(emailMessage.getSubject(), emailMessage.getContent(), emailMessage.getTo());
            }

            // 3. 发送成功后手动 ACK，确认当前消息已经被完整处理。
            channel.basicAck(deliveryTag, false);
            log.info("Email message consumed successfully: to={}", emailMessage.getTo());
        } catch (Exception e) {
            log.error("Email message consume failed", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 登录日志消费者。
     *
     * @param message     JSON 消息体
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.LOGIN_LOG_QUEUE)
    public void consumeLoginLogMessage(String message, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 1. 先反序列化 MQ 消息，拿到登录日志所需字段。
            LoginLogMessage logMessage = objectMapper.readValue(message, LoginLogMessage.class);

            // 2. 再组装数据库实体，保持 MQ 消息体和数据表实体解耦。
            UserLoginLog loginLog = new UserLoginLog();
            loginLog.setUserId(logMessage.getUserId());
            loginLog.setLoginIp(logMessage.getLoginIp());
            loginLog.setLoginType(logMessage.getLoginType());
            loginLog.setLoginResult(logMessage.getLoginResult());
            userLoginLogMapper.insert(loginLog);

            // 3. 落库成功再 ACK，避免同一条日志被误认为已消费。
            channel.basicAck(deliveryTag, false);
            log.info("Login log message consumed successfully: userId={}", logMessage.getUserId());
        } catch (Exception e) {
            log.error("Login log message consume failed", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 系统通知消费者。
     *
     * @param message     JSON 消息体
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.SYSTEM_NOTIFICATION_QUEUE)
    public void consumeSystemNotificationMessage(String message, Channel channel,
                                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // 1. 先把 MQ 中的通知载荷还原成业务对象。
            SystemNoticeMessage noticeMessage = objectMapper.readValue(message, SystemNoticeMessage.class);

            // 2. 再落库到系统通知表，保证离线用户之后也能从通知中心看到记录。
            SystemNotice notice = new SystemNotice();
            notice.setUserId(noticeMessage.getUserId());
            notice.setNoticeType(noticeMessage.getNoticeType());
            notice.setNoticeContent(noticeMessage.getNoticeContent());
            notice.setRelatedId(noticeMessage.getRelatedId());
            notice.setIsRead(0);
            notice.setIsDelete(0);
            systemNoticeMapper.insert(notice);

            // 3. 如果用户当前在线，再额外走一次 WebSocket 即时推送，兼顾实时性和可追溯性。
            pushSystemNotice(notice);
            channel.basicAck(deliveryTag, false);
            log.info("System notice message consumed successfully: userId={}, type={}",
                    noticeMessage.getUserId(), noticeMessage.getNoticeType());
        } catch (Exception e) {
            log.error("System notice message consume failed", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 聊天消息消费者。
     * 这是本次高并发削峰方案的核心落地点。
     *
     * @param message     JSON 聊天消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_MESSAGE_QUEUE)
    public void consumeChatMessage(String message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long msgId = null;
        try {
            // 1. 先反序列化 MQ 中的聊天消息，恢复发送阶段生成的 msgId、createTime 和消息体内容。
            ChatSendMessage chatSendMessage = objectMapper.readValue(message, ChatSendMessage.class);
            msgId = chatSendMessage.getMsgId();
            if (msgId == null) {
                throw new IllegalArgumentException("chat message msgId is null");
            }

            // 2. 重复消费时优先做幂等兜底：如果数据库中已经存在同 msgId 消息，就只清理挂起态并 ACK。
            ChatMessage existing = chatMessageMapper.selectById(msgId);
            if (existing != null && !Integer.valueOf(1).equals(existing.getIsDelete())) {
                chatPendingMessageStateService.clearPendingState(msgId);
                channel.basicAck(deliveryTag, false);
                log.info("Chat message duplicated, skip consume: msgId={}", msgId);
                return;
            }

            // 3. 读取挂起操作状态：未消费前的编辑/撤回都只会落在 Redis，不会直接改 MQ 队列。
            PendingChatOperation pendingOperation = chatPendingMessageStateService.getPendingOperation(msgId);

            // 4. 如果已经被挂起撤回，就直接丢弃，不落库、不推送、不更新未读，彻底吃掉这条队列消息。
            if (pendingOperation != null
                    && PendingChatOperation.TYPE_REVOKE.equals(pendingOperation.getOperationType())) {
                chatPendingMessageStateService.clearPendingState(msgId);
                channel.basicAck(deliveryTag, false);
                log.info("Chat message revoked before consume, skip persist: msgId={}", msgId);
                return;
            }

            // 5. 构建最终要落库的消息实体；如果有挂起编辑，则这里会自动替换成最新内容并打上编辑痕迹。
            ChatMessage persistMessage = chatSupportService.buildPersistMessage(chatSendMessage, pendingOperation);
            chatMessageMapper.insert(persistMessage);

            // 6. 落库后再回读一次数据库最新状态，尽量吃到“刚入库就被编辑/撤回”的极短并发窗口。
            ChatMessage latestMessage = chatMessageMapper.selectById(msgId);
            if (latestMessage == null || Integer.valueOf(1).equals(latestMessage.getIsDelete())) {
                chatPendingMessageStateService.clearPendingState(msgId);
                channel.basicAck(deliveryTag, false);
                log.warn("Chat message disappeared after insert, skip post actions: msgId={}", msgId);
                return;
            }

            // 7. 如果消息在落库后立刻被撤回，就不再补做未读和实时推送，避免把本该消失的消息再次推给前端。
            if (!Integer.valueOf(1).equals(latestMessage.getIsRevoke())) {
                chatSupportService.afterMessagePersisted(latestMessage, chatSendMessage.getAtUserIds());
            }

            // 8. 最后清理挂起态并 ACK，说明这条消息已经被消费者完整处理完成。
            chatPendingMessageStateService.clearPendingState(msgId);
            channel.basicAck(deliveryTag, false);
            log.info("Chat message consumed successfully: msgId={}, conversationId={}",
                    msgId, chatSendMessage.getConversationId());
        } catch (Exception e) {
            log.error("Chat message consume failed, msgId={}", msgId, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 占位队列统一处理。
     *
     * @param messageType 消息类型
     * @param message     原始消息
     * @param channel     RabbitMQ 信道
     * @param deliveryTag 投递标识
     * @throws IOException ACK/NACK 异常
     */
    private void handlePlaceholderMessage(String messageType, String message, Channel channel, long deliveryTag)
            throws IOException {
        log.info("Received {} message: {}", messageType, message);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 在线推送系统通知。
     *
     * @param notice 已落库通知
     */
    private void pushSystemNotice(SystemNotice notice) {
        // 1. 先组装前端约定的统一事件结构，保持系统通知和聊天事件的消费方式一致。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "system_notice");

        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("relatedId", notice.getRelatedId());
        push.put("data", data);

        // 2. 只有用户当前在线时这条实时通知才会立即可见，离线用户则继续依赖通知中心列表查看。
        chatWebSocketHandler.sendToUser(notice.getUserId(), JSON.toJSONString(push));
    }
}
