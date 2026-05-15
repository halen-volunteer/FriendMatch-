package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.MessageCollection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息收藏 Mapper。
 * 负责消息收藏记录的基础 CRUD，供收藏、新增备注、取消收藏等能力复用。
 */
@Mapper
public interface MessageCollectionMapper extends BaseMapper<MessageCollection> {
}
