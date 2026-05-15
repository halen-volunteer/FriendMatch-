package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.DTO.Result;

/**
 * 聊天服务接口。
 * 聚合私聊、群聊、消息管理、举报和会话态相关能力。
 */
public interface ChatService {

    /**
     * 发送私聊消息。
     * 发送前需要校验黑名单、隐私设置和全局禁言状态。
     *
     * @param dto 私聊消息参数，包含接收方、消息类型和消息内容
     * @return 统一响应结果，成功时包含已发送消息信息
     */
    Result sendPrivateMsg(PrivateMsgDTO dto);

    /**
     * 查询私聊历史记录。
     *
     * @param friendId 对方用户 ID
     * @param beforeMsgId 向前翻页游标；为空时表示拉取最新一页
     * @param pageSize 每次返回的消息条数，服务端会做上限保护
     * @return 统一响应结果，成功时包含游标分页后的私聊历史数据
     */
    Result getPrivateHistory(Long friendId, Long beforeMsgId, int pageSize);

    /**
     * 发送群聊消息。
     * 发送前需要校验成员资格、全局禁言和团队禁言状态。
     *
     * @param dto 群聊消息参数，包含团队 ID、消息类型和消息内容
     * @return 统一响应结果，成功时包含已发送消息信息
     */
    Result sendTeamMsg(TeamMsgDTO dto);

    /**
     * 查询群聊历史记录。
     *
     * @param teamId 团队 ID
     * @param beforeMsgId 向前翻页游标；为空时表示拉取最新一页
     * @param pageSize 每次返回的消息条数，服务端会做上限保护
     * @return 统一响应结果，成功时包含游标分页后的群聊历史数据
     */
    Result getTeamHistory(Long teamId, Long beforeMsgId, int pageSize);

    /**
     * 编辑消息。
     * 当前仅允许 5 分钟内编辑本人消息。
     *
     * @param dto 消息编辑参数，包含消息 ID 和新的消息内容
     * @return 统一响应结果，成功时表示消息已更新
     */
    Result editMsg(MsgEditDTO dto);

    /**
     * 撤回消息。
     * 当前仅允许 5 分钟内撤回本人消息。
     *
     * @param dto 消息撤回参数，包含消息 ID 和会话信息
     * @return 统一响应结果，成功时表示消息已撤回
     */
    Result revokeMsg(MsgRevokeDTO dto);

    /**
     * 收藏消息。
     *
     * @param dto 消息收藏参数，包含消息 ID 和会话信息
     * @return 统一响应结果，成功时表示消息已加入收藏
     */
    Result collectMsg(MessageCollectDTO dto);

    /**
     * 取消收藏。
     *
     * @param collectionId 收藏记录 ID
     * @return 统一响应结果，成功时表示收藏已取消
     */
    Result cancelCollect(Long collectionId);

    /**
     * 获取收藏列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含收藏分页列表
     */
    Result getCollections(int page, int pageSize);

    /**
     * 置顶消息。
     *
     * @param dto 消息置顶参数，包含消息 ID 和会话标识
     * @return 统一响应结果，成功时表示消息已置顶
     */
    Result pinMsg(MessagePinDTO dto);

    /**
     * 取消置顶。
     *
     * @param pinId 置顶记录 ID
     * @return 统一响应结果，成功时表示消息已取消置顶
     */
    Result unpinMsg(Long pinId);

    /**
     * 获取会话置顶列表。
     *
     * @param conversationId 会话 ID
     * @return 统一响应结果，成功时包含该会话下的置顶消息列表
     */
    Result getPinList(String conversationId);

    /**
     * 搜索会话消息。
     *
     * @param conversationId 会话 ID
     * @param keyword 搜索关键词
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含匹配的消息分页数据
     */
    Result searchMsg(String conversationId, String keyword, int page, int pageSize);

    /**
     * 举报消息。
     *
     * @param dto 消息举报参数，包含消息 ID、举报原因和补充说明
     * @return 统一响应结果，成功时表示举报已提交
     */
    Result reportMsg(MessageReportDTO dto);

    /**
     * 管理员查询消息举报详情和上下文。
     *
     * @param reportId 消息举报单 ID
     * @return 统一响应结果，成功时包含举报详情及消息上下文
     */
    Result adminGetReportContext(Long reportId);

    /**
     * 管理员分页查询消息举报列表。
     *
     * @param adminStatus 管理端处理状态筛选条件
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含消息举报分页列表
     */
    Result adminGetMsgReportList(Integer adminStatus, int page, int pageSize);

    /**
     * 管理员处理消息举报。
     *
     * @param dto 举报处理参数，包含举报单 ID、处理结果和处罚动作
     * @return 统一响应结果，成功时表示消息举报已处理
     */
    Result adminHandleMsgReport(MessageReportHandleDTO dto);

    /**
     * 查询消息举报状态。
     *
     * @param reportId 消息举报单 ID
     * @return 统一响应结果，成功时包含举报处理状态
     */
    Result getMsgReportStatus(Long reportId);

    /**
     * 设置群公告。
     *
     * @param dto 群公告参数，包含会话标识和公告内容
     * @return 统一响应结果，成功时表示群公告已更新
     */
    Result setGroupNotice(GroupNoticeDTO dto);

    /**
     * 获取群公告。
     *
     * @param conversationId 会话 ID
     * @return 统一响应结果，成功时包含当前群公告内容
     */
    Result getGroupNotice(String conversationId);

    /**
     * 标记消息为已读。
     *
     * @param dto 已读参数，包含会话 ID、消息范围或消息 ID 等信息
     * @return 统一响应结果，成功时表示已读状态已更新
     */
    Result markMsgRead(MsgReadDTO dto);

    /**
     * 获取所有会话未读消息数。
     *
     * @return 统一响应结果，成功时包含所有会话的未读数量统计
     */
    Result getUnreadCount();

    /**
     * 获取当前用户最近会话列表。
     *
     * @return 统一响应结果，成功时包含最近会话列表
     */
    Result getRecentConversations();

    /**
     * 将指定会话从当前用户最近会话列表中隐藏。
     *
     * @param conversationId 会话 ID
     * @return 统一响应结果，成功时表示会话已隐藏
     */
    Result hideConversation(String conversationId);
}
