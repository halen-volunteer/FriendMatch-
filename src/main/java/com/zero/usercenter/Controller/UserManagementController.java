package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.*;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 用户资料、隐私和搜索相关接口入口。
 */
@RestController
@RequestMapping("/api/user")
public class UserManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 更新用户资料。
     */
    @PostMapping("/profile/update")
    public Result updateUserProfile(@RequestBody UserProfileUpdateDTO dto) {
        // 用户资料更新的字段白名单、头像等扩展校验都在 service 层完成。
        return userManagementService.updateUserProfile(dto);
    }

    /**
     * 查看隐私设置。
     */
    @GetMapping("/privacy")
    public Result getPrivacySetting() {
        // 隐私设置读取只暴露当前登录用户自己的配置。
        return userManagementService.getPrivacySetting();
    }

    /**
     * 更新隐私设置。
     */
    @PostMapping("/privacy/update")
    public Result updatePrivacySetting(@RequestBody PrivacySettingDTO dto) {
        // 隐私配置变更会影响资料可见性和搜索曝光，交给 service 统一处理。
        return userManagementService.updatePrivacySetting(dto);
    }

    /**
     * 查看指定用户资料。
     * 返回结果会结合隐私设置和黑名单关系做可见性控制。
     */
    @GetMapping("/{userId}/profile")
    public Result getUserProfile(@PathVariable Long userId) {
        // 查看他人资料时，service 会结合黑名单和隐私设置做可见性控制。
        return userManagementService.getUserProfile(userId);
    }

    /**
     * 获取用户列表。
     * 会自动过滤当前用户本人和黑名单不可见用户。
     */
    @GetMapping("/list")
    public Result getUserList(@RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int pageSize,
                             @RequestParam(defaultValue = "id") String sort) {
        // 用户列表会过滤本人和不可见用户，排序、分页策略由 service 决定。
        return userManagementService.getUserList(page, pageSize, sort);
    }

    /**
     * 搜索用户。
     * 支持按账号、昵称、标签或邮箱搜索，并会按可见性规则过滤结果。
     */
    @GetMapping("/search")
    public Result searchUser(@RequestParam String keyword,
                            @RequestParam(defaultValue = "nickname") String type,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        // 搜索用户支持多字段检索，过滤和结果组装都在 service 中完成。
        return userManagementService.searchUser(keyword, type, page, pageSize);
    }
}
