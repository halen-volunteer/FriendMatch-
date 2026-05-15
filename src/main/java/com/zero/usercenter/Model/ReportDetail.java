package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报明细实体。
 * 对应表 `t_report_detail`，用于记录每一次具体举报提交的原始内容。
 */
@Data
@TableName("t_report_detail")
public class ReportDetail {

    /**
     * 明细 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联举报主单 ID
     */
    private Long caseId;

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
     * 举报人 ID
     */
    private Long reporterId;

    /**
     * 举报原因
     */
    private Integer reportReason;

    /**
     * 举报补充说明
     */
    private String reportContent;

    /**
     * 举报证据。
     */
    private String reportEvidence;

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
