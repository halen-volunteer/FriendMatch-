package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.UserFriend;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户好友关系 Mapper。
 * 负责好友申请、好友关系、拒绝状态和拉黑降级状态的持久化操作。
 */
@Mapper
public interface UserFriendMapper extends BaseMapper<UserFriend> {
}
