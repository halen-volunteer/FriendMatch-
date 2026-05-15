package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.MessageReadReceiptMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.mq.AsyncMessageService;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.PendingChatOperation;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.CHAT_MESSAGE_EDIT_WINDOW_MINUTES;
import static com.zero.usercenter.utils.Number.CHAT_LATEST_HISTORY_CACHE_KEY;
import static com.zero.usercenter.utils.Number.GROUP_NOTICE_KEY;
import static com.zero.usercenter.utils.Number.GROUP_NOTICE_TTL_DAYS;
import static com.zero.usercenter.utils.Number.HIDDEN_CONVERSATIONS_KEY;
import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_KEY;
import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_TTL_DAYS;
import static com.zero.usercenter.utils.Number.LARGE_TEAM_FLAG_KEY;
import static com.zero.usercenter.utils.Number.LARGE_TEAM_FLAG_TTL_MINUTES;
import static com.zero.usercenter.utils.Number.LARGE_TEAM_MEMBER_THRESHOLD;
import static com.zero.usercenter.utils.Number.MSG_BITMAP_TTL_DAYS;
import static com.zero.usercenter.utils.Number.MSG_DELIVER_KEY;
import static com.zero.usercenter.utils.Number.MSG_READ_KEY;
import static com.zero.usercenter.utils.Number.UNREAD_COUNT_KEY;

/**
 * 聊天通用支撑服务。
 * 负责承接聊天域内可复用的底层能力，包括：
 * 登录态兜底、会话 ID 规范、消息内容编码、回执写入、会话摘要缓存、
 * WebSocket 推送载荷组装，以及 MQ 消费后的统一收尾逻辑。
 */
@Service
public class ChatSupportService {

