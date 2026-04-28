package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息置顶表
 * @TableName t_message_pin
 */
@Data
@TableName("t_message_pin")
public class MessagePin {

    /**
     * 置顶主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话ID（私聊=min_max格式，群聊=team_{teamId}）
     */
    private String conversationId;

    /**
     * 被置顶的消息ID
     */
    private Long messageId;

    /**
     * 执行置顶操作的用户ID
     */
    private Long pinUserId;

    /**
     * 置顶排序顺序（值越小越靠前，会话内已有置顶数+1）
     */
    private Integer pinOrder;

    /**
     * 是否软删除：0-否，1-是
     */
    private Integer isDelete;

    /**
     * 置顶时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
