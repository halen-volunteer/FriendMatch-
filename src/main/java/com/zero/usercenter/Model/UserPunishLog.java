package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户处罚主记录表
 * @TableName t_user_punish_log
 */
@TableName(value = "t_user_punish_log")
@Data
public class UserPunishLog {

    /** 处罚记录主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被处罚用户ID */
    private Long punishUserId;

    /**
     * 处罚类型：1-全局禁言，2-永久封号
     */
    private Integer punishType;

    /** 关联团队ID（已废弃，始终为 NULL） */
    private Long teamId;

    /** 处罚原因 */
    private String punishReason;

    /** 禁言时长（单位：分钟，封号填-1） */
    private Integer punishDuration;

    /** 处罚开始时间 */
    private LocalDateTime punishStartTime;

    /** 处罚结束时间（封号填NULL） */
    private LocalDateTime punishEndTime;

    /**
     * 操作类型：1-系统自动，2-管理员手动
     */
    private Integer operateType;

    /** 操作人ID（系统自动填NULL） */
    private Long operateUserId;

    /** 是否撤销处罚：0-未撤销，1-已撤销 */
    private Integer isCancel;

    /** 处罚撤销时间 */
    private LocalDateTime cancelTime;

    /** 撤销人ID（管理员） */
    private Long cancelUserId;

    /** 撤销原因 */
    private String cancelReason;

    /**
     * 处罚记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
