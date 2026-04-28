package com.zero.usercenter.config;

import com.zero.usercenter.utils.Interceptor.RegisterInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 暴露自定义响应头，让前端能读取图形验证码标识
                .exposedHeaders("captchaId")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //这里的逻辑是，拦截处理登录接口之外的一切接口，目的是重置redis中设置的用户过期时间
        //但是由于/me接口是将当前用户数据返回给前端，所以要进行拦截？
        registry.addInterceptor(new RegisterInterceptor(stringRedisTemplate))
                // 拦截所有 /api 开头的接口（系统全部接口）
                .addPathPatterns("/api/**")
                // 排除不需要登录的接口（免登录白名单）
                .excludePathPatterns(
                        "/api/auth/captcha",    // 图形验证码
                        "/api/auth/login",      // 登录
                        "/api/auth/register",   // 注册
                        "/api/auth/code"  ,      // 发送邮箱验证码
                        "/api/auth/forget"      //忘记密码
                );
    }
}
