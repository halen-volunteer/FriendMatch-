# FriendMatch 实现文档 - Part 1：用户认证模块

> 版本：V1.0 | 日期：2026-03-24

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `LoginController.java` |
| Service | `UserService.java` / `UserServiceImpl.java` |
| Mapper | `UserMapper.java` / `UserLoginLogMapper.java` |
| Model | `User.java` / `UserLoginLog.java` |
| DTO | `LoginDTO.java` / `UserFormat.java` |
| 工具 | `EmailApi.java` / `SensitiveWordConfig.java` / `Number.java` |

---

## 一点五、数据库设计

### t_user 表（核心字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint auto_increment | 用户主键 |
| `user_account` | varchar | 公开唯一账号（10位纯数字，雪花生成）|
| `user_email` | varchar | 私密邮箱（注册/登录/找回密码）|
| `user_nickname` | varchar | 昵称（可重复，3-16位）|
| `user_avatar` | varchar | 头像 URL |
| `user_intro` | varchar | 个人简介（≤512字符）|
| `user_password` | varchar | BCrypt 加密密码 |
| `user_tags` | varchar | 用户标签（逗号分隔）|
| `privacy_setting` | json | 隐私设置 |
| `global_punish_type` | tinyint | 0-无，1-禁言，2-封号 |
| `global_unpunish_time` | datetime | 处罚解除时间 |
| `last_login_time` | datetime | 最后登录时间 |
| `last_login_ip` | varchar | 最后登录 IP |
| `is_delete` | tinyint | 软删除标记 |

**关键索引**：`uk_account`（user_account）、`uk_user_email`（user_email）、`idx_global_punish`（global_punish_type, global_unpunish_time）

### t_user_login_log 表

| 字段 | 说明 |
|---|---|
| `user_id` | 关联用户 ID |
| `login_ip` | 登录 IP |
| `login_type` | 1-账号密码，2-验证码 |
| `login_result` | 0-失败，1-成功 |
| `create_time` | 登录时间 |

> 轻量化设计，记录所有登录尝试（成功/失败），可用于检测暴力破解、异地登录等异常。

---

## 二、接口实现详情

### GET /api/auth/captcha — 获取图形验证码

> 文件：`LoginController.java`

**请求参数**：无

**响应**：响应头 `captchaId`（String），Body 为 `image/jpeg` 二进制图片流

**流程图**：
```
GET /api/auth/captcha
  → CaptchaUtil 生成图形验证码（Hutool）
  → 生成唯一 captchaId（UUID）
  → 写入 Redis captcha:{captchaId}，TTL 1 分钟
  → 响应头写入 captchaId
  → Body 返回 image/jpeg 二进制流
```

- Controller：`LoginController.getCaptcha()`
- 使用 Hutool `CaptchaUtil` 生成图形验证码
- 写入 Redis `captcha:{captchaId}`，TTL 1 分钟
- 响应头返回 `captchaId`，Body 为 `image/jpeg` 二进制流

**响应示例**：
```
HTTP/1.1 200 OK
Content-Type: image/jpeg
captchaId: 3f2a1b4c-7d8e-4f9a-b2c3-1d4e5f6a7b8c

<image/jpeg 二进制流>
```

### POST /api/auth/code — 发送邮箱验证码

> 文件：`LoginController.java` → `UserServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | String | 是 | 目标邮箱，≤128位，格式校验 |

**响应**：`Result.ok()`（成功）/ `Result.fail(msg)`（冷却中/格式错误）

**流程图**：
```
POST /api/auth/code
  → 校验邮箱格式（EMAIL_REGEX，≤128位）
  → 检查 Redis send_limit:{email}
      存在（60s内）→ 返回失败"发送太频繁"
      不存在 ↓
  → ThreadLocalRandom 生成 6 位数字验证码
  → EmailApi.sendHtmlEmail() 发送 HTML 邮件
  → 写入 Redis verify_code:{email}，TTL 5 分钟
  → 写入 Redis send_limit:{email}，TTL 60 秒
  → 返回 Result.ok()
```

- Service：`UserServiceImpl.sendCode()`
- 校验邮箱格式（`EMAIL_REGEX`，最大 128 位）
- 检查 `send_limit:{email}` 冷却（60 秒内禁止重发）
- `ThreadLocalRandom` 生成 6 位数字验证码
- `EmailApi.sendHtmlEmail()` 发送 HTML 邮件
- 写入 `verify_code:{email}`（TTL 5 分钟，新码覆盖旧码）
- 写入 `send_limit:{email}`（TTL 60 秒）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "验证码发送成功",
  "message": null
}
```

