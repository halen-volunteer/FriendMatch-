package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈表
 * @TableName t_user_feedback
 */
@TableName(value = "t_user_feedback")
@Data
public class UserFeedback {

    /** 反馈主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 反馈用户ID */
    private Long userId;

    /**
     * 反馈类型：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议
     */
    private Integer feedbackType;

    /** 反馈标题（可选） */
    private String feedbackTitle;

    /** 反馈详细内容 */
    private String feedbackContent;

    /** 附件URL（逗号分隔，可选） */
    private String feedbackAttachment;

    /** 反馈图片URL（逗号分隔，兼容旧字段） */
    private String feedbackImg;

    /** 处理人ID（管理员） */
    private Long handleUserId;

    /**
     * 处理状态：0-待处理，1-处理中，2-已解决，3-已驳回
     */
    private Integer handleStatus;

    /** 处理结果/回复内容 */
    private String handleContent;

    /** 处理完成时间 */
    private LocalDateTime handleTime;

    /** 是否软删除：0-否，1-是 */
    private Integer isDelete;

    /**
     * 提交时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
