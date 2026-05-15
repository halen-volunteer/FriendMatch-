package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 管理员处理消息举报 DTO。
 */
@Data
public class MessageReportHandleDTO {

    /** 举报主单 ID。 */
    private Long reportId;

    /**
     * 首审处理决定：
     * 1-确认违规
     * 2-确认未违规
     */
    private Integer adminDecision;

    /** 管理员处理备注。 */
    private String adminNote;
}
