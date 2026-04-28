package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队举报实体，对应表 `t_team_report`。
 */
@Data
@TableName("t_team_report")
public class TeamReport {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 举报人用户ID */
    private Long reporterId;

    /** 被举报团队ID */
    private Long reportedTeamId;

    /** 举报原因（枚举值） */
    private Integer reportReason;

    /** 举报内容 */
    private String reportContent;

    /** 举报证据（URL或文本） */
    private String reportEvidence;

    /** 举报状态 */
    private Integer reportStatus;

    /** 管理员处理动作 */
    private Integer adminAction;

    /** 管理员处理备注 */
    private String adminNote;

    /** 管理员ID */
    private Long adminId;

    /** 处理时间 */
    private LocalDateTime processTime;

    /** 软删除标记（0-未删，1-已删） */
    private Integer isDelete;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
