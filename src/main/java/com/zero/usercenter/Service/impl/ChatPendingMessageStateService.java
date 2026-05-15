package com.zero.usercenter.Service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.PendingChatOperation;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.CHAT_PENDING_MESSAGE_KEY;
import static com.zero.usercenter.utils.Number.CHAT_PENDING_MESSAGE_TTL_MINUTES;
import static com.zero.usercenter.utils.Number.CHAT_PENDING_OPERATION_KEY;
import static com.zero.usercenter.utils.Number.CHAT_PENDING_OPERATION_TTL_MINUTES;

/**
 * 待消费聊天消息状态服务。
 * 聊天发送改成 MQ 异步后，消息会经历“已响应前端，但尚未真正落库”的短暂窗口。
 * 这个服务负责把这段窗口中的消息元数据和挂起操作统一收口到 Redis。
 */
@Service
public class ChatPendingMessageStateService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 缓存一条待消费聊天消息。
     *
     * @param message 已经生成业务 msgId、但尚未被 MQ 消费的聊天消息
     */
    public void cachePendingMessage(ChatSendMessage message) {
        if (message == null || message.getMsgId() == null) {
            return;
        }
        writeJson(
                buildPendingMessageKey(message.getMsgId()),
                message,
                CHAT_PENDING_MESSAGE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * 读取待消费聊天消息元数据。
     *
     * @param msgId 业务消息 ID
     * @return 待消费消息；不存在或解析失败时返回 null
     */
    public ChatSendMessage getPendingMessage(Long msgId) {
        return readJson(buildPendingMessageKey(msgId), ChatSendMessage.class);
    }

    /**
     * 为待消费消息记录一次“编辑”操作。
     *
     * @param msgId         业务消息 ID
     * @param editedContent 编辑后的最新内容
     * @param operateTime   编辑发生时间
     */
    public void savePendingEdit(Long msgId, String editedContent, LocalDateTime operateTime) {
        savePendingOperation(msgId, new PendingChatOperation(
                PendingChatOperation.TYPE_EDIT,
                editedContent,
                operateTime
        ));
    }

    /**
     * 为待消费消息记录一次“撤回”操作。
     *
     * @param msgId       业务消息 ID
     * @param operateTime 撤回发生时间
     */
    public void savePendingRevoke(Long msgId, LocalDateTime operateTime) {
        savePendingOperation(msgId, new PendingChatOperation(
                PendingChatOperation.TYPE_REVOKE,
                null,
                operateTime
        ));
    }

    /**
     * 读取待消费消息上的挂起操作。
     *
     * @param msgId 业务消息 ID
     * @return 挂起操作；不存在或解析失败时返回 null
     */
    public PendingChatOperation getPendingOperation(Long msgId) {
        return readJson(buildPendingOperationKey(msgId), PendingChatOperation.class);
    }

    /**
     * 清理待消费消息及其挂起操作。
     *
     * @param msgId 业务消息 ID
     */
    public void clearPendingState(Long msgId) {
        if (msgId == null) {
            return;
        }
        stringRedisTemplate.delete(buildPendingMessageKey(msgId));
        stringRedisTemplate.delete(buildPendingOperationKey(msgId));
    }

    /**
     * 保存挂起操作，并尽量与待消费消息本身保持相同生命周期。
     *
     * @param msgId     业务消息 ID
     * @param operation 最新挂起操作
     */
    private void savePendingOperation(Long msgId, PendingChatOperation operation) {
        if (msgId == null || operation == null) {
            return;
        }
        long ttlSeconds = resolveOperationTtlSeconds(msgId);
        writeJson(buildPendingOperationKey(msgId), operation, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 计算挂起操作的有效期。
     * 优先复用待消费消息本身剩余 TTL；如果消息缓存已丢失，则回退到固定 TTL。
     *
     * @param msgId 业务消息 ID
     * @return 操作缓存 TTL（秒）
     */
    private long resolveOperationTtlSeconds(Long msgId) {
        Long remainSeconds = stringRedisTemplate.getExpire(buildPendingMessageKey(msgId), TimeUnit.SECONDS);
        if (remainSeconds != null && remainSeconds > 0) {
            return remainSeconds;
        }
        return TimeUnit.MINUTES.toSeconds(CHAT_PENDING_OPERATION_TTL_MINUTES);
    }

    /**
     * 统一写入 JSON。
     *
     * @param key      Redis Key
     * @param value    待写入对象
     * @param ttl      有效期数值
     * @param timeUnit 有效期单位
     */
    private void writeJson(String key, Object value, long ttl, TimeUnit timeUnit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl, timeUnit);
        } catch (Exception e) {
            throw new RuntimeException("缓存待消费聊天状态失败", e);
        }
    }

    /**
     * 统一读取 JSON。
     *
     * @param key   Redis Key
     * @param clazz 目标类型
     * @return 反序列化结果；不存在或解析失败时返回 null
     * @param <T> 目标对象类型
     */
    private <T> T readJson(String key, Class<T> clazz) {
        if (key == null || clazz == null) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 拼出待消费消息缓存 Key。
     *
     * @param msgId 业务消息 ID
     * @return Redis Key
     */
    private String buildPendingMessageKey(Long msgId) {
        return CHAT_PENDING_MESSAGE_KEY + msgId;
    }

    /**
     * 拼出待消费消息挂起操作缓存 Key。
     *
     * @param msgId 业务消息 ID
     * @return Redis Key
     */
    private String buildPendingOperationKey(Long msgId) {
        return CHAT_PENDING_OPERATION_KEY + msgId;
    }
}
