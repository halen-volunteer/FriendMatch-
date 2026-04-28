# FriendMatch 详细设计文档 - Part 1：用户认证模块

> 版本：V1.0 | 日期：2026-03-16

---

## 一、模块概述

用户认证模块负责注册、登录、密码管理、安全审计。采用 BCrypt 加密、Redis Token 鉴权、分布式锁防并发。

### 核心特性
- ✅ BCrypt 密码加密（工作因子12）
- ✅ 分布式锁防并发重复注册
- ✅ 邮箱验证码 + 图形验证码双重验证
- ✅ 敏感词检测（用户名）
- ✅ 登录日志记录（IP、类型、结果）
- ✅ Redis Token 鉴权（2小时有效期）
- ✅ 异步更新登录信息（不阻塞主流程）

---

## 二、数据库设计

### 2.1 t_user 表（用户基础信息表）

**核心字段**：
- `id`：用户主键（bigint auto_increment）
- `user_account`：公开唯一账号（6-20位，字母/数字/下划线）
- `user_email`：私密邮箱（用于注册、登录、找回密码）
- `user_nickname`：用户昵称（可重复，用于展示）
- `user_avatar`：头像URL
- `user_intro`：个人简介
- `user_password`：BCrypt加密密码
- `user_tags`：用户标签（逗号分隔）
- `privacy_setting`：隐私设置（JSON）
- `global_punish_type`：全局处罚类型（0-无，1-禁言，2-封号）
- `global_unpunish_time`：处罚解除时间
- `last_login_time`：最后登录时间
- `last_login_ip`：最后登录IP
- `is_delete`：软删除标记
- `create_time`、`update_time`：时间戳

**关键索引**：
- `uk_account`：user_account 唯一索引
- `uk_user_email`：user_email 唯一索引
- `idx_global_punish`：(global_punish_type, global_unpunish_time)

### 2.2 t_user_login_log 表（登录日志表）

**字段**：
- `id`：日志主键
- `user_id`：关联用户ID
- `login_ip`：登录IP
- `login_type`：登录类型（1-账号密码，2-验证码）
- `login_result`：登录结果（0-失败，1-成功）
- `create_time`：登录时间

**设计说明**：
- 轻量化设计，仅保留核心审计字段
- 记录所有登录尝试（成功和失败）
- 支持按用户ID查询登录历史
- 可用于检测暴力破解、异地登录等异常行为

---

## 三、Redis 设计

### 3.1 验证码存储

**图形验证码**：
```
Key: captcha:{captchaId}
Value: 验证码文本（如：1234）
TTL: 1分钟
```

**邮箱验证码**：
```
Key: verify_code:{email}
Value: 6位数字验证码
TTL: 5分钟
```

**验证码发送冷却**（解耦重发频率与有效期）：
```
Key: send_limit:{email}
Value: 1（占位符）
TTL: 60秒
```

**忘记密码频率限制**：
```
Key: forget_pwd_limit:{email}
Value: 1（占位符）
TTL: 1分钟
```

### 3.2 Token 存储

**登录 Token**：
```
Key: token:{token}
Type: Hash
Fields:
  - id: 用户ID
  - userAccount: 账号
  - userNickname: 昵称
  - userAvatar: 头像URL
  - userEmail: 邮箱
  - userTags: 标签
  - userIntro: 简介
TTL: 2小时（滑动续期）
```

**设计说明**：
- Hash 结构存储用户基础信息，减少数据库查询
- 每次请求自动刷新 TTL（滑动续期）
- 拦截器从 Redis 反序列化为 UserFormat，存入 ThreadLocal
- 业务层通过 `UserHolder.getUser()` 获取当前用户

### 3.3 处罚状态缓存

**用户全局处罚状态**：
```
Key: user_punish:{userId}
Value: JSON字符串
  {
    "type": 1,                    // 1-禁言，2-封号
    "unpunishTime": "2026-03-17"  // 解除时间
  }
TTL: 5分钟
```

---

## 四、业务流程设计

### 4.1 用户注册流程

**步骤**：
1. 邮箱格式校验
2. 检查 send_limit:{email} 冷却（60秒内不能重复发送）
3. 生成6位数字验证码
4. 发送HTML邮件
5. 写入 verify_code:{email}（TTL: 5分钟）
6. 写入 send_limit:{email}（TTL: 60秒）

**注册提交**：
1. 参数校验（用户名/邮箱/密码/验证码）
2. 用户名校验：
   - 长度3-16位
   - 仅支持中文/字母/数字/下划线
   - 不能以下划线开头
   - 不能是系统保留名（admin/root等）
   - 敏感词检测
3. 邮箱校验：格式校验 + 长度限制
4. 密码校验：至少8位 + 大小写字母
5. 验证码校验：verify_code:{email} 匹配
6. 分布式锁：lock:user:email:{email}（10秒）
7. 邮箱唯一性检查（加锁后再次确认）
8. 雪花ID生成 user_account（10位纯数字）
9. BCrypt加密密码
10. 写入 t_user
11. 删除 verify_code:{email}
12. 返回脱敏用户信息

**关键设计点**：
- 分布式锁防止并发重复注册同一邮箱
- 双重验证：邮箱验证码 + 用户名敏感词检测
- 自动生成账号，避免重名冲突
- 异步操作，不等待邮件发送完成

### 4.2 用户登录流程

