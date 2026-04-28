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

@Component
public class UserAccountGeneratorSupport {

    private static final Snowflake SNOWFLAKE;

    static {
        long workerId;
        try {
            workerId = NetUtil.ipv4ToLong(NetUtil.getLocalhostStr()) % 31;
            workerId = Math.max(0, Math.min(workerId, 31));
        } catch (Exception e) {
            workerId = new Random().nextInt(31);
        }
        SNOWFLAKE = IdUtil.getSnowflake(workerId, 1);
    }

    @Resource
    private UserMapper userMapper;

    public String generateSnowflakeAccount() {
        String account = createAccount();
        if (checkAccountExists(account)) {
            account = createAccount();
        }
        return account;
    }

    private String createAccount() {
        long snowflakeId = SNOWFLAKE.nextId();
        String snowflakeStr = String.valueOf(snowflakeId);
        String subStr = snowflakeStr.length() >= 10
                ? snowflakeStr.substring(snowflakeStr.length() - 10)
                : snowflakeStr;
        return String.format("%10s", subStr).replace(' ', '0');
    }

    private boolean checkAccountExists(String account) {
        if (StrUtil.isBlank(account)) {
            return true;
        }
        return userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUserAccount, account));
    }
}
