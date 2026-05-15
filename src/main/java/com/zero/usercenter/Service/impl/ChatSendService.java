package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.ChatHistoryPageDTO;
import com.zero.usercenter.DTO.PrivacySettingDTO;
import com.zero.usercenter.DTO.PrivateMsgDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.TeamMsgDTO;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.TeamMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserBlacklistMapper;
import com.zero.usercenter.Mapper.UserFriendMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.Team;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.Model.UserBlacklist;
import com.zero.usercenter.Model.UserFriend;
import com.zero.usercenter.mq.AsyncMessageService;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.utils.ChatMessageIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.CHAT_HISTORY_CACHE_PAGE_SIZE;
import static com.zero.usercenter.utils.Number.CHAT_HISTORY_RATE_LIMIT_KEY;
import static com.zero.usercenter.utils.Number.CHAT_HISTORY_RATE_LIMIT_MAX_REQUESTS;
import static com.zero.usercenter.utils.Number.CHAT_HISTORY_RATE_LIMIT_WINDOW_SECONDS;
import static com.zero.usercenter.utils.Number.CHAT_LATEST_HISTORY_CACHE_TTL_SECONDS;
import static com.zero.usercenter.utils.Number.TEAM_ALL_MUTE_KEY;
import static com.zero.usercenter.utils.Number.USER_PUNISH_CACHE_TTL_MINUTES;
import static com.zero.usercenter.utils.Number.USER_PUNISH_KEY;

/**
 * 聊天发送与历史查询服务。
 * 发送接口现在只负责同步鉴权、参数校验和快速入队，
 * 真正的落库、未读数更新和 WebSocket 推送由 MQ 消费端继续完成。
 */
@Service
public class ChatSendService {

    private static final int HISTORY_PAGE_SIZE_DEFAULT = 30;
    private static final int HISTORY_PAGE_SIZE_MAX = 30;

    @Resource
    private SensitiveWordBs sensitiveWordBs;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserBlacklistMapper userBlacklistMapper;

    @Resource
    private UserFriendMapper userFriendMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private ChatPendingMessageStateService chatPendingMessageStateService;

    @Resource
    private ChatMessageIdGenerator chatMessageIdGenerator;

    @Resource
    private AsyncMessageService asyncMessageService;

    /**
     * 发送私聊消息。
     *
     * @param dto 私聊消息 DTO
     * @return 立即返回的消息回执，包含真实 msgId
     */
    public Result sendPrivateMsg(PrivateMsgDTO dto) {
        // 1. 先校验登录态、接收方和消息体基础格式，避免非法数据进入发送链路。
        Long userId = chatSupportService.requireLogin();
        Long recipientId = dto.getRecipientId();
        if (recipientId == null || recipientId <= 0) {
            return Result.fail("接收方ID无效");
        }
        if (userId.equals(recipientId)) {
            return Result.fail("不能给自己发送消息");
        }
        if (dto.getMsgType() == null || dto.getMsgType() < 1 || dto.getMsgType() > 5) {
            return Result.fail("消息类型无效");
        }
        Result validateResult = validateMessagePayload(
                dto.getMsgType(),
                dto.getMsgContent(),
                dto.getFileUrl(),
                dto.getFileName(),
                dto.getEmojiId()
        );
        if (validateResult != null) {
            return validateResult;
        }

        // 2. 校验接收方状态、黑名单和私聊权限设置，防止越权或打扰式消息发送。
        User recipient = userMapper.selectById(recipientId);
        if (recipient == null || Integer.valueOf(1).equals(recipient.getIsDelete())) {
            return Result.fail("用户不存在");
        }
        if (isBlacklisted(recipientId, userId)) {
            return Result.fail("无法发送消息");
        }
        if (isBlacklisted(userId, recipientId)) {
            return Result.fail("当前好友已被拉黑，无法发送消息");
        }
        Result privacyResult = checkPrivateMessagePermission(userId, recipientId, recipient);
        if (privacyResult != null) {
            return privacyResult;
        }

        // 3. 最后补全全局禁言和文本敏感词校验，确保真正入队前的业务规则已经全部兜住。
        Result muteCheck = checkGlobalMute(userId);
        if (muteCheck != null) {
            return muteCheck;
        }
        if ((dto.getMsgType() == 1 || dto.getMsgType() == 5)
                && sensitiveWordBs.contains(dto.getMsgContent())) {
            return Result.fail("消息包含违规内容");
        }

        // 4. 校验通过后，先生成真实业务 msgId 和 createTime，再构建统一的待消费消息体。
        String conversationId = chatSupportService.buildConvId(userId, recipientId);
        String storedContent = chatSupportService.buildStoredContent(
                dto.getMsgType(),
                dto.getMsgContent(),
                dto.getFileUrl(),
                dto.getFileName(),
                dto.getFileSize(),
                dto.getMediaType(),
                dto.getEmojiId(),
                null
        );
        ChatSendMessage queueMessage = buildQueueMessage(
                userId,
                1,
                recipientId,
                conversationId,
                dto.getMsgType(),
                storedContent,
                Collections.emptyList()
        );

        // 5. 把消息元数据先缓存到 Redis，再投递到 RabbitMQ；只要成功入队，就立刻返回前端。
        return enqueueChatMessage(queueMessage, userId);
    }

