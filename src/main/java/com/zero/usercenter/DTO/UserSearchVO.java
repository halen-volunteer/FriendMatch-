package com.zero.usercenter.DTO;

import lombok.Data;

@Data
public class UserSearchVO {
    private Long userId;
    private String userAccount;
    private String userNickname;
    private String userAvatar;
    private String userIntro;
    private String userTags;
    private Integer similarityScore;
    private String matchReason;
}
