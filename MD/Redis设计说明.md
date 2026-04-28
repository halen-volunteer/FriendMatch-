# FriendMatch Redis 设计说明

> 校准日期：2026-04-20
> 校准依据：`src/main/java` 当前实现、`SQL/friendmatch.sql`
> 说明：本文档描述当前后端代码中已经落地的 Redis Key，以及 SQL 中已预留但代码尚未启用的缓存约定。

---

## 一、设计定位

Redis 在 FriendMatch 中承担四类职责：

1. 认证与验证码的短期状态存储
2. 聊天链路中的高频会话状态缓存
3. 平台治理与在线状态的快速判定
4. 搜索热词、推荐结果等可再生缓存

Redis 不是最终事实存储，用户、团队、消息、处罚、搜索记录等核心数据仍以 MySQL 为准。

---

## 二、当前实现中的 Key 总览

### 2.1 认证域

- `captcha:{captchaId}`
- `verify_code:{email}`
- `send_limit:{email}`
- `forget_pwd_limit:{email}`
- `token:{token}`

### 2.2 用户与关系域

- `user_info:{userId}`
- `friend_list:{userId}`
- `blacklist:{userId}`

### 2.3 聊天与会话状态域

- `unread:{userId}:{conversationId}`
- `msg_read:{msgId}`
- `msg_deliver:{msgId}`
- `last_msg:{conversationId}`
- `group_notice:{conversationId}`

### 2.4 团队与治理域

- `team_all_mute:{teamId}`
- `team_mute:{teamId}_{userId}`
- `user_punish:{userId}`

### 2.5 在线状态域

- `user_online`
- `user_online_ttl`

### 2.6 搜索与推荐域

- `search:hot:{searchType}:{limit}`
- `recommend:users:{userId}:{limit}`
- `recommend:teams:{userId}:{limit}`

---

## 三、关键 Key 说明

### 3.1 `captcha:{captchaId}`

- 用途：图形验证码缓存
- 类型：String
- TTL：1 分钟
- 写入入口：`GET /api/auth/captcha`
- 备注：登录时校验后删除

### 3.2 `verify_code:{email}`

- 用途：邮箱验证码缓存
- 类型：String
- TTL：5 分钟
- 写入入口：`POST /api/auth/code`
- 读取入口：注册、忘记密码

### 3.3 `send_limit:{email}`

- 用途：邮箱验证码发送冷却
- 类型：String
- TTL：60 秒
- 备注：防止同一邮箱短时间重复发送验证码

### 3.4 `forget_pwd_limit:{email}`

- 用途：忘记密码操作频率限制
- 类型：String
- TTL：1 分钟
- 写入入口：`POST /api/auth/forget`

### 3.5 `token:{token}`

- 用途：登录态缓存
- 类型：Hash
- 主要字段：`id`、`userAccount`、`userNickname`、`userAvatar`、`userIntro`、`userTags`
- TTL：120 分钟基础上带随机抖动
- 备注：拦截器每次请求会滑动续期

### 3.6 `user_info:{userId}`

- 用途：用户资料缓存
- 类型：Hash
- TTL：5 分钟
- 备注：`/api/user/{userId}/profile` 优先读缓存，资料更新后主动删除

### 3.7 `friend_list:{userId}`

- 用途：好友 ID 列表缓存
- 类型：Set
- TTL：24 小时
- 备注：好友新增、同意、删除、拉黑后主动失效

### 3.8 `blacklist:{userId}`

- 用途：黑名单用户 ID 列表缓存
- 类型：Set
- TTL：24 小时
- 备注：拉黑、取消拉黑后主动失效

### 3.9 `unread:{userId}:{conversationId}`

- 用途：按用户、按会话维度维护未读数
- 类型：String / Integer
- 写入时机：发送消息时自增
- 清理时机：标记已读时清零
- 备注：当前代码已不再使用历史文档中的 `unread_count:*`

### 3.10 `msg_read:{msgId}` / `msg_deliver:{msgId}`

- 用途：消息已读、送达的 Bitmap 辅助缓存
- 类型：Bitmap
- TTL：7 天
- 备注：最终事实仍以 `t_message_read_receipt` 为准

