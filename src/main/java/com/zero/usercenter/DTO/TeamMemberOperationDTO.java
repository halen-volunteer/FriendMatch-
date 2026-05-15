package com.zero.usercenter.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 成员操作 DTO。
 * 用于移除成员、修改角色和转让队长等成员管理场景。
 */
@Data
public class TeamMemberOperationDTO {
    /** 团队 ID。 */
    private Long teamId;

    /** 目标用户 ID。 */
    private Long userId;

    /**
     * 新队长 ID。
     * 兼容 Part7 文档中的 `new_captain_id` 字段，仅在转让队长场景使用。
     */
    @JsonProperty("new_captain_id")
    private Long newCaptainId;

    /** 角色类型，修改角色时使用：2-管理员，3-普通成员。 */
    private Integer roleType;
}
