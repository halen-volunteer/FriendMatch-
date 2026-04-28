package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 管理员处理反馈请求体
 */
@Data
public class FeedbackHandleDTO {

    /** 反馈ID */
    private Long feedbackId;

    /**
     * 处理状态：1-处理中，2-已解决，3-已驳回
     */
    private Integer handleStatus;

    /** 处理结果/回复内容（不能为空） */
    private String handleContent;
}
