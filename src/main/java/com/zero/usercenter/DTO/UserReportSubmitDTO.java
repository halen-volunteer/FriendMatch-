package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户举报提交 DTO。
 */
@Data
public class UserReportSubmitDTO {

    /** 被举报用户ID。 */
    private Long reportedUserId;

    /** 举报原因码（1~6，具体枚举由业务字典定义）。 */
    private Integer reportReason;

    /** 举报文本内容。 */
    private String reportContent;

    /** 举报证据（图片/附件URL，支持扩展格式）。 */
    private String reportEvidence;
}
