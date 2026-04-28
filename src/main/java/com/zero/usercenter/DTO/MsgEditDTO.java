package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 消息编辑请求 DTO。
 */
@Data
public class MsgEditDTO {

    /** 待编辑消息ID。 */
    private Long msgId;

    /** 编辑后的新内容。 */
    private String newContent;
}
