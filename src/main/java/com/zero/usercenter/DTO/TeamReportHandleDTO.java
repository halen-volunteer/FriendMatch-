package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 团队举报处理 DTO（管理员）。
 */
@Data
public class TeamReportHandleDTO {

    /** 举报记录ID。 */
    private Long reportId;

    /** 处理状态（1-通过处理，2-驳回，3-忽略；以业务字典为准）。 */
    private Integer reportStatus;

    /** 管理员处理动作编码。 */
    private Integer adminAction;

    /** 管理员处理备注。 */
    private String adminNote;
}
