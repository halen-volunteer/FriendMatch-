package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.UserBlacklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户黑名单 Mapper
 */
@Mapper
public interface UserBlacklistMapper extends BaseMapper<UserBlacklist> {
}
