package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知表
 * @TableName t_system_notice
 */
@TableName(value = "t_system_notice")
@Data
public class SystemNotice {

    /**
     * 通知主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    /**
     * 通知类型：1-好友申请，2-好友拒绝，3-入群审批/好友申请通过，4-入群审批拒绝，5-被移出团队，6-账号异常，7-处罚通知，8-反馈回复，9-@通知
     */
    private Integer noticeType;

    /**
     * 通知内容
     */
    private String noticeContent;

    /**
     * 关联ID（申请人ID、团队ID、处罚ID等）
     */
    private Long relatedId;

    /**
     * 是否已读：0-否，1-是
     */
    private Integer isRead;

    /**
     * 已读时间
     */
    private LocalDateTime readTime;

    /**
     * 是否软删除：0-否，1-是
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
