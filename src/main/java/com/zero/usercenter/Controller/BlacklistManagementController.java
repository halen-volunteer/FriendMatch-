package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.BlacklistOperationDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 黑名单管理接口入口。
 */
@RestController
@RequestMapping("/api/blacklist")
public class BlacklistManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 拉黑用户。
     */
    @PostMapping("/add")
    public Result addBlacklist(@RequestBody BlacklistOperationDTO dto) {
        // 拉黑用户会处理好友关系、可见性和后续推荐过滤等副作用。
        return userManagementService.addBlacklist(dto);
    }

    /**
     * 解除拉黑。
     */
    @PostMapping("/remove")
    public Result removeBlacklist(@RequestBody BlacklistOperationDTO dto) {
        // 解除拉黑只需要提交目标用户 ID，具体归属校验在 service 层完成。
        return userManagementService.removeBlacklist(dto.getBlackUserId());
    }

    /**
     * 获取黑名单列表。
     */
    @GetMapping
    public Result getBlacklist(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        // 黑名单列表由 service 负责分页和用户信息补齐。
        return userManagementService.getBlacklist(page, pageSize);
    }
}
