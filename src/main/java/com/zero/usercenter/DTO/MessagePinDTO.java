package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 消息置顶请求 DTO。
 */
@Data
public class MessagePinDTO {

    /** 会话ID（私聊或群聊会话唯一标识）。 */
    private String conversationId;

    /** 需要置顶的消息ID。 */
    private Long messageId;
}
