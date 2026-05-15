package com.zero.usercenter.Service.impl;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 聊天域聚合服务。
 * 这里主要负责暴露统一的聊天业务入口，具体实现拆分在发送、已读、公告、举报和消息管理等子服务中。
 */
@Service
public class ChatServiceImpl implements ChatService {

    @Resource private ChatSupportService chatSupportService;
    @Resource private ChatMessageManageService chatMessageManageService;
    @Resource private ChatReportManageService chatReportManageService;
    @Resource private ChatSendService chatSendService;
    @Resource private ChatNoticeService chatNoticeService;
    @Resource private ChatReadService chatReadService;

    // ==================== 私聊 ====================

    /**
     * 发送私聊消息
     * 校验链：黑名单（双向）→ 隐私设置（sendMsg）→ 全局禁言 → 敏感词
     * 入库后：写送达回执、未读数自增、WebSocket 实时推送
     *
     * @param dto 私聊消息数据传输对象
     * @return 消息 ID
     */
    @Override
    public Result sendPrivateMsg(PrivateMsgDTO dto) {
        return chatSendService.sendPrivateMsg(dto);
    }

    /**
     * 查询私聊历史记录
     * 按会话 ID 分页查，查询后异步标记已读并清零未读数
     *
     * @param friendId    对方用户 ID
     * @param beforeMsgId 向前翻页游标；为空时返回最新一页
     * @param pageSize    每次返回的消息条数
     * @return 游标分页后的私聊消息列表
     */
    @Override
    public Result getPrivateHistory(Long friendId, Long beforeMsgId, int pageSize) {
        return chatSendService.getPrivateHistory(friendId, beforeMsgId, pageSize);
    }

    // ==================== 群聊 ====================

    /**
     * 发送群聊消息
     * 校验链：团队成员资格 → 全局禁言 → 全员禁言 → 个人禁言 → 敏感词
     * 入库后：写送达回执、未读数自增（所有成员）、WebSocket 广播
     *
     * @param dto 群聊消息数据传输对象
     * @return 消息 ID
     */
    @Override
    public Result sendTeamMsg(TeamMsgDTO dto) {
        return chatSendService.sendTeamMsg(dto);
    }

    /**
     * 查询群聊历史记录
     * 按会话 ID 分页查，需校验成员身份，查询后异步标记已读
     *
     * @param teamId      团队 ID
     * @param beforeMsgId 向前翻页游标；为空时返回最新一页
     * @param pageSize    每次返回的消息条数
     * @return 游标分页后的群聊消息列表
     */
    @Override
    public Result getTeamHistory(Long teamId, Long beforeMsgId, int pageSize) {
        return chatSendService.getTeamHistory(teamId, beforeMsgId, pageSize);
    }

    /**
     * 查询消息举报状态（举报人本人可查）
     * 返回 AI 检测结果、置信度、管理员处理状态
     *
     * @param reportId 举报记录 ID
     * @return 举报详情
     */
    @Override
    public Result getMsgReportStatus(Long reportId) {
        return chatReportManageService.getMsgReportStatus(reportId);
    }

    /**
     * 设置群公告（仅队长/管理员）
     * 写入 Redis String：group_notice:{conversationId}，TTL 30 天
     *
     * @param dto 群公告数据传输对象（conversationId、notice）
     * @return 操作结果
     */
    @Override
    public Result setGroupNotice(GroupNoticeDTO dto) {
        return chatNoticeService.setGroupNotice(dto);
    }

    /**
     * 获取群公告
     * 从 Redis 读取，不存在则返回空字符串
     *
     * @param conversationId 群聊会话 ID（team_{teamId}）
     * @return 群公告内容
     */
    @Override
    public Result getGroupNotice(String conversationId) {
        return chatNoticeService.getGroupNotice(conversationId);
    }

    // ==================== 消息管理 ====================

    /**
     * 编辑消息
     * 仅限发送者操作，5 分钟内可编辑，已撤回消息不可编辑
     * 编辑后 WebSocket 推送 message_edit 事件给对方/群成员
     *
     * @param dto 编辑数据传输对象（msgId、newContent）
     * @return 操作结果
     */
    @Override
    public Result editMsg(MsgEditDTO dto) {
        return chatMessageManageService.editMsg(dto);
    }

    /**
     * 撤回消息
     * 仅限发送者操作，5 分钟内可撤回
     * 撤回前检查是否有未处理的举报（admin_status=0），有则拒绝撤回
     * 物理删除消息，并级联删除关联的回执、收藏、置顶、举报记录
     * 异步推送 message_revoke 事件给对方/群成员
     *
     * @param dto 撤回数据传输对象（msgId）
     * @return 操作结果
     */
    @Override
    public Result revokeMsg(MsgRevokeDTO dto) {
        return chatMessageManageService.revokeMsg(dto);
    }

    /**
     * 收藏消息
     * 唯一索引防重复收藏，支持添加收藏备注
     *
     * @param dto 收藏数据传输对象（messageId、collectionNote）
     * @return 收藏记录 ID
     */
    @Override
    public Result collectMsg(MessageCollectDTO dto) {
        return chatMessageManageService.collectMsg(dto);
    }

