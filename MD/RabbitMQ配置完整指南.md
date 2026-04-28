# RabbitMQ 配置完整指南

## 目录
1. [RabbitMQ 本地安装](#rabbitmq-本地安装)
2. [项目依赖配置](#项目依赖配置)
3. [Spring Boot 配置](#spring-boot-配置)
4. [RabbitMQ 核心配置](#rabbitmq-核心配置)
5. [消息生产者](#消息生产者)
6. [消息消费者](#消息消费者)
7. [本地启动验证](#本地启动验证)
8. [常见问题排查](#常见问题排查)

---

## RabbitMQ 本地安装

### Windows 系统安装步骤

#### 1. 安装 Erlang 运行环境
RabbitMQ 基于 Erlang 开发，需要先安装 Erlang。

- 下载地址：https://www.erlang.org/downloads
- 选择最新稳定版本（推荐 OTP 26.x）
- 按照默认选项安装即可
- 安装完成后，验证：
  ```powershell
  erl -version
  ```

#### 2. 安装 RabbitMQ Server
- 下载地址：https://www.rabbitmq.com/download.html
- 选择 Windows 版本（.exe 安装程序）
- 按照默认选项安装即可
- 默认安装路径：`C:\Program Files\RabbitMQ Server\rabbitmq_server-x.x.x`

#### 3. 启用 RabbitMQ 管理插件
RabbitMQ 安装完成后，需要启用管理插件以便通过 Web 界面管理。

打开 PowerShell（以管理员身份运行）：
```powershell
# 进入 RabbitMQ 的 sbin 目录
cd "C:\Program Files\RabbitMQ Server\rabbitmq_server-x.x.x\sbin"

# 启用管理插件
.\rabbitmq-plugins.bat enable rabbitmq_management
```

#### 4. 启动 RabbitMQ 服务
```powershell
# 方式一：作为 Windows 服务启动（推荐）
net start RabbitMQ

# 方式二：在前台运行（用于调试）
rabbitmq-server.bat
```

#### 5. 验证安装
- 访问管理界面：http://localhost:15672
- 默认用户名：`guest`
- 默认密码：`guest`
- 如果能成功登录，说明 RabbitMQ 已正确安装

#### 6. 查看 RabbitMQ 状态
```powershell
# 查看 RabbitMQ 服务状态
rabbitmqctl.bat status

# 查看所有队列
rabbitmqctl.bat list_queues

# 查看所有交换机
rabbitmqctl.bat list_exchanges
```

---

## 项目依赖配置

### Maven 依赖（pom.xml）

项目已包含 RabbitMQ 依赖，确保 `pom.xml` 中有以下配置：

```xml
<!-- RabbitMQ 集成依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- JSON 序列化依赖（用于消息转换） -->
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.42</version>
</dependency>

<!-- Jackson JSON 处理 -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## Spring Boot 配置

### application.yaml 配置详解

项目已配置完整的 RabbitMQ 参数，位置：`src/main/resources/application.yaml`

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1                    # RabbitMQ 服务器地址
    port: 5672                         # RabbitMQ 服务器端口（AMQP 协议）
    username: guest                    # RabbitMQ 用户名
    password: guest                    # RabbitMQ 密码
    virtual-host: /                    # 虚拟主机（默认为 /）
    connection-timeout: 10000          # 连接超时时间（毫秒）
    
    # 发布者确认配置
    publisher-confirms: true           # 启用发布者确认（消息是否成功发送到交换机）
    publisher-returns: true            # 启用发布者返回（消息是否成功路由到队列）
    
    # 消费者配置
    listener:
      simple:
        acknowledge-mode: manual        # 手动确认消息（确保消息被正确处理）
        prefetch: 1                     # 每次预取 1 条消息（避免消费者过载）
        concurrency: 1                  # 初始并发消费者数
        max-concurrency: 10             # 最大并发消费者数
        
        # 重试配置
        retry:
          enabled: true                 # 启用重试机制
          max-attempts: 3               # 最大重试次数
          initial-interval: 1000        # 初始重试间隔（毫秒）
          multiplier: 1.0               # 重试间隔倍数
          max-interval: 10000           # 最大重试间隔（毫秒）
```

### 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `host` | RabbitMQ 服务器地址 | localhost |
| `port` | AMQP 协议端口 | 5672 |
| `username` | 连接用户名 | guest |
| `password` | 连接密码 | guest |
| `virtual-host` | 虚拟主机隔离 | / |
| `publisher-confirms` | 发布者确认 | false |
| `publisher-returns` | 发布者返回 | false |
| `acknowledge-mode` | 消息确认模式 | AUTO |
| `prefetch` | 预取消息数 | 250 |

---

## RabbitMQ 核心配置

### 配置类：RabbitMQConfig.java

位置：`src/main/java/com/zero/usercenter/config/RabbitMQConfig.java`

#### 1. 交换机定义

```java
// Topic 交换机（支持通配符路由）
@Bean
public TopicExchange friendRequestExchange() {
    return new TopicExchange(FRIEND_REQUEST_EXCHANGE, true, false);
}

// Fanout 交换机（广播模式）
@Bean
public FanoutExchange systemNotificationExchange() {
    return new FanoutExchange(SYSTEM_NOTIFICATION_EXCHANGE, true, false);
}
```

**交换机类型说明：**
- **Topic Exchange**：支持通配符路由，灵活性最高
  - `friend.request` - 精确匹配
  - `friend.*` - 匹配 friend 下的所有消息
  - `#` - 匹配所有消息
  
- **Fanout Exchange**：广播模式，所有绑定的队列都会收到消息

#### 2. 队列定义

```java
@Bean
public Queue friendRequestQueue() {
    return new Queue(FRIEND_REQUEST_QUEUE, true, false, false);
    // 参数说明：
    // - name: 队列名称
    // - durable: 是否持久化（true = 服务器重启后队列仍存在）
    // - exclusive: 是否排他（true = 仅创建者可访问）
    // - autoDelete: 是否自动删除（true = 消费者断开连接后自动删除）
}
```

#### 3. 绑定关系

```java
@Bean
public Binding friendRequestBinding() {
    return BindingBuilder.bind(friendRequestQueue())
            .to(friendRequestExchange())
            .with(FRIEND_REQUEST_ROUTING_KEY);
}
```

**绑定流程：**
```
生产者 → 交换机 → 路由键匹配 → 队列 → 消费者
```

#### 4. 消息转换器

```java
@Bean
public MessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

支持自动将 Java 对象转换为 JSON，反之亦然。

#### 5. RabbitTemplate 配置

```java
@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter());
    
    // 发布者确认回调
    rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
        if (ack) {
            System.out.println("消息发送成功");
        } else {
            System.out.println("消息发送失败: " + cause);
        }
    });
    
    // 发布者返回回调
    rabbitTemplate.setReturnsCallback(returned -> {
        System.out.println("消息被返回: " + returned.getMessage());
    });
    
    return rabbitTemplate;
}
```

---

## 消息生产者

### RabbitMQProducer.java

位置：`src/main/java/com/zero/usercenter/utils/RabbitMQProducer.java`

#### 核心方法

```java
@Component
@Slf4j
public class RabbitMQProducer {
    
    @Resource
    private RabbitTemplate rabbitTemplate;
    
    /**
     * 发送好友申请消息
     */
    public void sendFriendRequestMessage(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.FRIEND_REQUEST_EXCHANGE,
                    RabbitMQConfig.FRIEND_REQUEST_ROUTING_KEY,
                    message);
            log.info("好友申请消息发送成功: {}", JSON.toJSONString(message));
        } catch (Exception e) {
            log.error("好友申请消息发送失败", e);
        }
    }
}
```

#### 使用示例

```java
// 在 Controller 或 Service 中注入
@Resource
private RabbitMQProducer rabbitMQProducer;

// 发送消息
public void requestFriend(Long userId, Long friendId) {
    Map<String, Object> message = new HashMap<>();
    message.put("userId", userId);
    message.put("friendId", friendId);
    message.put("timestamp", System.currentTimeMillis());
    
    rabbitMQProducer.sendFriendRequestMessage(message);
}
```

#### 消息发送流程

```
1. 调用 sendFriendRequestMessage()
   ↓
2. RabbitTemplate.convertAndSend()
   ↓
3. 消息转换为 JSON
   ↓
4. 发送到交换机 (friend.request.exchange)
   ↓
5. 交换机根据路由键 (friend.request) 路由到队列
   ↓
6. 消息进入队列 (friend.request.queue)
   ↓
7. 发布者确认回调返回结果
```

---

## 消息消费者

### RabbitMQConsumer.java

位置：`src/main/java/com/zero/usercenter/utils/RabbitMQConsumer.java`

#### 核心方法

```java
@Component
@Slf4j
public class RabbitMQConsumer {
    
    /**
     * 消费好友申请消息
     */
    @RabbitListener(queues = RabbitMQConfig.FRIEND_REQUEST_QUEUE)
    public void consumeFriendRequestMessage(String message) {
        try {
            log.info("收到好友申请消息: {}", message);
            // 处理消息逻辑
        } catch (Exception e) {
            log.error("处理好友申请消息失败", e);
        }
    }
}
```

#### 消息消费流程

```
1. 消费者启动时，@RabbitListener 注解扫描队列
   ↓
2. 消费者连接到队列 (friend.request.queue)
   ↓
3. 队列中有消息时，消费者自动拉取
   ↓
4. 执行消费方法 consumeFriendRequestMessage()
   ↓
5. 消息处理成功 → 手动确认 (acknowledge)
   ↓
6. 消息从队列中删除
```

#### 消息确认机制

配置 `acknowledge-mode: manual` 后，需要手动确认消息：

```java
@RabbitListener(queues = RabbitMQConfig.FRIEND_REQUEST_QUEUE)
public void consumeFriendRequestMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        log.info("收到好友申请消息: {}", message);
        // 处理消息
        
        // 手动确认消息
        channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
        log.error("处理好友申请消息失败", e);
        try {
            // 消息处理失败，拒绝并重新入队
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException ioException) {
            log.error("消息拒绝失败", ioException);
        }
    }
}
```

---

## 本地启动验证

### 1. 启动 RabbitMQ 服务

```powershell
# 确保 RabbitMQ 服务已启动
net start RabbitMQ

