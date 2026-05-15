package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 撤回消息 DTO。
 * 用于发送者在允许时间窗口内撤回指定消息。
 */
@Data
public class MsgRevokeDTO {

    /** 待撤回的消息 ID。 */
    private Long msgId;
}
