package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 群公告 DTO。
 */
@Data
public class GroupNoticeDTO {

    /** 会话 ID，格式通常为 `team_{teamId}`。 */
    private String conversationId;

    /** 公告内容。 */
    private String notice;
}