**响应示例（发送过于频繁）**：
```json
{
  "success": false,
  "data": null,
  "message": "发送过于频繁，请 60 秒后重试"
}
```

### POST /api/auth/register — 用户注册

> 文件：`LoginController.java` → `UserServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userNickname` | String | 是 | 昵称，3-16位，中文/字母/数字/下划线，不以下划线开头 |
| `userEmail` | String | 是 | 注册邮箱 |
| `userPassword` | String | 是 | 密码，≥8位，含大小写字母 |
| `emailCode` | String | 是 | 邮箱验证码（6位数字）|

**响应**：`Result.ok(UserFormat)`（脱敏用户信息）

**流程图**：
```
POST /api/auth/register
  → 参数判空（userNickname/userEmail/userPassword/emailCode）
  → 昵称格式校验（3-16位，中文/字母/数字/下划线，不以下划线开头）
  → 系统保留名检查（RESERVED_NAMES）
  → 敏感词检测（SensitiveWordBs.contains）
  → 密码强度校验（≥8位，含大小写字母）
  → 邮箱格式校验（EMAIL_REGEX，≤128位）
  → Redis 验证码比对（verify_code:{email}）
      不匹配 → 返回失败
      匹配 ↓
  → 加分布式锁 lock:user:email:{email}（SET NX EX 10）
      加锁失败 → 返回"操作太频繁"
      加锁成功 ↓
  → 二次检查邮箱唯一性（t_user）
      已存在 → 释放锁 → 返回失败
      不存在 ↓
  → 雪花算法生成 10 位纯数字 user_account
  → BCrypt 加密密码
  → 插入 t_user
  → Lua 脚本原子释放锁
  → 删除 Redis 验证码
  → 返回脱敏 UserFormat
```

- Service：`UserServiceImpl.userRegister()`
- 用户名校验：3-16 位，仅中文/字母/数字/下划线，不以下划线开头
- 系统保留名检查（`RESERVED_NAMES` 常量集合）
- 敏感词检测：`SensitiveWordBs.contains()`
- 密码强度：至少 8 位，同时包含大小写字母
- 邮箱格式校验 + Redis 验证码比对（`verify_code:{email}`）
- 分布式锁防并发：`lock:user:email:{email}`，`SET NX EX 10`
- Lua 脚本原子释放锁（get + del 原子操作）
- 加锁后二次检查邮箱唯一性
- 雪花算法生成 10 位纯数字 `user_account`：`IdUtil.getSnowflake(workerId, 1)`，取 ID 末 10 位，冲突自动重试一次
- 重写 `save()`：入库前 BCrypt 加密密码，跳过已含 `$2a$/$2b$/$2y$` 前缀的密文
- 注册成功后删除 Redis 验证码，返回脱敏 `UserFormat`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "userAccount": "1234567890",
    "userNickname": "张三",
    "userAvatar": "",
    "userEmail": "zhangsan@example.com",
    "userTags": "",
    "userIntro": ""
  },
  "message": null
}
```

**响应示例（邮箱已注册）**：
```json
{
  "success": false,
  "data": null,
  "message": "当前邮箱已注册"
}
```

### POST /api/auth/login — 用户登录

> 文件：`LoginController.java` → `UserServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `loginId` | String | 是 | 账号（`user_account`）或邮箱（`user_email`）|
| `userPassword` | String | 是 | 登录密码 |
| `captchaId` | String | 是 | 图形验证码 ID（来自响应头）|
| `captchaCode` | String | 是 | 图形验证码内容 |

**响应**：`Result.ok(token)`（String，写入请求头 `authorization` 后续使用）

**流程图**：
```
POST /api/auth/login
  → 参数判空（loginId/userPassword/captchaId/captchaCode）
  → 账号/邮箱格式校验
  → 图形验证码校验（Redis captcha:{captchaId}）
      不匹配 → 返回失败（不写登录日志）
      匹配 → 立即删除验证码（防重放）
  → 查 t_user（user_account 或 user_email）
      不存在 → 异步写失败日志 → 返回失败
      存在 ↓
  → 封号检测（global_punish_type=2）
      已封号 → 返回失败
      未封号 ↓
  → BCrypt.matches() 密码比对
      不匹配 → 异步写失败日志 → 返回失败
      匹配 ↓
  → 异步虚拟线程：更新 last_login_time/last_login_ip + 写成功登录日志
  → UUID 生成 Token
  → 写入 Redis Hash token:{token}（TTL 120min ± 10min）
  → 返回 token
```

