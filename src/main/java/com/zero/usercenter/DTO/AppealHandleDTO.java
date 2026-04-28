package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 申诉处理 DTO（管理员）。
 */
@Data
public class AppealHandleDTO {

    /** 申诉记录ID。 */
    private Long appealId;

    /** 处理状态（1-通过，2-驳回）。 */
    private Integer appealStatus;

    /** 管理员处理回复。 */
    private String adminReply;
}
