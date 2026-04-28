package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 密码加入团队 DTO
 */
@Data
public class TeamJoinByPasswordDTO {
    /** 团队ID */
    private Long teamId;
    /** 加入密码 */
    private String password;
}
