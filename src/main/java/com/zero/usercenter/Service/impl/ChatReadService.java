package com.zero.usercenter.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.DTO.MsgReadDTO;
import com.zero.usercenter.DTO.Result;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.HIDDEN_CONVERSATIONS_KEY;
import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_KEY;
import static com.zero.usercenter.utils.Number.UNREAD_COUNT_KEY;
import static com.zero.usercenter.utils.Number.USER_ONLINE_KEY;

/**
 * 会话读取与未读状态服务。
 * 负责已读标记、未读数聚合、最近会话列表构建以及会话隐藏恢复。
 */
@Service
public class ChatReadService {
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ChatSupportService chatSupportService;
    @Resource private ChatMessageMapper chatMessageMapper;
    @Resource private UserMapper userMapper;
    @Resource private TeamMapper teamMapper;
    @Resource private TeamMemberMapper teamMemberMapper;

    /**
     * 批量标记消息为已读，并同步清理对应会话未读数。
     */
    @Transactional
    public Result markMsgRead(MsgReadDTO dto) {
        // 1. 已读上报必须同时带会话 ID 和消息 ID 列表，才能精确清理对应未读数。
        Long userId = chatSupportService.requireLogin();
        if (dto.getConversationId() == null || dto.getMsgIds() == null || dto.getMsgIds().isEmpty()) {
            return Result.fail("参数不能为空");
        }
        // 2. 具体已读落库和未读清理由统一支撑方法处理，避免多个入口重复实现。
        chatSupportService.doMarkRead(dto.getConversationId(), dto.getMsgIds(), userId);
        return Result.ok("已标记为已读");
    }