    /**
     * 查询私聊历史记录。
     *
     * @param friendId    对端用户 ID
     * @param beforeMsgId 向前翻页游标
     * @param pageSize    期望分页大小
     * @return 历史消息游标分页结果
     */
    public Result getPrivateHistory(Long friendId, Long beforeMsgId, int pageSize) {
        // 1. 先校验登录态和对端用户 ID，确保游标查询发生在合法私聊会话内。
        Long userId = chatSupportService.requireLogin();
        if (friendId == null || friendId <= 0) {
            return Result.fail("好友ID无效");
        }
        String convId = chatSupportService.buildConvId(userId, friendId);

        // 2. 同一个用户短时间内频繁刷某个会话历史时直接限流，防止把私聊历史接口打成热点读风暴。
        Result rateLimitResult = checkHistoryReadRateLimit(userId, convId);
        if (rateLimitResult != null) {
            return rateLimitResult;
        }

        // 3. 私聊仍保持游标分页，但不需要启用大群的最新页热点缓存。
        return Result.ok(loadHistoryPage(convId, userId, beforeMsgId, pageSize, false));
    }

    /**
     * 发送团队消息。
     *
     * @param dto 团队消息 DTO
     * @return 立即返回的消息回执，包含真实 msgId
     */
    public Result sendTeamMsg(TeamMsgDTO dto) {
        // 1. 先校验团队 ID、消息类型和不同消息类型所需字段。
        Long userId = chatSupportService.requireLogin();
        Long teamId = dto.getTeamId();
        if (teamId == null || teamId <= 0) {
            return Result.fail("团队ID无效");
        }
        if (dto.getMsgType() == null || dto.getMsgType() < 1 || dto.getMsgType() > 5) {
            return Result.fail("消息类型无效");
        }
        Result validateResult = validateMessagePayload(
                dto.getMsgType(),
                dto.getMsgContent(),
                dto.getFileUrl(),
                dto.getFileName(),
                dto.getEmojiId()
        );
        if (validateResult != null) {
            return validateResult;
        }

        // 2. 团队消息必须通过团队存在性、成员资格和禁言状态校验。
        Team team = teamMapper.selectById(teamId);
        if (team == null || Integer.valueOf(1).equals(team.getIsDelete())) {
            return Result.fail("团队不存在");
        }
        TeamMember member = chatSupportService.getMember(teamId, userId);
        if (member == null) {
            return Result.fail("您不是该团队成员");
        }
        Result globalMute = checkGlobalMute(userId);
        if (globalMute != null) {
            return globalMute;
        }
        if (member.getRoleType() >= 3) {
            Result allMute = checkTeamAllMute(team);
            if (allMute != null) {
                return allMute;
            }
            Result memberMute = checkMemberMute(member);
            if (memberMute != null) {
                return memberMute;
            }
        }

        // 3. @ 消息额外校验被 @ 的用户都属于当前团队有效成员。
        List<Long> atUserIds = Collections.emptyList();
        if (dto.getMsgType() == 5 && dto.getAtUsers() != null && !dto.getAtUsers().isEmpty()) {
            atUserIds = dto.getAtUsers().stream().distinct().collect(Collectors.toList());
            LambdaQueryWrapper<TeamMember> atQw = new LambdaQueryWrapper<>();
            atQw.eq(TeamMember::getTeamId, teamId)
                    .eq(TeamMember::getIsQuit, 0)
                    .in(TeamMember::getUserId, atUserIds);
            long validCount = teamMemberMapper.selectCount(atQw);
            if (validCount != atUserIds.size()) {
                return Result.fail("存在非团队成员，无法@消息");
            }
        }

        // 4. 团队中的文本类消息同样要走敏感词过滤。
        if ((dto.getMsgType() == 1 || dto.getMsgType() == 5)
                && sensitiveWordBs.contains(dto.getMsgContent())) {
            return Result.fail("消息包含违规内容");
        }

        // 5. 构建统一的待消费消息体，后续由消费者继续完成落库、未读和群推送。
        String conversationId = "team_" + teamId;
        String storedContent = chatSupportService.buildStoredContent(
                dto.getMsgType(),
                dto.getMsgContent(),
                dto.getFileUrl(),
                dto.getFileName(),
                dto.getFileSize(),
                dto.getMediaType(),
                dto.getEmojiId(),
                atUserIds
        );
        ChatSendMessage queueMessage = buildQueueMessage(
                userId,
                2,
                teamId,
                conversationId,
                dto.getMsgType(),
                storedContent,
                atUserIds
        );

        // 6. 成功入队后立即返回，让高并发发送请求尽快从接口线程释放出去。
        return enqueueChatMessage(queueMessage, userId);
    }

