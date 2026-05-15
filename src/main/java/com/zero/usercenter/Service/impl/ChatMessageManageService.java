package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.MessageCollectDTO;
import com.zero.usercenter.DTO.MessagePinDTO;
import com.zero.usercenter.DTO.MsgEditDTO;
import com.zero.usercenter.DTO.MsgRevokeDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.MessageCollectionMapper;
import com.zero.usercenter.Mapper.MessagePinMapper;
import com.zero.usercenter.Mapper.MessageReportMapper;
import com.zero.usercenter.Mapper.ReportCaseMapper;
import com.zero.usercenter.Mapper.ReportDetailMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.MessageCollection;
import com.zero.usercenter.Model.MessagePin;
import com.zero.usercenter.Model.MessageReport;
import com.zero.usercenter.Model.ReportCase;
import com.zero.usercenter.Model.ReportDetail;
import com.zero.usercenter.aop.annotation.RequireTeamConversationRole;
import com.zero.usercenter.aop.annotation.TeamRoleScope;
import com.zero.usercenter.mq.message.ChatSendMessage;
import com.zero.usercenter.mq.message.PendingChatOperation;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 聊天消息管理服务。
 * 负责消息编辑、撤回、收藏、置顶、会话内搜索等能力，
 * 同时兼容两类消息状态：
 * 1. 已经完成 MQ 消费、已经落库的正式消息；
 * 2. 已经响应前端、但仍在 MQ 队列中等待消费的挂起消息。
 */
@Service
public class ChatMessageManageService {

    private static final int REPORT_TYPE_MESSAGE = 2;

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private ChatPendingMessageStateService chatPendingMessageStateService;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private MessageCollectionMapper messageCollectionMapper;

    @Resource
    private MessagePinMapper messagePinMapper;

    @Resource
    private MessageReportMapper messageReportMapper;

    @Resource
    private ReportCaseMapper reportCaseMapper;

    @Resource
    private ReportDetailMapper reportDetailMapper;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private SensitiveWordBs sensitiveWordBs;

    /**
     * 编辑消息。
     * 支持两种路径：
     * 1. 消息已落库：直接更新数据库，并推送 message_edit 事件；
     * 2. 消息仍在 MQ 排队：只在 Redis 记录“挂起编辑”，等消费者落库时再应用最新内容。
     *
     * @param dto 编辑请求参数，包含消息 ID 和新内容
     * @return 编辑结果
     */
    @Transactional
    public Result editMsg(MsgEditDTO dto) {
        // 1. 先校验登录态和基础参数，避免无效编辑请求进入后续消息状态分支。
        Long userId = chatSupportService.requireLogin();
        if (dto == null || dto.getMsgId() == null) {
            return Result.fail("消息ID不能为空");
        }
        if (dto.getNewContent() == null || dto.getNewContent().isBlank()) {
            return Result.fail("编辑内容不能为空");
        }

        String trimmedContent = dto.getNewContent().trim();
        if (trimmedContent.length() > 2000) {
            return Result.fail("编辑内容不能超过2000字符");
        }
        if (sensitiveWordBs.contains(trimmedContent)) {
            return Result.fail("消息包含违规内容");
        }

        // 2. 优先按“已落库消息”处理；这是最常见路径，也能覆盖真正已经被其他用户看到的消息。
        ChatMessage persistedMessage = loadActiveMessage(dto.getMsgId());
        if (persistedMessage != null) {
            return editPersistedMessage(userId, persistedMessage, trimmedContent);
        }

        // 3. 数据库里查不到时，再按“已入队但未消费”的挂起消息处理，避免和 MQ 排队窗口打架。
        ChatSendMessage pendingMessage = chatPendingMessageStateService.getPendingMessage(dto.getMsgId());
        if (pendingMessage != null) {
            return editPendingMessage(userId, pendingMessage, trimmedContent);
        }

        // 4. 两条路径都找不到，说明消息既不在库里也不在挂起态，统一返回不存在。
        return Result.fail("消息不存在");
    }

