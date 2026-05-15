package com.zero.usercenter.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zero.usercenter.DTO.BlacklistOperationDTO;
import com.zero.usercenter.DTO.FriendOperationDTO;
import com.zero.usercenter.DTO.PrivacySettingDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.UserProfileUpdateDTO;
import com.zero.usercenter.Model.User;

/**
 * 用户管理服务接口。
 * 覆盖资料、隐私、好友、黑名单和通知中心等用户侧管理能力。
 */
public interface UserManagementService extends IService<User> {

    /**
     * 更新用户资料。
     *
     * @param dto 用户资料更新参数，包含头像、昵称、简介、标签等信息
     * @return 统一响应结果，成功时表示资料已更新
     */
    Result updateUserProfile(UserProfileUpdateDTO dto);

    /**
     * 获取用户隐私设置。
     *
     * @return 统一响应结果，成功时包含当前用户隐私设置
     */
    Result getPrivacySetting();

    /**
     * 更新用户隐私设置。
     *
     * @param dto 隐私设置参数，包含加好友、搜索可见性等配置项
     * @return 统一响应结果，成功时表示隐私设置已更新
     */
    Result updatePrivacySetting(PrivacySettingDTO dto);

    /**
     * 添加好友。
     *
     * @param dto 好友操作参数，通常包含目标用户 ID 和附言等信息
     * @return 统一响应结果，成功时表示好友申请已发起
     */
    Result addFriend(FriendOperationDTO dto);

    /**
     * 获取好友列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含好友分页列表
     */
    Result getFriendList(int page, int pageSize);

    /**
     * 同意好友申请。
     *
     * @param friendId 发起申请的目标用户 ID
     * @return 统一响应结果，成功时表示好友关系已建立
     */
    Result agreeFriend(Long friendId);

    /**
     * 拒绝好友申请。
     *
     * @param friendId 发起申请的目标用户 ID
     * @return 统一响应结果，成功时表示好友申请已拒绝
     */
    Result rejectFriend(Long friendId);

    /**
     * 删除好友。
     *
     * @param friendId 目标好友用户 ID
     * @return 统一响应结果，成功时表示好友关系已解除
     */
    Result deleteFriend(Long friendId);

    /**
     * 拉黑用户。
     *
     * @param dto 黑名单操作参数，包含目标用户 ID 和备注等信息
     * @return 统一响应结果，成功时表示用户已加入黑名单
     */
    Result addBlacklist(BlacklistOperationDTO dto);

    /**
     * 解除拉黑。
     *
     * @param blackUserId 被移出黑名单的用户 ID
     * @return 统一响应结果，成功时表示黑名单关系已删除
     */
    Result removeBlacklist(Long blackUserId);

    /**
     * 获取黑名单。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含黑名单分页列表
     */
    Result getBlacklist(int page, int pageSize);

    /**
     * 搜索用户。
     *
     * @param keyword 搜索关键词
     * @param type 搜索类型或排序类型
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含用户搜索结果
     */
    Result searchUser(String keyword, String type, int page, int pageSize);

    /**
     * 获取用户列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param sort 排序方式
     * @return 统一响应结果，成功时包含用户分页列表
     */
    Result getUserList(int page, int pageSize, String sort);

    /**
     * 查看用户资料。
     *
     * @param userId 目标用户 ID
     * @return 统一响应结果，成功时包含用户资料详情
     */
    Result getUserProfile(Long userId);

    /**
     * 获取好友申请列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 统一响应结果，成功时包含好友申请分页数据
     */
    Result getFriendRequests(int page, int pageSize);

    /**
     * 获取未读通知数。
     *
     * @return 统一响应结果，成功时包含未读通知统计
     */
    Result getUnreadNoticeCount();

    /**
     * 获取通知列表。
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @param isRead 已读状态筛选条件
     * @return 统一响应结果，成功时包含通知分页列表
     */
    Result getNoticeList(int page, int pageSize, Integer isRead);

    /**
     * 标记通知为已读。
     *
     * @param noticeIds 需要标记的通知 ID 列表
     * @return 统一响应结果，成功时表示通知状态已更新
     */
    Result markNoticeAsRead(java.util.List<Long> noticeIds);

    /**
     * 删除通知。
     *
     * @param noticeIds 需要删除的通知 ID 列表
     * @return 统一响应结果，成功时表示通知已删除
     */
    Result deleteNotice(java.util.List<Long> noticeIds);
}
