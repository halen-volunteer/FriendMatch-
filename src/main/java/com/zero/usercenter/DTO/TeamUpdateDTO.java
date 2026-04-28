package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 团队信息编辑 DTO（队长权限）
 */
@Data
public class TeamUpdateDTO {
    /** 团队ID（必填） */
    private Long teamId;
    /** 团队名称 */
    private String teamName;
    /** 团队头像URL */
    private String teamAvatar;
    /** 团队简介 */
    private String teamIntro;
    /** 团队标签 */
    private String teamTags;
    /** 最大成员数 */
    private Integer maxMember;
}
