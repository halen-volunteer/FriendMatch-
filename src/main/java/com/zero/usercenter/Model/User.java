package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户基础信息表
 * @TableName t_user
 */
@TableName(value = "t_user")
@Data
public class User {

    /**
     * 用户主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 公开唯一账号（6-20位，字母/数字/下划线，用于搜索加好友、登录）
     */
    private String userAccount;

    /**
     * 私密邮箱（用于注册验证、找回密码、登录）
     */
    private String userEmail;

    /**
     * 用户昵称（可重复，用于展示）
     */
    private String userNickname;

    /**
     * 用户头像URL
     */
    private String userAvatar;

    /**
     * 用户个人简介
     */
    private String userIntro;

    /**
     * 登录密码（BCrypt加密密文）
     */
    private String userPassword;

    /**
     * 用户标签（逗号分隔）
     */
    private String userTags;

    /**
     * 隐私设置（JSON字符串）：
     * view_info(1-所有人，2-仅团队成员)；
     * send_msg(1-所有人，2-仅团队成员，3-需验证)；
     * search_by_email(0-不允许通过邮箱搜索，1-允许)
     */
    private String privacySetting;

    /**
     * 全局处罚类型：0-无处罚，1-全局禁言，2-永久封号
     */
    private Integer globalPunishType;

    /**
     * 全局处罚解除时间（禁言有效，封号为NULL）
     */
    private LocalDateTime globalUnpunishTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 是否软删除：0-否，1-是
     */
    private Integer isDelete;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
