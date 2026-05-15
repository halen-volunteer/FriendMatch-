package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员关系实体。
 * 对应表 `t_team_member`，用于承载角色、禁言和入退队状态。
 */
@TableName(value = "t_team_member")
@Data
public class TeamMember {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团队 ID。 */
    private Long teamId;

    /** 用户 ID。 */
    private Long userId;

    /** 角色类型（1-队长，2-管理员，3-普通成员） */
    private Integer roleType;

    /** 团队内禁言状态（0-正常，1-禁言） */
    private Integer teamMuteType;

    /** 加入来源（1-直接加入，2-邀请加入，3-申请审批） */
    private Integer joinSource;

    /** 邀请人用户 ID。 */
    private Long inviteUserId;

    /** 团队内禁言解除时间 */
    private LocalDateTime teamMuteUnpunishTime;

    /** 加入团队时间 */
    private LocalDateTime joinTime;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 退出团队时间 */
    private LocalDateTime quitTime;

    /** 是否已退出（0-否，1-是） */
    private Integer isQuit;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
