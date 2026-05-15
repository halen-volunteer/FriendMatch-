package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申诉实体。
 * 对应表 `t_appeal`，用于承载举报或处罚后的多轮申诉流程。
 */
@Data
@TableName("t_appeal")
public class Appeal {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申诉人 ID。 */
    private Long appellantId;

    /** 申诉人类型。 */
    private Integer appellantType;

    /** 关联举报 ID。 */
    private Long relatedReportId;

    /** 关联举报主单 ID。 */
    private Long relatedCaseId;

    /** 关联举报类型。 */
    private Integer relatedReportType;

    /** 关联处罚记录 ID。 */
    private Long relatedPunishId;

    /** 申诉轮次。 */
    private Integer appealRound;

    /** 申诉理由。 */
    private String appealReason;

    /** 申诉证据。 */
    private String appealEvidence;

    /** 申诉状态。 */
    private Integer appealStatus;

    /** 当前处理管理员 ID。 */
    private Long adminId;

    /** 分配时间。 */
    private LocalDateTime assignTime;

    /** 接单时间。 */
    private LocalDateTime acceptTime;

    /** 最近一次派单时间。 */
    private LocalDateTime lastDispatchTime;

    /** 派单次数。 */
    private Integer dispatchCount;

    /** 管理员回复。 */
    private String adminReply;

    /** 处理时间。 */
    private LocalDateTime processTime;

    /** 软删除标记。 */
    private Integer isDelete;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