    /**
     * 查询团队历史记录。
     *
     * @param teamId      团队 ID
     * @param beforeMsgId 向前翻页游标
     * @param pageSize    期望分页大小
     * @return 历史消息游标分页结果
     */
    public Result getTeamHistory(Long teamId, Long beforeMsgId, int pageSize) {
        // 1. 先校验登录态、团队 ID 和成员身份，防止越权读取群历史消息。
        Long userId = chatSupportService.requireLogin();
        if (teamId == null || teamId <= 0) {
            return Result.fail("团队ID无效");
        }
        if (chatSupportService.getMember(teamId, userId) == null) {
            return Result.fail("您不是该团队成员");
        }
        String convId = "team_" + teamId;

        // 2. 大群热点历史接口更容易被反复滚动触发，这里同样先做短窗口限流保护。
        Result rateLimitResult = checkHistoryReadRateLimit(userId, convId);
        if (rateLimitResult != null) {
            return rateLimitResult;
        }

        // 3. 只有大群会启用“最新页热点缓存”，普通群仍按游标直接查库返回。
        boolean largeTeam = chatSupportService.isLargeTeam(teamId);
        return Result.ok(loadHistoryPage(convId, userId, beforeMsgId, pageSize, largeTeam));
    }

    /**
     * 统一执行“缓存挂起状态 + MQ 入队 + 立即回包”。
     *
     * @param queueMessage  待消费聊天消息
     * @param currentUserId 当前用户 ID
     * @return 发送接口立即返回的消息数据
     */
    private Result enqueueChatMessage(ChatSendMessage queueMessage, Long currentUserId) {
        // 1. 先把挂起消息缓存进 Redis，这样在“已入队但未消费”的窗口里，撤回/编辑接口就能根据 msgId 找到它。
        chatPendingMessageStateService.cachePendingMessage(queueMessage);
        try {
            // 2. 真正投递到 RabbitMQ；只要成功入队，就立即把真实 msgId 返回给前端。
            asyncMessageService.sendChatMessage(queueMessage);
        } catch (Exception e) {
            // 3. 入队失败时要立刻清理 Redis 挂起态，避免留下无法消费的脏数据。
            chatPendingMessageStateService.clearPendingState(queueMessage.getMsgId());
            return Result.fail("消息发送失败，请稍后重试");
        }

        // 4. 返回前端的消息对象直接沿用最终会落库的业务主键和创建时间，保证后续撤回/编辑都基于同一个 msgId。
        ChatMessage ackMessage = chatSupportService.buildPersistMessage(queueMessage, null);
        return Result.ok(chatSupportService.buildMessageVo(ackMessage, currentUserId));
    }

