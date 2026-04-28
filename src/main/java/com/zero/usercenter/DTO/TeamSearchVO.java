package com.zero.usercenter.DTO;

import lombok.Data;

@Data
public class TeamSearchVO {
    private Long teamId;
    private String teamName;
    private String teamAvatar;
    private String teamIntro;
    private String teamTags;
    private Integer memberCount;
    private Integer isMember;
    private Integer similarityScore;
    private String matchReason;
}
