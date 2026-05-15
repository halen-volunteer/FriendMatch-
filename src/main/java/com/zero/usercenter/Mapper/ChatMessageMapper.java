package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper。
 * 负责聊天消息主表的基础 CRUD，私聊、群聊、搜索、撤回等链路都会复用这里。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
