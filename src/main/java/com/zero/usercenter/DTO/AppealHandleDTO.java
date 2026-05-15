package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 管理员处理申诉 DTO。
 */
@Data
public class AppealHandleDTO {

    /** 申诉记录 ID。 */
    private Long appealId;

    /**
     * 申诉处理结果：
     * 1-撤销处罚
     * 2-维持处罚
     */
    private Integer appealStatus;

    /** 管理员处理回复。 */
    private String adminReply;
}
