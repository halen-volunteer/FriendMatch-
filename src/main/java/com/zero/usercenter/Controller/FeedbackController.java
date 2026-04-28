package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.FeedbackHandleDTO;
import com.zero.usercenter.DTO.FeedbackSubmitDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.FeedbackService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 用户反馈 Controller
 *
 * 基础路径：/api/feedback
 * 所有接口需在请求头携带 Authorization: {token}
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    /**
     * 用户提交反馈/申诉
     * POST /api/feedback/submit
     * 类型：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议
     * 每日最多提交 10 条
     *
     * @param dto 反馈提交数据传输对象，包含反馈类型和反馈内容
     * @return 提交结果
     */
    @PostMapping("/submit")
    public Result submitFeedback(@RequestBody FeedbackSubmitDTO dto) {
        return feedbackService.submitFeedback(dto);
    }

    /**
     * 查询我的反馈列表
     * GET /api/feedback/my-list?page=1&pageSize=20
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 我的反馈分页列表
     */
    @GetMapping("/my-list")
    public Result getMyFeedbackList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return feedbackService.getMyFeedbackList(page, pageSize);
    }

    /**
     * 查询反馈详情
     * GET /api/feedback/detail?feedbackId=1001
     *
     * @param feedbackId 反馈记录 ID
     * @return 反馈详情信息
     */
    @GetMapping("/detail")
    public Result getFeedbackDetail(@RequestParam Long feedbackId) {
        return feedbackService.getFeedbackDetail(feedbackId);
    }

    /**
     * 管理员处理反馈
     * POST /api/feedback/handle
     * 状态：1-处理中，2-已解决，3-已驳回
     * 处理完成后自动发送系统通知给反馈用户
     *
     * @param dto 反馈处理数据传输对象，包含反馈 ID、处理状态和回复内容
     * @return 处理结果
     */
    @PostMapping("/handle")
    public Result handleFeedback(@RequestBody FeedbackHandleDTO dto) {
        return feedbackService.handleFeedback(dto);
    }

    /**
     * 管理员查询所有反馈列表（分页，可按状态筛选）
     * GET /api/feedback/admin/list?handleStatus=0&page=1&pageSize=20
     * handleStatus：0-待处理，1-处理中，2-已解决，3-已驳回；不传=全部
     *
     * @param handleStatus 处理状态（0-待处理，1-处理中，2-已解决，3-已驳回，null-全部）
     * @param page         页码
     * @param pageSize     每页条数
     * @return 反馈分页列表
     */
    @GetMapping("/admin/list")
    public Result adminGetFeedbackList(
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return feedbackService.adminGetFeedbackList(handleStatus, page, pageSize);
    }
}
