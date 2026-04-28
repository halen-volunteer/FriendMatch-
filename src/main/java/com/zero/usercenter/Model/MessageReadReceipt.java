package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息回执表
 * @TableName t_message_read_receipt
 */
@TableName(value = "t_message_read_receipt")
@Data
public class MessageReadReceipt {

    /**
     * 回执主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联消息ID
     */
    private Long msgId;

    /**
     * 已读/已送达的用户ID
     */
    private Long userId;

    /**
     * 回执类型：1-已送达，2-已读
     */
    private Integer receiptType;

    /**
     * 回执时间
     */
    private LocalDateTime receiptTime;

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