- Service：`UserServiceImpl.userLogin()`
- 支持 `user_account` 或 `user_email` 两种方式登录
- 图形验证码校验（`captcha:{captchaId}`），校验后立即删除防重放攻击
- 永久封号检测：`global_punish_type == 2` 拒绝登录；`global_punish_type == 1`（禁言）允许登录
- `BCryptPasswordEncoder.matches()` 密码比对
- 异步（虚拟线程 `Thread.ofVirtual()`）更新 `last_login_time` / `last_login_ip`
- 异步写入 `t_user_login_log`（login_type=1，成功=1/失败=0）
  - 账号不存在 → 写日志（login_result=0）
  - 密码错误 → 写日志（login_result=0）
  - **验证码错误 → 不写日志**（直接返回错误，不记录）
- UUID 生成 Token，写入 Redis Hash `token:{token}`：
  - 字段：id / userAccount / userNickname / userAvatar / userEmail / userTags / userIntro
  - TTL：2 小时（拦截器滑动续期）
- IP 获取优先级：`X-Forwarded-For` > `X-Real-IP` > `getRemoteAddr()`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "eyJhbGciOiJub25lIn0.abc123xyz",
  "message": null
}
```

**响应示例（验证码错误）**：
```json
{
  "success": false,
  "data": null,
  "message": "验证码错误"
}
```

**响应示例（账号已封禁）**：
```json
{
  "success": false,
  "data": null,
  "message": "账号已被永久封禁，如有疑问请联系管理员"
}
```

### POST /api/auth/forget — 忘记密码

> 文件：`LoginController.java` → `UserServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userEmail` | String | 是 | 注册邮箱 |
| `newPassword` | String | 是 | 新密码，≥8位，含大小写字母 |
| `emailCode` | String | 是 | 邮箱验证码（6位数字）|

**响应**：`Result.ok()`

**流程图**：
```
POST /api/auth/forget
  → 邮箱格式校验 + 密码强度校验
  → Redis 验证码比对（verify_code:{email}）
      不匹配 → 返回失败
      匹配 ↓
  → 检查 forget_pwd_limit:{email}（1分钟内不可重复修改）
      存在 → 返回失败
      不存在 ↓
  → LambdaUpdateWrapper 按邮箱更新 BCrypt 密码
  → 删除 Redis 验证码
  → 写入 forget_pwd_limit:{email}，TTL 1 分钟
  → 返回 Result.ok()
```

- Service：`UserServiceImpl.forgetPassword()`
- 邮箱格式校验 + 密码强度校验
- Redis 验证码比对
- `forget_pwd_limit:{email}` 频率限制（1 分钟内不可重复修改）
- `LambdaUpdateWrapper` 按邮箱更新 BCrypt 密码
- 成功后删除验证码，写入频率限制 Key

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "密码修改成功",
  "message": null
}
```

**响应示例（验证码已过期）**：
```json
{
  "success": false,
  "data": null,
  "message": "验证码已过期"
}
```

### POST /api/auth/me — 获取当前用户

> 文件：`LoginController.java`

**请求参数**：无（需请求头 `authorization: {token}`）

**响应**：`Result.ok(UserFormat)`（当前登录用户信息）

**流程图**：
```
POST /api/auth/me
  → RegisterInterceptor 已将 UserFormat 写入 UserHolder（ThreadLocal）
  → UserHolder.getUser() 直接读取
  → 返回 UserFormat（无额外 DB 查询）
```

- Controller：`LoginController.getMe()`
- 从 `UserHolder.getUser()`（ThreadLocal）读取拦截器写入的 `UserFormat`
- 直接返回，无额外数据库查询