    /**
     * 撤回消息。
     * 支持两种路径：
     * 1. 消息已落库：改为软撤回，保留消息主体和时间线位置；
     * 2. 消息仍在 MQ 排队：只记录挂起撤回，消费者读到后直接丢弃。
     *
     * @param dto 撤回请求参数
     * @return 撤回结果
     */
    @Transactional
    public Result revokeMsg(MsgRevokeDTO dto) {
        // 1. 先校验登录态和消息 ID，避免空请求误触发删除/撤回逻辑。
        Long userId = chatSupportService.requireLogin();
        if (dto == null || dto.getMsgId() == null) {
            return Result.fail("消息ID不能为空");
        }

        // 2. 优先处理已落库消息；如果消息已经被消费，就必须走数据库软撤回和实时通知。
        ChatMessage persistedMessage = loadActiveMessage(dto.getMsgId());
        if (persistedMessage != null) {
            return revokePersistedMessage(userId, persistedMessage);
        }

        // 3. 数据库中不存在时，再尝试处理挂起消息，避免对 MQ 队列做不可控的直接删除。
        ChatSendMessage pendingMessage = chatPendingMessageStateService.getPendingMessage(dto.getMsgId());
        if (pendingMessage != null) {
            return revokePendingMessage(userId, pendingMessage);
        }

        // 4. 两边都没有时统一返回不存在，避免把内部状态暴露给外部。
        return Result.fail("消息不存在");
    }

    /**
     * 收藏消息。
     *
     * @param dto 收藏请求参数
     * @return 收藏结果
     */
    @Transactional
    public Result collectMsg(MessageCollectDTO dto) {
        // 1. 收藏功能只允许针对真实存在、且当前未撤回的正式消息执行。
        Long userId = chatSupportService.requireLogin();
        if (dto == null || dto.getMessageId() == null) {
            return Result.fail("消息ID不能为空");
        }

        ChatMessage msg = loadActiveMessage(dto.getMessageId());
        if (msg == null) {
            return Result.fail("消息不存在");
        }
        if (Integer.valueOf(1).equals(msg.getIsRevoke())) {
            return Result.fail("已撤回消息不能收藏");
        }

        // 2. 先校验是否已经收藏过，避免同一用户对同一消息重复创建记录。
        LambdaQueryWrapper<MessageCollection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessageCollection::getUserId, userId)
                .eq(MessageCollection::getMessageId, dto.getMessageId())
                .eq(MessageCollection::getIsDelete, 0);
        if (messageCollectionMapper.selectOne(queryWrapper) != null) {
            return Result.fail("该消息已收藏");
        }

