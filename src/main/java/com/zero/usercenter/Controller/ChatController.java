package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.ChatService;
import com.zero.usercenter.Service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天相关接口入口。
 * 统一暴露私聊、群聊、消息管理、举报、群公告和会话状态接口。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private TeamService teamService;

    // ==================== 私聊 ====================

    /**
     * 发送私聊消息。
     */
    @PostMapping("/private/send")
    public Result sendPrivateMsg(@RequestBody PrivateMsgDTO dto) {
        // 私聊发送的鉴权、落库、未读数和实时推送都在聊天 service 内统一处理。
        return chatService.sendPrivateMsg(dto);
    }

    /**
     * 查询私聊历史记录。
     */
    @GetMapping("/private/history")
    public Result getPrivateHistory(
            @RequestParam Long friendId,
            @RequestParam(required = false) Long beforeMsgId,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 进入会话时只拉取一小页正文，后续上滑再基于 beforeMsgId 继续向前翻更早的消息。
        return chatService.getPrivateHistory(friendId, beforeMsgId, pageSize);
    }

    // ==================== 群聊 ====================

    /**
     * 发送群聊消息。
     */
    @PostMapping("/team/send")
    public Result sendTeamMsg(@RequestBody TeamMsgDTO dto) {
        // 群聊发送会串起成员权限、禁言校验、消息落库和群推送链路。
        return chatService.sendTeamMsg(dto);
    }

    /**
     * 查询群聊历史记录。
     */
    @GetMapping("/team/history")
    public Result getTeamHistory(
            @RequestParam Long teamId,
            @RequestParam(required = false) Long beforeMsgId,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 群会话同样采用游标分页，避免一次把过多历史正文压到网络和数据库链路上。
        return chatService.getTeamHistory(teamId, beforeMsgId, pageSize);
    }

    // ==================== 消息管理 ====================

    /**
     * 编辑消息。
     */
    @PostMapping("/message/edit")
    public Result editMsg(@RequestBody MsgEditDTO dto) {
        // 编辑消息涉及发送者校验、可编辑窗口和消息同步推送，因此只做转发。
        return chatService.editMsg(dto);
    }

    /**
     * 撤回消息。
     */
    @PostMapping("/message/revoke")
    public Result revokeMsg(@RequestBody MsgRevokeDTO dto) {
        // 撤回会连带更新消息状态并广播给会话内其他用户。
        return chatService.revokeMsg(dto);
    }

    /**
     * 收藏消息。
     */
    @PostMapping("/message/collect")
    public Result collectMsg(@RequestBody MessageCollectDTO dto) {
        // 收藏消息使用独立收藏链路，controller 不关心收藏表的具体结构。
        return chatService.collectMsg(dto);
    }

    /**
     * 取消收藏。
     */
    @DeleteMapping("/message/collect/{collectionId}")
    public Result cancelCollect(@PathVariable Long collectionId) {
        // 取消收藏只传收藏记录 ID，实际归属校验在 service 中完成。
        return chatService.cancelCollect(collectionId);
    }

    /**
     * 查询当前用户的收藏列表。
     */
    @GetMapping("/message/collections")
    public Result getCollections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 收藏列表分页、消息补充信息和可见性控制都在 service 层处理。
        return chatService.getCollections(page, pageSize);
    }

    /**
     * 置顶消息。
     */
    @PostMapping("/message/pin")
    public Result pinMsg(@RequestBody MessagePinDTO dto) {
        // 置顶消息会判断会话权限并更新置顶列表缓存或数据库状态。
        return chatService.pinMsg(dto);
    }

    /**
     * 取消置顶。
     */
    @DeleteMapping("/message/pin/{pinId}")
    public Result unpinMsg(@PathVariable Long pinId) {
        // 取消置顶复用同一套置顶记录校验逻辑。
        return chatService.unpinMsg(pinId);
    }

    /**
     * 查询会话置顶消息列表。
     */
    @GetMapping("/message/pins")
    public Result getPinList(@RequestParam String conversationId) {
        // 置顶列表按会话维度查询，service 会负责校验当前用户是否有查看权限。
        return chatService.getPinList(conversationId);
    }

    /**
     * 搜索会话内消息。
     */
    @GetMapping("/message/search")
    public Result searchMsg(
            @RequestParam String conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 会话内搜索会在 service 里处理关键词清洗、消息过滤和分页。
        return chatService.searchMsg(conversationId, keyword, page, pageSize);
    }

    /**
     * 举报消息。
     */
    @PostMapping("/message/report")
    public Result reportMsg(@RequestBody MessageReportDTO dto) {
        // 消息举报会下沉到举报链路，可能触发审核、处罚和通知等副作用。
        return chatService.reportMsg(dto);
    }

    /**
     * 查询消息举报状态。
     */
    @GetMapping("/message/report/{reportId}")
    public Result getMsgReportStatus(@PathVariable Long reportId) {
        // 用户查看自己的消息举报状态，由 service 判断归属关系。
        return chatService.getMsgReportStatus(reportId);
    }

    /**
     * 管理员查询消息举报详情和上下文。
     */
    @GetMapping("/message/report/admin/{reportId}/context")
    public Result adminGetReportContext(@PathVariable Long reportId) {
        // 管理员上下文查询会在 service 中补齐原消息、上下文消息和举报详情。
        return chatService.adminGetReportContext(reportId);
    }

    /**
     * 管理员分页查询消息举报列表。
     */
    @GetMapping("/message/report/admin/list")
    public Result adminGetMsgReportList(
            @RequestParam(required = false) Integer adminStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 管理端列表查询只做参数接收，筛选和排序策略都在 service 中统一实现。
        return chatService.adminGetMsgReportList(adminStatus, page, pageSize);
    }

    /**
     * 管理员处理消息举报。
     */
    @PostMapping("/message/report/admin/handle")
    public Result adminHandleMsgReport(@RequestBody MessageReportHandleDTO dto) {
        // 举报处理会触发审核结论、处罚、通知等链路，因此由 service 统一编排。
        return chatService.adminHandleMsgReport(dto);
    }

    /**
     * 标记消息为已读。
     */
    @PostMapping("/message/read")
    public Result markMsgRead(@RequestBody MsgReadDTO dto) {
        // 已读上报会更新未读计数和会话状态，细节都在 service 层封装。
        return chatService.markMsgRead(dto);
    }

    // ==================== 群聊辅助（Part6兼容路径） ====================

    /**
     * 设置群公告。
     */
    @PostMapping("/group/notice")
    public Result setGroupNotice(@RequestBody GroupNoticeDTO dto) {
        // 群公告属于聊天域下的辅助能力，实际处理链路仍由 chatService 统一转发。
        return chatService.setGroupNotice(dto);
    }

    /**
     * 获取群公告。
     */
    @GetMapping("/group/notice")
    public Result getGroupNotice(@RequestParam String conversationId) {
        // 获取公告时，service 会校验团队成员身份并从缓存/存储中读取。
        return chatService.getGroupNotice(conversationId);
    }

    /**
     * 全员禁言。
     * 该路径是聊天模块下的兼容入口，内部复用团队服务。
     */
    @PostMapping("/group/mute-all")
    public Result muteAllByGroup(@RequestBody TeamMuteDTO dto) {
        // 这是聊天模块下的兼容入口，底层直接复用团队服务的禁言实现。
        return teamService.muteAll(dto);
    }

    /**
     * 禁言指定成员。
     * 该路径是聊天模块下的兼容入口，内部复用团队服务。
     */
    @PostMapping("/group/mute-member")
    public Result muteMemberByGroup(@RequestBody TeamMuteDTO dto) {
        // 兼容旧前端路径，避免聊天域和团队域出现两套禁言实现。
        return teamService.muteMember(dto);
    }

    /**
     * 解除成员禁言。
     * 该路径是聊天模块下的兼容入口，内部复用团队服务。
     */
    @PostMapping("/group/unmute-member")
    public Result unmuteMemberByGroup(@RequestBody TeamMuteDTO dto) {
        // 解除成员禁言同样复用团队服务，保证权限规则完全一致。
        return teamService.unmuteMember(dto);
    }

    /**
     * 获取所有会话未读消息数。
     */
    @GetMapping("/unread-count")
    public Result getUnreadCount() {
        // 会话未读统计由 service 汇总私聊、群聊等多个来源的数据。
        return chatService.getUnreadCount();
    }

    /**
     * 获取当前用户最近会话列表。
     */
    @GetMapping("/recent-conversations")
    public Result getRecentConversations() {
        // 最近会话列表会在 service 中补齐最后一条消息、未读数和会话展示信息。
        return chatService.getRecentConversations();
    }

    /**
     * 将当前用户的指定会话从会话列表中移除。
     */
    @PostMapping("/conversation/hide")
    public Result hideConversation(@RequestParam String conversationId) {
        // 隐藏会话只影响当前用户自己的会话列表，不会删除实际聊天记录。
        return chatService.hideConversation(conversationId);
    }
}
