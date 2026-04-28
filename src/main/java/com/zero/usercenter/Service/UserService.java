package com.zero.usercenter.Service;

import com.zero.usercenter.DTO.LoginDTO;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.Model.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户 Service 接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录（传入 request 用于获取真实 IP）
     *
     * @param loginDTO 登录数据传输对象，包含账号、密码、图形验证码等
     * @param request  HTTP 请求对象，用于获取客户端真实 IP
     * @return 登录结果，包含 token
     */
    Result userLogin(LoginDTO loginDTO, HttpServletRequest request);

    /**
     * 用户注册
     *
     * @param username  用户名
     * @param email     注册邮箱
     * @param emailCode 邮箱验证码
     * @param password  登录密码
     * @return 注册结果
     */
    Result userRegister(String username, String email, String emailCode, String password);

    /**
     * 发送邮箱验证码
     *
     * @param email 目标邮箱地址
     * @return 发送结果
     */
    Result sendCode(String email);

    /**
     * 忘记密码 —— 通过邮箱验证码重置
     *
     * @param email       注册邮箱
     * @param emailCode   邮箱验证码
     * @param newPassword 新密码
     * @return 重置结果
     */
    Result forgetPassword(String email, String emailCode, String newPassword);
}
