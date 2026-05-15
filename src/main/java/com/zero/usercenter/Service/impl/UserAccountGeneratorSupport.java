package com.zero.usercenter.Service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.UserMapper;
import com.zero.usercenter.Model.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 用户账号生成支撑类。
 * 统一负责生成格式稳定、碰撞概率低的用户账号，并在落库前做一次存在性校验。
 */
@Component
public class UserAccountGeneratorSupport {

    private static final Snowflake SNOWFLAKE;

    static {
        // 1. 优先根据本机 IP 生成 workerId，降低多实例之间的雪花 ID 冲突概率。
        long workerId;
        try {
            workerId = NetUtil.ipv4ToLong(NetUtil.getLocalhostStr()) % 31;
            workerId = Math.max(0, Math.min(workerId, 31));
        } catch (Exception e) {
            // 2. 获取 IP 失败时退化为随机 workerId，保证服务仍可启动。
            workerId = new Random().nextInt(31);
        }
        SNOWFLAKE = IdUtil.getSnowflake(workerId, 1);
    }

    @Resource
    private UserMapper userMapper;

    /**
     * 生成一个尽量唯一的用户账号。
     *
     * @return 10 位账号字符串
     */
    public String generateSnowflakeAccount() {
        // 1. 先根据雪花 ID 规则生成账号。
        String account = createAccount();
        if (checkAccountExists(account)) {
            // 2. 如果碰巧撞号，则再生成一次，作为轻量兜底。
            account = createAccount();
        }
        return account;
    }

    /**
     * 基于雪花 ID 生成 10 位账号。
     *
     * @return 10 位账号字符串
     */
    private String createAccount() {
        // 1. 取雪花 ID 的后 10 位作为账号主体，兼顾长度稳定和随机性。
        long snowflakeId = SNOWFLAKE.nextId();
        String snowflakeStr = String.valueOf(snowflakeId);
        String subStr = snowflakeStr.length() >= 10
                ? snowflakeStr.substring(snowflakeStr.length() - 10)
                : snowflakeStr;

        // 2. 不足 10 位时左侧补 0，保证账号长度固定。
        return String.format("%10s", subStr).replace(' ', '0');
    }

    /**
     * 检查账号是否已被占用。
     *
     * @param account 待检查账号
     * @return true 表示账号为空或已存在，false 表示可用
     */
    private boolean checkAccountExists(String account) {
        // 1. 空账号直接按不可用处理，避免把异常值继续向下游传递。
        if (StrUtil.isBlank(account)) {
            return true;
        }

        // 2. 通过账号唯一字段查询数据库，确认是否已被占用。
        return userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUserAccount, account));
    }
}
