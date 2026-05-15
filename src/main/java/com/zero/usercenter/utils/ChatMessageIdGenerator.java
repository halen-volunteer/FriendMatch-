package com.zero.usercenter.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 聊天消息 ID 生成器。
 * 为了让消息在“入队前”就拥有稳定业务 ID，这里使用雪花算法生成消息主键，
 * 这样发送成功响应、后续编辑/撤回请求和 MQ 消费落库都可以围绕同一个 msgId 协同。
 */
@Component
public class ChatMessageIdGenerator {

    private static final Snowflake SNOWFLAKE;

    static {
        long workerId;
        try {
            workerId = NetUtil.ipv4ToLong(NetUtil.getLocalhostStr()) % 31;
            workerId = Math.max(0, Math.min(workerId, 31));
        } catch (Exception e) {
            workerId = new Random().nextInt(31);
        }
        SNOWFLAKE = IdUtil.getSnowflake(workerId, 2);
    }

    /**
     * 生成一个全局唯一的聊天消息 ID。
     *
     * @return 雪花算法生成的消息 ID
     */
    public Long nextId() {
        return SNOWFLAKE.nextId();
    }
}
