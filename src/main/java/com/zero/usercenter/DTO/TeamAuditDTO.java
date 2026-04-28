package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 审批加入申请 DTO
 */
@Data
public class TeamAuditDTO {
    /** 申请记录ID */
    private Long applyId;
    /** 审核状态：1-通过，2-拒绝 */
    private Integer auditStatus;
    /** 审核备注 */
    private String auditMsg;
}
