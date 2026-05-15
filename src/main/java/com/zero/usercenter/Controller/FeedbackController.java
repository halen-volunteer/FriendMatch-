package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.FeedbackHandleDTO;
import com.zero.usercenter.DTO.FeedbackSubmitDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.FeedbackService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 用户反馈 Controller。
 *
 * 基础路径：`/api/feedback`
 * 当前反馈中心只承载普通反馈，不再作为举报或申诉主入口。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    /**
     * 提交普通用户反馈。
     */
    @PostMapping("/submit")
    public Result submitFeedback(@RequestBody FeedbackSubmitDTO dto) {
        // 反馈提交后的校验、防刷和落库都在 feedbackService 中统一处理。
        return feedbackService.submitFeedback(dto);
    }

    /**
     * 查询我的反馈列表。
     */
    @GetMapping("/my-list")
    public Result getMyFeedbackList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 我的反馈列表只查询当前登录用户的数据，分页逻辑由 service 封装。
        return feedbackService.getMyFeedbackList(page, pageSize);
    }

    /**
     * 查询反馈详情。
     */
    @GetMapping("/detail")
    public Result getFeedbackDetail(@RequestParam Long feedbackId) {
        // 反馈详情会在 service 层校验是否为本人可见。
        return feedbackService.getFeedbackDetail(feedbackId);
    }

    /**
     * 管理员处理反馈。
     * 处理完成后会通过系统通知把结果回推给提交人。
     */
    @PostMapping("/handle")
    public Result handleFeedback(@RequestBody FeedbackHandleDTO dto) {
        // 管理员处理反馈后可能触发系统通知，因此整体流程由 service 统一编排。
        return feedbackService.handleFeedback(dto);
    }

    /**
     * 管理员分页查询反馈列表，可按处理状态筛选。
     */
    @GetMapping("/admin/list")
    public Result adminGetFeedbackList(
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 管理端列表支持状态筛选，具体排序和分页策略都在 service 内实现。
        return feedbackService.adminGetFeedbackList(handleStatus, page, pageSize);
    }
}
