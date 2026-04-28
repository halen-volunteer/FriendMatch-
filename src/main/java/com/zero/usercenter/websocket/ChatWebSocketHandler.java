package com.zero.usercenter.websocket;

import com.alibaba.fastjson2.JSON;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.TeamMember;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.UNREAD_COUNT_KEY;

/**
 * WebSocket 消息处理器
 * 管理用户连接，支持私聊推送、群聊推送、撤回通知
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 在线用户会话映射：userId -> WebSocketSession
     * 同一用户仅保留最后一个连接
     */
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private TeamMemberMapper teamMemberMapper;

    // ==================== 连接生命周期 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        // 踢出旧连接
        WebSocketSession old = SESSIONS.put(userId, session);
        if (old != null && old.isOpen()) closeQuietly(old);
        log.info("[WS] 用户 {} 已连接，当前在线 {} 人", userId, SESSIONS.size());
        // 推送离线期间未读消息
        Thread.ofVirtual().start(() -> pushOfflineMsgs(userId, session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            SESSIONS.remove(userId, session);
            log.info("[WS] 用户 {} 已断开，当前在线 {} 人", userId, SESSIONS.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("[WS] 传输异常 sessionId={}: {}", session.getId(), ex.getMessage());
        closeQuietly(session);
    }

    // ==================== 消息推送 ====================

    /**
     * 推送私聊消息给指定用户
     *
     * @param userId  目标用户 ID
     * @param message 要推送的 JSON 消息字符串
     */
    public void sendToUser(Long userId, String message) {
        sendMsg(userId, message);
    }

    /**
     * 推送群聊消息给团队所有在线成员（排除发送者）
     *
     * @param teamId   团队 ID
     * @param message  要推送的 JSON 消息字符串
     * @param senderId 发送者用户 ID（推送时排除该用户）
     */
    public void sendToTeam(Long teamId, String message, Long senderId) {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .eq(TeamMember::getIsQuit, 0)
          .ne(TeamMember::getUserId, senderId);
        List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId).collect(Collectors.toList());
        for (Long memberId : memberIds) {
            sendMsg(memberId, message);
        }
    }

    public void sendToTeamAll(Long teamId, String message) {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
          .eq(TeamMember::getIsQuit, 0);
        List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId).collect(Collectors.toList());
        for (Long memberId : memberIds) {
            sendMsg(memberId, message);
        }
    }

    /**
     * 推送撤回通知给消息接收方
     * 私聊：推送给对方；群聊：推送给所有成员（排除发送者）
     *
     * @param msg 被撤回的聊天消息实体，包含会话 ID、接收方 ID、消息类型等
     */
    public void sendRevoke(ChatMessage msg) {
        Map<String, Object> push = new HashMap<>();
        push.put("type", "message_revoke");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", msg.getId());
        data.put("conversationId", msg.getConversationId());
        push.put("data", data);
        String json = JSON.toJSONString(push);

        if (msg.getRecvType() == 1) {
            // 私聊：推送给接收方
            sendMsg(msg.getRecvId(), json);
        } else {
            // 群聊：推送给所有成员（排除发送者）
            sendToTeam(msg.getRecvId(), json, msg.getSenderId());
        }
    }

    // ==================== 辅助方法 ====================

    private void sendMsg(Long userId, String message) {
        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 用户在线 → 实时推送
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.warn("[WS] 推送消息给用户 {} 失败: {}", userId, e.getMessage());
                closeQuietly(session);
                SESSIONS.remove(userId, session);
            }
            // 用户离线 → session 为 null，直接跳过，不报错
            // 消息已存数据库，不会丢失
        }
    }

    private Long getUserId(WebSocketSession session) {
        try {
            Object attr = session.getAttributes().get("userId");
            if (attr != null) return Long.valueOf(attr.toString());
        } catch (Exception e) {
            log.warn("[WS] 解析 userId 失败: {}", e.getMessage());
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (IOException e) {
            log.warn("[WS] 关闭会话失败: {}", e.getMessage());
        }
    }

    /** 获取当前在线用户数 */
    public int getOnlineCount() {
        return SESSIONS.size();
    }

    /**
     * 判断用户是否在线
     *
     * @param userId 要查询的用户 ID
     * @return true-在线，false-离线
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    // ==================== 离线消息补推 ====================

    /**
     * 用户上线后推送离线期间的全部未读消息
     * 按会话分组，每个有未读的会话将所有未读消息按时间升序推送给客户端
     *
     * @param userId  刚上线的用户 ID
     * @param session 该用户的 WebSocket 会话
     */
    private void pushOfflineMsgs(Long userId, WebSocketSession session) {
        try {
            // 1. 通过 Redis SCAN 扫出该用户所有有未读数的会话，不依赖消息表，不受条数限制
            Set<String> allConvIds = new LinkedHashSet<>();
            String keyPrefix = UNREAD_COUNT_KEY + userId + ":";
            try (Cursor<String> cursor =
                         stringRedisTemplate.scan(
                                 ScanOptions.scanOptions()
                                         .match(keyPrefix + "*")
                                         .count(100)
                                         .build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    String convId = key.substring(keyPrefix.length());
                    allConvIds.add(convId);
                }
            }

            // 2. 遍历每个会话，检查 Redis 未读数
            for (String convId : allConvIds) {
                String unreadStr = stringRedisTemplate.opsForValue().get(UNREAD_COUNT_KEY + userId + ":" + convId);
                long unread = unreadStr != null ? Long.parseLong(unreadStr) : 0;
                if (unread <= 0) continue;

                // 3. 从数据库查该会话的全部未读消息，按时间升序（旧→新），不限条数
                LambdaQueryWrapper<ChatMessage> convQw = new LambdaQueryWrapper<>();
                convQw.eq(ChatMessage::getConversationId, convId)
                      .eq(ChatMessage::getIsDelete, 0)
                      .ne(ChatMessage::getSenderId, userId)
                      .orderByAsc(ChatMessage::getCreateTime)
                      .last("LIMIT " + unread);
                List<ChatMessage> offlineMsgs = chatMessageMapper.selectList(convQw);

                // 4. 逐条推送，顺序为时间升序（符合聊天记录展示顺序）
                for (ChatMessage msg : offlineMsgs) {
                    if (!session.isOpen()) return;
                    Map<String, Object> data = new HashMap<>();
                    data.put("msgId", msg.getId());
                    data.put("senderId", msg.getSenderId());
                    data.put("msgType", msg.getMsgType());
                    data.put("content", msg.getMsgContent());
                    data.put("createTime", msg.getCreateTime());
                    data.put("offline", true); // 标记为离线消息，供前端区分
                    Map<String, Object> push = new HashMap<>();
                    if (msg.getRecvType() == 1) {
                        push.put("type", "private_message");
                    } else {
                        data.put("teamId", msg.getRecvId());
                        push.put("type", "team_message");
                    }
                    push.put("data", data);
                    session.sendMessage(new TextMessage(JSON.toJSONString(push)));
                }
            }
            log.info("[WS] 用户 {} 离线消息推送完成", userId);
        } catch (Exception e) {
            log.warn("[WS] 推送离线消息失败 userId={}: {}", userId, e.getMessage());
        }
    }
}