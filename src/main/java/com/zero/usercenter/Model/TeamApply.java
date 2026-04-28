package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队加入申请表
 * @TableName t_team_apply
 */
@TableName(value = "t_team_apply")
@Data
public class TeamApply {

    /** 申请主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联团队ID */
    private Long teamId;

    /** 申请人ID */
    private Long applyUserId;

    /** 审核人ID（队长/管理员） */
    private Long auditUserId;

    /** 申请备注 */
    private String applyMsg;

    /**
     * 审核状态：
     * 0-待审核
     * 1-通过
     * 2-拒绝
     */
    private Integer auditStatus;

    /** 审核备注 */
    private String auditMsg;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 是否软删除：0-否，1-是 */
    private Integer isDelete;

    /** 申请时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
