package com.zero.usercenter.DTO;

import lombok.Data;

import java.util.List;

/**
 * 发送群聊消息 DTO
 * msgType 枚举：
 *   1 - 文本：msgContent 为正文
 *   2 - 图片：fileUrl 为图片URL（≤10MB），msgContent 可选图片描述
 *   3 - 文件：fileUrl 为文件URL（≤100MB），fileName 为原始文件名，fileSize 为字节数
 *   4 - 表情包：emojiId 为表情包标识
 *   5 - @消息：msgContent 为正文，atUsers 为被@用户ID列表
 */
@Data
public class TeamMsgDTO {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 消息类型：1-文本，2-图片，3-文件，4-表情包，5-@消息
     */
    private Integer msgType;

    /**
     * 消息内容（文本消息正文；图片消息可选描述；@消息正文）
     */
    private String msgContent;

    /**
     * 文件/图片 URL（msgType=2 或 3 时必填）
     */
    private String fileUrl;

    /**
     * 文件大小（字节），msgType=3 时填写
     */
    private Long fileSize;

    /**
     * 原始文件名，msgType=3 时填写
     */
    private String fileName;

    /**
     * 表情包标识，msgType=4 时必填
     */
    private String emojiId;

    /**
     * @指定用户ID列表（msgType=5 时使用）
     */
    private List<Long> atUsers;
}
