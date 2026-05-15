package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.FriendOperationDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 好友关系相关接口入口。
 */
@RestController
@RequestMapping("/api/friend")
public class FriendManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 添加好友。
     */
    @PostMapping("/add")
    public Result addFriend(@RequestBody FriendOperationDTO dto) {
        // 添加好友会触发关系校验、申请创建或直接建立好友关系，统一由 service 处理。
        return userManagementService.addFriend(dto);
    }

    /**
     * 获取好友列表。
     */
    @GetMapping("/list")
    public Result getFriendList(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        // 好友列表的分页和展示信息补齐都在用户管理 service 中完成。
        return userManagementService.getFriendList(page, pageSize);
    }

    /**
     * 同意好友申请。
     */
    @PostMapping("/agree")
    public Result agreeFriend(@RequestBody FriendOperationDTO dto) {
        // 同意好友申请只传 friendId，具体申请单校验和双方关系写入由 service 负责。
        return userManagementService.agreeFriend(dto.getFriendId());
    }

    /**
     * 拒绝好友申请。
     */
    @PostMapping("/reject")
    public Result rejectFriend(@RequestBody FriendOperationDTO dto) {
        // 拒绝好友申请同样走 service 的申请状态流转逻辑。
        return userManagementService.rejectFriend(dto.getFriendId());
    }

    /**
     * 删除好友。
     * 当前实现会同步删除双方好友关系。
     */
    @PostMapping("/delete")
    public Result deleteFriend(@RequestBody FriendOperationDTO dto) {
        // 删除好友会同步清理双方好友关系，避免单边残留。
        return userManagementService.deleteFriend(dto.getFriendId());
    }

    /**
     * 获取好友申请列表。
     */
    @GetMapping("/requests")
    public Result getFriendRequests(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        // 好友申请列表按当前用户视角查询，排序和分页逻辑在 service 层统一维护。
        return userManagementService.getFriendRequests(page, pageSize);
    }
}
