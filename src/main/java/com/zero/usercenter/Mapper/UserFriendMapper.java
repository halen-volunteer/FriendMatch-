package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.UserFriend;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户好友关系 Mapper
 */
@Mapper
public interface UserFriendMapper extends BaseMapper<UserFriend> {
}
