package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.ChatService;
import com.zero.usercenter.Service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天系统 Controller
 *
 * 基础路径：/api/chat
 * 所有接口需在请求头携带 Authorization: {token}
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
     * 发送私聊消息
     * POST /api/chat/private/send
     * 检查黑名单、隐私设置、全局禁言
     *
     * @param dto 私聊消息数据传输对象，包含接收方 ID、消息内容、消息类型等
     * @return 发送结果
     */
    @PostMapping("/private/send")
    public Result sendPrivateMsg(@RequestBody PrivateMsgDTO dto) {
        return chatService.sendPrivateMsg(dto);
    }

    /**
     * 查询私聊历史记录
     * GET /api/chat/private/history?friendId=1002&page=1&pageSize=20
     *
     * @param friendId 好友用户 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 私聊历史消息分页列表
     */
    @GetMapping("/private/history")
    public Result getPrivateHistory(
            @RequestParam Long friendId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.getPrivateHistory(friendId, page, pageSize);
    }

    // ==================== 群聊 ====================

    /**
     * 发送群聊消息
     * POST /api/chat/team/send
     * 检查成员资格、全局禁言、团队禁言
     *
     * @param dto 群聊消息数据传输对象，包含团队 ID、消息内容、消息类型等
     * @return 发送结果
     */
    @PostMapping("/team/send")
    public Result sendTeamMsg(@RequestBody TeamMsgDTO dto) {
        return chatService.sendTeamMsg(dto);
    }

    /**
     * 查询群聊历史记录
     * GET /api/chat/team/history?teamId=1001&page=1&pageSize=20
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 群聊历史消息分页列表
     */
    @GetMapping("/team/history")
    public Result getTeamHistory(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.getTeamHistory(teamId, page, pageSize);
    }

    // ==================== 消息管理 ====================

    /**
     * 编辑消息（5分钟内，且仅发送者可编辑）
     * POST /api/chat/message/edit
     *
     * @param dto 消息编辑数据传输对象，包含消息 ID 和新内容
     * @return 编辑结果
     */
    @PostMapping("/message/edit")
    public Result editMsg(@RequestBody MsgEditDTO dto) {
        return chatService.editMsg(dto);
    }

    /**
     * 撤回消息（5分钟内可撤回）
     * POST /api/chat/message/revoke
     *
     * @param dto 消息撤回数据传输对象，包含消息 ID
     * @return 撤回结果
     */
    @PostMapping("/message/revoke")
    public Result revokeMsg(@RequestBody MsgRevokeDTO dto) {
        return chatService.revokeMsg(dto);
    }

    /**
     * 收藏消息
     * POST /api/chat/message/collect
     *
     * @param dto 消息收藏数据传输对象，包含消息 ID
     * @return 收藏结果
     */
    @PostMapping("/message/collect")
    public Result collectMsg(@RequestBody MessageCollectDTO dto) {
        return chatService.collectMsg(dto);
    }

    /**
     * 取消收藏
     * DELETE /api/chat/message/collect/{collectionId}
     *
     * @param collectionId 收藏记录 ID
     * @return 取消收藏结果
     */
    @DeleteMapping("/message/collect/{collectionId}")
    public Result cancelCollect(@PathVariable Long collectionId) {
        return chatService.cancelCollect(collectionId);
    }

    /**
     * 查询我的收藏列表
     * GET /api/chat/message/collections?page=1&pageSize=20
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 收藏消息分页列表
     */
    @GetMapping("/message/collections")
    public Result getCollections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.getCollections(page, pageSize);
    }

    /**
     * 置顶消息
     * POST /api/chat/message/pin
     *
     * @param dto 消息置顶数据传输对象，包含消息 ID 和会话 ID
     * @return 置顶结果
     */
    @PostMapping("/message/pin")
    public Result pinMsg(@RequestBody MessagePinDTO dto) {
        return chatService.pinMsg(dto);
    }

    /**
     * 取消置顶
     * DELETE /api/chat/message/pin/{pinId}
     *
     * @param pinId 置顶记录 ID
     * @return 取消置顶结果
     */
    @DeleteMapping("/message/pin/{pinId}")
    public Result unpinMsg(@PathVariable Long pinId) {
        return chatService.unpinMsg(pinId);
    }

    /**
     * 查询会话置顶列表
     * GET /api/chat/message/pins?conversationId=1_2
     *
     * @param conversationId 会话 ID（如 private_1_2 或 team_1001）
     * @return 置顶消息列表
     */
    @GetMapping("/message/pins")
    public Result getPinList(@RequestParam String conversationId) {
        return chatService.getPinList(conversationId);
    }

    /**
     * 搜索消息
     * GET /api/chat/message/search?conversationId=1_2&keyword=你好&page=1&pageSize=20
     *
     * @param conversationId 会话 ID
     * @param keyword        搜索关键词
     * @param page           页码
     * @param pageSize       每页条数
     * @return 符合条件的消息分页列表
     */
    @GetMapping("/message/search")
    public Result searchMsg(
            @RequestParam String conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.searchMsg(conversationId, keyword, page, pageSize);
    }

    /**
     * 举报消息
     * POST /api/chat/message/report
     *
     * @param dto 消息举报数据传输对象，包含消息 ID 和举报原因
     * @return 举报结果
     */
    @PostMapping("/message/report")
    public Result reportMsg(@RequestBody MessageReportDTO dto) {
        return chatService.reportMsg(dto);
    }

    /**
     * 查询举报状态
     * GET /api/chat/message/report/{reportId}
     *
     * @param reportId 举报记录 ID
     * @return 举报状态信息
     */
    @GetMapping("/message/report/{reportId}")
    public Result getMsgReportStatus(@PathVariable Long reportId) {
        return chatService.getMsgReportStatus(reportId);
    }

    /**
     * 管理员查询举报详情（含上下文消息）
     * GET /api/chat/message/report/admin/{reportId}/context
     * 返回举报记录 + 被举报消息前后各10条，供管理员人工判断
     *
     * @param reportId 举报记录 ID
     * @return 举报详情及上下文消息
     */
    @GetMapping("/message/report/admin/{reportId}/context")
    public Result adminGetReportContext(@PathVariable Long reportId) {
        return chatService.adminGetReportContext(reportId);
    }

    /**
     * 管理员查询举报列表
     * GET /api/chat/message/report/admin/list?adminStatus=0&page=1&pageSize=20
     *
     * @param adminStatus 处理状态（0-待处理，1-处理中，2-已处理，null-全部）
     * @param page        页码
     * @param pageSize    每页条数
     * @return 举报分页列表
     */
    @GetMapping("/message/report/admin/list")
    public Result adminGetMsgReportList(
            @RequestParam(required = false) Integer adminStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.adminGetMsgReportList(adminStatus, page, pageSize);
    }

    /**
     * 管理员处理举报
     * POST /api/chat/message/report/admin/handle
     *
     * @param dto 举报处理数据传输对象（adminDecision: 1-维持处罚 2-撤销处罚 3-确认违规）
     * @return 处理结果
     */
    @PostMapping("/message/report/admin/handle")
    public Result adminHandleMsgReport(@RequestBody MessageReportHandleDTO dto) {
        return chatService.adminHandleMsgReport(dto);
    }

    /**
     * 提交申诉（举报人A或被举报人B均可）
     * POST /api/chat/message/report/{reportId}/appeal
     * 总申诉次数上限3次
     *
     * @param reportId 举报记录ID
     * @return 申诉结果
     */
    @PostMapping("/message/report/{reportId}/appeal")
    public Result appealMsgReport(@PathVariable Long reportId) {
        return chatService.appealMsgReport(reportId);
    }

    /**
     * 标记消息为已读
     * POST /api/chat/message/read
     *
     * @param dto 消息已读数据传输对象，包含会话 ID 和最后已读消息 ID
     * @return 标记结果
     */
    @PostMapping("/message/read")
    public Result markMsgRead(@RequestBody MsgReadDTO dto) {
        return chatService.markMsgRead(dto);
    }

    // ==================== 群聊辅助（Part6兼容路径） ====================

    /**
     * 设置群公告
     * POST /api/chat/group/notice
     *
     * @param dto 群公告数据传输对象，包含会话 ID 和公告内容
     * @return 设置结果
     */
    @PostMapping("/group/notice")
    public Result setGroupNotice(@RequestBody GroupNoticeDTO dto) {
        return chatService.setGroupNotice(dto);
    }

    /**
     * 获取群公告
     * GET /api/chat/group/notice?conversationId=team_1001
     *
     * @param conversationId 会话 ID（如 team_1001）
     * @return 群公告内容
     */
    @GetMapping("/group/notice")
    public Result getGroupNotice(@RequestParam String conversationId) {
        return chatService.getGroupNotice(conversationId);
    }

    /**
     * 全员禁言（兼容 Part6 路径，内部复用 Team 模块）
     * POST /api/chat/group/mute-all
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和禁言开关
     * @return 操作结果
     */
    @PostMapping("/group/mute-all")
    public Result muteAllByGroup(@RequestBody TeamMuteDTO dto) {
        return teamService.muteAll(dto);
    }

    /**
     * 禁言指定成员（兼容 Part6 路径，内部复用 Team 模块）
     * POST /api/chat/group/mute-member
     *
     * @param dto 禁言数据传输对象，包含团队 ID、目标用户 ID 和禁言时长
     * @return 禁言结果
     */
    @PostMapping("/group/mute-member")
    public Result muteMemberByGroup(@RequestBody TeamMuteDTO dto) {
        return teamService.muteMember(dto);
    }

    /**
     * 解除禁言（兼容 Part6 路径，内部复用 Team 模块）
     * POST /api/chat/group/unmute-member
     *
     * @param dto 禁言数据传输对象，包含团队 ID 和目标用户 ID
     * @return 解除结果
     */
    @PostMapping("/group/unmute-member")
    public Result unmuteMemberByGroup(@RequestBody TeamMuteDTO dto) {
        return teamService.unmuteMember(dto);
    }

    /**
     * 自定义群名（Part6已取消该功能，返回提示）
     * POST /api/chat/group/name
     *
     * @param dto 群公告数据传输对象（该接口已取消，参数不再使用）
     * @return 功能已取消的提示信息
     */
    @PostMapping("/group/name")
    public Result updateGroupName(@RequestBody GroupNoticeDTO dto) {
        return Result.fail("自定义群名功能已取消");
    }

    /**
     * 获取所有会话未读消息数
     * GET /api/chat/unread-count
     *
     * @return 各会话未读消息数 Map
     */
    @GetMapping("/unread-count")
    public Result getUnreadCount() {
        return chatService.getUnreadCount();
    }
}
