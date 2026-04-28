package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 管理员处理消息举报 DTO
 */
@Data
public class MessageReportHandleDTO {

    private Long reportId;

    /**
     * 处理决定：
     * 1 - 维持处罚（申诉方申诉无效，保持现有处罚）
     * 2 - 撤销处罚/认定不违规（撤销处罚并扣减违规次数）
     * 3 - 确认违规执行处罚（AI无法判断时管理员首次确认，或补充执行处罚）
     */
    private Integer adminDecision;

    /** 管理员备注（可选） */
    private String adminNote;
}
