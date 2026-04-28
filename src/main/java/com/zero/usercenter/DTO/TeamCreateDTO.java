package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 团队创建/编辑 DTO
 */
@Data
public class TeamCreateDTO {
    /** 团队名称（1-64字符） */
    private String teamName;
    /** 团队头像URL */
    private String teamAvatar;
    /** 团队简介（0-512字符） */
    private String teamIntro;
    /** 团队标签（逗号分隔，最多5个） */
    private String teamTags;
    /** 最大成员数（1-1000） */
    private Integer maxMember;
    /** 团队类型：1-公开，2-私有 */
    private Integer teamType;
    /** 加入规则：1-申请审批，2-仅邀请，3-密码加入 */
    private Integer joinRule;
    /** 加入密码（仅 joinRule=3 时需要） */
    private String joinPassword;
}
