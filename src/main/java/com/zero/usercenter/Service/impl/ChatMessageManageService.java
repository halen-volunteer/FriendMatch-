package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Mapper.*;
import com.zero.usercenter.Model.*;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatMessageManageService {
    @Resource private ChatSupportService chatSupportService;
    @Resource private ChatMessageMapper chatMessageMapper;
    @Resource private MessageReadReceiptMapper messageReadReceiptMapper;
    @Resource private MessageCollectionMapper messageCollectionMapper;
    @Resource private MessagePinMapper messagePinMapper;
    @Resource private MessageReportMapper messageReportMapper;
    @Resource private TeamMemberMapper teamMemberMapper;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;

    @Transactional
    public Result editMsg(MsgEditDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getMsgId() == null) return Result.fail("消息ID不能为空");
        if (dto.getNewContent() == null || dto.getNewContent().isBlank()) return Result.fail("编辑内容不能为空");
        if (dto.getNewContent().length() > 2000) return Result.fail("编辑内容不能超过2000字符");
        ChatMessage msg = chatMessageMapper.selectById(dto.getMsgId());
        if (msg == null || msg.getIsDelete() == 1) return Result.fail("消息不存在");
        if (!msg.getSenderId().equals(userId)) return Result.fail("只能编辑自己发送的消息");
        if (LocalDateTime.now().isAfter(msg.getCreateTime().plusMinutes(5))) return Result.fail("消息已过期，无法编辑");
        LambdaUpdateWrapper<ChatMessage> uw = new LambdaUpdateWrapper<>();
        uw.eq(ChatMessage::getId, dto.getMsgId()).set(ChatMessage::getMsgContent, dto.getNewContent().trim()).set(ChatMessage::getIsEdited, 1).set(ChatMessage::getEditTime, LocalDateTime.now()).setSql("edit_count = IFNULL(edit_count,0) + 1");
        chatMessageMapper.update(null, uw);
        ChatMessage updated = chatMessageMapper.selectById(dto.getMsgId());
        Map<String, Object> push = new HashMap<>();
        push.put("type", "message_edit");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", updated.getId());
        data.put("conversationId", updated.getConversationId());
        data.put("content", updated.getMsgContent());
        data.put("editCount", updated.getEditCount());
        data.put("editTime", updated.getEditTime());
        push.put("data", data);
        String json = JSON.toJSONString(push);
        if (updated.getRecvType() == 1) chatWebSocketHandler.sendToUser(updated.getRecvId(), json); else chatWebSocketHandler.sendToTeam(updated.getRecvId(), json, updated.getSenderId());
        return Result.ok("消息已编辑");
    }

    @Transactional
    public Result revokeMsg(MsgRevokeDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getMsgId() == null) return Result.fail("消息ID不能为空");
        ChatMessage msg = chatMessageMapper.selectById(dto.getMsgId());
        if (msg == null || msg.getIsDelete() == 1) return Result.fail("消息不存在");
        if (!msg.getSenderId().equals(userId)) return Result.fail("只能撤回自己发送的消息");
        if (LocalDateTime.now().isAfter(msg.getCreateTime().plusMinutes(5))) return Result.fail("消息已过期，无法撤回");
        LambdaQueryWrapper<MessageReport> reportQw = new LambdaQueryWrapper<>();
        reportQw.eq(MessageReport::getMessageId, dto.getMsgId()).eq(MessageReport::getAdminStatus, 0).eq(MessageReport::getIsDelete, 0);
        if (messageReportMapper.selectCount(reportQw) > 0) return Result.fail("消息正在审核中，暂时无法撤回");
        chatMessageMapper.deleteById(dto.getMsgId());
        LambdaQueryWrapper<MessageReadReceipt> receiptQw = new LambdaQueryWrapper<>();
        receiptQw.eq(MessageReadReceipt::getMsgId, dto.getMsgId());
        messageReadReceiptMapper.delete(receiptQw);
        LambdaQueryWrapper<MessageCollection> collectionQw = new LambdaQueryWrapper<>();
        collectionQw.eq(MessageCollection::getMessageId, dto.getMsgId());
        messageCollectionMapper.delete(collectionQw);
        LambdaQueryWrapper<MessagePin> pinQw = new LambdaQueryWrapper<>();
        pinQw.eq(MessagePin::getMessageId, dto.getMsgId());
        messagePinMapper.delete(pinQw);
        LambdaQueryWrapper<MessageReport> reportDelQw = new LambdaQueryWrapper<>();
        reportDelQw.eq(MessageReport::getMessageId, dto.getMsgId());
        messageReportMapper.delete(reportDelQw);
        Thread.ofVirtual().start(() -> chatWebSocketHandler.sendRevoke(msg));
        return Result.ok("消息已撤回");
    }

    @Transactional
    public Result collectMsg(MessageCollectDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getMessageId() == null) return Result.fail("消息ID不能为空");
        ChatMessage msg = chatMessageMapper.selectById(dto.getMessageId());
        if (msg == null || msg.getIsDelete() == 1) return Result.fail("消息不存在");
        LambdaQueryWrapper<MessageCollection> qw = new LambdaQueryWrapper<>();
        qw.eq(MessageCollection::getUserId, userId).eq(MessageCollection::getMessageId, dto.getMessageId()).eq(MessageCollection::getIsDelete, 0);
        if (messageCollectionMapper.selectOne(qw) != null) return Result.fail("该消息已收藏");
        MessageCollection collection = new MessageCollection();
        collection.setUserId(userId); collection.setMessageId(dto.getMessageId()); collection.setCollectionNote(dto.getCollectionNote() != null ? dto.getCollectionNote().trim() : ""); collection.setIsDelete(0);
        messageCollectionMapper.insert(collection);
        return Result.ok(collection.getId());
    }

    @Transactional
    public Result cancelCollect(Long collectionId) {
        Long userId = chatSupportService.requireLogin();
        if (collectionId == null) return Result.fail("收藏ID不能为空");
        LambdaUpdateWrapper<MessageCollection> uw = new LambdaUpdateWrapper<>();
        uw.eq(MessageCollection::getId, collectionId).eq(MessageCollection::getUserId, userId).eq(MessageCollection::getIsDelete, 0).set(MessageCollection::getIsDelete, 1);
        int rows = messageCollectionMapper.update(null, uw);
        if (rows == 0) return Result.fail("收藏记录不存在");
        return Result.ok("已取消收藏");
    }

    public Result getCollections(int page, int pageSize) {
        Long userId = chatSupportService.requireLogin();
        Page<MessageCollection> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<MessageCollection> qw = new LambdaQueryWrapper<>();
        qw.eq(MessageCollection::getUserId, userId).eq(MessageCollection::getIsDelete, 0).orderByDesc(MessageCollection::getCreateTime);
        Page<MessageCollection> result = messageCollectionMapper.selectPage(pageObj, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            ChatMessage m = chatMessageMapper.selectById(c.getMessageId());
            item.put("collectionId", c.getId()); item.put("messageId", c.getMessageId()); item.put("collectionNote", c.getCollectionNote()); item.put("collectionTime", c.getCreateTime()); item.put("msgContent", m != null ? m.getMsgContent() : ""); item.put("senderId", m != null ? m.getSenderId() : null);
            return item;
        }).collect(Collectors.toList());
        return Result.ok(list, result.getTotal());
    }

    @Transactional
    public Result pinMsg(MessagePinDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getConversationId() == null || dto.getMessageId() == null) return Result.fail("参数不能为空");
        ChatMessage msg = chatMessageMapper.selectById(dto.getMessageId());
        if (msg == null || msg.getIsDelete() == 1) return Result.fail("消息不存在");
        if (!dto.getConversationId().equals(msg.getConversationId())) return Result.fail("会话与消息不匹配");
        if (dto.getConversationId().startsWith("team_")) {
            Long teamId = chatSupportService.parseTeamId(dto.getConversationId());
            LambdaQueryWrapper<TeamMember> roleQw = new LambdaQueryWrapper<>();
            roleQw.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId).eq(TeamMember::getIsQuit, 0).in(TeamMember::getRoleType, 1, 2);
            if (teamMemberMapper.selectOne(roleQw) == null) return Result.fail("仅队长/管理员可置顶消息");
        }
        LambdaQueryWrapper<MessagePin> q = new LambdaQueryWrapper<>();
        q.eq(MessagePin::getConversationId, dto.getConversationId()).eq(MessagePin::getMessageId, dto.getMessageId()).eq(MessagePin::getIsDelete, 0);
        if (messagePinMapper.selectOne(q) != null) return Result.fail("该消息已置顶");
        LambdaQueryWrapper<MessagePin> maxQ = new LambdaQueryWrapper<>();
        maxQ.eq(MessagePin::getConversationId, dto.getConversationId()).eq(MessagePin::getIsDelete, 0).orderByDesc(MessagePin::getPinOrder).last("LIMIT 1");
        MessagePin last = messagePinMapper.selectOne(maxQ);
        int nextOrder = last == null || last.getPinOrder() == null ? 1 : last.getPinOrder() + 1;
        MessagePin pin = new MessagePin();
        pin.setConversationId(dto.getConversationId()); pin.setMessageId(dto.getMessageId()); pin.setPinUserId(userId); pin.setPinOrder(nextOrder); pin.setIsDelete(0);
        messagePinMapper.insert(pin);
        return Result.ok(pin.getId());
    }

    @Transactional
    public Result unpinMsg(Long pinId) {
        Long userId = chatSupportService.requireLogin();
        if (pinId == null) return Result.fail("置顶ID不能为空");
        LambdaUpdateWrapper<MessagePin> uw = new LambdaUpdateWrapper<>();
        uw.eq(MessagePin::getId, pinId).eq(MessagePin::getPinUserId, userId).eq(MessagePin::getIsDelete, 0).set(MessagePin::getIsDelete, 1);
        int rows = messagePinMapper.update(null, uw);
        if (rows == 0) return Result.fail("置顶记录不存在");
        return Result.ok("已取消置顶");
    }

    public Result getPinList(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return Result.fail("会话ID不能为空");
        LambdaQueryWrapper<MessagePin> qw = new LambdaQueryWrapper<>();
        qw.eq(MessagePin::getConversationId, conversationId).eq(MessagePin::getIsDelete, 0).orderByAsc(MessagePin::getPinOrder);
        List<MessagePin> pins = messagePinMapper.selectList(qw);
        List<Map<String, Object>> list = pins.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            ChatMessage m = chatMessageMapper.selectById(p.getMessageId());
            item.put("pinId", p.getId()); item.put("messageId", p.getMessageId()); item.put("pinOrder", p.getPinOrder()); item.put("pinTime", p.getCreateTime()); item.put("msgContent", m != null ? m.getMsgContent() : ""); item.put("msgType", m != null ? m.getMsgType() : null);
            return item;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    public Result searchMsg(String conversationId, String keyword, int page, int pageSize) {
        Long userId = chatSupportService.requireLogin();
        if (conversationId == null || conversationId.isBlank()) return Result.fail("会话ID不能为空");
        if (keyword == null || keyword.isBlank()) return Result.fail("关键词不能为空");
        Page<ChatMessage> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ChatMessage::getConversationId, conversationId).eq(ChatMessage::getIsDelete, 0).like(ChatMessage::getMsgContent, keyword.trim()).orderByDesc(ChatMessage::getCreateTime);
        Page<ChatMessage> result = chatMessageMapper.selectPage(pageObj, qw);
        return Result.ok(chatSupportService.buildVoList(result.getRecords(), userId), result.getTotal());
    }
}
