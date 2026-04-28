package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.FriendOperationDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Service.UserManagementService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 好友管理 Controller
 * 负责处理好友关系相关的 HTTP 请求，包括添加、同意、拒绝、删除好友等操作
 * 
 * 基础路径：/api/friend
 * 所有接口都需要在请求头中包含 Authorization: {token} 用于身份验证
 */
@RestController
@RequestMapping("/api/friend")
public class FriendManagementController {

    @Resource
    private UserManagementService userManagementService;

    /**
     * 添加好友
     * 
     * 请求方式：POST
     * 请求路径：/api/friend/add
     * 请求体：FriendOperationDTO
     * 
     * 业务流程：
     * 1. 检查目标用户是否存在
     * 2. 检查是否已是好友
     * 3. 检查是否被对方拉黑
     * 4. 检查对方的隐私设置（消息接收权限）
     * 5. 如果对方设置为需验证，检查是否已申请过
     * 6. 插入好友申请记录（状态为待验证）
     * 
     * 请求体字段：
     * - friendId: 好友ID（必填）
     * - applyMsg: 申请备注（可选）
     * - friendRemark: 好友备注名（可选）
     *
     * @param dto 好友操作数据传输对象，包含目标用户 ID、申请备注、好友备注名
     * @return 操作结果
     */
    @PostMapping("/add")
    public Result addFriend(@RequestBody FriendOperationDTO dto) {
        return userManagementService.addFriend(dto);
    }

    /**
     * 获取好友列表
     * 
     * 请求方式：GET
     * 请求路径：/api/friend/list
     * 
     * 查询参数：
     * - page: 页码（默认1）
     * - pageSize: 每页数量（默认20）
     *
     * 返回已成为好友的用户列表（friend_status=1），包含以下信息：
     * - friendId: 好友ID
     * - friendRemark: 好友备注名
     * - userNickname: 好友昵称
     * - userAvatar: 好友头像
     * - userIntro: 好友简介
     * - agreeTime: 同意时间
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 好友分页列表
     */
    @GetMapping("/list")
    public Result getFriendList(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return userManagementService.getFriendList(page, pageSize);
    }

    /**
     * 同意好友申请
     * 
     * 请求方式：POST
     * 请求路径：/api/friend/agree
     * 请求体：FriendOperationDTO
     * 
     * 业务流程：
     * 1. 查询好友申请记录（状态为待验证）
     * 2. 更新申请记录状态为已成为好友
     * 3. 设置同意时间
     * 4. 插入反向好友关系记录（实现双向关系）
     * 
     * 请求体字段：
     * - friendId: 申请人ID（必填）
     *
     * @param dto 好友操作数据传输对象，包含申请人用户 ID
     * @return 操作结果
     */
    @PostMapping("/agree")
    public Result agreeFriend(@RequestBody FriendOperationDTO dto) {
        return userManagementService.agreeFriend(dto.getFriendId());
    }

    /**
     * 拒绝好友申请
     * 
     * 请求方式：POST
     * 请求路径：/api/friend/reject
     * 请求体：FriendOperationDTO
     * 
     * 业务流程：
     * 1. 查询好友申请记录（状态为待验证）
     * 2. 更新申请记录状态为已拒绝
     * 
     * 请求体字段：
     * - friendId: 申请人ID（必填）
     *
     * @param dto 好友操作数据传输对象，包含申请人用户 ID
     * @return 操作结果
     */
    @PostMapping("/reject")
    public Result rejectFriend(@RequestBody FriendOperationDTO dto) {
        return userManagementService.rejectFriend(dto.getFriendId());
    }

    /**
     * 删除好友
     * 
     * 请求方式：POST
     * 请求路径：/api/friend/delete
     * 请求体：FriendOperationDTO
     * 
     * 业务流程：
     * 1. 删除当前用户到好友的关系记录
     * 2. 删除好友到当前用户的反向关系记录
     * 
     * 注意：删除好友是双向的，两个用户都会失去好友关系
     * 
     * 请求体字段：
     * - friendId: 好友ID（必填）
     *
     * @param dto 好友操作数据传输对象，包含好友用户 ID
     * @return 操作结果
     */
    @PostMapping("/delete")
    public Result deleteFriend(@RequestBody FriendOperationDTO dto) {
        return userManagementService.deleteFriend(dto.getFriendId());
    }

    /**
     * 获取好友申请列表
     * 
     * 请求方式：GET
     * 请求路径：/api/friend/requests
     * 
     * 查询参数：
     * - page: 页码（默认1）
     * - pageSize: 每页数量（默认20）
     * 
     * 返回待验证的好友申请列表（friend_status=0），包含以下信息：
     * - requestId: 申请记录ID
     * - applicantId: 申请人ID
     * - userNickname: 申请人昵称
     * - userAvatar: 申请人头像
     * - userIntro: 申请人简介
     * - userTags: 申请人标签
     * - applyMsg: 申请备注
     * - createTime: 申请时间
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 待验证的好友申请分页列表
     */
    @GetMapping("/requests")
    public Result getFriendRequests(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return userManagementService.getFriendRequests(page, pageSize);
    }
}
