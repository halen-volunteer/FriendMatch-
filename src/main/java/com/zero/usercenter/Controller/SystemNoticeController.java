package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统通知 Controller
 * 负责处理系统通知相关的 HTTP 请求
 * 
 * 基础路径：/api/notice
 * 所有接口都需要在请求头中包含 Authorization: {token} 用于身份验证
 */
@RestController
@RequestMapping("/api/notice")
public class SystemNoticeController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 获取未读通知数
     * 
     * 请求方式：GET
     * 请求路径：/api/notice/unread-count
     * 
     * 返回当前用户的未读通知总数
     * 
     * @return 未读通知数
     */
    @GetMapping("/unread-count")
    public Result getUnreadNoticeCount() {
        return userManagementService.getUnreadNoticeCount();
    }

    /**
     * 获取通知列表
     * 
     * 请求方式：GET
     * 请求路径：/api/notice/list
     * 
     * 查询参数：
     * - page: 页码（默认1）
     * - pageSize: 每页数量（默认20）
     * - isRead: 是否已读（0-未读，1-已读，不传-全部）
     *
     * 返回通知列表，包含以下信息：
     * - id: 通知ID
     * - noticeType: 通知类型（1-好友申请，2-好友拒绝，3-入群审批通过，4-入群审批拒绝，5-被移出团队，6-账号异常，7-处罚通知，8-反馈回复，9-@提醒）
     * - noticeContent: 通知内容
     * - relatedId: 关联ID（申请人ID、团队ID、处罚ID等）
     * - isRead: 是否已读
     * - createTime: 创建时间
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param isRead   是否已读（0-未读，1-已读，null-全部）
     * @return 通知分页列表
     */
    @GetMapping("/list")
    public Result getNoticeList(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize,
                               @RequestParam(required = false) Integer isRead) {
        return userManagementService.getNoticeList(page, pageSize, isRead);
    }

    /**
     * 标记通知为已读
     * 
     * 请求方式：POST
     * 请求路径：/api/notice/read
     * 请求体：通知ID列表
     * 
     * 将指定的通知标记为已读
     * 
     * 请求体示例：
     * {
     *   "noticeIds": [1, 2, 3]
     * }
     *
     * @param noticeIds 要标记为已读的通知 ID 列表
     * @return 操作结果
     */
    @PostMapping("/read")
    public Result markNoticeAsRead(@RequestBody List<Long> noticeIds) {
        return userManagementService.markNoticeAsRead(noticeIds);
    }

    /**
     * 删除通知
     * 
     * 请求方式：POST
     * 请求路径：/api/notice/delete
     * 请求体：通知ID列表
     * 
     * 软删除指定的通知
     * 
     * 请求体示例：
     * {
     *   "noticeIds": [1, 2, 3]
     * }
     *
     * @param noticeIds 要删除的通知 ID 列表
     * @return 操作结果
     */
    @PostMapping("/delete")
    public Result deleteNotice(@RequestBody List<Long> noticeIds) {
        return userManagementService.deleteNotice(noticeIds);
    }
}
