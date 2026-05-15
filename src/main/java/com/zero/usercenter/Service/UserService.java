package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.LoginDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Model.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户认证与账号服务接口。
 * 覆盖登录、注册、验证码发送和忘记密码等基础认证能力。
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录。
     * 传入 request 主要用于获取真实 IP 并记录登录日志。
     *
     * @param loginDTO 登录参数，包含账号、密码及登录端信息
     * @param request 当前 HTTP 请求对象，用于提取客户端上下文
     * @return 统一响应结果，成功时包含登录后的用户信息和认证状态
     */
    Result userLogin(LoginDTO loginDTO, HttpServletRequest request);

    /**
     * 用户主动退出登录。
     * 会清理当前请求携带 token 对应的 Redis 登录态，以及该用户的唯一活跃 token 索引。
     *
     * @param request 当前 HTTP 请求对象，用于读取 authorization token
     * @return 统一响应结果，成功时表示当前登录态已失效
     */
    Result logout(HttpServletRequest request);

    /**
     * 用户注册。
     *
     * @param username 用户昵称或用户名
     * @param email 注册邮箱
     * @param emailCode 邮箱验证码
     * @param password 明文密码，方法内部会完成合法性校验和加密
     * @return 统一响应结果，成功时表示账号注册完成
     */
    Result userRegister(String username, String email, String emailCode, String password);

    /**
     * 发送邮箱验证码。
     *
     * @param email 目标邮箱地址
     * @return 统一响应结果，成功时表示验证码已发送
     */
    Result sendCode(String email);

    /**
     * 通过邮箱验证码重置密码。
     *
     * @param email 账号绑定邮箱
     * @param emailCode 邮箱验证码
     * @param newPassword 新密码
     * @return 统一响应结果，成功时表示密码已重置
     */
    Result forgetPassword(String email, String emailCode, String newPassword);
}
