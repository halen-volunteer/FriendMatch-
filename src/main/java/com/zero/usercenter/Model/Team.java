package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队基础信息表
 * @TableName t_team
 */
@TableName(value = "t_team")
@Data
public class Team {

    /** 团队主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团队名称（1-64字符） */
    private String teamName;

    /** 团队头像URL */
    private String teamAvatar;

    /** 团队简介（0-512字符） */
    private String teamIntro;

    /** 团队标签（逗号分隔，最多5个） */
    private String teamTags;

    /** 团队创建人ID */
    private Long creatorId;

    /** 团队最大成员数（默认200） */
    private Integer maxMember;

    /**
     * 团队类型：
     * 1-公开团队（所有人可见）
     * 2-私有团队（仅成员可见）
     */
    private Integer teamType;

    /**
     * 加入规则：
     * 1-申请审批（需队长/管理员审批）
     * 2-仅邀请（仅队长邀请）
     * 3-密码加入（输入密码直接加入）
     */
    private Integer joinRule;

    /** 加入密码（仅 join_rule=3 有效，BCrypt加密） */
    private String joinPassword;

    /** 团队全员禁言：0-正常，1-禁言（队长/管理员除外） */
    private Integer teamAllMute;

    /** 是否软删除：0-否，1-是 */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