    /**
     * 构建待消费聊天消息体。
     *
     * @param senderId       发送者 ID
     * @param recvType       接收类型
     * @param recvId         接收目标 ID
     * @param conversationId 会话 ID
     * @param msgType        消息类型
     * @param storedContent  存储内容
     * @param atUserIds      @ 目标列表
     * @return 待消费聊天消息体
     */
    private ChatSendMessage buildQueueMessage(Long senderId,
                                              Integer recvType,
                                              Long recvId,
                                              String conversationId,
                                              Integer msgType,
                                              String storedContent,
                                              List<Long> atUserIds) {
        // 1. 业务 msgId 和 createTime 在“入队前”就固定下来，保证响应前端和最终落库完全一致。
        return new ChatSendMessage(
                chatMessageIdGenerator.nextId(),
                senderId,
                recvType,
                recvId,
                conversationId,
                msgType,
                storedContent,
                LocalDateTime.now(),
                atUserIds == null ? Collections.emptyList() : atUserIds
        );
    }

    /**
     * 校验不同消息类型必填字段。
     *
     * @param msgType    消息类型
     * @param msgContent 文本内容
     * @param fileUrl    文件/图片 URL
     * @param fileName   文件名
     * @param emojiId    表情 ID
     * @return 失败结果；通过时返回 null
     */
    private Result validateMessagePayload(Integer msgType,
                                          String msgContent,
                                          String fileUrl,
                                          String fileName,
                                          String emojiId) {
        switch (msgType) {
            case 1, 5 -> {
                if (msgContent == null || msgContent.isBlank()) {
                    return Result.fail("消息内容不能为空");
                }
                if (msgContent.length() > 2000) {
                    return Result.fail("消息内容不能超过2000字符");
                }
            }
            case 2 -> {
                if (fileUrl == null || fileUrl.isBlank()) {
                    return Result.fail("图片URL不能为空");
                }
            }
            case 3 -> {
                if (fileUrl == null || fileUrl.isBlank()) {
                    return Result.fail("文件URL不能为空");
                }
                if (fileName == null || fileName.isBlank()) {
                    return Result.fail("文件名不能为空");
                }
            }
            case 4 -> {
                if (emojiId == null || emojiId.isBlank()) {
                    return Result.fail("表情标识不能为空");
                }
            }
            default -> {
                return Result.fail("消息类型无效");
            }
        }
        return null;
    }

    /**
     * 校验私聊发送权限。
     *
     * @param userId      当前用户 ID
     * @param recipientId 接收方用户 ID
     * @param recipient   接收方用户实体
     * @return 失败结果；通过时返回 null
     */
    private Result checkPrivateMessagePermission(Long userId, Long recipientId, User recipient) {
        String privacyStr = recipient.getPrivacySetting();
        if (privacyStr == null || privacyStr.isBlank()) {
            return null;
        }
        PrivacySettingDTO privacy = JSON.parseObject(privacyStr, PrivacySettingDTO.class);
        if (privacy == null || privacy.getSendMsg() == null) {
            return null;
        }
        if (privacy.getSendMsg() == 2 && !isInSameTeam(userId, recipientId)) {
            return Result.fail("对方仅接收团队成员消息");
        }
        if (privacy.getSendMsg() == 3 && !hasActiveFriendRelation(userId, recipientId)) {
            return Result.fail("需先成为好友才能发送消息");
        }
        return null;
    }

