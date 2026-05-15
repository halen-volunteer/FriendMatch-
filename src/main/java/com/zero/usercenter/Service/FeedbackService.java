package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.FeedbackHandleDTO;
import com.zero.usercenter.DTO.FeedbackSubmitDTO;
import com.zero.usercenter.DTO.Result;

/**
 * 用户反馈服务接口。
 * 覆盖用户提交、用户查看以及管理员处理反馈链路。
 */
public interface FeedbackService {

    /**
     * 提交反馈。
     *
     * @param dto 反馈提交参数，包含反馈类型、内容和联系方式等信息
     * @return 统一响应结果，成功时表示反馈已提交
     */
    Result submitFeedback(FeedbackSubmitDTO dto);

    /**
     * 查询当前登录用户的反馈列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含反馈分页数据
     */
    Result getMyFeedbackList(int page, int pageSize);

    /**
     * 查询反馈详情。
     *
     * @param feedbackId 反馈记录 ID
     * @return 统一响应结果，成功时包含反馈详情
     */
    Result getFeedbackDetail(Long feedbackId);

    /**
     * 管理员处理反馈并回推处理结果。
     *
     * @param dto 反馈处理参数，包含反馈 ID、处理状态和回复内容
     * @return 统一响应结果，成功时表示反馈处理完成
     */
    Result handleFeedback(FeedbackHandleDTO dto);

    /**
     * 管理员分页查询反馈列表。
     *
     * @param handleStatus 处理状态筛选条件
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含后台反馈分页数据
     */
    Result adminGetFeedbackList(Integer handleStatus, int page, int pageSize);
}
