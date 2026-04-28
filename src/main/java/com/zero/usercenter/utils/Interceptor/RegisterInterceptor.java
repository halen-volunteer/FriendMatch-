package com.zero.usercenter.utils.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zero.usercenter.DTO.UserFormat;
import com.zero.usercenter.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zero.usercenter.utils.Number.TOKEN_KEY;
import static com.zero.usercenter.utils.Number.TOKEN_TTL_MINUTES;

/**
 * 登录鉴权拦截器
 * 从请求头读取 Token，基于 Redis Key "token:{token}" 校验登录态，并刷新 TTL（滑动续期）
 */
public class RegisterInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造函数，注入 Redis 模板
     *
     * @param stringRedisTemplate Spring Redis 字符串操作模板
     */
    public RegisterInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 请求前置处理：校验 Token、刷新 TTL、写入 ThreadLocal
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  处理器对象
     * @return true-放行，false-拦截并返回 401
     * @throws Exception 处理异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行，否则会导致携带 Authorization 的跨域请求在 OPTIONS 阶段被 401 拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(200);
            return true;
        }

        // 1. 获取请求头中的 Token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }

        // 2. 基于 token:{token} 从 Redis 取用户 Hash
        String redisKey = TOKEN_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(redisKey);
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        // 3. 反序列化为 UserFormat（key 与字段名一致，BeanUtil 可正确映射）
        UserFormat userFormat = BeanUtil.fillBeanWithMap(userMap, new UserFormat(), false);

        // 4. 滑动续期：每次请求重置 TTL 为 TOKEN_TTL_MINUTES
        stringRedisTemplate.expire(redisKey, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

        // 5. 存入 ThreadLocal，供本次请求链路使用
        UserHolder.saveUser(userFormat);
        return true;
    }

    /**
     * 请求完成后置处理：清理 ThreadLocal 防止内存泄漏
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  处理器对象
     * @param ex       处理过程中抛出的异常（无异常时为 null）
     * @throws Exception 处理异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, @Nullable Exception ex) throws Exception {
        // 请求结束后清理 ThreadLocal，防止内存泄漏
        UserHolder.removeUser();
    }
}
