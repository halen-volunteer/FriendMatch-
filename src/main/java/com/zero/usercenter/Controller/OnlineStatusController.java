package com.zero.usercenter.Controller;

import com.zero.usercenter.DTO.Result;
import com.zero.usercenter.utils.UserHolder;
import com.zero.usercenter.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static com.zero.usercenter.utils.Number.*;

/**
 * 用户在线状态 Controller
 *
 * 基础路径：/api/online
 * 所有接口需在请求头携带 Authorization: {token}
 *
 * Redis 结构：
 *   user_online       Hash  field=userId  value=状态(1-在线,2-离开,3-忙碌,4-隐身)
 *   user_online_ttl   ZSet  member=userId  score=过期时间戳
 */
@Slf4j
@RestController
@RequestMapping("/api/online")
public class OnlineStatusController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置当前用户在线状态
     * POST /api/online/status
     * status：1-在线，2-离开，3-忙碌，4-隐身
     * 同时刷新 TTL（5分钟）
     *
     * @param status 在线状态值（1-在线，2-离开，3-忙碌，4-隐身）
     * @return 设置结果
     */
    @PostMapping("/status")
    public Result setOnlineStatus(@RequestParam Integer status) {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        if (status == null || status < 1 || status > 4) return Result.fail("状态值无效（1-在线,2-离开,3-忙碌,4-隐身）");

        long expireAt = System.currentTimeMillis() + USER_ONLINE_TIMEOUT_MINUTES * 60 * 1000;
        stringRedisTemplate.opsForHash().put(USER_ONLINE_KEY, userId.toString(), status.toString());
        stringRedisTemplate.opsForZSet().add(USER_ONLINE_TTL_KEY, userId.toString(), expireAt);
        return Result.ok("在线状态已更新");
    }

    /**
     * 心跳接口：刷新在线状态 TTL（保持在线）
     * POST /api/online/heartbeat
     * 前端每 2-3 分钟调用一次
     */
    @PostMapping("/heartbeat")
    public Result heartbeat() {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");

        // 若状态不存在则默认设为在线
        Object current = stringRedisTemplate.opsForHash().get(USER_ONLINE_KEY, userId.toString());
        if (current == null) {
            stringRedisTemplate.opsForHash().put(USER_ONLINE_KEY, userId.toString(), "1");
        }
        long expireAt = System.currentTimeMillis() + USER_ONLINE_TIMEOUT_MINUTES * 60 * 1000;
        stringRedisTemplate.opsForZSet().add(USER_ONLINE_TTL_KEY, userId.toString(), expireAt);
        return Result.ok();
    }

    /**
     * 查询指定用户的在线状态
     * GET /api/online/status?userId=1001
     * 返回：status（1-在线,2-离开,3-忙碌,4-隐身,0-离线）
     *
     * @param userId 要查询的用户 ID
     * @return 用户在线状态信息
     */
    @GetMapping("/status")
    public Result getOnlineStatus(@RequestParam Long userId) {
        if (userId == null) return Result.fail("用户ID不能为空");
        Object status = stringRedisTemplate.opsForHash().get(USER_ONLINE_KEY, userId.toString());
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("status", status != null ? Integer.parseInt(status.toString()) : 0); // 0=离线
        return Result.ok(data);
    }

    /**
     * 主动下线（清除在线状态）
     * POST /api/online/offline
     */
    @PostMapping("/offline")
    public Result goOffline() {
        Long userId = UserHolder.getUserId();
        if (userId == null) throw new BusinessException("用户未登录");
        stringRedisTemplate.opsForHash().delete(USER_ONLINE_KEY, userId.toString());
        stringRedisTemplate.opsForZSet().remove(USER_ONLINE_TTL_KEY, userId.toString());
        return Result.ok("已下线");
    }
}
