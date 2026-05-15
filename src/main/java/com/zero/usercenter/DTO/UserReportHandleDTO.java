package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 管理员处理用户举报 DTO。
 */
@Data
public class UserReportHandleDTO {

    /** 举报主单 ID。 */
    private Long reportId;

    /**
     * 首审处理决定：
     * 1-确认违规
     * 2-确认未违规
     */
    private Integer reportStatus;

    /** 预留的处理动作字段，当前首审阶段不依赖该值。 */
    private Integer adminAction;

    /** 管理员处理备注。 */
    private String adminNote;
}