    /**
     * 按消息 ID 游标向前加载会话历史。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @param beforeMsgId    翻页游标
     * @param pageSize       期望分页大小
     * @param enableLatestPageCache 是否启用“最新一页热点缓存”
     * @return 历史消息分页结果
     */
    private ChatHistoryPageDTO loadHistoryPage(String conversationId,
                                               Long userId,
                                               Long beforeMsgId,
                                               int pageSize,
                                               boolean enableLatestPageCache) {
        // 1. 规范化分页参数，把单次查询控制在安全窗口内。
        int normalizedPageSize = normalizeHistoryPageSize(pageSize);
        boolean latestPageRequest = isLatestPageRequest(beforeMsgId);

        // 2. 大群最新一页优先读 Redis 热点缓存，避免大量用户同时点开同一个群时都直冲数据库。
        if (enableLatestPageCache && latestPageRequest) {
            ChatHistoryPageDTO cachedPage = loadLatestHistoryPageFromCache(conversationId, userId, normalizedPageSize);
            if (cachedPage != null) {
                return cachedPage;
            }
        }

        // 3. 缓存未命中时再回库；如果要构建热点缓存，就固定取 canonical 的 30 条最新消息。
        int queryPageSize = enableLatestPageCache && latestPageRequest
                ? CHAT_HISTORY_CACHE_PAGE_SIZE
                : normalizedPageSize;
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getIsDelete, 0);
        if (beforeMsgId != null && beforeMsgId > 0) {
            qw.lt(ChatMessage::getId, beforeMsgId);
        }
        qw.orderByDesc(ChatMessage::getId)
                .last("LIMIT " + (queryPageSize + 1));
        List<ChatMessage> rawMessages = chatMessageMapper.selectList(qw);

        // 4. 统一先构造成 canonical 历史页，后续无论是返回前端还是写缓存都复用这份结构。
        CachedConversationHistoryPage cachedPage = buildCachedHistoryPage(rawMessages, queryPageSize);

        // 5. 大群最新页只缓存 canonical 版本，后续 20~30 条请求都从这里切片复用。
        if (enableLatestPageCache && latestPageRequest) {
            cacheLatestHistoryPage(conversationId, cachedPage);
        }

