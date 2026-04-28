package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 申诉提交 DTO。
 */
@Data
public class AppealSubmitDTO {

    /** 关联举报记录ID。 */
    private Long relatedReportId;

    /** 关联举报类型（1-用户举报，2-消息举报，3-团队举报）。 */
    private Integer relatedReportType;

    /** 申诉人类型（1-用户，2-其他业务角色）。 */
    private Integer appellantType;

    /** 申诉理由（必填）。 */
    private String appealReason;

    /** 申诉证据（可选，图片/附件URL）。 */
    private String appealEvidence;

    /** 关联处罚记录ID（可选，通常用于撤销处罚联动）。 */
    private Long relatedPunishId;
}
