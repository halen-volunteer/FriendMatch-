package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户资料更新 DTO
 */
@Data
public class UserProfileUpdateDTO {

    /**
     * 用户昵称（3-16位）
     */
    private String userNickname;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    /**
     * 用户个人简介（0-512个字符）
     */
    private String userIntro;

    /**
     * 用户标签（逗号分隔，最多5个）
     */
    private String userTags;
}