        // 3. 校验通过后插入收藏记录，并返回收藏记录 ID，方便前端后续取消收藏。
        MessageCollection collection = new MessageCollection();
        collection.setUserId(userId);
        collection.setMessageId(dto.getMessageId());
        collection.setCollectionNote(dto.getCollectionNote() != null ? dto.getCollectionNote().trim() : "");
        collection.setIsDelete(0);
        messageCollectionMapper.insert(collection);
        return Result.ok(collection.getId());
    }

    /**
     * 取消收藏。
     *
     * @param collectionId 收藏记录 ID
     * @return 取消结果
     */
    @Transactional
    public Result cancelCollect(Long collectionId) {
        // 1. 取消收藏采用软删除，既能保留历史痕迹，也不会影响唯一索引设计。
        Long userId = chatSupportService.requireLogin();
        if (collectionId == null) {
            return Result.fail("收藏ID不能为空");
        }

        // 2. 只允许收藏者本人取消，且只处理当前仍生效的收藏记录。
        LambdaUpdateWrapper<MessageCollection> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MessageCollection::getId, collectionId)
                .eq(MessageCollection::getUserId, userId)
                .eq(MessageCollection::getIsDelete, 0)
                .set(MessageCollection::getIsDelete, 1);
        int rows = messageCollectionMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("收藏记录不存在");
        }
        return Result.ok("已取消收藏");
    }

    /**
     * 获取收藏列表。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 收藏分页结果
     */
    public Result getCollections(int page, int pageSize) {
        // 1. 先分页查询收藏记录，再回查消息主体，避免一次性扫描过多消息内容。
        Long userId = chatSupportService.requireLogin();
        Page<MessageCollection> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<MessageCollection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessageCollection::getUserId, userId)
                .eq(MessageCollection::getIsDelete, 0)
                .orderByDesc(MessageCollection::getCreateTime);
        Page<MessageCollection> result = messageCollectionMapper.selectPage(pageObj, queryWrapper);

        // 2. 收藏列表对撤回消息统一展示“消息已撤回”，避免继续暴露原文内容。
        List<Map<String, Object>> list = result.getRecords().stream().map(collection -> {
            ChatMessage message = loadActiveMessage(collection.getMessageId());
            Map<String, Object> item = new HashMap<>();
            item.put("collectionId", collection.getId());
            item.put("messageId", chatSupportService.stringifyId(collection.getMessageId()));
            item.put("collectionNote", collection.getCollectionNote());
            item.put("collectionTime", collection.getCreateTime());
            item.put("msgType", message != null ? message.getMsgType() : null);
            item.put("senderId", message != null ? message.getSenderId() : null);
            item.put("msgContent", buildDisplayContent(message));
            item.put("isRevoke", message != null && Integer.valueOf(1).equals(message.getIsRevoke()));
            return item;
        }).collect(Collectors.toList());

        // 3. 返回收藏列表和总数，供前端继续做分页滚动。
        return Result.ok(list, result.getTotal());
    }

    /**
     * 置顶消息。
     * 团队会话下，权限由 AOP 统一兜底校验。
     *
     * @param dto 置顶请求参数
     * @return 置顶结果
     */
    @Transactional
    @RequireTeamConversationRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            conversationId = "#p0.conversationId",
            requiredTeamConversation = false,
            forbiddenMessage = "仅队长/管理员可置顶消息")
    public Result pinMsg(MessagePinDTO dto) {
        // 1. 先校验登录态、会话 ID 和消息 ID；团队会话权限由切面提前兜底。
        Long userId = chatSupportService.requireLogin();
        if (dto == null || dto.getConversationId() == null || dto.getMessageId() == null) {
            return Result.fail("参数不能为空");
        }

        ChatMessage msg = loadActiveMessage(dto.getMessageId());
        if (msg == null) {
            return Result.fail("消息不存在");
        }
        if (Integer.valueOf(1).equals(msg.getIsRevoke())) {
            return Result.fail("已撤回消息不能置顶");
        }
        if (!dto.getConversationId().equals(msg.getConversationId())) {
            return Result.fail("会话与消息不匹配");
        }

        // 2. 先检查是否已经存在有效置顶记录，避免重复置顶同一条消息。
        LambdaQueryWrapper<MessagePin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessagePin::getConversationId, dto.getConversationId())
                .eq(MessagePin::getMessageId, dto.getMessageId())
                .eq(MessagePin::getIsDelete, 0);
        if (messagePinMapper.selectOne(queryWrapper) != null) {
            return Result.fail("该消息已置顶");
        }

        // 3. 读取当前会话最大的 pinOrder，给新置顶消息分配新的顺序值。
        LambdaQueryWrapper<MessagePin> maxQuery = new LambdaQueryWrapper<>();
        maxQuery.eq(MessagePin::getConversationId, dto.getConversationId())
                .eq(MessagePin::getIsDelete, 0)
                .orderByDesc(MessagePin::getPinOrder)
                .last("LIMIT 1");
        MessagePin last = messagePinMapper.selectOne(maxQuery);
        int nextOrder = last == null || last.getPinOrder() == null ? 1 : last.getPinOrder() + 1;

        // 4. 写入置顶记录，返回 pinId 给前端用于后续取消置顶。
        MessagePin pin = new MessagePin();
        pin.setConversationId(dto.getConversationId());
        pin.setMessageId(dto.getMessageId());
        pin.setPinUserId(userId);
        pin.setPinOrder(nextOrder);
        pin.setIsDelete(0);
        messagePinMapper.insert(pin);
        return Result.ok(pin.getId());
    }

    /**
     * 取消置顶。
     *
     * @param pinId 置顶记录 ID
     * @return 取消结果
     */
    @Transactional
    public Result unpinMsg(Long pinId) {
        // 1. 取消置顶采用软删除，只影响当前会话展示，不破坏历史记录。
        Long userId = chatSupportService.requireLogin();
        if (pinId == null) {
            return Result.fail("置顶ID不能为空");
        }

        // 2. 只允许置顶操作发起人本人取消，避免普通成员撤销他人置顶。
        LambdaUpdateWrapper<MessagePin> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MessagePin::getId, pinId)
                .eq(MessagePin::getPinUserId, userId)
                .eq(MessagePin::getIsDelete, 0)
                .set(MessagePin::getIsDelete, 1);
        int rows = messagePinMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("置顶记录不存在");
        }
        return Result.ok("已取消置顶");
    }

    /**
     * 获取指定会话的置顶列表。
     *
     * @param conversationId 会话 ID
     * @return 置顶消息列表
     */
    public Result getPinList(String conversationId) {
        // 1. 没有会话 ID 就无法定位置顶范围，因此这里先做参数校验。
        if (conversationId == null || conversationId.isBlank()) {
            return Result.fail("会话ID不能为空");
        }

        // 2. 按 pinOrder 升序读取，保证前端展示顺序稳定。
        LambdaQueryWrapper<MessagePin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessagePin::getConversationId, conversationId)
                .eq(MessagePin::getIsDelete, 0)
                .orderByAsc(MessagePin::getPinOrder);
        List<MessagePin> pins = messagePinMapper.selectList(queryWrapper);

        // 3. 逐条补齐消息摘要；对于撤回消息，同样统一展示占位文案。
        List<Map<String, Object>> list = pins.stream().map(pin -> {
            ChatMessage message = loadActiveMessage(pin.getMessageId());
            Map<String, Object> item = new HashMap<>();
            item.put("pinId", pin.getId());
            item.put("messageId", chatSupportService.stringifyId(pin.getMessageId()));
            item.put("pinOrder", pin.getPinOrder());
            item.put("pinTime", pin.getCreateTime());
            item.put("msgType", message != null ? message.getMsgType() : null);
            item.put("msgContent", buildDisplayContent(message));
            item.put("isRevoke", message != null && Integer.valueOf(1).equals(message.getIsRevoke()));
            return item;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 会话内搜索消息。
     *
     * @param conversationId 会话 ID
     * @param keyword        搜索关键字
     * @param page           页码
     * @param pageSize       每页条数
     * @return 搜索结果
     */
    public Result searchMsg(String conversationId, String keyword, int page, int pageSize) {
        // 1. 先校验登录态和查询参数，确保搜索发生在一个明确的会话范围内。
        Long userId = chatSupportService.requireLogin();
        if (conversationId == null || conversationId.isBlank()) {
            return Result.fail("会话ID不能为空");
        }
        if (keyword == null || keyword.isBlank()) {
            return Result.fail("关键字不能为空");
        }

        // 2. 搜索只针对“未删除且未撤回”的消息，避免用户通过搜索重新看到已撤回原文。
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getIsDelete, 0)
                .eq(ChatMessage::getIsRevoke, 0)
                .orderByDesc(ChatMessage::getCreateTime)
                .orderByDesc(ChatMessage::getId);
        List<ChatMessage> messages = chatMessageMapper.selectList(queryWrapper);

        // 3. 统一通过聊天支撑服务提取可搜索文本，兼容文本、图片说明、文件名等结构化消息。
        String search = keyword.trim().toLowerCase();
        List<ChatMessage> matched = messages.stream()
                .filter(msg -> chatSupportService.extractSearchableText(msg.getMsgType(), msg.getMsgContent())
                        .toLowerCase()
                        .contains(search))
                .collect(Collectors.toList());

        // 4. 过滤完成后再做手动分页，并统一转成消息 VO，保持返回结构一致。
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, matched.size());
        int toIndex = Math.min(fromIndex + safePageSize, matched.size());
        List<ChatMessage> pageList = matched.subList(fromIndex, toIndex);
        return Result.ok(chatSupportService.buildVoList(pageList, userId), Long.valueOf(matched.size()));
    }

    /**
     * 编辑已落库消息。
     *
     * @param userId         当前用户 ID
     * @param persistedMsg   已落库消息
     * @param trimmedContent 编辑后的内容
     * @return 编辑结果
     */
    private Result editPersistedMessage(Long userId, ChatMessage persistedMsg, String trimmedContent) {
        // 1. 先校验归属、撤回态、消息类型和 5 分钟窗口，确保只有发送者本人能编辑可编辑文本消息。
        if (!Objects.equals(persistedMsg.getSenderId(), userId)) {
            return Result.fail("只能编辑自己发送的消息");
        }
        if (Integer.valueOf(1).equals(persistedMsg.getIsRevoke())) {
            return Result.fail("已撤回消息不能编辑");
        }
        if (!chatSupportService.supportsMessageEdit(persistedMsg.getMsgType())) {
            return Result.fail("当前消息类型不支持编辑");
        }
        if (!chatSupportService.isWithinMessageOperateWindow(persistedMsg.getCreateTime())) {
            return Result.fail("消息已过期，无法编辑");
        }

        // 2. 更新数据库中的消息内容、编辑状态和编辑次数，保留完整编辑痕迹。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ChatMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatMessage::getId, persistedMsg.getId())
                .eq(ChatMessage::getIsDelete, 0)
                .eq(ChatMessage::getIsRevoke, 0)
                .set(ChatMessage::getMsgContent, trimmedContent)
                .set(ChatMessage::getIsEdited, 1)
                .set(ChatMessage::getEditTime, now)
                .setSql("edit_count = IFNULL(edit_count,0) + 1");
        int rows = chatMessageMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("消息状态已变化，请稍后重试");
        }

        // 3. 重新读取最新消息实体并刷新会话摘要缓存，防止会话列表仍展示旧内容。
        ChatMessage updated = loadActiveMessage(persistedMsg.getId());
        if (updated == null) {
            return Result.fail("消息不存在");
        }
        chatSupportService.refreshConversationLastMessageCache(updated.getConversationId());

        // 4. 最后推送 message_edit 事件，让对端或群成员实时刷新这条消息。
        pushEditEvent(updated);
        return Result.ok("消息已编辑");
    }

    /**
     * 编辑挂起消息。
     *
     * @param userId         当前用户 ID
     * @param pendingMessage 仍在 MQ 队列中的消息
     * @param trimmedContent 编辑后的内容
     * @return 编辑结果
     */
    private Result editPendingMessage(Long userId, ChatSendMessage pendingMessage, String trimmedContent) {
        // 1. 挂起编辑同样要校验发送者、消息类型和 5 分钟窗口，保持和已落库消息一致的业务规则。
        if (!Objects.equals(pendingMessage.getSenderId(), userId)) {
            return Result.fail("只能编辑自己发送的消息");
        }
        if (!chatSupportService.supportsMessageEdit(pendingMessage.getMsgType())) {
            return Result.fail("当前消息类型不支持编辑");
        }
        if (!chatSupportService.isWithinMessageOperateWindow(pendingMessage.getCreateTime())) {
            return Result.fail("消息已过期，无法编辑");
        }

        // 2. 如果这条挂起消息已经被标记为撤回，就不允许再继续编辑，避免两种操作冲突。
        PendingChatOperation operation = chatPendingMessageStateService.getPendingOperation(pendingMessage.getMsgId());
        if (operation != null && PendingChatOperation.TYPE_REVOKE.equals(operation.getOperationType())) {
            return Result.fail("消息已撤回，无法编辑");
        }

        // 3. 不直接碰 MQ 队列，只在 Redis 记录最新的挂起编辑内容，等待消费者落库时应用。
        chatPendingMessageStateService.savePendingEdit(
                pendingMessage.getMsgId(),
                trimmedContent,
                LocalDateTime.now()
        );
        return Result.ok("消息已编辑");
    }

    /**
     * 撤回已落库消息。
     *
     * @param userId       当前用户 ID
     * @param persistedMsg 已落库消息
     * @return 撤回结果
     */
    private Result revokePersistedMessage(Long userId, ChatMessage persistedMsg) {
        // 1. 先校验发送者本人、撤回窗口和举报处理中状态，避免破坏后续审核上下文。
        if (!Objects.equals(persistedMsg.getSenderId(), userId)) {
            return Result.fail("只能撤回自己发送的消息");
        }
        if (Integer.valueOf(1).equals(persistedMsg.getIsRevoke())) {
            return Result.fail("消息已撤回");
        }
        if (!chatSupportService.isWithinMessageOperateWindow(persistedMsg.getCreateTime())) {
            return Result.fail("消息已过期，无法撤回");
        }
        if (hasProcessingMessageReportCase(persistedMsg.getId())) {
            return Result.fail("消息正在审核中，暂时无法撤回");
        }

        // 2. 撤回改为软撤回：保留消息位置和关联关系，只修改撤回状态和撤回时间。
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ChatMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatMessage::getId, persistedMsg.getId())
                .eq(ChatMessage::getIsDelete, 0)
                .eq(ChatMessage::getIsRevoke, 0)
                .set(ChatMessage::getIsRevoke, 1)
                .set(ChatMessage::getRevokeTime, now);
        int rows = chatMessageMapper.update(null, updateWrapper);
        if (rows == 0) {
            return Result.fail("消息状态已变化，请稍后重试");
        }

        // 3. 重新读取最新消息状态并刷新会话摘要，确保最近会话和未读摘要都展示“消息已撤回”。
        ChatMessage revoked = loadActiveMessage(persistedMsg.getId());
        if (revoked == null) {
            return Result.fail("消息不存在");
        }
        chatSupportService.refreshConversationLastMessageCache(revoked.getConversationId());

        // 4. 最后再走 WebSocket 广播撤回事件，让在线用户同步把这条消息切换成撤回态。
        pushRevokeEvent(revoked);
        return Result.ok("消息已撤回");
    }

    /**
     * 撤回挂起消息。
     *
     * @param userId         当前用户 ID
     * @param pendingMessage 仍在 MQ 队列中的消息
     * @return 撤回结果
     */
    private Result revokePendingMessage(Long userId, ChatSendMessage pendingMessage) {
        // 1. 挂起撤回也要校验发送者本人和 5 分钟窗口，保证挂起态和落库态规则一致。
        if (!Objects.equals(pendingMessage.getSenderId(), userId)) {
            return Result.fail("只能撤回自己发送的消息");
        }
        if (!chatSupportService.isWithinMessageOperateWindow(pendingMessage.getCreateTime())) {
            return Result.fail("消息已过期，无法撤回");
        }

        // 2. 不直接删除队列消息，只在 Redis 打挂起撤回标记，等待消费者读到后主动丢弃。
        chatPendingMessageStateService.savePendingRevoke(pendingMessage.getMsgId(), LocalDateTime.now());
        return Result.ok("消息已撤回");
    }

    /**
     * 推送消息编辑事件。
     *
     * @param message 最新消息实体
     */
    private void pushEditEvent(ChatMessage message) {
        // 1. 统一组装 message_edit 载荷，让前端按 msgId 定位并替换现有消息内容。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "message_edit");

        Map<String, Object> data = new HashMap<>();
        data.put("msgId", chatSupportService.stringifyId(message.getId()));
        data.put("conversationId", message.getConversationId());
        data.put("content", message.getMsgContent());
        data.put("editCount", message.getEditCount());
        data.put("editTime", message.getEditTime());
        data.put("isEdited", true);
        push.put("data", data);

        String json = JSON.toJSONString(push);

        // 2. 私聊只通知对端，群聊则广播给除发送者外的其他成员，保持和发送链路一致。
        if (message.getRecvType() == 1) {
            chatWebSocketHandler.sendToUser(message.getRecvId(), json);
        } else {
            // 3. 大群编辑时不再把完整编辑事件广播给所有在线成员，而是降级成摘要刷新，避免再次触发广播风暴。
            if (chatSupportService.isLargeTeam(message.getRecvId())) {
                List<Long> memberIds = chatSupportService.listActiveTeamMemberIds(message.getRecvId(), message.getSenderId());
                chatWebSocketHandler.sendToUsersSmoothly(memberIds, chatSupportService.buildTeamSummaryPush(message));
            } else {
                chatWebSocketHandler.sendToTeam(message.getRecvId(), json, message.getSenderId());
            }
        }
    }

    /**
     * 推送消息撤回事件。
     *
     * @param message 最新撤回态消息实体
     */
    private void pushRevokeEvent(ChatMessage message) {
        // 1. 统一组装 message_revoke 载荷，让客户端按 msgId 把对应消息切换成“已撤回”状态。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "message_revoke");

        Map<String, Object> data = new HashMap<>();
        data.put("msgId", chatSupportService.stringifyId(message.getId()));
        data.put("conversationId", message.getConversationId());
        push.put("data", data);

        String json = JSON.toJSONString(push);

        // 2. 私聊仍只通知对端；大群撤回降级成摘要刷新，小群继续广播撤回事件。
        if (message.getRecvType() == 1) {
            chatWebSocketHandler.sendToUser(message.getRecvId(), json);
            return;
        }

        if (chatSupportService.isLargeTeam(message.getRecvId())) {
            List<Long> memberIds = chatSupportService.listActiveTeamMemberIds(message.getRecvId(), message.getSenderId());
            chatWebSocketHandler.sendToUsersSmoothly(memberIds, chatSupportService.buildTeamSummaryPush(message));
            return;
        }
        chatWebSocketHandler.sendToTeam(message.getRecvId(), json, message.getSenderId());
    }

    /**
     * 查询一条仍有效的消息。
     *
     * @param messageId 消息 ID
     * @return 消息实体；不存在或已删除时返回 null
     */
    private ChatMessage loadActiveMessage(Long messageId) {
        // 1. 统一收口消息查询，避免不同方法对“已删除消息”的判断口径不一致。
        ChatMessage msg = chatMessageMapper.selectById(messageId);
        if (msg == null || Integer.valueOf(1).equals(msg.getIsDelete())) {
            return null;
        }
        return msg;
    }

    /**
     * 构建对外展示的消息摘要内容。
     *
     * @param message 消息实体
     * @return 展示内容
     */
    private String buildDisplayContent(ChatMessage message) {
        // 1. 撤回消息统一返回占位文案；不存在的消息则返回空串，避免前端出现 null。
        if (message == null) {
            return "";
        }
        if (Integer.valueOf(1).equals(message.getIsRevoke())) {
            return "消息已撤回";
        }
        return message.getMsgContent() == null ? "" : message.getMsgContent();
    }

    /**
     * 软删除与某条消息强绑定的举报数据。
     * 当前软撤回后不主动调用该逻辑，但保留它，便于以后如果需要做彻底清理时复用。
     *
     * @param messageId 消息 ID
     */
    private void softDeleteMessageReportData(Long messageId) {
        // 1. 先软删除举报主记录，避免后续继续从举报入口看到这条消息。
        if (messageId == null) {
            return;
        }

        LambdaUpdateWrapper<MessageReport> reportUpdate = new LambdaUpdateWrapper<>();
        reportUpdate.eq(MessageReport::getMessageId, messageId)
                .eq(MessageReport::getIsDelete, 0)
                .set(MessageReport::getIsDelete, 1);
        messageReportMapper.update(null, reportUpdate);

        // 2. 再同步软删除举报明细，保持举报链路数据的一致性。
        LambdaUpdateWrapper<ReportDetail> detailUpdate = new LambdaUpdateWrapper<>();
        detailUpdate.eq(ReportDetail::getReportType, REPORT_TYPE_MESSAGE)
                .eq(ReportDetail::getTargetId, messageId)
                .eq(ReportDetail::getIsDelete, 0)
                .set(ReportDetail::getIsDelete, 1);
        reportDetailMapper.update(null, detailUpdate);

        // 3. 最后软删除聚合案件，避免后台继续处理一条已经不再展示的消息举报。
        LambdaUpdateWrapper<ReportCase> caseUpdate = new LambdaUpdateWrapper<>();
        caseUpdate.eq(ReportCase::getReportType, REPORT_TYPE_MESSAGE)
                .eq(ReportCase::getTargetId, messageId)
                .eq(ReportCase::getIsDelete, 0)
                .set(ReportCase::getIsDelete, 1);
        reportCaseMapper.update(null, caseUpdate);
    }

    /**
     * 判断消息是否仍存在待处理举报案件。
     *
     * @param messageId 消息 ID
     * @return true-存在待处理案件，false-不存在
     */
    private boolean hasProcessingMessageReportCase(Long messageId) {
        // 1. 只要仍存在一条“未删除且待处理”的举报案件，就不允许撤回原消息。
        if (messageId == null) {
            return false;
        }

        LambdaQueryWrapper<ReportCase> query = new LambdaQueryWrapper<>();
        query.eq(ReportCase::getReportType, REPORT_TYPE_MESSAGE)
                .eq(ReportCase::getTargetId, messageId)
                .eq(ReportCase::getIsDelete, 0)
                .eq(ReportCase::getCaseStatus, 0)
                .last("LIMIT 1");
        return reportCaseMapper.selectOne(query) != null;
    }
}
