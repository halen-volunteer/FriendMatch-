package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统通知相关接口入口。
 */
@RestController
@RequestMapping("/api/notice")
public class SystemNoticeController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 获取未读通知数。
     *
     * @return 当前登录用户的未读通知总数
     */
    @GetMapping("/unread-count")
    public Result getUnreadNoticeCount() {
        // 未读数由 service 统一按当前登录用户统计，controller 只暴露结果。
        return userManagementService.getUnreadNoticeCount();
    }

    /**
     * 获取通知列表。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param isRead 已读状态筛选条件，可为空
     * @return 当前用户通知分页列表
     */
    @GetMapping("/list")
    public Result getNoticeList(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize,
                               @RequestParam(required = false) Integer isRead) {
        // 通知列表支持按已读状态筛选，分页查询逻辑都放在 service 层。
        return userManagementService.getNoticeList(page, pageSize, isRead);
    }

    /**
     * 标记通知为已读。
     *
     * @param noticeIds 待标记为已读的通知 ID 列表
     * @return 已读处理结果
     */
    @PostMapping("/read")
    public Result markNoticeAsRead(@RequestBody List<Long> noticeIds) {
        // 批量已读会校验通知归属关系，避免篡改别人的通知状态。
        return userManagementService.markNoticeAsRead(noticeIds);
    }

    /**
     * 删除通知。
     *
     * @param noticeIds 待删除的通知 ID 列表
     * @return 删除处理结果
     */
    @PostMapping("/delete")
    public Result deleteNotice(@RequestBody List<Long> noticeIds) {
        // 删除通知同样按当前用户维度处理，底层通常采用软删除或状态位变更。
        return userManagementService.deleteNotice(noticeIds);
    }
}
