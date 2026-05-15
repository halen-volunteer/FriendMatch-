package com.zero.usercenter.DTO;

import lombok.Data;

import java.util.List;

/**
 * 发送群聊消息 DTO。
 * `msgType` 约定：1-文本，2-图片，3-文件，4-表情包，5-@ 消息。
 */
@Data
public class TeamMsgDTO {

    /** 团队 ID。 */
    private Long teamId;

    /**
     * 消息类型：1-文本，2-图片，3-文件，4-表情包，5-@消息
     */
    private Integer msgType;

    /** 消息内容。 */
    private String msgContent;

    /** 文件或图片 URL。 */
    private String fileUrl;

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 原始文件名。 */
    private String fileName;

    /** 媒体类型，如 `image/png`、`video/mp4`。 */
    private String mediaType;

    /** 表情包标识。 */
    private String emojiId;

    /** 被 @ 的用户 ID 列表。 */
    private List<Long> atUsers;
}
