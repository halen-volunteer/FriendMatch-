package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 申请加入团队 DTO
 */
@Data
public class TeamApplyDTO {
    /** 团队ID */
    private Long teamId;
    /** 申请备注 */
    private String applyMsg;
}
