package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 消息收藏请求 DTO。
 */
@Data
public class MessageCollectDTO {

    /** 需要收藏的消息ID。 */
    private Long messageId;

    /** 收藏备注（可选，便于后续检索）。 */
    private String collectionNote;
}
