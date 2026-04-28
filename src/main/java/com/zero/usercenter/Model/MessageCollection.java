package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息收藏表
 * @TableName t_message_collection
 */
@Data
@TableName("t_message_collection")
public class MessageCollection {

    /**
     * 收藏主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 收藏用户ID
     */
    private Long userId;

    /**
     * 被收藏的消息ID
     */
    private Long messageId;

    /**
     * 收藏备注（可选）
     */
    private String collectionNote;

    /**
     * 是否软删除：0-否，1-是
     */
    private Integer isDelete;

    /**
     * 收藏时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
