package com.zero.usercenter.Service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.MessageReadReceiptMapper;
import com.zero.usercenter.Mapper.SystemNoticeMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.SystemNotice;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.Model.User;
import com.zero.usercenter.exception.BusinessException;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zero.usercenter.utils.Number.GROUP_NOTICE_KEY;
import static com.zero.usercenter.utils.Number.GROUP_NOTICE_TTL_DAYS;
import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_KEY;
import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_TTL_DAYS;
import static com.zero.usercenter.utils.Number.MSG_BITMAP_TTL_DAYS;
import static com.zero.usercenter.utils.Number.MSG_DELIVER_KEY;
import static com.zero.usercenter.utils.Number.MSG_READ_KEY;
import static com.zero.usercenter.utils.Number.UNREAD_COUNT_KEY;

@Service
public class ChatSupportService {

    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private MessageReadReceiptMapper messageReadReceiptMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;
    @Resource
    private SystemNoticeMapper systemNoticeMapper;
    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    @Resource
    private ChatMessageMapper chatMessageMapper;

    public Long requireLogin() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        return userId;
    }

    public Long parseTeamId(String conversationId) {
        try {
            return Long.parseLong(conversationId.substring(5));
        } catch (Exception e) {
            throw new BusinessException("团队会话ID格式错误");
        }
    }

    public String buildConvId(Long a, Long b) {
        return Math.min(a, b) + "_" + Math.max(a, b);
    }

    public TeamMember getMember(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getIsQuit, 0);
        return teamMemberMapper.selectOne(qw);
    }

    public ResultHolder checkConversationAccess(String conversationId, Long userId) {
        if (conversationId.startsWith("team_")) {
            Long teamId = parseTeamId(conversationId);
            if (getMember(teamId, userId) == null) {
                return ResultHolder.fail("您不是该团队成员");
            }
            return null;
        }

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

    public void writeReceipt(Long msgId, Long userId, int receiptType) {
        messageReadReceiptMapper.batchInsertIgnore(
                Collections.singletonList(msgId), userId, receiptType, LocalDateTime.now());
        if (receiptType == 1) {
            stringRedisTemplate.opsForValue().setBit(MSG_DELIVER_KEY + msgId, userId, true);
            stringRedisTemplate.expire(MSG_DELIVER_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    public void writeTeamReceipts(Long teamId, Long msgId, Long senderId) {
        LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
        qw.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getIsQuit, 0)
                .ne(TeamMember::getUserId, senderId);
        List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toList());
        if (memberIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long memberId : memberIds) {
            messageReadReceiptMapper.batchInsertIgnore(Collections.singletonList(msgId), memberId, 1, now);
            stringRedisTemplate.opsForValue().setBit(MSG_DELIVER_KEY + msgId, memberId, true);
            stringRedisTemplate.expire(MSG_DELIVER_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    public void doMarkRead(String convId, List<Long> msgIds, Long userId) {
        messageReadReceiptMapper.batchInsertIgnore(msgIds, userId, 2, LocalDateTime.now());
        stringRedisTemplate.delete(UNREAD_COUNT_KEY + userId + ":" + convId);
        if (convId.startsWith("team_")) {
            for (Long msgId : msgIds) {
                stringRedisTemplate.opsForValue().setBit(MSG_READ_KEY + msgId, userId, true);
                stringRedisTemplate.expire(MSG_READ_KEY + msgId, MSG_BITMAP_TTL_DAYS, TimeUnit.DAYS);
            }
        }
    }

    public List<Map<String, Object>> buildVoList(List<ChatMessage> msgs, Long currentUserId) {
        return msgs.stream().map(m -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("msgId", m.getId());
            vo.put("senderId", m.getSenderId());
            vo.put("isSelf", m.getSenderId().equals(currentUserId));
            vo.put("msgType", m.getMsgType());
            vo.put("msgContent", m.getMsgContent());
            vo.put("isEdited", m.getIsEdited());
            vo.put("editTime", m.getEditTime());
            vo.put("editCount", m.getEditCount());
            vo.put("createTime", m.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    public String buildStoredContent(Integer msgType, String msgContent,
                                     String fileUrl, String fileName, Long fileSize,
                                     String emojiId, List<Long> atUsers) {
        return switch (msgType) {
            case 2 -> {
                Map<String, Object> m = new HashMap<>();
                m.put("url", fileUrl);
                if (msgContent != null && !msgContent.isBlank()) m.put("caption", msgContent.trim());
                yield JSON.toJSONString(m);
            }
            case 3 -> {
                Map<String, Object> m = new HashMap<>();
                m.put("url", fileUrl);
                m.put("name", fileName);
                if (fileSize != null) m.put("size", fileSize);
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

    public String buildPrivatePush(ChatMessage msg) {
        Map<String, Object> push = new HashMap<>();
        push.put("type", "private_message");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", msg.getId());
        data.put("senderId", msg.getSenderId());
        data.put("msgType", msg.getMsgType());
        data.put("content", msg.getMsgContent());
        data.put("createTime", msg.getCreateTime());
        push.put("data", data);
        return JSON.toJSONString(push);
    }

    public String buildTeamPush(ChatMessage msg) {
        Map<String, Object> push = new HashMap<>();
        push.put("type", "team_message");
        Map<String, Object> data = new HashMap<>();
        data.put("msgId", msg.getId());
        data.put("teamId", msg.getRecvId());
        data.put("senderId", msg.getSenderId());
        data.put("msgType", msg.getMsgType());
        data.put("content", msg.getMsgContent());
        data.put("createTime", msg.getCreateTime());
        push.put("data", data);
        return JSON.toJSONString(push);
    }

    public void cacheLastMsg(ChatMessage msg) {
        String key = LAST_MSG_CACHE_KEY + msg.getConversationId();
        Map<String, String> map = new HashMap<>();
        map.put("msgId", String.valueOf(msg.getId()));
        map.put("senderId", String.valueOf(msg.getSenderId()));
        map.put("msgType", String.valueOf(msg.getMsgType()));
        map.put("msgContent", msg.getMsgContent() != null ? msg.getMsgContent() : "");
        map.put("createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : "");
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, LAST_MSG_CACHE_TTL_DAYS, TimeUnit.DAYS);
    }

    public void cacheGroupNotice(String conversationId, String notice) {
        stringRedisTemplate.opsForValue().set(GROUP_NOTICE_KEY + conversationId, notice, GROUP_NOTICE_TTL_DAYS, TimeUnit.DAYS);
    }

    public String getGroupNotice(String conversationId) {
        return stringRedisTemplate.opsForValue().get(GROUP_NOTICE_KEY + conversationId);
    }

    public void sendAtNotices(List<Long> atUserIds, Long teamId, Long senderId, Long msgId) {
        User sender = userMapper.selectById(senderId);
        String senderName = sender != null ? sender.getUserNickname() : String.valueOf(senderId);

        for (Long atUserId : atUserIds) {
            if (atUserId == null || atUserId.equals(senderId)) continue;

            SystemNotice notice = new SystemNotice();
            notice.setUserId(atUserId);
            notice.setNoticeType(9);
            notice.setNoticeContent("你在团队【" + teamId + "】中被【" + senderName + "】@了");
            notice.setRelatedId(msgId);
            notice.setIsRead(0);
            notice.setIsDelete(0);
            systemNoticeMapper.insert(notice);

            Map<String, Object> push = new HashMap<>();
            push.put("type", "system_notice");
            Map<String, Object> data = new HashMap<>();
            data.put("noticeId", notice.getId());
            data.put("noticeType", notice.getNoticeType());
            data.put("noticeContent", notice.getNoticeContent());
            data.put("relatedId", notice.getRelatedId());
            push.put("data", data);
            chatWebSocketHandler.sendToUser(atUserId, JSON.toJSONString(push));
        }
    }

    public void sendReportNotice(Long userId, Long reportId, String content) {
        SystemNotice notice = new SystemNotice();
        notice.setUserId(userId);
        notice.setNoticeType(7);
        notice.setNoticeContent(content);
        notice.setRelatedId(reportId);
        notice.setIsRead(0);
        notice.setIsDelete(0);
        systemNoticeMapper.insert(notice);
        Map<String, Object> push = new HashMap<>();
        push.put("type", "system_notice");
        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", notice.getId());
        data.put("noticeType", 7);
        data.put("noticeContent", content);
        data.put("relatedId", reportId);
        push.put("data", data);
        chatWebSocketHandler.sendToUser(userId, JSON.toJSONString(push));
    }

    public record ResultHolder(boolean failed, String message) {
        public static ResultHolder fail(String message) {
            return new ResultHolder(true, message);
        }
    }
}
