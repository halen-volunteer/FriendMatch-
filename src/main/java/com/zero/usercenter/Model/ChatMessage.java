package com.zero.usercenter.Model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息表
 * @TableName t_chat_message
 */
@TableName(value = "t_chat_message")
@Data
public class ChatMessage {

    /**
     * 消息主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息发送人ID
     */
    private Long senderId;

    /**
     * 接收类型：1-私聊，2-群聊（团队）
     */
    private Integer recvType;

    /**
     * 接收ID：私聊=对方用户ID，群聊=团队ID
     */
    private Long recvId;

    /**
     * 会话ID：私聊=min(uid1,uid2)_max(uid1,uid2)；群聊=team_{teamId}
     */
    private String conversationId;

    /**
     * 消息类型：1-文本，2-图片，3-文件，4-表情包，5-@消息
     */
    private Integer msgType;

    /**
     * 消息内容：文本=内容，图片/文件=URL，表情包=标识
     */
    private String msgContent;

    /**
     * 是否已编辑：0-否，1-是
     */
    private Integer isEdited;

    /**
     * 编辑时间
     */
    private LocalDateTime editTime;

    /**
     * 编辑次数
     */
    private Integer editCount;

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
