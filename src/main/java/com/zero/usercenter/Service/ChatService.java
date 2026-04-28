package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.DTO.Result;

/**
 * 聊天系统 Service 接口
 */
public interface ChatService {

    /**
     * 发送私聊消息
     * 检查黑名单、隐私设置、全局禁言
     *
     * @param dto 私聊消息数据传输对象，包含接收方 ID、消息内容、消息类型等
     * @return 发送结果
     */
    Result sendPrivateMsg(PrivateMsgDTO dto);

    /**
     * 查询私聊历史记录
     *
     * @param friendId 好友用户 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 私聊历史消息列表
     */
    Result getPrivateHistory(Long friendId, int page, int pageSize);

    /**
     * 发送群聊消息
     * 检查成员资格、全局禁言、团队禁言
     *
     * @param dto 群聊消息数据传输对象，包含团队 ID、消息内容、消息类型等
     * @return 发送结果
     */
    Result sendTeamMsg(TeamMsgDTO dto);

    /**
     * 查询群聊历史记录
     *
     * @param teamId   团队 ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 群聊历史消息列表
     */
    Result getTeamHistory(Long teamId, int page, int pageSize);

    /**
     * 编辑消息（5分钟内）
     *
     * @param dto 消息编辑数据传输对象，包含消息 ID 和新内容
     * @return 编辑结果
     */
    Result editMsg(MsgEditDTO dto);

    /**
     * 撤回消息（5分钟内）
     *
     * @param dto 消息撤回数据传输对象，包含消息 ID
     * @return 撤回结果
     */
    Result revokeMsg(MsgRevokeDTO dto);

    /**
     * 收藏消息
     *
     * @param dto 消息收藏数据传输对象，包含消息 ID
     * @return 收藏结果
     */
    Result collectMsg(MessageCollectDTO dto);

    /**
     * 取消收藏
     *
     * @param collectionId 收藏记录 ID
     * @return 取消收藏结果
     */
    Result cancelCollect(Long collectionId);

    /**
     * 获取收藏列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 收藏消息列表
     */
    Result getCollections(int page, int pageSize);

    /**
     * 置顶消息
     *
     * @param dto 消息置顶数据传输对象，包含消息 ID 和会话 ID
     * @return 置顶结果
     */
    Result pinMsg(MessagePinDTO dto);

    /**
     * 取消置顶
     *
     * @param pinId 置顶记录 ID
     * @return 取消置顶结果
     */
    Result unpinMsg(Long pinId);

    /**
     * 获取会话置顶列表
     *
     * @param conversationId 会话 ID（如 private_1_2 或 team_1001）
     * @return 置顶消息列表
     */
    Result getPinList(String conversationId);

    /**
     * 搜索会话消息
     *
     * @param conversationId 会话 ID
     * @param keyword        搜索关键词
     * @param page           页码
     * @param pageSize       每页条数
     * @return 符合条件的消息列表
     */
    Result searchMsg(String conversationId, String keyword, int page, int pageSize);

    /**
     * 举报消息
     *
     * @param dto 消息举报数据传输对象，包含消息 ID 和举报原因
     * @return 举报结果
     */
    Result reportMsg(MessageReportDTO dto);

    /**
     * 管理员查询举报详情（含被举报消息及上下文）
     *
     * @param reportId 举报记录 ID
     * @return 举报详情及前后各10条上下文消息
     */
    Result adminGetReportContext(Long reportId);

    /**
     * 管理员查询举报列表
     *
     * @param adminStatus 处理状态（0-待处理，1-处理中，2-已处理，null-全部）
     * @param page        页码
     * @param pageSize    每页条数
     * @return 举报列表
     */
    Result adminGetMsgReportList(Integer adminStatus, int page, int pageSize);

    /**
     * 管理员处理举报
     *
     * @param dto 举报处理数据传输对象，包含举报 ID 和处理决定
     * @return 处理结果
     */
    Result adminHandleMsgReport(MessageReportHandleDTO dto);

    /**
     * 提交申诉（举报人A或被举报人B均可，总次数上限3次）
     *
     * @param reportId 举报记录ID
     * @return 申诉结果
     */
    Result appealMsgReport(Long reportId);

    /**
     * 查询举报状态
     *
     * @param reportId 举报记录 ID
     * @return 举报状态信息
     */
    Result getMsgReportStatus(Long reportId);

    /**
     * 设置群公告
     *
     * @param dto 群公告数据传输对象，包含会话 ID 和公告内容
     * @return 设置结果
     */
    Result setGroupNotice(GroupNoticeDTO dto);

    /**
     * 获取群公告
     *
     * @param conversationId 会话 ID（如 team_1001）
     * @return 群公告内容
     */
    Result getGroupNotice(String conversationId);

    /**
     * 标记消息为已读
     *
     * @param dto 消息已读数据传输对象，包含会话 ID 和最后已读消息 ID
     * @return 标记结果
     */
    Result markMsgRead(MsgReadDTO dto);

    /**
     * 获取所有会话未读消息数
     *
     * @return 各会话未读消息数 Map
     */
    Result getUnreadCount();
}