### 3.11 `last_msg:{conversationId}`

- 用途：缓存会话最后一条消息摘要
- 类型：Hash
- TTL：30 天
- 写入入口：私聊、群聊发送成功后

### 3.12 `group_notice:{conversationId}`

- 用途：群公告缓存
- 类型：String
- TTL：30 天
- 备注：当前群会话 ID 采用 `team_{teamId}` 格式

### 3.13 `team_all_mute:{teamId}`

- 用途：团队全员禁言状态缓存
- 类型：String
- TTL：5 分钟
- 备注：与 `t_team.team_all_mute` 联动

### 3.14 `team_mute:{teamId}_{userId}`

- 用途：团队成员禁言状态缓存
- 类型：String
- TTL：5 分钟或按剩余禁言时间
- 备注：与 `t_team_member.team_mute_type`、`team_mute_unpunish_time` 联动

### 3.15 `user_punish:{userId}`

- 用途：全局禁言 / 封号状态缓存
- 类型：String
- TTL：5 分钟或按处罚时长
- 备注：发送消息与登录校验都会使用

### 3.16 `user_online`

- 用途：在线状态主存储
- 类型：Hash
- 结构：`field=userId`，`value=status`
- 状态值：`1-在线`、`2-离开`、`3-忙碌`、`4-隐身`

### 3.17 `user_online_ttl`

- 用途：在线状态超时清理辅助结构
- 类型：ZSet
- 结构：`member=userId`，`score=expireAt`
- 备注：`MuteExpireScheduler.cleanExpiredOnlineStatus()` 每分钟清理过期在线状态

### 3.18 `search:hot:{searchType}:{limit}`

- 用途：热门搜索词缓存
- 类型：JSON 字符串
- 备注：当前定时任务会重点刷新并删除 `search:hot:1:10`、`search:hot:2:10`

### 3.19 `recommend:users:{userId}:{limit}` / `recommend:teams:{userId}:{limit}`

- 用途：个性化推荐结果缓存
- 类型：JSON 字符串
- 备注：用户标签、好友关系、私聊关系、共同团队、团队热度等变化后会触发失效或刷新

---

## 四、与模块的映射关系

### Part1 用户认证

- `captcha:{captchaId}`
- `verify_code:{email}`
- `send_limit:{email}`
- `forget_pwd_limit:{email}`
- `token:{token}`

### Part2 用户管理

- `user_info:{userId}`
- `friend_list:{userId}`
- `blacklist:{userId}`
- `user_online`
- `user_online_ttl`

### Part4 聊天基础与会话状态

- `unread:{userId}:{conversationId}`
- `msg_read:{msgId}`
- `msg_deliver:{msgId}`
- `last_msg:{conversationId}`

### Part5 平台治理与安全

- `user_punish:{userId}`
- `team_mute:{teamId}_{userId}`
- `team_all_mute:{teamId}`

### Part6 聊天增强与消息治理

- `group_notice:{conversationId}`
- `last_msg:{conversationId}`

### Part7 搜索推荐

- `search:hot:{searchType}:{limit}`
- `recommend:users:{userId}:{limit}`
- `recommend:teams:{userId}:{limit}`

---

## 五、实现校准结论

- 当前后端代码中，未读数 Key 的真实前缀是 `unread:`，不是旧文档里的 `unread_count:`
- 当前在线状态已经落地为 `user_online` + `user_online_ttl` 两个结构，不再是 `user:online:{userId}` 预留方案
- 用户资料、好友列表、黑名单、全员禁言这几类缓存已经在业务代码中实际使用，应纳入正式 Redis 设计说明
- Redis 中的大多数聊天状态都属于“高频加速层”，实际持久化仍由 MySQL 表承担

---

## 六、结论

FriendMatch 的 Redis 设计已经与当前后端实现对齐，主线围绕“认证、用户关系、聊天状态、治理、搜索推荐”五个方向展开。后续新增 Key 时，建议继续遵循：

- 命名可读且按业务域分组
- TTL 有明确来源
- 缓存失效点与业务写入点成对设计
- MySQL 仍作为最终事实来源
