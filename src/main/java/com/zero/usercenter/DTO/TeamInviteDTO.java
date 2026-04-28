package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 邀请加入团队 DTO
 */
@Data
public class TeamInviteDTO {
    /** 团队ID */
    private Long teamId;
    /** 被邀请的用户ID */
    private Long userId;
}
