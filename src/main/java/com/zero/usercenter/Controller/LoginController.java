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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.REDIS_CAPTCHA_KEY;

/**
 * 用户认证 Controller
 * 负责注册、登录、登出、Token 续期、忘记密码等认证相关接口
 *
 * 基础路径：/api/auth
 * 登录/注册接口无需鉴权，其余接口需在请求头携带 Authorization: {token}
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
     * 生成图形验证码
     * 返回图片流，同时通过响应头 captchaId 传递 Redis 查询标识
     *
     * @param response HTTP 响应对象，用于写出图片流及响应头
     * @throws IOException 图片流写出异常
     */
    @GetMapping("/captcha")
    public void generateCatch(HttpServletResponse response) throws IOException {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(100, 40, 4, 40);
        String code = captcha.getCode();
        String captchaId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(REDIS_CAPTCHA_KEY + captchaId, code, 1, TimeUnit.MINUTES);
        response.setHeader("captchaId", captchaId);
        captcha.write(response.getOutputStream());
    }

    /**
     * 用户登录
     * 传入 HttpServletRequest 用于获取客户端真实 IP，写入登录日志和更新 last_login_ip
     *
     * @param loginDTO 登录数据传输对象，包含账号、密码、图形验证码 ID 及验证码
     * @param request  HTTP 请求对象，用于获取客户端真实 IP
     * @return 登录结果，成功时包含 token
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        return userService.userLogin(loginDTO, request);
    }

    /**
     * 用户注册（邮箱验证码注册）
     *
     * @param username  用户名
     * @param email     注册邮箱
     * @param emailCode 邮箱验证码
     * @param password  登录密码
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result register(@RequestParam("username") String username,
                           @RequestParam("email") String email,
                           @RequestParam("emailCode") String emailCode,
                           @RequestParam("password") String password) {
        return userService.userRegister(username, email, emailCode, password);
    }

    /**
     * 发送邮箱验证码
     *
     * @param email 目标邮箱地址
     * @return 发送结果
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("email") String email) {
        return userService.sendCode(email);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户信息
     */
    @PostMapping("/me")
    public Result me() {
        UserFormat user = UserHolder.getUser();
        if (user != null) {
            boolean isAdmin = adminAuthService.isAdmin(user.getId());
            user.setIsAdmin(isAdmin);
        }
        return Result.ok(user);
    }

    /**
     * 忘记密码 —— 通过邮箱验证码重置
     *
     * @param email       注册邮箱
     * @param emailCode   邮箱验证码
     * @param newPassword 新密码
     * @return 重置结果
     */
    @PostMapping("/forget")
    public Result forgetPassword(@RequestParam("email") String email,
                                 @RequestParam("emailCode") String emailCode,
                                 @RequestParam("newPassword") String newPassword) {
        return userService.forgetPassword(email, emailCode, newPassword);
    }
}
