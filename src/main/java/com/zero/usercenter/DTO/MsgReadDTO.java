package com.zero.usercenter.DTO;

import lombok.Data;

import java.util.List;

/**
 * 标记消息已读 DTO
 */
@Data
public class MsgReadDTO {

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 消息ID列表
     */
    private List<Long> msgIds;
}
