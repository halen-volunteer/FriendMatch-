package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 禁言操作 DTO
 */
@Data
public class TeamMuteDTO {
    /** 团队ID */
    private Long teamId;
    /** 目标用户ID（全员禁言时不传） */
    private Long userId;
    /** 是否全员禁言（全员禁言接口用） */
    private Boolean isMute;
    /** 禁言时长（分钟，禁言指定成员时用） */
    private Integer muteDuration;
}
