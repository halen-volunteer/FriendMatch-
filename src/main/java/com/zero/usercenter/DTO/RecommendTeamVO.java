package com.zero.usercenter.DTO;

import lombok.Data;

@Data
public class RecommendTeamVO {
    private Long recommendId;
    private Long teamId;
    private String teamName;
    private String teamAvatar;
    private String teamIntro;
    private String teamTags;
    private Integer memberCount;
    private Integer commonMembers;
    private Integer recommendScore;
    private String recommendReason;
}
