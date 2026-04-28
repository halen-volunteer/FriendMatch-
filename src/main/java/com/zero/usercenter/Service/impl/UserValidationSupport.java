package com.zero.usercenter.Service.impl;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.Result;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.zero.usercenter.utils.Number.*;

@Component
public class UserValidationSupport {

    @Resource
    private SensitiveWordBs sensitiveWordBs;

    public Result checkUserAccount(String userAccount) {
        if (userAccount.length() < 6 || userAccount.length() > 20) {
            return Result.fail("账户长度需在6-20位之间");
        }
        if (!Pattern.matches("^[a-zA-Z0-9_]+$", userAccount)) {
            return Result.fail("账户只能包含字母、数字和下划线");
        }
        return Result.ok();
    }

    public Result checkUserPassword(String userPassword) {
        if (userPassword.length() < 8) {
            return Result.fail("密码长度不能小于8位");
        }
        if (!Pattern.matches(".*[A-Z].*", userPassword) || !Pattern.matches(".*[a-z].*", userPassword)) {
            return Result.fail("密码需要同时包含大写字母和小写字母");
        }
        return Result.ok();
    }

    public Result checkUsername(String trimUsername) {
        if (trimUsername.length() < 3 || trimUsername.length() > 16) {
            return Result.fail("用户名长度需在3-16位之间");
        }
        if (!trimUsername.matches(USERNAME_CN_REGEX)) {
            return Result.fail("用户名仅支持中文、字母、数字、下划线，且不能以下划线开头");
        }
        if (RESERVED_NAMES.contains(trimUsername.toLowerCase())) {
            return Result.fail("该用户名已被系统保留，请更换");
        }
        if (sensitiveWordBs.contains(trimUsername)) {
            return Result.fail("用户名包含违规内容，请更换");
        }
        return Result.ok();
    }

    public Result checkEmail(String trimEmail) {
        if (trimEmail.length() > MAX_EMAIL_LENGTH) {
            return Result.fail("邮箱长度不能超过128位");
        }
        if (!trimEmail.matches(EMAIL_REGEX)) {
            return Result.fail("邮箱格式错误（示例：xxx@qq.com）");
        }
        return Result.ok();
    }
}
