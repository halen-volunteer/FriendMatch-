package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 消息举报提交 DTO。
 */
@Data
public class MessageReportDTO {

    /**
     * 被举报消息 ID。
     */
    private Long messageId;

    /**
     * 举报原因编码：
     * 1-色情，2-暴力，3-骚扰，4-广告，5-诈骗，6-其他
     */
    private Integer reportReason;

    /**
     * 举报补充说明。
     */
    private String reportContent;
}
