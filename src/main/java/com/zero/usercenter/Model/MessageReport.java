package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息举报实体。
 * 对应表 `t_message_report`，保留消息举报专属的 AI 审核和申诉元数据。
 */
@Data
@TableName("t_message_report")
public class MessageReport {

    /**
     * 举报主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 被举报消息 ID
     */
    private Long messageId;

    /**
     * 举报人用户 ID
     */
    private Long reporterId;

    /**
     * 举报原因：
     * 1-色情，2-暴力，3-骚扰，4-广告，5-诈骗，6-其他
     */
    private Integer reportReason;

    /**
     * 举报详细说明
     */
    private String reportContent;

    /**
     * AI 检测结果：
     * 0-待检测，1-违规，2-正常
     */
    private Integer aiCheckResult;

    /**
     * AI 检测时间
     */
    private LocalDateTime aiCheckTime;

    /**
     * AI 检测置信度：0-100
     */
    private Integer aiConfidence;

    /**
     * 管理员处理状态：
     * 0-待处理，1-已处理，2-已驳回
     */
    private Integer adminStatus;

    /**
     * 管理员处理动作文本
     */
    private String adminAction;

    /**
     * 管理员处理备注
     */
    private String adminNote;

    /**
     * 申诉次数，最多 3 次
     */
    private Integer appealCount;

    /**
     * 当前申诉轮次：1-3
     */
    private Integer appealRound;

    /**
     * 当前申诉方：
     * reporter-举报人，sender-被举报消息发送者
     */
    private String appealer;

    /**
     * 是否删除：0-否，1-是
     */
    private Integer isDelete;

    /**
     * 举报时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
