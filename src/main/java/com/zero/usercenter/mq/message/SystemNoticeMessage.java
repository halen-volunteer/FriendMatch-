package com.zero.usercenter.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统通知异步消息体。
 * 负责在业务主流程和通知落库/推送流程之间传递通知基础数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemNoticeMessage {

    /** 接收通知的用户 ID。 */
    private Long userId;

    /** 通知类型，和 t_system_notice.notice_type 定义保持一致。 */
    private Integer noticeType;

    /** 通知正文。 */
    private String noticeContent;

    /** 关联业务 ID，例如好友申请、团队、处罚记录等。 */
    private Long relatedId;
}
