package com.zero.usercenter.DTO;

import lombok.Data;

@Data
public class RecommendUserVO {
    private Long recommendId;
    private Long userId;
    private String userNickname;
    private String userAvatar;
    private String userIntro;
    private String userTags;
    private Integer mutualFriends;
    private Integer mutualTeams;
    private Integer recommendScore;
    private String recommendReason;
}
