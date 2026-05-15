package com.zero.usercenter.DTO;

import lombok.Data;

import java.util.List;

/**
 * 标记消息已读 DTO。
 * 前端上报“当前会话哪些消息已经读过”时使用。
 */
@Data
public class MsgReadDTO {

    /** 会话 ID，用于定位清理哪一个会话的未读数。 */
    private String conversationId;

    /** 本次确认已读的消息 ID 列表。 */
    private List<Long> msgIds;
}
