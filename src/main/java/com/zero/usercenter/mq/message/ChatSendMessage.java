package com.zero.usercenter.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天发送异步消息体。
 * 发送接口只做鉴权、校验和快速入队，真正的落库、未读数更新与 WebSocket 推送
 * 都由 MQ 消费端基于这份消息体继续完成。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendMessage {

    /** 业务消息 ID。发送接口在入队前就生成，用于串起发送、编辑、撤回和消费全过程。 */
    private Long msgId;

    /** 发送人用户 ID。 */
    private Long senderId;

    /** 接收类型：1-私聊，2-群聊（团队）。 */
    private Integer recvType;

    /** 接收目标 ID：私聊时是接收方用户 ID，群聊时是团队 ID。 */
    private Long recvId;

    /** 会话 ID。私聊形如 minUid_maxUid，群聊形如 team_{teamId}。 */
    private String conversationId;

    /** 消息类型：1-文本，2-图片，3-文件，4-表情，5-@消息。 */
    private Integer msgType;

    /** 已按后端统一规则编码后的消息内容。 */
    private String msgContent;

    /** 发送时间。这里在入队前就固化，保证返回前端和最终落库时间一致。 */
    private LocalDateTime createTime;

    /** @ 消息涉及的目标用户列表，私聊消息为空。 */
    private List<Long> atUserIds;
}
