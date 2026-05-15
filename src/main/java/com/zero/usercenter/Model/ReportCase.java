package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报主单实体。
 * 对应表 `t_report_case`，用于聚合同一目标的举报、审核和申诉状态。
 */
@Data
@TableName("t_report_case")
public class ReportCase {

    /**
     * 主单 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 举报类型：
     * 1-用户举报，2-消息举报，3-团队举报
     */
    private Integer reportType;

    /**
     * 被举报目标 ID：用户 ID / 消息 ID / 团队 ID
     */
    private Long targetId;

    /**
     * 案件状态：0-处理中，1-已结案
     */
    private Integer caseStatus;

    /**
     * 聚合举报次数。
     */
    private Integer reportCount;

    /**
     * 最近一次举报时间
     */
    private LocalDateTime latestReportTime;

    /**
     * 优先级：0-普通，1-高优先级
     */
    private Integer priorityLevel;

    /**
     * AI 检查结果：
     * 0-待检查或不适用，1-违规，2-正常
     */
    private Integer aiCheckResult;

    /**
     * AI 置信度：0-100
     */
    private Integer aiConfidence;

    /**
     * 管理员处理状态：
     * 0-待审核，1-成立，2-驳回，3-忽略
     */
    private Integer adminStatus;

    /**
     * 管理员处理动作编码
     */
    private Integer adminAction;

    /**
     * 管理员备注
     */
    private String adminNote;

    /**
     * 当前处理管理员 ID。
     */
    private Long adminId;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

    /**
     * 已申诉次数。
     */
    private Integer appealCount;

    /**
     * 是否删除：0-否，1-是
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
