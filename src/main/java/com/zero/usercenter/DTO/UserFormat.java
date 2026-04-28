package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户登录/注册后返回给前端的脱敏数据，同时作为 Redis Token 缓存结构。
 * 字段对齐 t_user 表，确保 BeanUtil.fillBeanWithMap() 能正确映射。
 */
@Data
public class UserFormat {

    /**
     * 用户主键ID（后续业务通过 UserHolder.getUser().getId() 直接使用）
     */
    private Long id;

    /**
     * 公开唯一账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userNickname;

    /**
     * 用户头像URL
     */
    private String userAvatar;

    /**
     * 私密邮箱（仅自己可见，脱敏展示）
     */
    private String userEmail;

    /**
     * 用户标签（逗号分隔）
     */
    private String userTags;

    /**
     * 用户个人简介
     */
    private String userIntro;

    /** 是否管理员 */
    private Boolean isAdmin;

    /** 管理员ID（t_admin_user.id） */
    private Long adminId;

    /** 管理员显示名 */
    private String adminName;
}
