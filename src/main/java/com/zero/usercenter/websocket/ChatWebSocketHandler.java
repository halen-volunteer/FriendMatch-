package com.zero.usercenter.websocket;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.TeamMember;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.LARGE_TEAM_PUSH_BATCH_DELAY_MILLIS;
import static com.zero.usercenter.utils.Number.LARGE_TEAM_PUSH_BATCH_SIZE;

/**
 * 聊天 WebSocket 处理器。
 * 负责在线连接管理、私聊/群聊实时推送、撤回通知和群公告更新通知。
 *
 * 注意：
 * 当前 WebSocket 只承担“实时事件通知”职责，不再在建连瞬间自动补推全部离线正文。
 * 离线场景统一由“未读数 + 最近会话摘要 + 进入会话后按游标分页拉历史”来承接，
 * 避免单个用户消息堆积过多或大量用户同时上线时，把发送链路和数据库链路一起打重。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 在线用户连接映射。
     * 同一用户只保留最后一次成功建立的连接。
     */
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 大群平滑推送调度器。
     * 大群消息会拆成多个小批次延迟下发，避免某一瞬间把大量 sendMessage 全压在同一个业务线程上。
     */
    private final ScheduledExecutorService smoothPushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "chat-large-team-push");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private TeamMemberMapper teamMemberMapper;

    /**
     * 关闭应用时主动停止平滑推送调度器，避免非守护线程阻塞应用退出。
     */
    @PreDestroy
    public void shutdownExecutor() {
        smoothPushExecutor.shutdown();
    }

    /**
     * 连接建立后登记当前用户。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1. 先从握手阶段写入的 attributes 中解析 userId，拿不到就直接关闭连接。
        Long userId = getUserId(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }

        // 2. 新连接写入在线映射；如果同一用户已有旧连接，则关闭旧连接，只保留最新会话。
        WebSocketSession old = SESSIONS.put(userId, session);
        if (old != null && old.isOpen()) {
            closeQuietly(old);
        }

        // 3. 建连成功后只登记在线状态，不在这里自动回放离线消息正文。
        log.info("[WS] 用户 {} 已连接，当前在线 {} 人", userId, SESSIONS.size());
    }

    /**
     * 连接关闭时清理在线映射。
     *
     * @param session 当前会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 1. 关闭事件到来时，只移除与当前 session 对应的映射，避免误删用户的新连接。
        Long userId = getUserId(session);
        if (userId != null) {
            SESSIONS.remove(userId, session);
            log.info("[WS] 用户 {} 已断开，当前在线 {} 人", userId, SESSIONS.size());
        }
    }

    /**
     * 传输异常时主动关闭连接，避免损坏会话长期留在在线表里。
     *
     * @param session 当前会话
     * @param ex      传输异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        // 1. 先记录异常，方便排查弱网或客户端异常断开场景。
        log.warn("[WS] 传输异常 sessionId={}: {}", session.getId(), ex.getMessage());

        // 2. 主动关闭异常连接，让后续重连重新建立干净会话。
        closeQuietly(session);
    }

    /**
     * 给单个用户推送消息。
     *
     * @param userId 目标用户 ID
     * @param message 推送内容
     */
    public void sendToUser(Long userId, String message) {
        // 1. 私聊和系统通知都复用底层单用户发送逻辑。
        sendMsg(userId, message);
    }

    /**
     * 给团队内其他有效成员推送消息，默认排除发送者自己。
     *
     * @param teamId 团队 ID
     * @param message 推送内容
     * @param senderId 发送者用户 ID
     */
    public void sendToTeam(Long teamId, String message, Long senderId) {
        // 1. 先找出团队内当前仍有效的成员，并排除发送者本人。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0)
                .ne(TeamMember::getUserId, senderId);
        List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toList());

        // 2. 统一复用“按成员列表发送”的能力，避免后续调用方只能重复写循环。
        sendToUsers(memberIds, message);
    }

    /**
     * 给团队内全部有效成员推送消息。
     * 主要用于系统广播类场景，需要包含发送者本人。
     *
     * @param teamId 团队 ID
     * @param message 推送内容
     */
    public void sendToTeamAll(Long teamId, String message) {
        // 1. 系统广播需要覆盖团队内全部有效成员，因此这里不排除任何人。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0);
        List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toList());

        // 2. 系统广播同样复用统一的成员列表发送逻辑。
        sendToUsers(memberIds, message);
    }

    /**
     * 按“已知成员列表”直接发送 WebSocket 消息。
     * 适合调用方已经拿到成员列表的场景，避免重复查团队成员表。
     *
     * @param userIds  目标用户 ID 列表
     * @param message  推送内容
     */
    public void sendToUsers(List<Long> userIds, String message) {
        // 1. 先清洗空值和重复用户，避免同一个用户在一轮推送中被重复发送。
        List<Long> normalizedUserIds = normalizeUserIds(userIds);
        if (normalizedUserIds.isEmpty()) {
            return;
        }

        // 2. 逐个成员发送；是否在线和异常连接清理由 sendMsg 统一处理。
        for (Long userId : normalizedUserIds) {
            sendMsg(userId, message);
        }
    }

    /**
     * 大群平滑推送。
     * 把一次大广播拆成多个小批次异步下发，降低单次广播对 MQ 消费线程和 WebSocket I/O 的瞬时冲击。
     *
     * @param userIds  目标用户 ID 列表
     * @param message  推送内容
     */
    public void sendToUsersSmoothly(List<Long> userIds, String message) {
        // 1. 先清洗成员列表，保证批次计算基于干净、去重后的在线目标集合。
        List<Long> normalizedUserIds = normalizeUserIds(userIds);
        if (normalizedUserIds.isEmpty()) {
            return;
        }

        // 2. 每批只推固定数量用户，并在批次之间插入一个很短的时间间隔，避免瞬时广播风暴。
        for (int fromIndex = 0; fromIndex < normalizedUserIds.size(); fromIndex += LARGE_TEAM_PUSH_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + LARGE_TEAM_PUSH_BATCH_SIZE, normalizedUserIds.size());
            List<Long> batchUserIds = new ArrayList<>(normalizedUserIds.subList(fromIndex, toIndex));
            long delay = (long) (fromIndex / LARGE_TEAM_PUSH_BATCH_SIZE) * LARGE_TEAM_PUSH_BATCH_DELAY_MILLIS;
            smoothPushExecutor.schedule(() -> sendToUsers(batchUserIds, message), delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 推送消息撤回通知。
     * 私聊仅通知对端，群聊通知除发送者外的其他成员。
     *
     * @param msg 被撤回的消息实体
     */
    public void sendRevoke(ChatMessage msg) {
        // 1. 先组装统一撤回载荷，前端据此删除或标记对应消息。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "message_revoke");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", stringifyId(msg.getId()));
        data.put("conversationId", msg.getConversationId());
        push.put("data", data);
        String json = JSON.toJSONString(push);

        // 2. 私聊只通知对端；群聊则通知除发送者外的其他成员。
        if (msg.getRecvType() == 1) {
            sendMsg(msg.getRecvId(), json);
        } else {
            sendToTeam(msg.getRecvId(), json, msg.getSenderId());
        }
    }

    /**
     * 向指定用户发送一条 WebSocket 消息。
     *
     * @param userId 目标用户 ID
     * @param message 推送消息体
     */
    private void sendMsg(Long userId, String message) {
        // 1. 先从在线映射中取出该用户当前连接，不在线则静默返回。
        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 2. 连接正常时直接发送文本消息。
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                // 3. 发送失败通常说明连接已损坏，这里关闭并清理映射，等待客户端重连。
                log.warn("[WS] 推送消息给用户 {} 失败: {}", userId, e.getMessage());
                closeQuietly(session);
                SESSIONS.remove(userId, session);
            }
        }
    }

    /**
     * 规范化待推送用户列表。
     *
     * @param userIds 原始用户 ID 列表
     * @return 去空、去重后的用户 ID 列表
     */
    private List<Long> normalizeUserIds(List<Long> userIds) {
        // 1. 空列表直接返回，避免后续出现无意义的批次调度。
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        // 2. 过滤 null 并保持原有顺序去重，保证广播顺序稳定且不会重复推送同一个用户。
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 从握手阶段写入的 attributes 中解析 userId。
     *
     * @param session WebSocket 会话
     * @return 当前连接对应的用户 ID；解析失败时返回 null
     */
    private Long getUserId(WebSocketSession session) {
        try {
            // 1. 握手拦截器会把 userId 放入 attributes，这里直接读取并转成 Long。
            Object attr = session.getAttributes().get("userId");
            if (attr != null) {
                return Long.valueOf(attr.toString());
            }
        } catch (Exception e) {
            // 2. 解析失败只记日志并返回 null，让调用方按无效连接处理。
            log.warn("[WS] 解析 userId 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 安静关闭连接，避免在清理链路里抛出额外异常。
     *
     * @param session WebSocket 会话
     */
    private String stringifyId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            // 1. 只有连接仍处于打开状态时才尝试关闭，避免重复 close。
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            // 2. 关闭异常只记日志，不再向上抛，避免清理链路被二次打断。
            log.warn("[WS] 关闭会话失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前在线连接数。
     *
     * @return 当前在线用户连接数
     */
    public int getOnlineCount() {
        return SESSIONS.size();
    }

    /**
     * 判断用户当前是否在线。
     *
     * @param userId 用户 ID
     * @return 当前用户是否存在可用连接
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
    }
}