    /**
     * 聚合当前用户所有会话的未读数。
     * 直接扫描 Redis 中的未读 key，并附带最近一条消息预览。
     */
    public Result getUnreadCount() {
        // 1. 未读数优先直接扫描 Redis，避免每次都去消息表和回执表聚合统计。
        Long userId = chatSupportService.requireLogin();
        String pattern = UNREAD_COUNT_KEY + userId + ":*";
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(200).build())) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception ignored) {
        }

        long totalUnread = 0;
        List<Map<String, Object>> conversations = new ArrayList<>();
        String prefix = UNREAD_COUNT_KEY + userId + ":";

        // 2. 每个未读 key 再补上最近一条消息摘要，方便前端角标和会话预览共用。
        for (String key : keys) {
            String unreadStr = stringRedisTemplate.opsForValue().get(key);
            long unread = unreadStr != null ? Long.parseLong(unreadStr) : 0;
            if (unread <= 0) continue;
            totalUnread += unread;
            String convId = key.substring(prefix.length());

            Map<Object, Object> lastMsgCache = stringRedisTemplate.opsForHash().entries(LAST_MSG_CACHE_KEY + convId);
            String lastMsgContent = lastMsgCache.isEmpty() ? "" : String.valueOf(lastMsgCache.getOrDefault("msgContent", ""));

            Map<String, Object> conv = new HashMap<>();
            conv.put("conversationId", convId);
            conv.put("unreadCount", unread);
            Integer msgType = null;
            if (!lastMsgCache.isEmpty()) {
                Object cachedType = lastMsgCache.get("msgType");
                if (cachedType != null) {
                    try {
                        msgType = Integer.parseInt(String.valueOf(cachedType));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            conv.put("msgType", msgType);
            conv.put("lastMessage", lastMsgContent);
            conversations.add(conv);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalUnread", totalUnread);
        data.put("conversations", conversations);
        return Result.ok(data);
    }

    /**
     * 构建最近会话列表。
     * 只保留用户当前仍有访问权限的会话，并过滤用户主动隐藏的会话。
     */
    public Result getRecentConversations() {
        // 1. 先拿到当前用户手动隐藏的会话，以及仍有效的团队成员身份。
        Long userId = chatSupportService.requireLogin();
        Set<String> hiddenConversationIds = loadHiddenConversationIds(userId);
        Set<Long> joinedTeamIds = loadJoinedTeamIds(userId);

        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getIsDelete, 0).and(wrapper -> {
            wrapper.eq(ChatMessage::getSenderId, userId)
                    .or(sub -> sub.eq(ChatMessage::getRecvType, 1).eq(ChatMessage::getRecvId, userId));
            if (!joinedTeamIds.isEmpty()) {
                wrapper.or(sub -> sub.eq(ChatMessage::getRecvType, 2).in(ChatMessage::getRecvId, joinedTeamIds));
            }
        }).orderByDesc(ChatMessage::getCreateTime).orderByDesc(ChatMessage::getId);

        List<ChatMessage> rawMessages = chatMessageMapper.selectList(qw);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 一次遍历里只保留每个会话最新的一条可见消息，并过滤已隐藏或无权限访问的会话。
        LinkedHashMap<String, ChatMessage> latestByConversation = new LinkedHashMap<>();
        for (ChatMessage message : rawMessages) {
            String conversationId = message.getConversationId();
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }
            if (hiddenConversationIds.contains(conversationId)) {
                continue;
            }
            if (latestByConversation.containsKey(conversationId)) {
                continue;
            }
            // 消息表里可能还能扫出“当前用户已经无权访问”的历史会话，
            // 例如退队后的群消息或异常数据，这里要二次校验可见性后才能放进最近会话列表。
            ChatSupportService.ResultHolder access = chatSupportService.checkConversationAccess(conversationId, userId);
            if (access != null && access.failed()) {
                continue;
            }
            latestByConversation.put(conversationId, message);
        }

        if (latestByConversation.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3. 先批量收集私聊对端和团队 ID，后面统一补齐展示信息，避免 N+1 查询。
        Set<Long> privateUserIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        for (Map.Entry<String, ChatMessage> entry : latestByConversation.entrySet()) {
            String conversationId = entry.getKey();
            ChatMessage message = entry.getValue();
            if (conversationId.startsWith("team_")) {
                teamIds.add(chatSupportService.parseTeamId(conversationId));
            } else {
                Long targetUserId = resolvePrivateTargetUserId(message, userId);
                if (targetUserId != null) {
                    privateUserIds.add(targetUserId);
                }
            }
        }

        Map<Long, User> userMap = loadUsers(privateUserIds);
        Map<Long, Team> teamMap = loadTeams(teamIds);

        // 4. 最终统一拼装最近会话列表，并按最后消息时间倒序返回。
        List<Map<String, Object>> result = latestByConversation.entrySet().stream()
                .map(entry -> buildConversationItem(entry.getKey(), entry.getValue(), userId, userMap, teamMap))
                .filter(Objects::nonNull)
                .sorted((a, b) -> compareTime((LocalDateTime) b.get("lastTime"), (LocalDateTime) a.get("lastTime")))
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    /**
     * 隐藏指定会话。
     * 只影响当前用户的会话列表展示，不会删除任何业务数据。
     */
    public Result hideConversation(String conversationId) {
        // 1. 隐藏前先校验当前用户对该会话仍然有访问权。
        Long userId = chatSupportService.requireLogin();
        if (conversationId == null || conversationId.isBlank()) {
            return Result.fail("会话ID不能为空");
        }
        ChatSupportService.ResultHolder access = chatSupportService.checkConversationAccess(conversationId, userId);
        if (access != null && access.failed()) {
            return Result.fail(access.message());
        }
        // 2. 隐藏只影响当前用户自己的会话列表展示，不删除任何历史消息。
        stringRedisTemplate.opsForSet().add(HIDDEN_CONVERSATIONS_KEY + userId, conversationId);
        return Result.ok("已从会话列表移除");
    }

    /**
     * 如果会话曾被手动隐藏，则在新消息到达或主动进入会话时恢复显示。
     */
    public void restoreConversationIfHidden(Long userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        // 收到新消息或用户主动进入会话时，自动把会话从隐藏集合里恢复出来。
        stringRedisTemplate.opsForSet().remove(HIDDEN_CONVERSATIONS_KEY + userId, conversationId);
    }

    /**
     * 读取当前用户已隐藏的会话 ID 集合。
     *
     * @param userId 当前用户 ID
     * @return 隐藏会话 ID 集合
     */
    private Set<String> loadHiddenConversationIds(Long userId) {
        // 1. 隐藏会话直接存 Redis Set，读取成本低，也便于后续增删。
        Set<String> members = stringRedisTemplate.opsForSet().members(HIDDEN_CONVERSATIONS_KEY + userId);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members;
    }

    /**
     * 读取当前用户仍有效的团队 ID 集合。
     *
     * @param userId 当前用户 ID
     * @return 当前未退出的团队 ID 集合
     */
    private Set<Long> loadJoinedTeamIds(Long userId) {
        // 1. 最近会话列表只允许展示用户当前仍有权限访问的群会话，因此只取未退出团队。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getUserId, userId).eq(TeamMember::getIsQuit, 0).select(TeamMember::getTeamId);
        return teamMemberMapper.selectList(qw).stream().map(TeamMember::getTeamId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 批量加载用户信息。
     *
     * @param userIds 用户 ID 集合
     * @return Map&lt;用户 ID, 用户实体&gt;
     */
    private Map<Long, User> loadUsers(Set<Long> userIds) {
        // 1. 只对私聊对端做批量补数，避免最近会话列表构建时出现 N+1 查询。
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.in(User::getId, userIds).eq(User::getIsDelete, 0);
        return userMapper.selectList(qw).stream().collect(Collectors.toMap(User::getId, item -> item));
    }

    /**
     * 批量加载团队信息。
     *
     * @param teamIds 团队 ID 集合
     * @return Map&lt;团队 ID, 团队实体&gt;
     */
    private Map<Long, Team> loadTeams(Set<Long> teamIds) {
        // 1. 只查询未删除团队，避免最近会话列表展示到已经被解散的群。
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<Team> qw = new LambdaQueryWrapper<>();
        qw.in(Team::getId, teamIds).eq(Team::getIsDelete, 0);
        return teamMapper.selectList(qw).stream().collect(Collectors.toMap(Team::getId, item -> item));
    }

    /**
     * 解析私聊会话中的对端用户 ID。
     *
     * @param message       最近一条消息
     * @param currentUserId 当前用户 ID
     * @return 私聊对端用户 ID；非私聊消息时返回 null
     */
    private Long resolvePrivateTargetUserId(ChatMessage message, Long currentUserId) {
        // 1. 只有单聊消息才需要计算对端用户，群消息直接返回 null。
        if (message == null || message.getRecvType() == null || message.getRecvType() != 1) {
            return null;
        }
        // 2. 如果最近消息是我发出的，对端就是 recvId；否则对端就是 senderId。
        if (Objects.equals(message.getSenderId(), currentUserId)) {
            return message.getRecvId();
        }
        return message.getSenderId();
    }

    /**
     * 构建最近会话列表项。
     * 私聊展示对端用户信息，群聊展示团队信息，并补充未读数和在线状态。
     */
    private Map<String, Object> buildConversationItem(String conversationId,
                                                      ChatMessage lastMessage,
                                                      Long currentUserId,
                                                      Map<Long, User> userMap,
                                                      Map<Long, Team> teamMap) {
        // 先组装会话公共字段，后面再按私聊/群聊分支补齐各自展示信息。
        Map<String, Object> item = new HashMap<>();
        item.put("conversationId", conversationId);
        item.put("id", conversationId);
        String previewContent = chatSupportService.buildConversationPreviewContent(lastMessage);
        item.put("msgType", lastMessage.getMsgType());
        item.put("lastMsg", previewContent);
        item.put("lastMessage", previewContent);
        item.put("lastTime", lastMessage.getCreateTime());
        item.put("time", lastMessage.getCreateTime());
        item.put("unreadCount", getUnreadCountOfConversation(currentUserId, conversationId));

        if (conversationId.startsWith("team_")) {
            // 群聊会话展示团队名和头像，目标实体是团队。
            Long teamId = chatSupportService.parseTeamId(conversationId);
            Team team = teamMap.get(teamId);
            if (team == null) {
                return null;
            }
            item.put("type", "team");
            item.put("targetId", teamId);
            item.put("teamId", teamId);
            item.put("name", team.getTeamName());
            item.put("avatar", team.getTeamAvatar());
            return item;
        }

        Long targetUserId = resolvePrivateTargetUserId(lastMessage, currentUserId);
        if (targetUserId == null) {
            return null;
        }
        // 私聊会话展示对端用户昵称、头像和在线状态。
        User targetUser = userMap.get(targetUserId);
        if (targetUser == null) {
            return null;
        }
        item.put("type", "private");
        item.put("targetId", targetUserId);
        item.put("friendId", targetUserId);
        item.put("name", targetUser.getUserNickname());
        item.put("avatar", targetUser.getUserAvatar());
        item.put("onlineStatus", resolveVisibleOnlineStatus(targetUserId));
        return item;
    }

    /**
     * 读取用户当前可见的在线状态。
     *
     * @param userId 用户 ID
     * @return 1-在线，0-离线
     */
    private int resolveVisibleOnlineStatus(Long userId) {
        // 1. 在线状态以 Redis Hash 为准，不再回库读取，降低最近会话接口成本。
        if (userId == null) {
            return 0;
        }
        Object status = stringRedisTemplate.opsForHash().get(USER_ONLINE_KEY, String.valueOf(userId));
        if (status == null) {
            return 0;
        }
        try {
            // 2. 当前只对外暴露在线/离线二值，其他异常值统一降级成离线。
            int onlineStatus = Integer.parseInt(String.valueOf(status));
            return onlineStatus == 1 ? 1 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 读取指定会话的未读数。
     *
     * @param userId         当前用户 ID
     * @param conversationId 会话 ID
     * @return 会话未读数
     */
    private long getUnreadCountOfConversation(Long userId, String conversationId) {
        // 1. 未读数直接从 Redis 中按“用户 + 会话”维度读取。
        String key = UNREAD_COUNT_KEY + userId + ":" + conversationId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            // 2. 解析失败时按 0 处理，避免脏数据影响会话列表展示。
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 兜底处理消息内容空值。
     *
     * @param content 原始消息内容
     * @return 非 null 的消息内容
     */
    private String safeContent(String content) {
        return content == null ? "" : content;
    }

    /**
     * 比较两个消息时间。
     *
     * @param left  左侧时间
     * @param right 右侧时间
     * @return 比较结果
     */
    private int compareTime(LocalDateTime left, LocalDateTime right) {
        // 1. 统一处理空时间，保证排序逻辑稳定。
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        return left.compareTo(right);
    }
}
