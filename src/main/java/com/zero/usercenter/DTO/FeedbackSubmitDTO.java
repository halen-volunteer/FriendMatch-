package com.zero.usercenter.DTO;

import lombok.Data;

/** 用户提交反馈请求 DTO。 */
@Data
public class FeedbackSubmitDTO {

    /** 反馈类型：1-功能问题，2-体验建议，3-内容纠错，4-其他。 */
    private Integer feedbackType;

    /** 反馈标题，可选。 */
    private String feedbackTitle;

    /** 反馈详细内容，不能为空。 */
    private String feedbackContent;

    /** 附件 URL，逗号分隔，可选。 */
    private String feedbackAttachment;

    /** 反馈图片 URL，逗号分隔，可选，兼容旧字段。 */
    private String feedbackImg;

    /**
     * 兼容历史字段。
     * 当前反馈中心不再承担处罚申诉主入口，这个字段保留是为了兼容旧数据结构和旧前端调用。
     */
    private Long punishLogId;
}