        // 6. 最后再根据本次 pageSize 从 canonical 页中切出真正要返回的那一段消息。
        return buildHistoryPageResult(cachedPage, normalizedPageSize, userId);
    }

    /**
     * 规范化历史消息分页大小。
     *
     * @param pageSize 原始分页大小
     * @return 安全范围内的分页大小
     */
    private int normalizeHistoryPageSize(int pageSize) {
        if (pageSize <= 0) {
            return HISTORY_PAGE_SIZE_DEFAULT;
        }
        return Math.min(pageSize, HISTORY_PAGE_SIZE_MAX);
    }

    /**
     * 校验历史消息读取频率。
     * 同一个用户在短时间内反复刷同一个会话历史时，会直接命中限流，避免数据库和 Redis 被热 key 打穿。
     *
     * @param userId         当前用户 ID
     * @param conversationId 会话 ID
     * @return 命中限流时返回失败结果；正常返回 null
     */
    private Result checkHistoryReadRateLimit(Long userId, String conversationId) {
        // 1. 限流维度固定为“用户 + 会话”，尽量只拦住异常刷接口行为，不影响其他正常会话读取。
        String limitKey = CHAT_HISTORY_RATE_LIMIT_KEY + userId + ":" + conversationId;
        Long requestCount = stringRedisTemplate.opsForValue().increment(limitKey);
        if (requestCount != null && requestCount == 1L) {
            stringRedisTemplate.expire(limitKey, CHAT_HISTORY_RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        // 2. 一旦超过阈值就直接失败，强制客户端放缓翻页节奏，避免单用户把热点会话读链路拖垮。
        if (requestCount != null && requestCount > CHAT_HISTORY_RATE_LIMIT_MAX_REQUESTS) {
            return Result.fail("历史消息加载过于频繁，请稍后再试");
        }
        return null;
    }

    /**
     * 从 Redis 读取最新一页历史缓存。
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID
     * @param pageSize       当前请求分页大小
     * @return 命中时返回分页结果；未命中返回 null
     */
    private ChatHistoryPageDTO loadLatestHistoryPageFromCache(String conversationId, Long userId, int pageSize) {
        // 1. 只缓存“游标为空”的最新页，解决的是大群用户集中打开同一会话时的热点读问题。
        String cacheKey = chatSupportService.buildLatestHistoryCacheKey(conversationId);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson == null || cachedJson.isBlank()) {
            return null;
        }

        // 2. 反序列化失败时立即删除脏缓存，让下一次请求自动回库并重建。
        try {
            CachedConversationHistoryPage cachedPage = JSON.parseObject(cachedJson, CachedConversationHistoryPage.class);
            if (cachedPage == null) {
                stringRedisTemplate.delete(cacheKey);
                return null;
            }
            return buildHistoryPageResult(cachedPage, pageSize, userId);
        } catch (Exception e) {
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    /**
     * 缓存 canonical 最新页历史。
     *
     * @param conversationId 会话 ID
     * @param cachedPage     canonical 历史页
     */
    private void cacheLatestHistoryPage(String conversationId, CachedConversationHistoryPage cachedPage) {
        // 1. canonical 最新页保留最近 30 条消息，供 20~30 条请求直接切片复用。
        if (conversationId == null || conversationId.isBlank() || cachedPage == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                chatSupportService.buildLatestHistoryCacheKey(conversationId),
                JSON.toJSONString(cachedPage),
                CHAT_LATEST_HISTORY_CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 把数据库原始结果标准化成 canonical 历史页。
     *
     * @param rawMessages 原始查询结果，顺序为新到旧
     * @param pageSize    canonical 页大小
     * @return 标准化后的历史页
     */
    private CachedConversationHistoryPage buildCachedHistoryPage(List<ChatMessage> rawMessages, int pageSize) {
        // 1. 多取出来的那一条只用来判断“是否还有更早页”，不会直接返回给前端。
        boolean hasMore = rawMessages.size() > pageSize;
        List<ChatMessage> pageMessages = hasMore
                ? new ArrayList<>(rawMessages.subList(0, pageSize))
                : new ArrayList<>(rawMessages);

        // 2. canonical 页统一转成“旧到新”，这样前端渲染和缓存切片都能复用同一份顺序。
        pageMessages.sort(Comparator.comparing(ChatMessage::getId));
        return new CachedConversationHistoryPage(pageMessages, hasMore);
    }

    /**
     * 从 canonical 历史页中切出本次请求真正需要返回的消息段。
     *
     * @param cachedPage canonical 历史页
     * @param pageSize   当前请求分页大小
     * @param userId     当前用户 ID
     * @return 最终返回给前端的分页结果
     */
    private ChatHistoryPageDTO buildHistoryPageResult(CachedConversationHistoryPage cachedPage, int pageSize, Long userId) {
        // 1. canonical 页里可能保存了 30 条，但当前请求只想拿 20 条，所以这里统一从尾部取“最新的一段”。
        List<ChatMessage> cachedMessages = cachedPage == null || cachedPage.getRecords() == null
                ? Collections.emptyList()
                : cachedPage.getRecords();
        if (cachedMessages.isEmpty()) {
            return new ChatHistoryPageDTO(Collections.emptyList(), null, false);
        }

        int actualPageSize = Math.min(pageSize, cachedMessages.size());
        List<ChatMessage> pageMessages = new ArrayList<>(
                cachedMessages.subList(cachedMessages.size() - actualPageSize, cachedMessages.size())
        );

        // 2. 只要 canonical 页中还有未返回的更早消息，或者数据库明确告诉我们还有更早页，就继续返回 hasMore。
        boolean hasMore = cachedMessages.size() > actualPageSize || Boolean.TRUE.equals(cachedPage.getHasMore());
        Long nextCursor = hasMore && !pageMessages.isEmpty() ? pageMessages.get(0).getId() : null;
        return new ChatHistoryPageDTO(chatSupportService.buildVoList(pageMessages, userId), nextCursor, hasMore);
    }

    /**
     * 判断当前请求是否在加载“最新一页”。
     *
     * @param beforeMsgId 翻页游标
     * @return true-拉最新页，false-继续向前翻历史
     */
    private boolean isLatestPageRequest(Long beforeMsgId) {
        return beforeMsgId == null || beforeMsgId <= 0;
    }

    /**
     * 判断单向黑名单关系。
     *
     * @param userId       拉黑方用户 ID
     * @param targetUserId 被拉黑方用户 ID
     * @return true-已拉黑，false-未拉黑
     */
    private boolean isBlacklisted(Long userId, Long targetUserId) {
        LambdaQueryWrapper<UserBlacklist> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlackUserId, targetUserId)
                .eq(UserBlacklist::getIsDelete, 0);
        return userBlacklistMapper.selectOne(queryWrapper) != null;
    }

    /**
     * 判断双方是否存在有效好友关系。
     *
     * @param userId      当前用户 ID
     * @param recipientId 对端用户 ID
     * @return true-存在有效好友关系，false-不存在
     */
    private boolean hasActiveFriendRelation(Long userId, Long recipientId) {
        if (userId == null || recipientId == null) {
            return false;
        }
        LambdaQueryWrapper<UserFriend> friendQuery = new LambdaQueryWrapper<>();
        friendQuery.eq(UserFriend::getFriendStatus, 1)
                .and(wrapper -> wrapper
                        .and(w -> w.eq(UserFriend::getUserId, userId).eq(UserFriend::getFriendId, recipientId))
                        .or(w -> w.eq(UserFriend::getUserId, recipientId).eq(UserFriend::getFriendId, userId)));
        return userFriendMapper.selectCount(friendQuery) > 0;
    }

    /**
     * 判断双方是否属于至少一个共同团队。
     *
     * @param userId      当前用户 ID
     * @param recipientId 对端用户 ID
     * @return true-同团队，false-不同团队
     */
    private boolean isInSameTeam(Long userId, Long recipientId) {
        LambdaQueryWrapper<TeamMember> myTeamsQuery = new LambdaQueryWrapper<>();
        myTeamsQuery.eq(TeamMember::getUserId, userId).eq(TeamMember::getIsQuit, 0);
        List<Long> myTeamIds = teamMemberMapper.selectList(myTeamsQuery).stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toList());
        if (myTeamIds.isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<TeamMember> targetTeamsQuery = new LambdaQueryWrapper<>();
        targetTeamsQuery.eq(TeamMember::getUserId, recipientId)
                .eq(TeamMember::getIsQuit, 0)
                .in(TeamMember::getTeamId, myTeamIds);
        return teamMemberMapper.selectCount(targetTeamsQuery) > 0;
    }

    /**
     * 校验全局禁言或封禁状态。
     *
     * @param userId 当前用户 ID
     * @return 命中限制时返回失败结果；正常可发言时返回 null
     */
    private Result checkGlobalMute(Long userId) {
        String cacheKey = USER_PUNISH_KEY + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("0".equals(cached)) {
                return null;
            }
            String[] parts = cached.split("\\|");
            int punishType = Integer.parseInt(parts[0]);
            if (punishType == 1) {
                if (parts.length < 2) {
                    return Result.fail("您已被全局禁言");
                }
                LocalDateTime unpunishTime = LocalDateTime.parse(parts[1]);
                if (unpunishTime.isAfter(LocalDateTime.now())) {
                    return Result.fail("您已被全局禁言");
                }
                stringRedisTemplate.delete(cacheKey);
            } else if (punishType == 2) {
                return Result.fail("账号已被封禁");
            }
            return null;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        long ttl = USER_PUNISH_CACHE_TTL_MINUTES + ThreadLocalRandom.current().nextLong(-1, 2);
        if (user.getGlobalPunishType() == null || user.getGlobalPunishType() == 0) {
            stringRedisTemplate.opsForValue().set(cacheKey, "0", ttl, TimeUnit.MINUTES);
            return null;
        } else if (user.getGlobalPunishType() == 1) {
            String val = "1|" + (user.getGlobalUnpunishTime() != null ? user.getGlobalUnpunishTime().toString() : "");
            stringRedisTemplate.opsForValue().set(cacheKey, val, ttl, TimeUnit.MINUTES);
            if (user.getGlobalUnpunishTime() == null || user.getGlobalUnpunishTime().isAfter(LocalDateTime.now())) {
                return Result.fail("您已被全局禁言");
            }
        } else if (user.getGlobalPunishType() == 2) {
            stringRedisTemplate.opsForValue().set(cacheKey, "2", ttl, TimeUnit.MINUTES);
            return Result.fail("账号已被封禁");
        }
        return null;
    }

    /**
     * 校验团队是否处于全员禁言中。
     *
     * @param team 团队实体
     * @return 命中限制时返回失败结果；正常可发言时返回 null
     */
    private Result checkTeamAllMute(Team team) {
        String cached = stringRedisTemplate.opsForValue().get(TEAM_ALL_MUTE_KEY + team.getId());
        int muteVal;
        LocalDateTime unpunishTime = team.getTeamAllMuteUnpunishTime();
        if (cached != null && cached.contains("|")) {
            String[] parts = cached.split("\\|", 2);
            muteVal = Integer.parseInt(parts[0]);
            if (parts.length > 1 && parts[1] != null && !parts[1].isBlank()) {
                unpunishTime = LocalDateTime.parse(parts[1]);
            }
        } else {
            muteVal = cached != null ? Integer.parseInt(cached) : team.getTeamAllMute();
        }
        if (muteVal == 1 && unpunishTime != null && !unpunishTime.isAfter(LocalDateTime.now())) {
            stringRedisTemplate.delete(TEAM_ALL_MUTE_KEY + team.getId());
            return null;
        }
        if (muteVal == 1) {
            return Result.fail("当前团队已开启全员禁言");
        }
        return null;
    }

    /**
     * 校验当前成员是否仍处于团队禁言中。
     *
     * @param member 团队成员记录
     * @return 命中限制时返回失败结果；正常可发言时返回 null
     */
    private Result checkMemberMute(TeamMember member) {
        if (member.getTeamMuteType() == 1) {
            if (member.getTeamMuteUnpunishTime() == null
                    || member.getTeamMuteUnpunishTime().isAfter(LocalDateTime.now())) {
                return Result.fail("您已被禁言");
            }
        }
        return null;
    }

    /**
     * Redis 中缓存的 canonical 历史页。
     * records 固定按“旧到新”排序，hasMore 表示数据库中是否还存在更早的一页。
     */
    private static class CachedConversationHistoryPage {
        private List<ChatMessage> records;
        private Boolean hasMore;

        public CachedConversationHistoryPage() {
        }

        public CachedConversationHistoryPage(List<ChatMessage> records, Boolean hasMore) {
            this.records = records;
            this.hasMore = hasMore;
        }

        public List<ChatMessage> getRecords() {
            return records;
        }

        public void setRecords(List<ChatMessage> records) {
            this.records = records;
        }

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }
    }
}
