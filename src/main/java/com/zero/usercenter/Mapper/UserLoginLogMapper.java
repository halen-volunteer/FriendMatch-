package com.zero.usercenter.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zero.usercenter.Model.UserLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户登录日志 Mapper。
 * 负责登录审计日志的基础 CRUD，主要由 RabbitMQ 登录日志消费者异步写入。
 */
@Mapper
public interface UserLoginLogMapper extends BaseMapper<UserLoginLog> {
}
