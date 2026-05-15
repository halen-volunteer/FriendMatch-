package com.zero.usercenter.websocket;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.Map;

import static com.zero.usercenter.utils.Number.REDIS_WS_TICKET_KEY;

/**
 * WebSocket 配置类。
 * 当前采用一次性 ws-ticket 握手方案，而不是直接携带长期 token 建连。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${app.security.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}")
    private String[] allowedOriginPatterns;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注册聊天 WebSocket 端点，并挂上握手拦截器完成 ticket 校验。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(new TokenHandshakeInterceptor())
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * 握手拦截器。
     * 从 query 中取 ticket，到 Redis 校验并在成功后立刻删除，避免重复使用。
     */
    private class TokenHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String ticket = servletRequest.getServletRequest().getParameter("ticket");
                if (ticket == null || ticket.isBlank()) {
                    log.warn("[WS] 握手失败，ticket 为空");
                    return false;
                }
                String ticketKey = REDIS_WS_TICKET_KEY + ticket;
                String userIdValue = stringRedisTemplate.opsForValue().get(ticketKey);
                if (userIdValue == null || userIdValue.isBlank()) {
                    log.warn("[WS] 握手失败，ticket 无效或已过期");
                    return false;
                }

                // 校验通过后立刻删除 ticket，避免被截获后重复建立连接。
                Boolean deleted = stringRedisTemplate.delete(ticketKey);
                if (Boolean.FALSE.equals(deleted)) {
                    log.warn("[WS] 握手失败，ticket 已被消费 ticket={}", ticket);
                    return false;
                }
                Long userId = Long.valueOf(userIdValue);
                attributes.put("userId", userId);
                log.info("[WS] 握手成功，userId={}", userId);
                return true;
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 当前无额外收尾逻辑。
        }
    }
}
