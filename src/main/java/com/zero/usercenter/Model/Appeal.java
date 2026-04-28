package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申诉实体，对应表 `t_appeal`。
 */
@Data
@TableName("t_appeal")
public class Appeal {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申诉人ID */
    private Long appellantId;

    /** 申诉人类型 */
    private Integer appellantType;

    /** 关联举报ID */
    private Long relatedReportId;

    /** 关联举报类型 */
    private Integer relatedReportType;

    /** 关联处罚记录ID */
    private Long relatedPunishId;

    /** 申诉轮次 */
    private Integer appealRound;

    /** 申诉理由 */
    private String appealReason;

    /** 申诉证据 */
    private String appealEvidence;

    /** 申诉状态 */
    private Integer appealStatus;

    /** 处理管理员ID */
    private Long adminId;

    /** 管理员回复 */
    private String adminReply;

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
