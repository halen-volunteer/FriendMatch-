package com.zero.usercenter.DTO;

import lombok.Data;

/**
 * 用户提交反馈请求体
 */
@Data
public class FeedbackSubmitDTO {

    /**
     * 反馈类型：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议
     */
    private Integer feedbackType;

    /** 反馈标题（可选） */
    private String feedbackTitle;

    /** 反馈详细内容（不能为空，最多2000字符） */
    private String feedbackContent;

    /** 附件URL，逗号分隔（可选） */
    private String feedbackAttachment;

    /** 反馈图片URL，逗号分隔（可选，兼容旧字段） */
    private String feedbackImg;

    /**
     * 关联处罚记录ID（仅 feedbackType=3 处罚申诉时填写）
     * 用于关联 t_user_punish_log.id，标识针对哪条处罚提出申诉
     */
    private Long punishLogId;
}
