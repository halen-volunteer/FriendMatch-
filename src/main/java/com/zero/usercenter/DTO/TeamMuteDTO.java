package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 团队禁言操作 DTO。
 * 同时复用于全员禁言、成员禁言和解除禁言等场景，
 * 不同接口会按业务场景读取其中不同字段。
 */
@Data
public class TeamMuteDTO {
    /** 团队 ID。 */
    private Long teamId;

    /** 目标用户 ID，全员禁言场景下可不传。 */
    private Long userId;

    /** 是否开启全员禁言，仅全员禁言接口使用。 */
    private Boolean isMute;

    /** 禁言时长，单位分钟；用于全员禁言或成员禁言的生效时长。 */
    private Integer muteDuration;
}
