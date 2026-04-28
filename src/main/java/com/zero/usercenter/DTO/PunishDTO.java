package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 处罚用户请求体
 * 管理员只需提交被处罚用户ID和处罚原因，系统按梯度自动决定处罚力度：
 *   第1次违规 → 全局禁言 60 分钟
 *   第2次违规 → 全局禁言 1440 分钟（1天）
 *   第3次违规 → 全局禁言 10080 分钟（7天）
 *   第4次及以上 → 永久封号
 */
@Data
public class PunishDTO {

    /** 被处罚用户ID */
    private Long punishUserId;

    /** 处罚原因（不能为空，最多512字符） */
    private String punishReason;

    /** 关联违规消息ID（可选，有值时写入 t_punish_msg_relation） */
    private Long msgId;

    /** AI 审核结果（可选，无值时默认"管理员手动处罚"） */
    private String aiAuditResult;

    /**
     * 操作类型：1-系统自动（AI处罚），2-管理员手动
     * 不填默认2（管理员手动）
     */
    private Integer operateType;
}
