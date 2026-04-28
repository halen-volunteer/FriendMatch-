package com.zero.usercenter.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 成员操作 DTO（移除/修改角色/转让队长）
 */
@Data
public class TeamMemberOperationDTO {
    /** 团队ID */
    private Long teamId;

    /** 目标用户ID */
    private Long userId;

    /**
     * 新队长ID（兼容 Part7 文档中的 new_captain_id 字段）
     * 仅转让队长接口使用；若 userId 为空则使用该字段
     */
    @JsonProperty("new_captain_id")
    private Long newCaptainId;

    /** 角色类型（修改角色时用）：2-管理员，3-普通成员 */
    private Integer roleType;
}