    private static final int RECEIPT_INSERT_MAX_RETRIES = 3;
    private static final long RECEIPT_RETRY_SLEEP_MILLIS = 20L;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private MessageReadReceiptMapper messageReadReceiptMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private AsyncMessageService asyncMessageService;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前登录用户 ID
     */
    public Long requireLogin() {
        // 1. 聊天域的所有入口都统一从 UserHolder 读取登录态，避免每个 service 重复判断。
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            // 2. 未登录时直接抛业务异常，由全局异常处理器统一返回给前端。
            throw new BusinessException("用户未登录");
        }
        return userId;
    }

    /**
     * 从团队会话 ID 解析 teamId。
     *
     * @param conversationId 团队会话 ID，格式为 team_{teamId}
     * @return 解析出的团队 ID
     */
    public Long parseTeamId(String conversationId) {
        try {
            // 1. 团队会话固定使用 team_ 前缀，这里直接截断前缀后转 Long。
            return Long.parseLong(conversationId.substring(5));
        } catch (Exception e) {
            // 2. 任意格式异常都收口成统一的业务错误，避免把底层实现细节暴露给外部。
            throw new BusinessException("团队会话ID格式错误");
        }
    }

    /**
     * 构建私聊会话 ID。
     *
     * @param a 用户 A 的 ID
     * @param b 用户 B 的 ID
     * @return 规范化后的私聊会话 ID
     */
    public String buildConvId(Long a, Long b) {
        // 1. 会话 ID 固定使用“小 ID_大 ID”，确保双方看到的是同一条会话。
        return Math.min(a, b) + "_" + Math.max(a, b);
    }

    /**
     * 查询用户在指定团队中的有效成员记录。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return 未退出的成员记录；不存在时返回 null
     */
    public TeamMember getMember(Long teamId, Long userId) {
        // 1. 聊天权限只认“当前仍在团队内”的成员，历史退队记录不再参与校验。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectOne(qw);
    }

    /**
     * 校验当前用户是否有权访问指定会话。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @return 校验失败时返回失败结果；校验通过时返回 null
     */
    public ResultHolder checkConversationAccess(String conversationId, Long userId) {
        // 1. 团队会话只校验“当前是否仍是团队成员”，私聊会话则校验“当前用户是否属于会话两端之一”。
        if (conversationId.startsWith("team_")) {
            Long teamId = parseTeamId(conversationId);
            if (getMember(teamId, userId) == null) {
                return ResultHolder.fail("您不是该团队成员");
            }
            return null;
        }

        // 2. 私聊会话必须满足 userA_userB 的双端格式，否则直接拒绝访问。
        String[] arr = conversationId.split("_");
        if (arr.length != 2) {
            return ResultHolder.fail("会话ID格式错误");
        }
        try {
            Long u1 = Long.parseLong(arr[0]);
            Long u2 = Long.parseLong(arr[1]);
            if (!userId.equals(u1) && !userId.equals(u2)) {
                return ResultHolder.fail("无权操作该会话");
            }
        } catch (NumberFormatException e) {
            return ResultHolder.fail("会话ID格式错误");
        }
        return null;
    }

    /**
     * 写入单条消息回执。
     *
     * @param msgId       消息 ID
     * @param userId      回执归属用户 ID
     * @param receiptType 回执类型：1-送达，2-已读
     */
    public void writeReceipt(Long msgId, Long userId, int receiptType) {
        // 1. 单条写回执同样复用批量写入逻辑，统一去重、排序和轻量重试策略。
        batchInsertReceiptWithRetry(Collections.singletonList(msgId), userId, receiptType, LocalDateTime.now());
        if (receiptType == 1) {
            // 2. 送达回执额外同步到 Redis 位图，便于高频读取消息送达状态。
            stringRedisTemplate.opsForValue().setBit(MSG_DELIVER_KEY + msgId, userId, true);
            stringRedisTemplate.expire(MSG_DELIVER_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 为团队消息批量写入送达回执。
     *
     * @param teamId   团队 ID
     * @param msgId    消息 ID
     * @param senderId 发送者用户 ID
     */
    public void writeTeamReceipts(Long teamId, Long msgId, Long senderId) {
        // 1. 先查出团队内除发送者外的有效成员，后续回执逻辑统一复用这份成员名单。
        List<Long> memberIds = listActiveTeamMemberIds(teamId, senderId);
        if (memberIds.isEmpty()) {
            return;
        }

        // 2. 成员名单确认后，再统一补写送达回执和 Redis 位图。
        writeTeamReceiptsToMembers(memberIds, msgId);
    }

    /**
     * 已知成员列表时，批量写入团队消息送达回执。
     *
     * @param memberIds 需要写回执的成员 ID 列表
     * @param msgId     消息 ID
     */
    public void writeTeamReceiptsToMembers(List<Long> memberIds, Long msgId) {
        // 1. 成员列表为空时直接返回，避免空循环和无意义的 Redis 写操作。
        if (memberIds == null || memberIds.isEmpty() || msgId == null) {
            return;
        }

        // 2. 逐个成员补写送达回执，并把送达状态同步进 Redis 位图。
        LocalDateTime now = LocalDateTime.now();
        for (Long memberId : memberIds) {
            batchInsertReceiptWithRetry(Collections.singletonList(msgId), memberId, 1, now);
            stringRedisTemplate.opsForValue().setBit(MSG_DELIVER_KEY + msgId, memberId, true);
            stringRedisTemplate.expire(MSG_DELIVER_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 查询团队内当前仍有效的成员 ID 列表。
     *
     * @param teamId        团队 ID
     * @param excludeUserId 需要排除的用户 ID，可为空
     * @return 有效成员 ID 列表
     */
    public List<Long> listActiveTeamMemberIds(Long teamId, Long excludeUserId) {
        // 1. 只认未退出成员；如果调用方传了 excludeUserId，则一并在查询阶段排除。
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0);
        if (excludeUserId != null) {
            qw.ne(TeamMember::getUserId, excludeUserId);
        }
        return teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toList());
    }

    /**
     * 判断指定团队是否需要走“大群优化”链路。
     * 会优先读取短 TTL 缓存，避免历史消息接口频繁回表统计成员数。
     *
     * @param teamId 团队 ID
     * @return true-当前团队已达到大群阈值，false-仍按普通群处理
     */
    public boolean isLargeTeam(Long teamId) {
        // 1. 先读短 TTL 缓存，让高频历史请求不必每次都回表数成员。
        if (teamId == null) {
            return false;
        }
        String cacheKey = LARGE_TEAM_FLAG_KEY + teamId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }

        // 2. 缓存未命中时再回表统计当前有效成员数，并把结果回填到 Redis。
        LambdaQueryWrapper<TeamMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0);
        long memberCount = teamMemberMapper.selectCount(queryWrapper);
        boolean largeTeam = isLargeTeamByMemberCount(memberCount);
        refreshLargeTeamFlagCache(teamId, memberCount);
        return largeTeam;
    }

    /**
     * 根据成员数判断是否属于大群。
     *
     * @param memberCount 当前有效成员数
     * @return true-超过阈值，false-未超过阈值
     */
    public boolean isLargeTeamByMemberCount(long memberCount) {
        return memberCount > LARGE_TEAM_MEMBER_THRESHOLD;
    }

    /**
     * 刷新团队“大群标记”缓存。
     * 群消息发送链路已经拿到实时成员数时，会顺手把这个标记写回缓存，减少后续历史接口的统计开销。
     *
     * @param teamId       团队 ID
     * @param memberCount  当前有效成员数
     */
    public void refreshLargeTeamFlagCache(Long teamId, long memberCount) {
        if (teamId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                LARGE_TEAM_FLAG_KEY + teamId,
                isLargeTeamByMemberCount(memberCount) ? "1" : "0",
                LARGE_TEAM_FLAG_TTL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * 批量恢复会话入口并累加未读数。
     * 这里统一走 Redis Pipeline，减少群消息 fan-out 时的网络往返次数。
     *
     * @param memberIds       目标成员 ID 列表
     * @param conversationId  会话 ID
     */
    public void batchRestoreConversationAndIncrementUnread(List<Long> memberIds, String conversationId) {
        // 1. 成员列表为空或会话 ID 非法时直接返回，避免创建无意义的管道请求。
        if (memberIds == null || memberIds.isEmpty() || conversationId == null || conversationId.isBlank()) {
            return;
        }

        // 2. 用同一条 Pipeline 同时完成“恢复会话入口”和“未读数自增”，把大群里的 O(N) Redis 往返压成一批。
        RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
        byte[] conversationBytes = serializer.serialize(conversationId);
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long memberId : memberIds) {
                if (memberId == null) {
                    continue;
                }
                connection.sRem(
                        serializer.serialize(HIDDEN_CONVERSATIONS_KEY + memberId),
                        conversationBytes
                );
                connection.incr(serializer.serialize(UNREAD_COUNT_KEY + memberId + ":" + conversationId));
            }
            return null;
        });
    }

    /**
     * 执行会话已读处理。
     *
     * @param convId 会话 ID
     * @param msgIds 需要标记已读的消息 ID 列表
     * @param userId 当前用户 ID
     */
    public void doMarkRead(String convId, List<Long> msgIds, Long userId) {
        // 1. 先清洗消息 ID，过滤空值和非法值，并做去重排序。
        List<Long> normalizedMsgIds = normalizeMsgIds(msgIds);
        if (normalizedMsgIds.isEmpty()) {
            return;
        }

        // 2. 批量写入已读回执，并清掉当前用户在该会话下的未读计数缓存。
        batchInsertReceiptWithRetry(normalizedMsgIds, userId, 2, LocalDateTime.now());
        stringRedisTemplate.delete(UNREAD_COUNT_KEY + userId + ":" + convId);

        // 3. 团队会话额外维护“消息维度已读位图”，给已读人数统计等高频场景使用。
        if (convId.startsWith("team_")) {
            for (Long msgId : normalizedMsgIds) {
                stringRedisTemplate.opsForValue().setBit(MSG_READ_KEY + msgId, userId, true);
                stringRedisTemplate.expire(MSG_READ_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
            }
        }
    }

    /**
     * 批量插入消息回执，并对瞬时死锁做轻量重试。
     *
     * @param msgIds      消息 ID 列表
     * @param userId      回执归属用户 ID
     * @param receiptType 回执类型
     * @param receiptTime 回执时间
     */
    private void batchInsertReceiptWithRetry(List<Long> msgIds, Long userId, int receiptType, LocalDateTime receiptTime) {
        // 1. 先标准化消息 ID，避免无效数据进入批量 SQL。
        List<Long> normalizedMsgIds = normalizeMsgIds(msgIds);
        if (normalizedMsgIds.isEmpty() || userId == null) {
            return;
        }

        // 2. 高并发下批量 INSERT IGNORE 可能会遇到死锁，这里做短退避重试，优先消化瞬时冲突。
        int attempt = 0;
        while (true) {
            try {
                messageReadReceiptMapper.batchInsertIgnore(normalizedMsgIds, userId, receiptType, receiptTime);
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                attempt++;
                if (attempt >= RECEIPT_INSERT_MAX_RETRIES) {
                    throw ex;
                }
                try {
                    Thread.sleep(RECEIPT_RETRY_SLEEP_MILLIS * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    /**
     * 标准化消息 ID 列表。
     *
     * @param msgIds 原始消息 ID 列表
     * @return 清洗后的消息 ID 列表
     */
    private List<Long> normalizeMsgIds(List<Long> msgIds) {
        // 1. 空列表直接返回，避免后续逻辑继续处理。
        if (msgIds == null || msgIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 过滤掉 null 和非正数消息 ID，只保留合法主键。
        List<Long> normalized = new ArrayList<>();
        for (Long msgId : msgIds) {
            if (msgId != null && msgId > 0) {
                normalized.add(msgId);
            }
        }
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 去重并按升序排序，降低批量 SQL 出现索引访问顺序不一致的概率。
        return normalized.stream().distinct().sorted().collect(Collectors.toList());
    }

    /**
     * 将消息列表批量转换为前端展示视图。
     *
     * @param msgs          消息实体列表
     * @param currentUserId 当前登录用户 ID
     * @return 转换后的消息 VO 列表
     */
    public List<Map<String, Object>> buildVoList(List<ChatMessage> msgs, Long currentUserId) {
        // 1. 列表场景统一复用单条消息转换逻辑，保证详情和列表返回结构一致。
        return msgs.stream().map(m -> buildMessageVo(m, currentUserId)).collect(Collectors.toList());
    }

    /**
     * 将单条消息实体转换为前端展示结构。
     *
     * @param msg           消息实体
     * @param currentUserId 当前登录用户 ID
     * @return 单条消息展示数据
     */
    public Map<String, Object> buildMessageVo(ChatMessage msg, Long currentUserId) {
        Map<String, Object> vo = new HashMap<>();
        if (msg == null) {
            return vo;
        }

        // 1. 先输出基础字段；如果消息已撤回，则对外统一展示占位文案，避免继续暴露原消息内容。
        vo.put("msgId", stringifyId(msg.getId()));
        vo.put("senderId", msg.getSenderId());
        vo.put("isSelf", msg.getSenderId() != null && msg.getSenderId().equals(currentUserId));
        vo.put("msgType", msg.getMsgType());
        vo.put("msgContent", Integer.valueOf(1).equals(msg.getIsRevoke()) ? "消息已撤回" : msg.getMsgContent());
        vo.put("isEdited", msg.getIsEdited());
        vo.put("editTime", msg.getEditTime());
        vo.put("editCount", msg.getEditCount());
        vo.put("isRevoke", Integer.valueOf(1).equals(msg.getIsRevoke()));
        vo.put("revokeTime", msg.getRevokeTime());
        vo.put("createTime", msg.getCreateTime());
        return vo;
    }

    /**
     * 判断消息是否仍处于允许编辑/撤回的 5 分钟窗口内。
     *
     * @param createTime 消息创建时间
     * @return true-仍在窗口内，false-已超时
     */
    public boolean isWithinMessageOperateWindow(LocalDateTime createTime) {
        // 1. 创建时间为空时视为非法消息，直接按不可操作处理。
        if (createTime == null) {
            return false;
        }
        // 2. 只要当前时间还没超过“创建时间 + 5 分钟”，就允许继续操作。
        return !LocalDateTime.now().isAfter(createTime.plusMinutes(CHAT_MESSAGE_EDIT_WINDOW_MINUTES));
    }

    /**
     * 判断消息类型是否支持“文本编辑”。
     *
     * @param msgType 消息类型
     * @return true-允许编辑，false-不允许编辑
     */
    public boolean supportsMessageEdit(Integer msgType) {
        // 1. 当前只开放纯文本和 @ 消息编辑，避免结构化消息被错误改写。
        return msgType != null && (msgType == 1 || msgType == 5);
    }

    /**
     * 根据 MQ 消息和挂起操作，构建最终要落库的聊天消息实体。
     *
     * @param message   MQ 中的发送消息
     * @param operation 等待消费期间产生的最新操作，可为 null
     * @return 可直接入库的消息实体
     */
    public ChatMessage buildPersistMessage(ChatSendMessage message, PendingChatOperation operation) {
        // 1. 先把 MQ 消息的基础字段落到实体上，保证 msgId 和 createTime 与发送响应保持一致。
        ChatMessage msg = new ChatMessage();
        msg.setId(message.getMsgId());
        msg.setSenderId(message.getSenderId());
        msg.setRecvType(message.getRecvType());
        msg.setRecvId(message.getRecvId());
        msg.setConversationId(message.getConversationId());
        msg.setMsgType(message.getMsgType());
        msg.setMsgContent(message.getMsgContent());
        msg.setIsEdited(0);
        msg.setEditCount(0);
        msg.setIsRevoke(0);
        msg.setIsDelete(0);
        msg.setCreateTime(message.getCreateTime());

        // 2. 如果消息在排队期间被编辑过，则直接使用最新内容入库，并保留编辑痕迹。
        if (operation != null && PendingChatOperation.TYPE_EDIT.equals(operation.getOperationType())) {
            msg.setMsgContent(operation.getEditedContent());
            msg.setIsEdited(1);
            msg.setEditCount(1);
            msg.setEditTime(operation.getOperateTime());
        }
        return msg;
    }

    /**
     * 构建数据库中的消息存储内容。
     *
     * @param msgType    消息类型
     * @param msgContent 文本内容
     * @param fileUrl    文件/图片 URL
     * @param fileName   文件名
     * @param fileSize   文件大小
     * @param mediaType  媒体类型
     * @param emojiId    表情 ID
     * @param atUsers    @ 用户列表
     * @return 统一编码后的消息内容
     */
    public String buildStoredContent(Integer msgType, String msgContent,
                                     String fileUrl, String fileName, Long fileSize, String mediaType,
                                     String emojiId, List<Long> atUsers) {
        // 1. 不同消息类型统一落到 msg_content 字段，文本直接存纯文本，复杂类型转结构化 JSON。
        return switch (msgType) {
            case 2 -> {
                Map<String, Object> m = new HashMap<>();
                m.put("url", fileUrl);
                if (fileName != null && !fileName.isBlank()) {
                    m.put("name", fileName);
                }
                if (fileSize != null) {
                    m.put("size", fileSize);
                }
                if (mediaType != null && !mediaType.isBlank()) {
                    m.put("mediaType", mediaType.trim());
                }
                if (msgContent != null && !msgContent.isBlank()) {
                    m.put("caption", msgContent.trim());
                }
                if (atUsers != null && !atUsers.isEmpty()) {
                    m.put("atUsers", atUsers);
                }
                yield JSON.toJSONString(m);
            }
            case 3 -> {
                Map<String, Object> m = new HashMap<>();
                m.put("url", fileUrl);
                m.put("name", fileName);
                if (fileSize != null) {
                    m.put("size", fileSize);
                }
                if (mediaType != null && !mediaType.isBlank()) {
                    m.put("mediaType", mediaType.trim());
                }
                if (atUsers != null && !atUsers.isEmpty()) {
                    m.put("atUsers", atUsers);
                }
                yield JSON.toJSONString(m);
            }
            case 4 -> {
                Map<String, Object> m = new HashMap<>();
                m.put("emojiId", emojiId);
                yield JSON.toJSONString(m);
            }
            default -> msgContent != null ? msgContent.trim() : "";
        };
    }

    /**
     * 从结构化消息中提取可搜索文本。
     *
     * @param msgType       消息类型
     * @param storedContent 数据库中存储的消息内容
     * @return 可搜索文本
     */
    public String extractSearchableText(Integer msgType, String storedContent) {
        if (storedContent == null || storedContent.isBlank()) {
            return "";
        }

        // 1. 文本消息直接返回原文；结构化消息则提取最适合搜索的可读文本。
        if (msgType == null) {
            return storedContent.trim();
        }
        return switch (msgType) {
            case 1, 5 -> storedContent.trim();
            case 2 -> {
                Map<String, Object> parsed = parseStructuredContent(storedContent);
                Object caption = parsed.get("caption");
                yield caption == null ? "" : String.valueOf(caption).trim();
            }
            case 3 -> {
                Map<String, Object> parsed = parseStructuredContent(storedContent);
                Object name = parsed.get("name");
                yield name == null ? "" : String.valueOf(name).trim();
            }
            case 4 -> "表情包";
            default -> storedContent.trim();
        };
    }

    /**
     * 提取 AI 审核使用的文本。
     *
     * @param msgType       消息类型
     * @param storedContent 存储内容
     * @return 审核文本
     */
    public String extractAuditText(Integer msgType, String storedContent) {
        return extractSearchableText(msgType, storedContent);
    }

    /**
     * 判断消息是否支持文本审核。
     *
     * @param msgType       消息类型
     * @param storedContent 存储内容
     * @return true-可做文本审核，false-不适合文本审核
     */
    public boolean supportsTextAudit(Integer msgType, String storedContent) {
        if (msgType == null) {
            return storedContent != null && !storedContent.isBlank();
        }

        // 1. 当前 AI 审核只处理能稳定提取文本语义的消息。
        return switch (msgType) {
            case 1, 5 -> storedContent != null && !storedContent.isBlank();
            case 2 -> {
                Map<String, Object> parsed = parseStructuredContent(storedContent);
                Object caption = parsed.get("caption");
                yield caption != null && !String.valueOf(caption).isBlank();
            }
            default -> false;
        };
    }

    /**
     * 判断当前文件消息是否为视频。
     *
     * @param msgType       消息类型
     * @param storedContent 存储内容
     * @return true-视频消息，false-非视频消息
     */
    public boolean isVideoMessage(Integer msgType, String storedContent) {
        if (msgType == null || msgType != 3 || storedContent == null || storedContent.isBlank()) {
            return false;
        }
        Map<String, Object> parsed = parseStructuredContent(storedContent);
        Object mediaType = parsed.get("mediaType");
        if (mediaType != null && String.valueOf(mediaType).toLowerCase().startsWith("video")) {
            return true;
        }
        Object name = parsed.get("name");
        if (name == null) {
            return false;
        }
        String lowerName = String.valueOf(name).toLowerCase();
        Set<String> videoExts = new HashSet<>(List.of(".mp4", ".mov", ".m4v", ".webm", ".avi", ".mkv"));
        return videoExts.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * 安全解析结构化消息内容。
     *
     * @param storedContent 数据库存储内容
     * @return 解析后的 Map；失败时返回空 Map
     */
    private Map<String, Object> parseStructuredContent(String storedContent) {
        try {
            Map<String, Object> parsed = JSON.parseObject(storedContent, Map.class);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    /**
     * 构建私聊 WebSocket 推送载荷。
     *
     * @param msg 聊天消息实体
     * @return 推送 JSON 字符串
     */
    public String buildPrivatePush(ChatMessage msg) {
        // 1. 私聊推送载荷保持轻量，只携带实时渲染所需的核心字段。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "private_message");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", stringifyId(msg.getId()));
        data.put("conversationId", msg.getConversationId());
        data.put("senderId", msg.getSenderId());
        data.put("msgType", msg.getMsgType());
        data.put("content", msg.getMsgContent());
        data.put("isEdited", Integer.valueOf(1).equals(msg.getIsEdited()));
        data.put("editTime", msg.getEditTime());
        data.put("editCount", msg.getEditCount());
        data.put("isRevoke", Integer.valueOf(1).equals(msg.getIsRevoke()));
        data.put("revokeTime", msg.getRevokeTime());
        data.put("createTime", msg.getCreateTime());
        push.put("data", data);
        return JSON.toJSONString(push);
    }

    /**
     * 构建团队消息 WebSocket 推送载荷。
     *
     * @param msg 聊天消息实体
     * @return 推送 JSON 字符串
     */
    public String buildTeamPush(ChatMessage msg) {
        // 1. 团队消息额外带上 teamId，便于前端快速定位目标会话并刷新列表。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "team_message");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", stringifyId(msg.getId()));
        data.put("teamId", msg.getRecvId());
        data.put("conversationId", msg.getConversationId());
        data.put("senderId", msg.getSenderId());
        data.put("msgType", msg.getMsgType());
        data.put("content", msg.getMsgContent());
        data.put("isEdited", Integer.valueOf(1).equals(msg.getIsEdited()));
        data.put("editTime", msg.getEditTime());
        data.put("editCount", msg.getEditCount());
        data.put("isRevoke", Integer.valueOf(1).equals(msg.getIsRevoke()));
        data.put("revokeTime", msg.getRevokeTime());
        data.put("createTime", msg.getCreateTime());
        push.put("data", data);
        return JSON.toJSONString(push);
    }

    /**
     * 构建大群轻量摘要推送载荷。
     * 大群消息不再实时下发完整正文，而是只广播会话摘要和新消息定位信息，让客户端按需再拉正文。
     *
     * @param msg 已落库团队消息
     * @return 大群摘要推送 JSON
     */
    public String buildTeamSummaryPush(ChatMessage msg) {
        // 1. 摘要事件只保留会话列表刷新所需的最小字段，避免把完整正文广播给大群所有在线成员。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "team_message_summary");

        Map<String, Object> data = new HashMap<>();
        data.put("msgId", stringifyId(msg.getId()));
        data.put("teamId", msg.getRecvId());
        data.put("conversationId", msg.getConversationId());
        data.put("senderId", msg.getSenderId());
        data.put("msgType", msg.getMsgType());
        data.put("lastMessage", buildConversationPreviewContent(msg));
        data.put("summaryOnly", true);
        data.put("createTime", msg.getCreateTime());
        push.put("data", data);
        return JSON.toJSONString(push);
    }

    /**
     * 构建会话列表和未读摘要使用的预览文案。
     *
     * @param msg 消息实体
     * @return 预览文案
     */
    public String buildConversationPreviewContent(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        if (Integer.valueOf(1).equals(msg.getIsRevoke())) {
            return "消息已撤回";
        }

        return switch (msg.getMsgType() == null ? 1 : msg.getMsgType()) {
            case 1, 5 -> msg.getMsgContent() == null ? "" : msg.getMsgContent();
            case 2 -> {
                String caption = extractSearchableText(msg.getMsgType(), msg.getMsgContent());
                yield caption.isBlank() ? "[图片]" : "[图片] " + caption;
            }
            case 3 -> {
                String fileName = extractSearchableText(msg.getMsgType(), msg.getMsgContent());
                yield fileName.isBlank() ? "[文件]" : "[文件] " + fileName;
            }
            case 4 -> "[表情]";
            default -> msg.getMsgContent() == null ? "" : msg.getMsgContent();
        };
    }

    /**
     * 缓存会话最后一条消息摘要。
     *
     * @param msg 最新消息
     */
    public void cacheLastMsg(ChatMessage msg) {
        // 1. 会话列表、未读摘要等高频场景都依赖这份缓存，因此发送链路和撤回/编辑链路都会维护它。
        String key = LAST_MSG_CACHE_KEY + msg.getConversationId();
        Map<String, String> map = new HashMap<>();
        map.put("msgId", String.valueOf(msg.getId()));
        map.put("senderId", String.valueOf(msg.getSenderId()));
        map.put("msgType", String.valueOf(msg.getMsgType()));
        map.put("msgContent", buildConversationPreviewContent(msg));
        map.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : "");
        map.put("isRevoke", String.valueOf(Integer.valueOf(1).equals(msg.getIsRevoke())));
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, LAST_MSG_CACHE_TTL_DAYS, TimeUnit.DAYS);

        // 2. 最近一条消息变化后，最新历史页缓存一定已经过时，需要立即失效，防止大群读到旧最新页。
        evictLatestHistoryCache(msg.getConversationId());
    }

    /**
     * 重新计算并刷新会话最后一条消息摘要缓存。
     *
     * @param conversationId 会话 ID
     */
    public void refreshConversationLastMessageCache(String conversationId) {
        // 1. 编辑或撤回“最后一条消息”后，不能继续沿用旧摘要，因此这里主动回源数据库刷新缓存。
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        // 2. 撤回/编辑场景下，最新历史页同样可能受影响，因此先统一清掉热点缓存。
        evictLatestHistoryCache(conversationId);

        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getIsDelete, 0)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1");
        ChatMessage lastMessage = chatMessageMapper.selectOne(qw);
        if (lastMessage == null) {
            stringRedisTemplate.delete(LAST_MSG_CACHE_KEY + conversationId);
            return;
        }
        cacheLastMsg(lastMessage);
    }

    /**
     * 处理消息成功入库后的后续动作。
     *
     * @param msg       已经落库的消息
     * @param atUserIds @ 消息关联的目标用户列表
     */
    public void afterMessagePersisted(ChatMessage msg, List<Long> atUserIds) {
        if (msg == null || msg.getRecvType() == null) {
            return;
        }

        // 1. 根据接收类型分流后续处理逻辑，私聊和群聊在未读、回执和推送上的处理方式不同。
        if (msg.getRecvType() == 1) {
            handlePrivateMessagePersisted(msg);
            return;
        }
        if (msg.getRecvType() == 2) {
            handleTeamMessagePersisted(msg, atUserIds);
        }
    }

    /**
     * 私聊消息落库后的收尾逻辑。
     *
     * @param msg 已落库私聊消息
     */
    private void handlePrivateMessagePersisted(ChatMessage msg) {
        // 1. 新消息到达后，把双方曾经手动隐藏过的会话入口恢复出来。
        restoreConversationIfHidden(msg.getSenderId(), msg.getConversationId());
        restoreConversationIfHidden(msg.getRecvId(), msg.getConversationId());

        // 2. 只给接收方补送达回执和未读数，然后刷新会话摘要缓存。
        writeReceipt(msg.getId(), msg.getRecvId(), 1);
        stringRedisTemplate.opsForValue().increment(UNREAD_COUNT_KEY + msg.getRecvId() + ":" + msg.getConversationId());
        cacheLastMsg(msg);

        // 3. 最后再尽量做实时推送；离线用户则依赖未读数和历史消息分页来承接。
        chatWebSocketHandler.sendToUser(msg.getRecvId(), buildPrivatePush(msg));
    }

    /**
     * 团队消息落库后的收尾逻辑。
     *
     * @param msg       已落库团队消息
     * @param atUserIds @ 消息关联用户列表
     */
    private void handleTeamMessagePersisted(ChatMessage msg, List<Long> atUserIds) {
        // 1. 先查出当前团队内除发送者外的有效成员，后续回执、未读和推送都基于这份名单执行。
        List<Long> memberIds = listActiveTeamMemberIds(msg.getRecvId(), msg.getSenderId());
        restoreConversationIfHidden(msg.getSenderId(), msg.getConversationId());
        int currentMemberCount = memberIds.size() + 1;
        boolean largeTeam = isLargeTeamByMemberCount(currentMemberCount);
        refreshLargeTeamFlagCache(msg.getRecvId(), currentMemberCount);

        // 2. 小群仍保留逐成员送达回执；大群降级为“只维护未读和摘要”，避免回执写放大。
        if (!largeTeam) {
            writeTeamReceiptsToMembers(memberIds, msg.getId());
        }
        batchRestoreConversationAndIncrementUnread(memberIds, msg.getConversationId());

        // 3. 更新会话摘要缓存，并按群规模决定推送策略：
        //    小群继续实时下发完整正文；大群只发摘要事件，并用分批调度做平滑广播。
        cacheLastMsg(msg);
        if (largeTeam) {
            chatWebSocketHandler.sendToUsersSmoothly(memberIds, buildTeamSummaryPush(msg));
        } else {
            chatWebSocketHandler.sendToUsers(memberIds, buildTeamPush(msg));
        }

        // 4. 如果是 @ 消息，再额外异步补一份系统提醒，保证会话外也能看到提示。
        if (atUserIds != null && !atUserIds.isEmpty()) {
            sendAtNotices(atUserIds, msg.getRecvId(), msg.getSenderId(), msg.getId());
        }
    }

    /**
     * 恢复曾被手动隐藏的会话入口。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     */
    private void restoreConversationIfHidden(Long userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForSet().remove(HIDDEN_CONVERSATIONS_KEY + userId, conversationId);
    }

    /**
     * 缓存群公告内容。
     *
     * @param conversationId 会话 ID
     * @param notice         公告内容
     */
    public void cacheGroupNotice(String conversationId, String notice) {
        // 1. 群公告属于“低写高读”数据，直接放 Redis 可以显著降低回源频率。
        stringRedisTemplate.opsForValue().set(GROUP_NOTICE_KEY + conversationId, notice, GROUP_NOTICE_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 读取群公告缓存。
     *
     * @param conversationId 会话 ID
     * @return 公告内容；不存在时返回 null
     */
    public String getGroupNotice(String conversationId) {
        return stringRedisTemplate.opsForValue().get(GROUP_NOTICE_KEY + conversationId);
    }

    /**
     * 构建“最新历史页缓存”对应的 Redis Key。
     *
     * @param conversationId 会话 ID
     * @return Redis Key
     */
    public String buildLatestHistoryCacheKey(String conversationId) {
        return CHAT_LATEST_HISTORY_CACHE_KEY + conversationId;
    }

    /**
     * 清理某个会话的最新历史页缓存。
     * 只要会话内新增消息、编辑或撤回，最新页都有可能变化，因此统一在这些链路里失效。
     *
     * @param conversationId 会话 ID
     */
    public void evictLatestHistoryCache(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(buildLatestHistoryCacheKey(conversationId));
    }

    /**
     * 给被 @ 的团队成员发送系统提醒。
     *
     * @param atUserIds 被 @ 的用户 ID 列表
     * @param teamId    团队 ID
     * @param senderId  发送者用户 ID
     * @param msgId     触发提醒的消息 ID
     */
    public void sendAtNotices(List<Long> atUserIds, Long teamId, Long senderId, Long msgId) {
        // 1. 先查出发送者昵称和团队名，用来拼装通知文案。
        User sender = userMapper.selectById(senderId);
        String senderName = sender != null ? sender.getUserNickname() : String.valueOf(senderId);
        Team team = teamId == null ? null : teamMapper.selectById(teamId);
        String teamName = team != null && team.getTeamName() != null && !team.getTeamName().isBlank()
                ? team.getTeamName()
                : String.valueOf(teamId);

        // 2. 逐个异步投递系统通知，同时排除对自己的 @ 提醒。
        for (Long atUserId : atUserIds) {
            if (atUserId == null || atUserId.equals(senderId)) {
                continue;
            }
            asyncMessageService.sendSystemNotice(
                    atUserId,
                    9,
                    "你在团队【" + teamName + "】中被【" + senderName + "】@了",
                    msgId
            );
        }
    }

    /**
     * 发送举报处理相关的系统通知。
     *
     * @param userId   接收通知的用户 ID
     * @param reportId 举报单 ID
     * @param content  通知文案
     */
    public void sendReportNotice(Long userId, Long reportId, String content) {
        asyncMessageService.sendSystemNotice(userId, 7, content, reportId);
    }

    /**
     * 将 Long 类型主键转成字符串返回给前端。
     * 聊天消息和举报链路里的雪花 ID 会超过 JavaScript Number 安全范围，
     * 因此这里统一按字符串输出，避免浏览器端精度丢失后再提交回后端查不到记录。
     *
     * @param id 原始 Long 主键
     * @return 可直接透传给前端的字符串 ID；原值为空时返回 null
     */
    public String stringifyId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 会话访问校验结果载体。
     *
     * @param failed  是否校验失败
     * @param message 失败提示文案
     */
    public record ResultHolder(boolean failed, String message) {

        /**
         * 构建一个失败结果。
         *
         * @param message 失败提示文案
         * @return 失败结果对象
         */
        public static ResultHolder fail(String message) {
            return new ResultHolder(true, message);
        }
    }
}
