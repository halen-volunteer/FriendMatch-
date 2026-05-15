package com.zero.usercenter.config;

import com.zero.usercenter.utils.Interceptor.RegisterInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 基础配置。
 * 统一处理跨域规则，以及需要登录态的接口拦截。
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.security.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}")
    private String[] allowedOriginPatterns;

    /**
     * 配置用户端和管理端的跨域访问规则。
     * 这里额外暴露 captchaId 响应头，供前端完成图形验证码校验链路。
     *
     * @param registry Spring MVC 跨域配置注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 统一放开 /api/** 的跨域访问，前后端分离开发时直接复用这套规则。
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 前端提交图形验证码时，需要读取服务端返回的 captchaId。
                .exposedHeaders("captchaId")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册登录态拦截器。
     * 统一完成 token 校验、当前用户注入和滑动续期。
     *
     * @param registry Spring MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 对绝大多数 /api/** 接口开启登录态校验。
        // 2. 注册、登录、验证码、忘记密码等匿名接口显式排除。
        registry.addInterceptor(new RegisterInterceptor(stringRedisTemplate))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/captcha",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/code",
                        "/api/auth/forget"
                );
    }
}
