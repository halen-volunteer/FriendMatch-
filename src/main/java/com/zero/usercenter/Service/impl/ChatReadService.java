package com.zero.usercenter.Service.impl;

import com.zero.usercenter.DTO.MsgReadDTO;
import com.zero.usercenter.DTO.Result;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.zero.usercenter.utils.Number.LAST_MSG_CACHE_KEY;
import static com.zero.usercenter.utils.Number.UNREAD_COUNT_KEY;

@Service
public class ChatReadService {
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private ChatSupportService chatSupportService;

    @Transactional
    public Result markMsgRead(MsgReadDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto.getConversationId() == null || dto.getMsgIds() == null || dto.getMsgIds().isEmpty()) {
            return Result.fail("参数不能为空");
        }
        chatSupportService.doMarkRead(dto.getConversationId(), dto.getMsgIds(), userId);
        return Result.ok("已标记为已读");
    }

    public Result getUnreadCount() {
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
            conv.put("lastMessage", lastMsgContent);
            conversations.add(conv);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalUnread", totalUnread);
        data.put("conversations", conversations);
        return Result.ok(data);
    }
}
