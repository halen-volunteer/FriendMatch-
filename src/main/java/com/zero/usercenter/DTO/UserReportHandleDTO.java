package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户举报处理 DTO（管理员）。
 */
@Data
public class UserReportHandleDTO {

    /** 举报记录ID。 */
    private Long reportId;

    /** 处理状态（1-通过处理，2-驳回，3-忽略；以业务字典为准）。 */
    private Integer reportStatus;

    /** 管理员处理动作编码（如警告、禁言、封号等）。 */
    private Integer adminAction;

    /** 管理员处理备注。 */
    private String adminNote;
}