# 或者在前台运行
rabbitmq-server.bat
```

### 2. 启动 Spring Boot 应用

```bash
# 方式一：使用 Maven
mvn spring-boot:run

# 方式二：使用 IDE 直接运行 UserCenterApplication.java
```

### 3. 验证连接

查看应用日志，应该看到类似输出：
```
2024-03-16 10:30:45.123  INFO 12345 --- [main] o.s.a.r.c.CachingConnectionFactory      : Attempting to connect to: [127.0.0.1:5672]
2024-03-16 10:30:45.456  INFO 12345 --- [main] o.s.a.r.c.CachingConnectionFactory      : Created new connection: rabbitConnectionFactory#...
```

### 4. 访问 RabbitMQ 管理界面

打开浏览器访问：http://localhost:15672

- 用户名：`guest`
- 密码：`guest`

在管理界面可以看到：
- **Exchanges**：所有已定义的交换机
- **Queues**：所有已定义的队列
- **Connections**：当前连接
- **Channels**：通道信息

### 5. 测试消息发送

#### 方式一：通过 API 测试

```bash
# 发送好友申请消息
curl -X POST http://localhost:8081/api/friend/request \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "friendId": 2}'
```

#### 方式二：通过 RabbitMQ 管理界面

1. 进入 Exchanges 页面
2. 选择 `friend.request.exchange`
3. 点击 "Publish message"
4. 输入路由键：`friend.request`
5. 输入消息内容：`{"userId": 1, "friendId": 2}`
6. 点击 "Publish message"

### 6. 查看消息处理

查看应用日志，应该看到消费者处理消息的日志：
```
2024-03-16 10:35:20.789  INFO 12345 --- [main] c.z.u.utils.RabbitMQConsumer            : 收到好友申请消息: {"userId":1,"friendId":2}
```

---

## 常见问题排查

### 问题 1：无法连接到 RabbitMQ

**症状：**
```
Connection refused: connect
```

**解决方案：**
1. 确认 RabbitMQ 服务已启动
   ```powershell
   net start RabbitMQ
   ```

2. 检查 RabbitMQ 是否监听在 5672 端口
   ```powershell
   netstat -ano | findstr :5672
   ```

3. 检查防火墙设置，确保 5672 端口未被阻止

4. 验证 application.yaml 中的连接配置是否正确

### 问题 2：消息发送成功但消费者未收到

**症状：**
- 生产者日志显示消息发送成功
- 消费者日志无任何输出

**解决方案：**
1. 检查队列是否正确绑定到交换机
   ```powershell
   rabbitmqctl.bat list_bindings
   ```

2. 检查路由键是否匹配
   - Topic 交换机需要精确匹配路由键
   - Fanout 交换机忽略路由键

3. 检查消费者是否正确启动
   - 查看应用日志中是否有 `@RabbitListener` 初始化日志

4. 检查 `acknowledge-mode` 配置
   - 如果设置为 `manual`，需要手动确认消息

### 问题 3：消息处理失败后无法重试

**症状：**
- 消息处理异常
- 消息未被重新入队

**解决方案：**
1. 确认 `retry.enabled: true` 已启用

2. 检查异常处理逻辑
   ```java
   // 错误：异常被吞掉
   try {
       // 处理消息
   } catch (Exception e) {
       // 没有重新抛出异常
   }
   
   // 正确：异常被抛出
   try {
       // 处理消息
   } catch (Exception e) {
       log.error("处理失败", e);
       throw e;  // 重新抛出异常，触发重试
   }
   ```

3. 检查重试配置参数
   ```yaml
   retry:
     enabled: true
     max-attempts: 3
     initial-interval: 1000
   ```

### 问题 4：RabbitMQ 管理界面无法访问

**症状：**
```
无法连接到 http://localhost:15672
```

**解决方案：**
1. 确认管理插件已启用
   ```powershell
   rabbitmq-plugins.bat list
   # 应该看到 rabbitmq_management 已启用
   ```

2. 如果未启用，执行启用命令
   ```powershell
   rabbitmq-plugins.bat enable rabbitmq_management
   ```

3. 重启 RabbitMQ 服务
   ```powershell
   net stop RabbitMQ
   net start RabbitMQ
   ```

### 问题 5：消息堆积在队列中

**症状：**
- RabbitMQ 管理界面显示队列中有大量未消费消息
- 消费者处理缓慢

**解决方案：**
1. 增加消费者并发数
   ```yaml
   listener:
     simple:
       concurrency: 5          # 增加初始并发数
       max-concurrency: 20     # 增加最大并发数
   ```

2. 优化消息处理逻辑，减少处理时间

3. 检查是否有消费者异常导致消息未被确认

4. 清空队列（仅用于测试环境）
   ```powershell
   rabbitmqctl.bat purge_queue friend.request.queue
   ```

---

## 监控和维护

### 常用 RabbitMQ 命令

```powershell
# 查看 RabbitMQ 状态
rabbitmqctl.bat status

