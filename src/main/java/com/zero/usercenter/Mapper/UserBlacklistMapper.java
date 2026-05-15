package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.UserBlacklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户黑名单 Mapper。
 * 负责拉黑关系的基础 CRUD，供资料可见性、私聊权限、搜索过滤等场景复用。
 */
@Mapper
public interface UserBlacklistMapper extends BaseMapper<UserBlacklist> {
}
