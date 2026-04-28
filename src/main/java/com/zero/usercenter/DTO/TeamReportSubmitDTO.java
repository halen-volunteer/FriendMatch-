package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 团队举报提交 DTO。
 */
@Data
public class TeamReportSubmitDTO {

    /** 被举报团队ID。 */
    private Long reportedTeamId;

    /** 举报原因码（1~5，具体枚举由业务字典定义）。 */
    private Integer reportReason;

    /** 举报文本内容。 */
    private String reportContent;

    /** 举报证据（图片/附件URL，支持扩展格式）。 */
    private String reportEvidence;
}
