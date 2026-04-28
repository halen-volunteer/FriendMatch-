package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 黑名单操作 DTO
 */
@Data
public class BlacklistOperationDTO {

    /**
     * 被拉黑用户 ID
     */
    private Long blackUserId;

    /**
     * 拉黑原因（可选）
     */
    private String blackReason;
}
