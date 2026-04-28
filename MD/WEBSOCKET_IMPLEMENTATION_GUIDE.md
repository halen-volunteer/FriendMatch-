# FriendMatch WebSocket 实现指南

## 📋 目录
1. [依赖配置](#依赖配置)
2. [核心组件](#核心组件)
3. [导入说明](#导入说明)
4. [使用流程](#使用流程)
5. [消息格式](#消息格式)
6. [功能详解](#功能详解)

---

## 依赖配置

### Maven 依赖

在 `pom.xml` 中添加 WebSocket 支持：

```xml
<!-- WebSocket 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

**版本信息：**
- Spring Boot 版本：4.0.3
- Java 版本：21
- 该依赖已包含在 Spring Boot 父工程中，无需指定版本号

### 其他相关依赖

WebSocket 实现还依赖以下组件：

| 依赖 | 用途 |
|------|------|
| `spring-boot-starter-data-redis` | Redis 缓存（Token 验证、未读计数） |
| `fastjson2` | JSON 序列化/反序列化 |
| `mybatis-plus-spring-boot4-starter` | 数据库操作 |
| `lombok` | 日志注解 (@Slf4j) |

---

## 核心组件

### 1. WebSocketConfig（配置类）

**文件位置：** `src/main/java/com/zero/usercenter/websocket/WebSocketConfig.java`

**职责：**
- 注册 WebSocket 端点
- 配置握手拦截器
- Token 验证

**关键导入：**

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
```

**核心配置：**

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(new TokenHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
```

**端点信息：**
- **URL：** `ws://host:port/ws?token=xxx`
- **协议：** WebSocket
- **认证方式：** Query Parameter (token)
- **跨域：** 允许所有源

---

### 2. ChatWebSocketHandler（消息处理器）

**文件位置：** `src/main/java/com/zero/usercenter/websocket/ChatWebSocketHandler.java`

**职责：**
- 管理用户连接生命周期
- 处理消息推送
- 管理离线消息

**关键导入：**

```java
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
```

**核心类结构：**

```java
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    // 在线用户会话映射：userId -> WebSocketSession
    private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    
    // 依赖注入
    @Resource private ChatMessageMapper chatMessageMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private TeamMemberMapper teamMemberMapper;
}
```

---

## 导入说明

### 完整导入清单

#### WebSocket 框架导入

```java
// Spring WebSocket 核心
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// WebSocket 配置
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

// HTTP 请求处理
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
```

#### Spring 框架导入

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.annotation.Resource;
```

#### 业务逻辑导入

```java
// 数据库操作
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zero.usercenter.Mapper.ChatMessageMapper;
import com.zero.usercenter.Mapper.TeamMemberMapper;
import com.zero.usercenter.Model.ChatMessage;
import com.zero.usercenter.Model.TeamMember;

// JSON 处理
import com.alibaba.fastjson2.JSON;

// 日志
import lombok.extern.slf4j.Slf4j;
```

#### 工具类导入

```java
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
```

---

## 使用流程

### 1. 客户端连接流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 客户端发起 WebSocket 连接                                 │
│    ws://host:port/ws?token=xxx                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. TokenHandshakeInterceptor 拦截握手请求                   │
│    - 从 Query Parameter 获取 token                           │
│    - 从 Redis 验证 token 有效性                              │
│    - 提取 userId 存入 session attributes                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. ChatWebSocketHandler.afterConnectionEstablished()        │
│    - 将 userId -> session 映射存入 SESSIONS                 │
│    - 踢出旧连接（同一用户仅保留最后一个连接）               │
│    - 异步推送离线消息                                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. 连接建立完成，等待消息推送                                │
└─────────────────────────────────────────────────────────────┘
```

### 2. 消息推送流程

#### 私聊消息推送

```
ChatServiceImpl.sendPrivateMsg()
    ↓
chatMessageMapper.insert(msg)  // 保存到数据库
    ↓
stringRedisTemplate.increment()  // 增加未读计数
    ↓
chatWebSocketHandler.sendToUser(recipientId, json)
    ↓
SESSIONS.get(recipientId)  // 获取接收方的 session
    ↓
session.sendMessage(new TextMessage(json))  // 推送消息
```

#### 群聊消息推送

```
ChatServiceImpl.sendTeamMsg()
    ↓
chatMessageMapper.insert(msg)  // 保存到数据库
    ↓
stringRedisTemplate.increment()  // 增加未读计数
    ↓
chatWebSocketHandler.sendToTeam(teamId, json, senderId)
    ↓
teamMemberMapper.selectList()  // 查询团队所有成员（除发送者）
    ↓
for each member: sendMsg(memberId, json)  // 逐个推送
```

#### 消息撤回推送

```
ChatServiceImpl.revokeMsg()
    ↓
chatMessageMapper.update(isRevoke=1)  // 标记为已撤回
    ↓
chatWebSocketHandler.sendRevoke(msg)
    ↓
if (msg.recvType == 1)  // 私聊
    sendMsg(msg.recvId, json)
else  // 群聊
    sendToTeam(msg.recvId, json, msg.senderId)
```

---

## 消息格式

### 私聊消息

**推送格式：**

```json
{
  "type": "private_message",
  "data": {
    "msgId": 123,
    "senderId": 456,
    "msgType": 1,
    "content": "Hello",
    "createTime": "2024-03-21T10:30:00",
    "offline": false
  }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| type | String | 消息类型：`private_message` |
| msgId | Long | 消息 ID |
| senderId | Long | 发送者 ID |
| msgType | Integer | 消息类型（1-文本, 2-图片, 3-视频, 4-文件） |
| content | String | 消息内容 |
| createTime | DateTime | 创建时间 |
| offline | Boolean | 是否为离线消息 |

### 群聊消息

**推送格式：**

```json
{
  "type": "team_message",
  "data": {
    "msgId": 123,
    "teamId": 789,
    "senderId": 456,
    "msgType": 1,
    "content": "Team announcement",
    "createTime": "2024-03-21T10:30:00",
    "offline": false
  }
}
```

### 消息撤回通知

**推送格式：**

```json
{
  "type": "message_revoke",
  "data": {
    "msgId": 123,
    "conversationId": "456_789"
  }
}
```

---

## 功能详解

### 1. 连接管理

#### 建立连接

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
    Long userId = getUserId(session);
    if (userId == null) {
        closeQuietly(session);
        return;
    }
    // 踢出旧连接
    WebSocketSession old = SESSIONS.put(userId, session);
    if (old != null && old.isOpen()) closeQuietly(old);
    log.info("[WS] 用户 {} 已连接，当前在线 {} 人", userId, SESSIONS.size());
    // 推送离线期间未读消息
    Thread.ofVirtual().start(() -> pushOfflineMsgs(userId, session));
}
```

**特点：**
- 同一用户仅保留最后一个连接（踢出旧连接）
- 异步推送离线消息（不阻塞连接建立）
- 使用虚拟线程提高性能

#### 关闭连接

```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Long userId = getUserId(session);
    if (userId != null) {
        SESSIONS.remove(userId, session);
        log.info("[WS] 用户 {} 已断开，当前在线 {} 人", userId, SESSIONS.size());
    }
}
```

### 2. 消息推送

#### 私聊推送

```java
public void sendToUser(Long userId, String message) {
    sendMsg(userId, message);
}

private void sendMsg(Long userId, String message) {
    WebSocketSession session = SESSIONS.get(userId);
    if (session != null && session.isOpen()) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.warn("[WS] 推送消息给用户 {} 失败: {}", userId, e.getMessage());
            closeQuietly(session);
            SESSIONS.remove(userId, session);
        }
    }
}
```

#### 群聊推送

```java
public void sendToTeam(Long teamId, String message, Long senderId) {
    LambdaQueryWrapper<TeamMember> qw = new LambdaQueryWrapper<>();
    qw.eq(TeamMember::getTeamId, teamId)
      .eq(TeamMember::getIsQuit, 0)
      .ne(TeamMember::getUserId, senderId);
    List<Long> memberIds = teamMemberMapper.selectList(qw).stream()
            .map(TeamMember::getUserId).collect(Collectors.toList());
    for (Long memberId : memberIds) {
        sendMsg(memberId, message);
    }
}
```

### 3. 离线消息补推

用户上线时自动推送离线期间的未读消息：

- 查询最近 500 条消息
- 按会话分组
- 每个会话最多推送 20 条未读消息
- 标记为离线消息（offline=true）

### 4. 状态查询

```java
/** 获取当前在线用户数 */
public int getOnlineCount() {
    return SESSIONS.size();
}

/** 判断用户是否在线 */
public boolean isOnline(Long userId) {
    WebSocketSession session = SESSIONS.get(userId);
    return session != null && session.isOpen();
}
```

---

## 集成示例

### 在 ChatService 中使用

```java
@Service
public class ChatServiceImpl implements ChatService {
    
    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    
    @Override
    @Transactional
    public Result sendPrivateMsg(PrivateMsgDTO dto) {
        // ... 验证逻辑 ...
        
        // 保存消息
        chatMessageMapper.insert(msg);
        
        // 增加未读计数
        stringRedisTemplate.opsForValue().increment(UNREAD_COUNT_KEY + conversationId);
        
        // 推送消息
        chatWebSocketHandler.sendToUser(recipientId, buildPrivatePush(msg));
        
        return Result.ok(msg.getId());
    }
    
    @Override
    @Transactional
    public Result revokeMsg(MsgRevokeDTO dto) {
        // ... 验证逻辑 ...
        
        // 标记为已撤回
        chatMessageMapper.update(null, uw);
        
        // 推送撤回通知
        chatWebSocketHandler.sendRevoke(msg);
        
        return Result.ok("消息已撤回");
    }
}
```

---

## 性能优化

### 1. 虚拟线程

使用 Java 21 虚拟线程处理异步任务：

```java
Thread.ofVirtual().start(() -> pushOfflineMsgs(userId, session));
Thread.ofVirtual().start(() -> writeReceipt(msg.getId(), recipientId, 1));
```

**优势：**
- 轻量级线程，支持大量并发
- 自动管理线程池
- 减少上下文切换开销

### 2. 并发数据结构

使用 `ConcurrentHashMap` 存储会话：

```java
private static final Map<Long, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
```

### 3. Redis 缓存

使用 Redis 存储：
- Token 验证信息
- 未读消息计数
- 消息已读位图

---

## 常见问题

### Q1: 如何处理连接断开？

**A:** 框架自动处理：
- 客户端断开时，`afterConnectionClosed()` 自动调用
- 从 SESSIONS 中移除用户映射
- 消息保存到数据库，用户上线时补推

### Q2: 同一用户多个连接如何处理？

**A:** 仅保留最后一个连接：
```java
WebSocketSession old = SESSIONS.put(userId, session);
if (old != null && old.isOpen()) closeQuietly(old);
```

### Q3: 离线消息如何推送？

**A:** 用户上线时自动推送：
- 查询最近 500 条消息
- 按会话分组
- 每个会话推送最多 20 条未读消息
- 标记为离线消息

### Q4: 如何验证 Token？

**A:** 握手拦截器中验证：
```java
Object idObj = stringRedisTemplate.opsForHash().get(Number.TOKEN_KEY + token, "id");
if (idObj == null) {
    log.warn("[WS] 握手失败：token 无效或已过期");
    return false;
}
```

### Q5: 消息推送失败如何处理？

**A:** 自动关闭并移除 session：
```java
try {
    session.sendMessage(new TextMessage(message));
} catch (IOException e) {
    log.warn("[WS] 推送消息给用户 {} 失败: {}", userId, e.getMessage());
    closeQuietly(session);
    SESSIONS.remove(userId, session);
}
```

---

## 总结

FriendMatch 的 WebSocket 实现具有以下特点：

✅ **完整的连接生命周期管理**
- 握手验证、连接建立、异常处理、连接关闭

✅ **灵活的消息推送**
- 私聊、群聊、撤回通知

✅ **离线消息补推**
- 用户上线时自动推送未读消息

✅ **高性能设计**
- 虚拟线程、并发数据结构、Redis 缓存

✅ **完善的错误处理**
- 异常自动恢复、日志记录

✅ **安全的认证机制**
- Token 验证、会话隔离
