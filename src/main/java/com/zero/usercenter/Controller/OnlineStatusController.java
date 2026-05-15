package com.zero.usercenter.Controller;

import cn.hutool.core.util.StrUtil;
import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.exception.BusinessException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static com.zero.usercenter.utils.Number.*;

/**
 * 用户在线状态接口入口。
 * 在线状态值存放在 Redis Hash 中，过期时间单独维护在 ZSet 中，用于心跳续期和离线清理。
 */
@Slf4j
@RestController
@RequestMapping("/api/online")
public class OnlineStatusController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置当前用户在线状态。
     * 可选状态包括在线、离开、忙碌和隐身，并会同步刷新在线状态过期时间。
     */
    @PostMapping("/status")
    public Result setOnlineStatus(@RequestParam Integer status) {
        // 1. 先确认登录并校验状态值，防止非法状态写入 Redis。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (status == null || status < 1 || status > 4) return Result.fail("状态值无效（1-在线,2-离开,3-忙碌,4-隐身）");

        // 2. 在线状态本体写入 Hash，过期时间写入 ZSet，便于后续心跳续期和离线清理。
        long expireAt = System.currentTimeMillis() + USER_ONLINE_TIMEOUT_MINUTES * 60 * 1000;
        stringRedisTemplate.opsForHash().put(USER_ONLINE_KEY, userId.toString(), status.toString());
        stringRedisTemplate.opsForZSet().add(USER_ONLINE_TTL_KEY, userId.toString(), expireAt);
        return Result.ok("在线状态已更新");
    }

    /**
     * 心跳续期。
     * 前端定时调用本接口，避免用户长连接存活但在线状态被误判为离线。
     */
    @PostMapping("/heartbeat")
    public Result heartbeat() {
        // 1. 心跳只给当前登录用户续期，不接受代他人续期。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 2. 若状态不存在则默认设为在线，避免只有心跳没有显式设置状态的场景丢失在线态。
        Object current = stringRedisTemplate.opsForHash().get(USER_ONLINE_KEY, userId.toString());
        if (current == null) {
            stringRedisTemplate.opsForHash().put(USER_ONLINE_KEY, userId.toString(), "1");
        }
        // 3. 每次心跳都刷新 TTL 索引，确保长连接活跃期间不会被误清理。
        long expireAt = System.currentTimeMillis() + USER_ONLINE_TIMEOUT_MINUTES * 60 * 1000;
        stringRedisTemplate.opsForZSet().add(USER_ONLINE_TTL_KEY, userId.toString(), expireAt);
        return Result.ok();
    }

    /**
     * 查询指定用户的在线状态。
     * 若状态不存在则统一按离线返回。
     */
    @GetMapping("/status")
    public Result getOnlineStatus(@RequestParam Long userId) {
        // 在线状态读取只查 Redis，查不到统一按离线返回，简化前端判断。
        if (userId == null) return Result.fail("用户ID不能为空");
        Object status = stringRedisTemplate.opsForHash().get(USER_ONLINE_KEY, userId.toString());
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("status", status != null ? Integer.parseInt(status.toString()) : 0); // 0 表示离线。
        return Result.ok(data);
    }

    /**
     * 主动下线。
     * 会立即清理当前用户的在线状态、过期时间索引以及登录 token。
     */
    @PostMapping("/offline")
    public Result goOffline(HttpServletRequest request) {
        // 1. 先确认当前请求已经登录，避免匿名请求误删在线状态。
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 2. 主动下线时同时删除在线状态值和 TTL 索引，避免离线后仍被误判在线。
        stringRedisTemplate.opsForHash().delete(USER_ONLINE_KEY, userId.toString());
        stringRedisTemplate.opsForZSet().remove(USER_ONLINE_TTL_KEY, userId.toString());

        // 3. 再清理当前请求对应的 token 登录态，让“退出登录”真正释放单账号唯一登录占位。
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            String activeTokenKey = USER_ACTIVE_TOKEN_KEY + userId;
            String activeToken = stringRedisTemplate.opsForValue().get(activeTokenKey);
            stringRedisTemplate.delete(TOKEN_KEY + token);
            if (token.equals(activeToken)) {
                stringRedisTemplate.delete(activeTokenKey);
            }
        }
        return Result.ok("已下线");
    }
}
