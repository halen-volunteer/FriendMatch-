package com.zero.usercenter.Service.impl;

import com.zero.usercenter.DTO.GroupNoticeDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.aop.annotation.RequireTeamConversationRole;
import com.zero.usercenter.aop.annotation.TeamRoleScope;
import com.zero.usercenter.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 群公告服务。
 */
@Service
public class ChatNoticeService {

    @Resource
    private ChatSupportService chatSupportService;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 只有队长或管理员可以设置群公告。
     */
    @RequireTeamConversationRole(
            value = TeamRoleScope.ADMIN_OR_LEADER,
            conversationId = "#p0.conversationId",
            forbiddenMessage = "仅队长/管理员可设置群公告")
    public Result setGroupNotice(GroupNoticeDTO dto) {
        // 1. 先做基础校验，保证会话一定是团队会话且公告内容合法。
        if (dto == null || dto.getConversationId() == null || dto.getConversationId().isBlank()) {
            return Result.fail("会话ID不能为空");
        }
        if (!dto.getConversationId().startsWith("team_")) {
            return Result.fail("仅支持团队会话");
        }
        if (dto.getNotice() == null || dto.getNotice().isBlank()) {
            return Result.fail("公告内容不能为空");
        }
        if (dto.getNotice().length() > 500) {
            return Result.fail("公告内容不能超过500字符");
        }

        // 2. 把会话ID解析成 teamId，并把公告内容缓存起来，保证后续读取有兜底值。
        Long teamId = chatSupportService.parseTeamId(dto.getConversationId());
        String notice = dto.getNotice().trim();
        chatSupportService.cacheGroupNotice(dto.getConversationId(), notice);

        // 3. 组装 WebSocket 推送消息，通知团队内所有在线成员公告已变更。
        Map<String, Object> push = new HashMap<>();
        push.put("type", "group_notice_update");

        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", dto.getConversationId());
        data.put("teamId", teamId);
        data.put("notice", notice);
        data.put("updateTime", LocalDateTime.now());
        push.put("data", data);

        chatWebSocketHandler.sendToTeamAll(teamId, com.alibaba.fastjson2.JSON.toJSONString(push));
        return Result.ok("群公告已设置");
    }

    /**
     * 仅团队成员可以查看群公告。
     */
    public Result getGroupNotice(String conversationId) {
        // 1. 先确认登录和会话合法性，防止跨团队读取公告。
        Long userId = chatSupportService.requireLogin();
        if (conversationId == null || conversationId.isBlank()) {
            return Result.fail("会话ID不能为空");
        }
        if (!conversationId.startsWith("team_")) {
            return Result.fail("仅支持团队会话");
        }

        // 2. 校验当前用户是否为团队成员，只有成员才允许查看群公告。
        Long teamId = chatSupportService.parseTeamId(conversationId);
        if (chatSupportService.getMember(teamId, userId) == null) {
            return Result.fail("您不是该团队成员");
        }

        // 3. 从缓存或持久层读取公告，并返回给前端展示。
        String notice = chatSupportService.getGroupNotice(conversationId);
        Map<String, Object> data = new HashMap<>();
        data.put("notice", notice != null ? notice : "");
        data.put("update_time", LocalDateTime.now());
        return Result.ok(data);
    }
}
