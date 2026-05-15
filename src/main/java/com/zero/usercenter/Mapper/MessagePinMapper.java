package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.MessagePin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息置顶 Mapper。
 * 负责会话内置顶消息记录的增删改查，置顶顺序计算和可见性校验由 service 层处理。
 */
@Mapper
public interface MessagePinMapper extends BaseMapper<MessagePin> {
}
