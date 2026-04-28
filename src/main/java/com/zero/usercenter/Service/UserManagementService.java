package com.zero.usercenter.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zero.usercenter.DTO.BlacklistOperationDTO;
import com.zero.usercenter.DTO.FriendOperationDTO;
import com.zero.usercenter.DTO.PrivacySettingDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.UserProfileUpdateDTO;
import com.zero.usercenter.Model.User;

/**
 * 用户管理 Service 接口
 */
public interface UserManagementService extends IService<User> {

    /**
     * 更新用户资料
     *
     * @param dto 用户资料更新数据传输对象，包含昵称、头像、简介、标签等
     * @return 更新后的用户信息
     */
    Result updateUserProfile(UserProfileUpdateDTO dto);

    /**
     * 获取用户隐私设置
     *
     * @return 当前用户的隐私设置信息
     */
    Result getPrivacySetting();

    /**
     * 更新用户隐私设置
     *
     * @param dto 隐私设置数据传输对象，包含资料可见性、消息接收权限、邮箱搜索权限
     * @return 更新后的隐私设置
     */
    Result updatePrivacySetting(PrivacySettingDTO dto);

    /**
     * 添加好友
     *
     * @param dto 好友操作数据传输对象，包含目标用户 ID、申请备注、好友备注名
     * @return 操作结果
     */
    Result addFriend(FriendOperationDTO dto);

    /**
     * 获取好友列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 好友分页列表
     */
    Result getFriendList(int page, int pageSize);

    /**
     * 同意好友申请
     *
     * @param friendId 申请人用户 ID
     * @return 操作结果
     */
    Result agreeFriend(Long friendId);

    /**
     * 拒绝好友申请
     *
     * @param friendId 申请人用户 ID
     * @return 操作结果
     */
    Result rejectFriend(Long friendId);

    /**
     * 删除好友
     *
     * @param friendId 好友用户 ID
     * @return 操作结果
     */
    Result deleteFriend(Long friendId);

    /**
     * 拉黑用户
     *
     * @param dto 黑名单操作数据传输对象，包含目标用户 ID
     * @return 操作结果
     */
    Result addBlacklist(BlacklistOperationDTO dto);

    /**
     * 解除拉黑
     *
     * @param blackUserId 被拉黑用户 ID
     * @return 操作结果
     */
    Result removeBlacklist(Long blackUserId);

    /**
     * 获取黑名单
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 黑名单分页列表
     */
    Result getBlacklist(int page, int pageSize);

    /**
     * 搜索用户
     *
     * @param keyword  搜索关键词
     * @param type     搜索类型（account-账号，nickname-昵称，tag-标签，email-邮箱）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 搜索结果列表
     */
    Result searchUser(String keyword, String type, int page, int pageSize);

    /**
     * 获取用户列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param sort     排序字段（id 或 createTime）
     * @return 用户分页列表
     */
    Result getUserList(int page, int pageSize, String sort);

    /**
     * 查看用户资料
     *
     * @param userId 目标用户 ID
     * @return 用户资料信息（根据隐私设置和黑名单状态返回相应权限内容）
     */
    Result getUserProfile(Long userId);

    /**
     * 获取好友申请列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 待验证的好友申请分页列表
     */
    Result getFriendRequests(int page, int pageSize);

    /**
     * 获取未读通知数
     *
     * @return 当前用户未读通知总数
     */
    Result getUnreadNoticeCount();

    /**
     * 获取通知列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param isRead   是否已读（0-未读，1-已读，null-全部）
     * @return 通知分页列表
     */
    Result getNoticeList(int page, int pageSize, Integer isRead);

    /**
     * 标记通知为已读
     *
     * @param noticeIds 要标记为已读的通知 ID 列表
     * @return 操作结果
     */
    Result markNoticeAsRead(java.util.List<Long> noticeIds);

    /**
     * 删除通知
     *
     * @param noticeIds 要删除的通知 ID 列表
     * @return 操作结果
     */
    Result deleteNotice(java.util.List<Long> noticeIds);
}