    /**
     * 取消收藏消息
     * 仅限收藏人操作，软删除（is_delete=1）
     *
     * @param collectionId 收藏记录 ID
     * @return 操作结果
     */
    @Override
    public Result cancelCollect(Long collectionId) {
        return chatMessageManageService.cancelCollect(collectionId);
    }

    /**
     * 获取收藏列表
     * 分页查当前用户的收藏记录，关联查消息内容和发送者
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @return 收藏列表及总数
     */
    @Override
    public Result getCollections(int page, int pageSize) {
        return chatMessageManageService.getCollections(page, pageSize);
    }

    /**
     * 置顶消息
     * 群聊需队长/管理员权限，唯一索引防重复置顶
     * pin_order = 当前会话已有置顶数 + 1
     *
     * @param dto 置顶数据传输对象（conversationId、messageId）
     * @return 置顶记录 ID
     */
    @Override
    public Result pinMsg(MessagePinDTO dto) {
        return chatMessageManageService.pinMsg(dto);
    }

    /**
     * 取消置顶消息
     * 仅限置顶操作人取消，软删除（is_delete=1）
     *
     * @param pinId 置顶记录 ID
     * @return 操作结果
     */
    @Override
    public Result unpinMsg(Long pinId) {
        return chatMessageManageService.unpinMsg(pinId);
    }

    /**
     * 获取会话置顶消息列表
     * 按 pin_order 升序返回，关联查消息内容
     *
     * @param conversationId 会话 ID
     * @return 置顶列表
     */
    @Override
    public Result getPinList(String conversationId) {
        return chatMessageManageService.getPinList(conversationId);
    }

    /**
     * 在指定会话内搜索消息。
     * 当前实现会先拉取会话消息，再基于不同消息类型提取可搜索文本后做内存过滤。
     */
    @Override
    public Result searchMsg(String conversationId, String keyword, int page, int pageSize) {
        return chatMessageManageService.searchMsg(conversationId, keyword, page, pageSize);
    }

    /**
     * 举报消息
     * 立即入库（ai_check_result=0），异步虚拟线程 AI 检测：
     * 优先通义千问，失败降级本地敏感词库；
     * AI 判定违规则自动触发梯度处罚，并通知举报人结果
     *
     * @param dto 举报数据传输对象（messageId、reportReason、reportContent）
     * @return 举报记录 ID
     */
    @Override
    public Result reportMsg(MessageReportDTO dto) {
        return chatReportManageService.reportMsg(dto);
    }

    /**
     * 管理员查询举报详情（含被举报消息及上下文）
     * 返回举报记录详情 + 被举报消息前后各50条消息，供管理员人工判断
     *
     * @param reportId 举报记录 ID
     * @return 举报详情及上下文消息列表（report、targetMsg、beforeMsgs、afterMsgs）
     */
    @Override
    public Result adminGetReportContext(Long reportId) {
        return chatReportManageService.adminGetReportContext(reportId);
    }

    /**
     * 管理员分页查询举报列表
     * 优先展示待处理（adminStatus=0），同状态按创建时间降序
     *
     * @param adminStatus 处理状态（0-待处理，1-已处理，2-驳回），null 查全部
     * @param page        页码
     * @param pageSize    每页数量
     * @return 举报列表及总数
     */
    @Override
    public Result adminGetMsgReportList(Integer adminStatus, int page, int pageSize) {
        return chatReportManageService.adminGetMsgReportList(adminStatus, page, pageSize);
    }

    /**
     * 管理员处理消息举报（支持多轮申诉）
     * adminDecision: 1-维持处罚  2-撤销处罚/不违规  3-确认违规执行处罚
     *
     * @param dto 举报处理数据传输对象（reportId、adminDecision、adminNote）
     * @return 处理结果
     */
    @Override
    public Result adminHandleMsgReport(MessageReportHandleDTO dto) {
        return chatReportManageService.adminHandleMsgReport(dto);
    }

    /**
     * 标记消息为已读
     * 批量写入已读回执，清零会话未读计数
     * 群聊消息同步写入 Redis Bitmap（用于群聊已读统计）
     *
     * @param dto 已读数据传输对象（conversationId、msgIds）
     * @return 操作结果
     */
    @Override
    public Result markMsgRead(MsgReadDTO dto) {
        return chatReadService.markMsgRead(dto);
    }

    /**
     * 获取所有会话未读消息数
     * 直接 SCAN Redis 中当前用户的所有未读 key（unread:{userId}:*），
     * 不依赖消息表查询会话列表，彻底避免 LIMIT 截断导致的漏统计问题
     * 同时查每个会话最后一条消息作为预览
     *
     * @return { totalUnread, conversations: [{conversationId, unreadCount, lastMessage}] }
     */
    @Override
    public Result getUnreadCount() {
        return chatReadService.getUnreadCount();
    }

    /**
     * 获取最近会话列表。
     * 返回用户有访问权限、且未被主动隐藏的最近活跃会话。
     */
    @Override
    public Result getRecentConversations() {
        return chatReadService.getRecentConversations();
    }

    /**
     * 从当前用户的会话列表中移除指定会话。
     * 这里只隐藏会话入口，不会删除好友关系、团队关系或历史消息。
     */
    @Override
    public Result hideConversation(String conversationId) {
        return chatReadService.hideConversation(conversationId);
    }

}
