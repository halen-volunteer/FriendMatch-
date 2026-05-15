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
import static com.zero.usercenter.utils.Number.USER_ACTIVE_TOKEN_KEY;

/**
 * 登录态拦截器。
 * 基于请求头中的 token 到 Redis 校验登录状态，并执行滑动续期。
 */
public class RegisterInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造登录态拦截器。
     *
     * @param stringRedisTemplate Redis 操作对象，用于读取和续期登录态
     */
    public RegisterInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 校验 token，并把当前用户写入 ThreadLocal。
     * 这里不依赖数据库查询，而是直接复用 Redis 登录态，减少每次请求的认证开销。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 当前命中的处理器
     * @return true-放行，false-拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行，避免跨域场景在 OPTIONS 阶段被 401 拦截。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(200);
            return true;
        }

        // 1. 从请求头读取登录 token，没有 token 直接返回 401。
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }

        // 2. 用 token 到 Redis 读取登录态，避免每次请求都查数据库。
        String redisKey = TOKEN_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(redisKey);
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        // 3. 把 Redis Hash 反序列化为当前请求上下文中的用户对象。
        UserFormat userFormat = BeanUtil.fillBeanWithMap(userMap, new UserFormat(), false);

        // 4. 校验当前 token 是否仍是该账号唯一允许生效的活跃 token。
        // 如果索引已经指向了别的 token，说明当前 token 是旧设备或旧会话，应立即视为失效。
        String activeTokenKey = USER_ACTIVE_TOKEN_KEY + userFormat.getId();
        String activeToken = stringRedisTemplate.opsForValue().get(activeTokenKey);
        if (StrUtil.isNotBlank(activeToken) && !token.equals(activeToken)) {
            stringRedisTemplate.delete(redisKey);
            response.setStatus(401);
            return false;
        }

        // 5. 每次有效请求都同时刷新 token Hash 和“用户 -> 当前活跃 token”索引的 TTL，维持滑动过期。
        stringRedisTemplate.expire(redisKey, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(activeTokenKey, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);

        // 6. 保存到 ThreadLocal，方便 service、AOP、权限校验链路直接取当前用户。
        UserHolder.saveUser(userFormat);
        return true;
    }

    /**
     * 请求完成后清理 ThreadLocal，防止线程复用导致脏数据串用。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 当前命中的处理器
     * @param ex 请求处理异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
