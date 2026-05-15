package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 消息编辑请求 DTO。
 * 用于发送者在允许时间窗口内修改已发送消息内容。
 */
@Data
public class MsgEditDTO {

    /** 待编辑消息ID。 */
    private Long msgId;

    /** 编辑后的新内容，仅适用于文本类消息。 */
    private String newContent;
}
