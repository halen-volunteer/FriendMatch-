package com.zero.usercenter.websocket;

import com.zero.usercenter.utils.Number;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.annotation.Resource;
import java.util.Map;

import static com.zero.usercenter.utils.Number.TOKEN_KEY;

/**
 * WebSocket 配置类
 * 注册 /ws/{userId} 端点，握手时校验 Token 并写入 userId
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(new TokenHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    /**
     * 握手拦截器：从请求参数中获取 token，校验并提取 userId 存入 attributes
     */
    private class TokenHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String token = servletRequest.getServletRequest().getParameter("token");
                if (token == null || token.isBlank()) {
                    log.warn("[WS] 握手失败：token 为空");
                    return false;
                }
                // 从 Redis Hash 获取用户ID
                Object idObj = stringRedisTemplate.opsForHash().get(TOKEN_KEY + token, "id");
                if (idObj == null) {
                    log.warn("[WS] 握手失败：token 无效或已过期");
                    return false;
                }
                Long userId = Long.valueOf(idObj.toString());
                attributes.put("userId", userId);
                log.info("[WS] 握手成功：userId={}", userId);
                return true;
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 握手后无需额外处理
        }
    }
}
