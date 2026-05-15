package com.zero.usercenter.Service.impl;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.zero.usercenter.DTO.Result;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.zero.usercenter.utils.Number.*;

/**
 * 用户信息校验支撑类。
 * 统一封装账号、密码、用户名、邮箱等基础校验规则，避免控制层和服务层重复散落硬编码。
 */
@Component
public class UserValidationSupport {

    @Resource
    private SensitiveWordBs sensitiveWordBs;

    /**
     * 校验用户账号格式。
     *
     * @param userAccount 用户账号
     * @return 校验结果
     */
    public Result checkUserAccount(String userAccount) {
        // 1. 先校验长度，避免过短或过长账号影响展示与索引设计。
        if (userAccount.length() < 6 || userAccount.length() > 20) {
            return Result.fail("账户长度需在6-20位之间");
        }

        // 2. 再校验字符集，只允许字母、数字和下划线，便于前后端统一处理。
        if (!Pattern.matches("^[a-zA-Z0-9_]+$", userAccount)) {
            return Result.fail("账户只能包含字母、数字和下划线");
        }
        return Result.ok();
    }

    /**
     * 校验用户密码强度。
     *
     * @param userPassword 用户密码
     * @return 校验结果
     */
    public Result checkUserPassword(String userPassword) {
        // 1. 先限制最小长度，避免过弱密码。
        if (userPassword.length() < 8) {
            return Result.fail("密码长度不能小于8位");
        }

        // 2. 再要求同时包含大小写字母，作为基础复杂度门槛。
        if (!Pattern.matches(".*[A-Z].*", userPassword) || !Pattern.matches(".*[a-z].*", userPassword)) {
            return Result.fail("密码需要同时包含大写字母和小写字母");
        }
        return Result.ok();
    }

    /**
     * 校验用户名格式与内容安全性。
     *
     * @param trimUsername 去除首尾空格后的用户名
     * @return 校验结果
     */
    public Result checkUsername(String trimUsername) {
        // 1. 先校验用户名长度，防止过短无辨识度或过长影响展示。
        if (trimUsername.length() < 3 || trimUsername.length() > 16) {
            return Result.fail("用户名长度需在3-16位之间");
        }

        // 2. 再校验字符规则，限制可用字符并禁止以下划线开头。
        if (!trimUsername.matches(USERNAME_CN_REGEX)) {
            return Result.fail("用户名仅支持中文、字母、数字、下划线，且不能以下划线开头");
        }

        // 3. 系统保留名不能被注册，避免和官方身份、内置账号混淆。
        if (RESERVED_NAMES.contains(trimUsername.toLowerCase())) {
            return Result.fail("该用户名已被系统保留，请更换");
        }

        // 4. 最后走敏感词校验，降低违规昵称进入系统的风险。
        if (sensitiveWordBs.contains(trimUsername)) {
            return Result.fail("用户名包含违规内容，请更换");
        }
        return Result.ok();
    }

    /**
     * 校验邮箱格式。
     *
     * @param trimEmail 去除首尾空格后的邮箱
     * @return 校验结果
     */
    public Result checkEmail(String trimEmail) {
        // 1. 先限制邮箱最大长度，避免异常长字符串进入数据库。
        if (trimEmail.length() > MAX_EMAIL_LENGTH) {
            return Result.fail("邮箱长度不能超过128位");
        }

        // 2. 再校验邮箱正则格式，过滤明显非法输入。
        if (!trimEmail.matches(EMAIL_REGEX)) {
            return Result.fail("邮箱格式错误（示例：xxx@qq.com）");
        }
        return Result.ok();
    }
}
