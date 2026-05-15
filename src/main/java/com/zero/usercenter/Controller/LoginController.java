package com.zero.usercenter.Controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zero.usercenter.DTO.LoginDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.DTO.UserFormat;
import com.zero.usercenter.Service.AdminAuthService;
import com.zero.usercenter.Service.UserService;
import com.zero.usercenter.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.REDIS_CAPTCHA_KEY;
import static com.zero.usercenter.utils.Number.REDIS_WS_TICKET_KEY;
import static com.zero.usercenter.utils.Number.WS_TICKET_TTL_SECONDS;

/**
 * 用户认证 Controller。
 * 负责登录、注册、验证码、找回密码以及 WebSocket 握手票据发放。
 *
 * 基础路径：`/api/auth`
 * 其中登录、注册、发码、图形验证码、找回密码属于免登录接口。
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    @Autowired
    private UserService userService;
    @Autowired
    private AdminAuthService adminAuthService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成图形验证码。
     * 验证码内容写入 Redis，前端通过响应头 `captchaId` 回传校验标识。
     */
    @GetMapping("/captcha")
    public void generateCatch(HttpServletResponse response) throws IOException {
        // 1. 现场生成图形验证码内容，不依赖数据库持久化。
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(100, 40, 4, 40);
        String code = captcha.getCode();
        String captchaId = UUID.randomUUID().toString();
        // 2. 以 captchaId 为键把验证码写入 Redis，供后续注册/登录链路校验。
        stringRedisTemplate.opsForValue().set(REDIS_CAPTCHA_KEY + captchaId, code, 1, TimeUnit.MINUTES);
        // 3. 把 captchaId 放到响应头中，前端后续提交验证码时需要一并回传。
        response.setHeader("captchaId", captchaId);
        captcha.write(response.getOutputStream());
    }

    /**
     * 用户登录。
     * 额外传入 HttpServletRequest，用于记录真实 IP 和登录日志。
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        // 登录链路会在 service 层处理账号校验、验证码/密码校验、IP 记录和 token 发放。
        return userService.userLogin(loginDTO, request);
    }

    /**
     * 用户注册。
     * 当前注册链路采用邮箱验证码校验。
     */
    @PostMapping("/register")
    public Result register(@RequestParam("username") String username,
                           @RequestParam("email") String email,
                           @RequestParam("emailCode") String emailCode,
                           @RequestParam("password") String password) {
        // 注册接口只负责收集参数，邮箱验证码校验和用户创建都在 service 层完成。
        return userService.userRegister(username, email, emailCode, password);
    }

    /**
     * 发送邮箱验证码。
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("email") String email) {
        // 发送邮箱验证码会在 service 中处理频控、验证码生成和邮件投递。
        return userService.sendCode(email);
    }

    /**
     * 获取当前登录用户信息。
     * 这里会补充一个 isAdmin 字段，方便前端按权限渲染入口。
     */
    @PostMapping("/me")
    public Result me() {
        // 1. 直接从 UserHolder 中读取当前登录用户，避免重复查库。
        UserFormat user = UserHolder.getUser();
        if (user != null) {
            // 2. 补充一个 isAdmin 字段，方便前端按权限显示管理入口。
            boolean isAdmin = adminAuthService.isAdmin(user.getId());
            user.setIsAdmin(isAdmin);
        }
        return Result.ok(user);
    }

    /**
     * 申请 WebSocket 一次性握手票据。
     * 前端先调用本接口获取短时 ticket，再带着 ticket 连接 `/ws`。
     */
    @PostMapping("/ws-ticket")
    public Result websocketTicket() {
        // 1. 只有已登录用户才能申请 WebSocket 握手票据。
        UserFormat user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("用户未登录");
        }
        // 2. 生成一次性短时 ticket，并在 Redis 中保存 userId 映射。
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                REDIS_WS_TICKET_KEY + ticket,
                String.valueOf(user.getId()),
                WS_TICKET_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        // 3. 返回 ticket 和过期时间，前端再携带 ticket 去连接 `/ws`。
        Map<String, Object> data = new HashMap<>();
        data.put("ticket", ticket);
        data.put("expiresIn", WS_TICKET_TTL_SECONDS);
        return Result.ok(data);
    }

    /**
     * 忘记密码。
     * 通过邮箱验证码重置登录密码。
     */
    @PostMapping("/forget")
    public Result forgetPassword(@RequestParam("email") String email,
                                 @RequestParam("emailCode") String emailCode,
                                 @RequestParam("newPassword") String newPassword) {
        // 忘记密码链路会在 service 层完成验证码校验和密码更新。
        return userService.forgetPassword(email, emailCode, newPassword);
    }
}