**响应示例**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "userAccount": "1234567890",
    "userNickname": "张三",
    "userAvatar": "https://example.com/avatar/1001.jpg",
    "userEmail": "zhangsan@example.com",
    "userTags": "Java,游戏,音乐",
    "userIntro": "热爱编程的开发者"
  },
  "message": null
}
```

---

## 三、安全机制实现

### BCrypt 密码加密

- `UserServiceImpl` 重写 `save()` 和 `updateById()`
- 入库前检测是否已加密（`$2a$` 前缀），避免二次加密
- `BCryptPasswordEncoder` 单例复用（`static final`）
- 注意：当前代码使用无参构造，工作因子为默认值 **10**（设计文档原定12，实现时调整为10以提升注册响应速度，加密时间约 ~60ms）

### 分布式锁

```java
// 加锁
stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
// Lua 原子释放
"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end"
```

### Token 鉴权（拦截器）

- `RegisterInterceptor` 拦截所有需要鉴权的接口
- 从请求头 `authorization` 读取 Token
- `stringRedisTemplate.opsForHash().entries("token:{token}")` 反序列化为 `UserFormat`
- 写入 `UserHolder`（ThreadLocal），每次请求刷新 TTL（滑动续期）

---

## 四、Redis Key 汇总

| Key 模板 | 类型 | TTL | 用途 |
|---|---|---|---|
| `captcha:{captchaId}` | String | 1 分钟 | 图形验证码 |
| `verify_code:{email}` | String | 5 分钟 | 邮箱验证码 |
| `send_limit:{email}` | String | 60 秒 | 发送冷却 |
| `forget_pwd_limit:{email}` | String | 1 分钟 | 找回密码频率限制 |
| `token:{token}` | Hash | 2 小时（滑动） | 登录 Token + 用户信息 |
| `user_punish:{userId}` | String | 5 分钟 | 用户处罚状态缓存 |
| `lock:user:email:{email}` | String | 10 秒 | 注册分布式锁 |

---

## 五、异步操作说明

| 操作 | 实现方式 | 触发时机 |
|---|---|---|
| 更新登录时间/IP | `Thread.ofVirtual().start()` | 登录成功后 |
| 写入登录日志 | `Thread.ofVirtual().start()` | 登录成功/失败均写 |
| 发送验证码邮件 | 同步调用（EmailApi 内部异步） | 发送验证码时 |

---

## 六、MVC 配置（MvcConfig.java）

### 跨域配置（CORS）

```java
// 路径：/api/**
// 允许所有来源（allowedOriginPatterns = "*"）
// 允许方法：GET / POST / PUT / DELETE / OPTIONS
// 暴露响应头：captchaId（前端读取图形验证码标识）
// allowCredentials = true（携带 Cookie/Authorization）
// maxAge = 3600 秒（预检缓存时间）
```

### 拦截器配置（RegisterInterceptor）

- 拦截路径：`/api/**`（所有接口）
- **白名单（免登录）**：
  - `GET /api/auth/captcha`
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `POST /api/auth/code`
  - `POST /api/auth/forget`
- **拦截逻辑**：
  1. 从请求头 `authorization` 读取 Token
  2. 查询 Redis Hash `token:{token}` 反序列化为 `UserFormat`
  3. 存入 `UserHolder`（ThreadLocal）
  4. 刷新 Token TTL（滑动续期 2 小时）
  5. Token 不存在/过期 → 返回 401

### 敏感词配置（SensitiveWordConfig.java）

- 使用 `houbb/sensitive-word` 库，注册 `SensitiveWordBs` Spring Bean
- 应用场景：用户名注册、昵称修改、简介修改、消息发送、团队名称/简介
- 调用方式：`sensitiveWordBs.contains(text)` → 返回 true 表示含敏感词

### MyMetaObjectHandler（自动填充）

- MyBatis-Plus 自动填充插件：`create_time`、`update_time` 字段
- `insertFill`：`create_time = now()`、`update_time = now()`
- `updateFill`：`update_time = now()`

---

## 七、RabbitMQ 配置（RabbitMQConfig.java）

> **说明**：RabbitMQ 已配置好交换机/队列/绑定，并提供 `RabbitMQProducer` 与 `RabbitMQConsumer` 示例封装；当前核心业务主链仍主要使用 WebSocket 直推 + 虚拟线程异步写库，MQ 更偏向后续可靠异步通知扩展与解耦预留。

### 已定义的交换机

| 交换机名 | 类型 | 用途 |
|---|---|---|
| `friend.request.exchange` | Topic | 好友申请/同意/拒绝 |
| `friend.operation.exchange` | Topic | 好友删除/拉黑 |
| `system.notification.exchange` | Fanout | 系统通知广播 |

### 已定义的队列与路由键

| 队列名 | 路由键 | 用途 |
|---|---|---|
| `friend.request.queue` | `friend.request` | 好友申请 |
| `friend.agree.queue` | `friend.agree` | 好友同意 |
| `friend.reject.queue` | `friend.reject` | 好友拒绝 |
| `friend.delete.queue` | `friend.delete` | 好友删除 |
| `blacklist.queue` | `blacklist` | 拉黑用户 |
| `system.notification.queue` | — | 系统通知（Fanout）|

### application.yaml 关键配置

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirms: true   # 启用发布者确认
    publisher-returns: true    # 启用发布者返回
    listener:
      simple:
        acknowledge-mode: manual  # 手动 ACK
        prefetch: 1
        retry:
          enabled: true
          max-attempts: 3
```
