package com.zero.usercenter.Service.impl;

import com.zero.usercenter.DTO.GroupNoticeDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Model.TeamMember;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChatNoticeService {
    @Resource private ChatSupportService chatSupportService;
    @Resource private ChatWebSocketHandler chatWebSocketHandler;

    public Result setGroupNotice(GroupNoticeDTO dto) {
        Long userId = chatSupportService.requireLogin();
        if (dto == null || dto.getConversationId() == null || dto.getConversationId().isBlank()) return Result.fail("会话ID不能为空");
        if (!dto.getConversationId().startsWith("team_")) return Result.fail("仅支持团队会话");
        if (dto.getNotice() == null || dto.getNotice().isBlank()) return Result.fail("公告内容不能为空");
        if (dto.getNotice().length() > 500) return Result.fail("公告内容不能超过500字符");

        Long teamId = chatSupportService.parseTeamId(dto.getConversationId());
        TeamMember member = chatSupportService.getMember(teamId, userId);
        if (member == null || member.getRoleType() > 2) return Result.fail("仅队长/管理员可设置群公告");

        chatSupportService.cacheGroupNotice(dto.getConversationId(), dto.getNotice().trim());

        Map<String, Object> push = new HashMap<>();
        push.put("type", "group_notice_update");
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", dto.getConversationId());
        data.put("teamId", teamId);
        data.put("notice", dto.getNotice().trim());
        data.put("updateTime", LocalDateTime.now());
        push.put("data", data);
        chatWebSocketHandler.sendToTeamAll(teamId, com.alibaba.fastjson2.JSON.toJSONString(push));
        return Result.ok("群公告已设置");
    }

    public Result getGroupNotice(String conversationId) {
        Long userId = chatSupportService.requireLogin();
        if (conversationId == null || conversationId.isBlank()) return Result.fail("会话ID不能为空");
        if (!conversationId.startsWith("team_")) return Result.fail("仅支持团队会话");

        Long teamId = chatSupportService.parseTeamId(conversationId);
        if (chatSupportService.getMember(teamId, userId) == null) return Result.fail("您不是该团队成员");

        String notice = chatSupportService.getGroupNotice(conversationId);
        Map<String, Object> data = new HashMap<>();
        data.put("notice", notice != null ? notice : "");
        data.put("update_time", LocalDateTime.now());
        return Result.ok(data);
    }
}
