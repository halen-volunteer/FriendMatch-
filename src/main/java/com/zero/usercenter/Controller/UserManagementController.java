package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 Controller
 * 负责处理用户信息、隐私设置、用户搜索等相关的 HTTP 请求
 *
 * 基础路径：/api/user
 * 所有接口都需要在请求头中包含 Authorization: {token} 用于身份验证
 */
@RestController
@RequestMapping("/api/user")
public class UserManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 更新用户资料
     *
     * 请求方式：POST
     * 请求路径：/api/user/profile/update
     * 请求体：UserProfileUpdateDTO
     *
     * 支持编辑以下字段：
     * - userNickname: 用户昵称（3-16位）
     * - userAvatar: 用户头像URL
     * - userIntro: 个人简介（≤512字符）
     * - userTags: 用户标签（逗号分隔，≤5个，每个≤20字符）
     *
     * @param dto 用户资料更新数据传输对象
     * @return 更新后的用户信息
     */
    @PostMapping("/profile/update")
    public Result updateUserProfile(@RequestBody UserProfileUpdateDTO dto) {
        return userManagementService.updateUserProfile(dto);
    }

    /**
     * 查看隐私设置
     *
     * 请求方式：GET
     * 请求路径：/api/user/privacy
     *
     * 返回用户的隐私设置，包括：
     * - viewInfo: 资料可见性（1-所有人，2-仅团队成员）
     * - sendMsg: 消息接收权限（1-所有人，2-仅团队成员，3-需验证）
     * - searchByEmail: 邮箱搜索权限（0-不允许，1-允许）
     *
     * @return 隐私设置信息
     */
    @GetMapping("/privacy")
    public Result getPrivacySetting() {
        return userManagementService.getPrivacySetting();
    }

    /**
     * 更新隐私设置
     *
     * 请求方式：POST
     * 请求路径：/api/user/privacy/update
     * 请求体：PrivacySettingDTO
     *
     * 支持更新以下字段（可选，只更新传入的字段）：
     * - viewInfo: 资料可见性（1-所有人，2-仅团队成员）
     * - sendMsg: 消息接收权限（1-所有人，2-仅团队成员，3-需验证）
     * - searchByEmail: 邮箱搜索权限（0-不允许，1-允许）
     *
     * @param dto 隐私设置数据传输对象
     * @return 更新后的隐私设置
     */
    @PostMapping("/privacy/update")
    public Result updatePrivacySetting(@RequestBody PrivacySettingDTO dto) {
        return userManagementService.updatePrivacySetting(dto);
    }

    /**
     * 查看用户资料
     *
     * 请求方式：GET
     * 请求路径：/api/user/{userId}/profile
     *
     * 根据隐私设置和黑名单状态返回相应权限的用户信息：
     * - 查看自己的资料：返回完整信息
     * - 被拉黑：返回"用户不存在"
     * - 隐私设置为仅团队成员可见：返回"无权查看"
     * - 其他情况：返回脱敏用户信息
     *
     * @param userId 要查看的用户 ID
     * @return 用户资料信息
     */
    @GetMapping("/{userId}/profile")
    public Result getUserProfile(@PathVariable Long userId) {
        return userManagementService.getUserProfile(userId);
    }

    /**
     * 获取用户列表
     *
     * 请求方式：GET
     * 请求路径：/api/user/list
     *
     * 查询参数：
     * - page: 页码（默认1）
     * - pageSize: 每页数量（默认20）
     * - sort: 排序字段（默认id，支持createTime-按创建时间排序）
     *
     * 返回结果会自动过滤：
     * - 当前用户自己
     * - 被拉黑的用户
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param sort     排序字段
     * @return 用户分页列表
     */
    @GetMapping("/list")
    public Result getUserList(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int pageSize,
                             @RequestParam(defaultValue = "id") String sort) {
        return userManagementService.getUserList(page, pageSize, sort);
    }

    /**
     * 搜索用户
     *
     * 请求方式：GET
     * 请求路径：/api/user/search
     *
     * 查询参数：
     * - keyword: 搜索关键词（必填）
     * - type: 搜索类型（默认nickname，支持account-账号，tag-标签，email-邮箱）
     * - page: 页码（默认1）
     * - pageSize: 每页数量（默认20）
     *
     * 搜索类型说明：
     * - account: 按公开账号搜索
     * - nickname: 按昵称搜索
     * - tag: 按标签搜索
     * - email: 按邮箱搜索（仅返回允许邮箱搜索的用户）
     *
     * 返回结果会自动过滤：
     * - 当前用户自己
     * - 被拉黑的用户
     *
     * @param keyword  搜索关键词
     * @param type     搜索类型
     * @param page     页码
     * @param pageSize 每页条数
     * @return 搜索结果列表
     */
    @GetMapping("/search")
    public Result searchUser(@RequestParam String keyword,
                            @RequestParam(defaultValue = "nickname") String type,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        return userManagementService.searchUser(keyword, type, page, pageSize);
    }
}
