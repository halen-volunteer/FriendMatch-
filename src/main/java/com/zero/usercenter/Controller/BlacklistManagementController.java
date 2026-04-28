package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.BlacklistOperationDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 黑名单管理 Controller
 *
 * 基础路径：/api/blacklist
 * 所有接口需在请求头携带 Authorization: {token}
 */
@RestController
@RequestMapping("/api/blacklist")
public class BlacklistManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 拉黑用户
     * POST /api/blacklist/add
     * 拉黑后对方无法向当前用户发消息，双方在搜索中互不可见
     *
     * @param dto 黑名单操作数据传输对象，包含目标用户 ID
     * @return 操作结果
     */
    @PostMapping("/add")
    public Result addBlacklist(@RequestBody BlacklistOperationDTO dto) {
        return userManagementService.addBlacklist(dto);
    }

    /**
     * 解除拉黑
     * POST /api/blacklist/remove
     *
     * @param dto 黑名单操作数据传输对象，包含目标用户 ID
     * @return 操作结果
     */
    @PostMapping("/remove")
    public Result removeBlacklist(@RequestBody BlacklistOperationDTO dto) {
        return userManagementService.removeBlacklist(dto.getBlackUserId());
    }

    /**
     * 获取黑名单列表
     * GET /api/blacklist?page=1&pageSize=20
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 黑名单分页列表
     */
    @GetMapping
    public Result getBlacklist(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int pageSize) {
        return userManagementService.getBlacklist(page, pageSize);
    }
}