**步骤**：
1. 参数校验（账号/密码/图形验证码）
2. 图形验证码校验：captcha:{captchaId} 匹配
3. 删除验证码（防重放攻击）
4. 账号/邮箱格式校验
5. 密码格式校验
6. 查询用户：by user_account OR user_email
7. 永久封号检测：globalPunishType == 2 → 拒绝登录
8. BCrypt密码比对
9. 异步更新 last_login_time / last_login_ip
10. 异步写入 t_user_login_log（login_result=1）
11. 生成 Token（UUID）
12. 写入 Redis Hash：token:{token}
13. 设置 TTL: 2小时
14. 返回 token

**登录失败处理**：
- 账号不存在 → 异步写入日志（login_result=0）
- 密码错误 → 异步写入日志（login_result=0）
- 验证码错误 → 直接返回错误（不写日志）

**关键设计点**：
- 永久封号检测：仅检查 globalPunishType == 2，禁言（1）允许登录
- 异步操作：登录信息更新和日志写入异步执行，不阻塞主流程
- Token 结构：Hash 存储用户信息，减少后续数据库查询
- 滑动续期：拦截器每次请求刷新 TTL，活跃用户 Token 永不过期

### 4.3 忘记密码流程

**步骤**：
1. 参数校验（邮箱/验证码/新密码）
2. 邮箱格式校验
3. 新密码格式校验
4. 验证码校验：verify_code:{email} 匹配
5. 频率限制检查：forget_pwd_limit:{email}（1分钟内不能重复修改）
6. BCrypt加密新密码
7. 更新 t_user.user_password（by user_email）
8. 删除 verify_code:{email}
9. 写入 forget_pwd_limit:{email}（TTL: 1分钟）
10. 返回成功

**关键设计点**：
- 频率限制防止用户频繁修改密码
- 邮箱唯一性确保修改的是正确账号

---

## 五、安全机制

### 5.1 密码安全

**BCrypt 加密**：
- 工作因子：12（默认）
- 加密时间：~100ms
- 防护：彩虹表攻击、GPU破解

### 5.2 并发安全

**分布式锁**（Lua脚本）：
- 加锁：SET lock:user:email:{email} {uuid} EX 10 NX
- 释放：if redis.call('get', key) == value then redis.call('del', key) end
- 防护：防止并发重复注册同一邮箱

### 5.3 验证码安全

**双重验证**：
- 图形验证码：防止自动化攻击
- 邮箱验证码：防止邮箱被冒用

**冷却机制**：
- 60秒内不能重复发送验证码
- 5分钟内验证码有效

### 5.4 登录安全

**登录日志**：
- 记录所有登录尝试（成功/失败）
- 记录登录IP、时间、类型
- 支持异地登录检测

**处罚检测**：
- 永久封号：拒绝登录
- 全局禁言：允许登录（仅限制发消息）

---

## 六、API 接口设计

### 6.1 获取图形验证码

**请求**：`GET /api/auth/captcha`

**响应**：
- HTTP 200 OK
- Content-Type: image/jpeg
- 响应头：captchaId: {uuid}
- 响应体：图片二进制数据

### 6.2 发送邮箱验证码

**请求**：`POST /api/auth/code?email=user@qq.com`

**响应**：
```json
{
  "success": true,
  "errorMsg": null,
  "data": "验证码发送成功"
}
```

### 6.3 用户注册

**请求**：`POST /api/auth/register`
```
username=张三
email=user@qq.com
emailCode=123456
password=Abc123456
```

**响应**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "userAccount": "1234567890",
    "userNickname": "张三",
    "userAvatar": "",
    "userEmail": "user@qq.com"
  }
}
```

### 6.4 用户登录

**请求**：`POST /api/auth/login`
```json
{
  "userAccount": "1234567890",
  "userPassword": "Abc123456",
  "checkNumber": "1234",
  "captchaID": "{uuid}"
}
```

**响应**：
```json
{
  "success": true,
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 6.5 忘记密码

**请求**：`POST /api/auth/forget`
```
email=user@qq.com
emailCode=123456
newPassword=Abc654321
```

**响应**：
```json
{
  "success": true,
  "data": "密码修改成功"
}
```

### 6.6 获取当前用户

**请求**：`POST /api/auth/me`
- 请求头：Authorization: {token}

**响应**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "userAccount": "1234567890",
    "userNickname": "张三",
    "userAvatar": "",
    "userEmail": "user@qq.com"
  }
}
```

---

## 七、错误处理

| 错误码 | HTTP状态 | 说明 |
|---|---|---|
| 1001 | 400 | 参数校验失败 |
| 1002 | 401 | Token 过期 |
| 1003 | 401 | Token 无效 |
| 1004 | 429 | 操作过于频繁 |
| 1005 | 400 | 验证码错误 |
| 1006 | 400 | 账号已存在 |
| 1007 | 403 | 账号已被封禁 |
| 1008 | 500 | 系统异常 |

---

## 八、性能优化

**数据库优化**：
- `uk_account`、`uk_user_email` 唯一索引快速查询
- `idx_global_punish` 处罚检测索引

**Redis 优化**：
- Token 存储用户信息，减少数据库查询
- 处罚状态缓存，5分钟过期后查库重建

**异步操作**：
- 登录信息更新异步执行（Thread.ofVirtual）
- 登录日志写入异步执行
- 不阻塞主流程

---

*Part 1 完成。后续将发布 Part 2（用户管理模块）、Part 3（团队管理模块）、Part 4（聊天系统模块）。*
