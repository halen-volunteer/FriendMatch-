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
    /** 团队类型：1-公开，2-私有 */
    private Integer teamType;
    /** 加入规则：1-申请审批，2-仅邀请，3-密码加入 */
    private Integer joinRule;
    /** 加入密码，仅 joinRule=3 时需要 */
    private String joinPassword;
}