# 查看所有队列
rabbitmqctl.bat list_queues

# 查看所有交换机
rabbitmqctl.bat list_exchanges

# 查看所有绑定
rabbitmqctl.bat list_bindings

# 查看所有连接
rabbitmqctl.bat list_connections

# 查看所有通道
rabbitmqctl.bat list_channels

# 重置 RabbitMQ（清空所有数据）
rabbitmqctl.bat reset

# 停止 RabbitMQ 应用
rabbitmqctl.bat stop_app

# 启动 RabbitMQ 应用
rabbitmqctl.bat start_app
```

### 性能优化建议

1. **调整预取数量**
   ```yaml
   prefetch: 1  # 保守设置，确保消息被正确处理
   ```

2. **启用消息持久化**
   ```java
   new Queue(QUEUE_NAME, true, false, false);  // 第二个参数为 true
   ```

3. **使用连接池**
   ```yaml
   rabbitmq:
     connection-timeout: 10000
   ```

4. **监控队列深度**
   - 定期检查队列中的消息数量
   - 如果堆积过多，增加消费者数量

---

## 总结

| 步骤 | 操作 | 验证方式 |
|------|------|---------|
| 1 | 安装 Erlang 和 RabbitMQ | `erl -version` 和 `rabbitmqctl.bat status` |
| 2 | 启用管理插件 | 访问 http://localhost:15672 |
| 3 | 配置 Spring Boot | 检查 application.yaml |
| 4 | 定义交换机、队列、绑定 | RabbitMQConfig.java |
| 5 | 实现生产者 | RabbitMQProducer.java |
| 6 | 实现消费者 | RabbitMQConsumer.java |
| 7 | 启动应用并测试 | 查看日志和管理界面 |

RabbitMQ 配置完成后，项目可以支持异步消息处理、解耦业务逻辑、提高系统吞吐量。
