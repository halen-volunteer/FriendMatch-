package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.FeedbackHandleDTO;
import com.zero.usercenter.DTO.FeedbackSubmitDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 用户反馈服务接口
 */
public interface FeedbackService {

    /**
     * 用户提交反馈/申诉
     * 类型：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议
     *
     * @param dto 反馈提交数据传输对象，包含反馈类型和反馈内容
     * @return 提交结果
     */
    Result submitFeedback(FeedbackSubmitDTO dto);

    /**
     * 查询我的反馈列表（分页）
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 我的反馈分页列表
     */
    Result getMyFeedbackList(int page, int pageSize);

    /**
     * 查询反馈详情
     *
     * @param feedbackId 反馈记录 ID
     * @return 反馈详情信息
     */
    Result getFeedbackDetail(Long feedbackId);

    /**
     * 管理员处理反馈
     * 状态：1-处理中，2-已解决，3-已驳回
     * 处理完成后发送系统通知给反馈用户
     *
     * @param dto 反馈处理数据传输对象，包含反馈 ID、处理状态和回复内容
     * @return 处理结果
     */
    Result handleFeedback(FeedbackHandleDTO dto);

    /**
     * 管理员查询反馈列表（分页，支持状态筛选）
     *
     * @param handleStatus 处理状态（0-待处理，1-处理中，2-已解决，3-已驳回，null-全部）
     * @param page         页码
     * @param pageSize     每页条数
     * @return 反馈分页列表
     */
    Result adminGetFeedbackList(Integer handleStatus, int page, int pageSize);
}
