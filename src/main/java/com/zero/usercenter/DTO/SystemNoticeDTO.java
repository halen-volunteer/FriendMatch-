package com.zero.usercenter.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统通知展示 DTO。
 * 用于把通知中心列表页需要的核心字段返回给前端，
 * 避免直接暴露数据库实体中的全部字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemNoticeDTO {

    /**
     * 通知主键 ID。
     */
    private Long id;

    /**
     * 通知类型：
     * 1-好友申请，2-好友拒绝，3-入群审批通过，4-入群审批拒绝，
     * 5-被移出团队，6-账号异常，7-处罚通知，8-反馈回复，9-@通知。
     */
    private Integer noticeType;

    /**
     * 通知正文内容。
     */
    private String noticeContent;

    /**
     * 关联业务 ID，例如申请人 ID、团队 ID、处罚 ID。
     */
    private Long relatedId;

    /**
     * 是否已读：0-否，1-是。
     */
    private Integer isRead;

    /**
     * 通知创建时间。
     */
    private LocalDateTime createTime;
}
