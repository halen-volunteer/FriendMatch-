# FriendMatch 系统综合设计文档

> 基于数据库设计、Redis设计、聊天系统功能点设计的完整系统架构文档
> 版本：V1.0 核心功能设计
> 日期：2026-03-16

---

## 一、系统架构概览

### 1.1 核心模块划分

| 模块 | 功能 | 优先级 | 状态 |
|---|---|---|---|
| 用户认证 | 注册、登录、密码管理、安全审计 | P0 | 开发中 |
| 用户管理 | 个人信息、隐私设置、好友关系、黑名单 | P0 | 规划中 |
| 团队管理 | 团队创建、成员管理、角色权限、加入审批 | P0 | 规划中 |
| 聊天基础与会话状态 | 私聊、群聊、消息状态、聊天记录 | P0 | 规划中 |
| 平台治理与安全 | 禁言、封号、违规统计、通知、举报、设备安全 | P1 | 规划中 |

### 1.2 技术栈

- **后端框架**：Spring Boot 4.0 + MyBatis-Plus
- **数据库**：MySQL 8.0（InnoDB）
- **缓存**：Redis 6.0+
- **认证**：JWT Token + Redis Session
- **密码加密**：BCrypt
- **邮件服务**：Spring Mail + QQ SMTP

---

## 二、数据库设计

### 2.1 用户模块表结构

#### t_user（用户基础信息表）

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

#### t_user_login_log（登录日志表）

记录每次登录的 IP、类型、结果，用于安全审计。

#### t_user_friend（好友关系表）

- `friend_status`：0-待验证，1-已成为好友，2-已拒绝，3-已拉黑
- 双向存储：A→B 和 B→A 各存一条记录

#### t_user_blacklist（黑名单表）

- `is_delete`：0-未解除，1-已解除（软删除）

### 2.2 团队模块表结构

#### t_team（团队基础信息表）

- `team_type`：1-公开，2-私有
- `join_rule`：1-申请审批，2-仅邀请，3-密码加入
- `team_all_mute`：团队全员禁言标记

#### t_team_member（团队成员表）

- `role_type`：1-队长，2-管理员，3-普通成员，4-嘉宾
- `team_mute_type`：团队内禁言状态
- `is_quit`：是否已退出

#### t_team_apply（加入申请表）

- `audit_status`：0-待审核，1-通过，2-拒绝

### 2.3 聊天模块表结构

#### t_chat_message（聊天消息表）

**核心设计**：
- `conversation_id`：会话ID（私聊：min(uid1,uid2)_max(uid1,uid2)；群聊：team_{teamId}）
- `recv_type`：1-私聊，2-群聊
- `msg_type`：1-文本，2-图片，3-文件，4-表情包
- `is_revoke`：消息撤回标记

**关键索引**：
- `idx_conversation_create_time`：(conversation_id, create_time) —— 核心查询索引

#### t_message_read_receipt（消息回执表）

- `receipt_type`：1-已送达，2-已读
- 记录每个用户对每条消息的状态

### 2.4 处罚模块表结构

#### t_user_punish_log（处罚记录表）

- `punish_type`：1-单团队禁言，2-全局禁言，3-永久封号
- `punish_duration`：禁言时长（分钟），封号填-1
- `operate_type`：1-系统自动，2-管理员手动

#### t_user_violation_count（违规统计表）

用于实现梯度处罚：第1次禁言1小时，第2次禁言1天，第3次永久封号。

---

## 三、Redis 设计

### 3.1 核心 Key 设计规范

| 场景 | Key格式 | 数据类型 | TTL | 说明 |
|---|---|---|---|---|
| 用户在线状态 | `user_online` | Hash | 无 | field=userId, value=状态(1-在线/2-离开/3-忙碌/4-隐身) |
| 在线状态过期 | `user_online_ttl` | ZSet | 无 | member=userId, score=过期时间戳 |
| 群聊已读状态 | `msg_read:{msgId}` | Bitmap | 7天 | 1位代表1个用户，标记是否已读 |
| 群聊已送达状态 | `msg_deliver:{msgId}` | Bitmap | 7天 | 标记是否已收到 |
| 用户处罚状态 | `user_punish:{userId}` | String | 5分钟 | JSON格式：{type:1, unpunishTime:"..."} |
| 团队禁言状态 | `team_mute:{teamId}_{userId}` | String | 5分钟 | 用户在指定团队的禁言状态 |
| 团队全员禁言 | `team_all_mute:{teamId}` | String | 5分钟 | 0/1 |
| 会话未读数 | `unread_count:{userId}:{conversationId}` | Int | 无 | 按用户+会话维度统计未读 |
| 验证码 | `verify_code:{email}` | String | 5分钟 | 注册/登录/找回密码验证码 |
| 验证码冷却 | `send_limit:{email}` | String | 60秒 | 防止验证码重复发送 |
| 登录Token | `token:{token}` | Hash | 2小时 | 存储用户基础信息，用于鉴权 |

### 3.2 关键操作流程

**用户登录流程**：
1. 验证图形验证码 → 删除验证码
2. 查询用户 → 检查封号状态 → BCrypt密码比对
3. 异步更新 last_login_time / last_login_ip
4. 异步写入 t_user_login_log
5. 生成 Token → 写入 Redis Hash（Key: token:{token}，TTL: 2小时）
6. 返回 token 给前端

**消息已读流程**：
1. 用户查看群消息 → `SETBIT msg_read:{msgId} {userId} 1`
2. 异步写入 t_message_read_receipt
3. 前端查询已读人数 → `BITCOUNT msg_read:{msgId}`

---

## 四、功能设计（V1.0 核心）

### 4.1 用户认证模块

**注册流程**：
1. 参数校验（用户名/邮箱/密码）
2. 邮箱验证码校验
3. 分布式锁防并发重复注册
4. 雪花ID生成 user_account（10位纯数字）
5. BCrypt加密密码 → 写入 t_user
6. 返回脱敏用户信息

**登录流程**：
1. 图形验证码校验
2. 账号/邮箱 + 密码登录
3. 永久封号检测（globalPunishType == 2 拒绝）
4. 异步更新登录信息和日志
5. 生成 Token 存入 Redis
6. 返回 token

**找回密码**：
1. 邮箱验证码校验
2. 频率限制（1分钟内不能重复修改）
3. BCrypt加密新密码 → 更新 t_user

### 4.2 用户管理模块

**隐私设置**（JSON存储）：
```json
{
  "view_info": 1,        // 1-所有人，2-仅团队成员
  "send_msg": 1,         // 1-所有人，2-仅团队成员，3-需验证
  "search_by_email": 0   // 0-不允许，1-允许
}
```

**好友关系**：
- 添加好友 → 待验证状态
- 对方同意 → 已成为好友
- 双向存储：A→B 和 B→A

**黑名单**：
- 拉黑后无法收到对方消息
- 被拉黑方无法搜索到拉黑方

### 4.3 团队管理模块

**团队创建**：
- 设置类型（公开/私有）
- 设置加入规则（申请审批/仅邀请/密码加入）
- 设置最大成员数

**加入流程**：
- 公开+申请审批：提交申请 → 队长审批 → 加入
- 公开+密码加入：输入密码 → 直接加入
- 私有：仅队长邀请

**成员管理**：
- 队长可移除成员、审批申请、转让权限、解散团队
- 管理员可审批申请、禁言成员

### 4.4 聊天模块

**私聊**：
- 一对一实时文本聊天
- 支持消息撤回（5分钟内）
- 可给离线用户发送消息

**群聊**：
- 团队内全员可见
- 支持@指定成员/全员
- 显示消息状态（已发送/已送达/已读）

**消息状态**：
- 已发送：消息入库
- 已送达：接收方收到（Bitmap标记）
- 已读：接收方查看（Bitmap标记）

---

## 五、安全设计

### 5.1 认证与授权

- **Token鉴权**：基于 Redis Hash 存储用户信息
- **拦截器**：每次请求验证 Token，刷新 TTL（滑动续期）
- **密码加密**：BCrypt（工作因子12）

### 5.2 处罚系统

**梯度处罚**：
1. 第1次违规 → 禁言1小时
2. 第2次违规 → 禁言1天
3. 第3次违规 → 永久封号

**处罚类型**：
- 单团队禁言：仅限制在该团队发消息
- 全局禁言：限制所有地方发消息（允许登录）
- 永久封号：拒绝登录

### 5.3 数据安全

- **软删除**：所有核心表含 is_delete 字段
- **无物理外键**：业务层保证数据一致性
- **登录日志**：记录所有登录尝试（IP、类型、结果）

---

## 六、性能优化

### 6.1 数据库优化

- **消息查询**：`idx_conversation_create_time` 复合索引
- **登录日志**：轻量化设计，仅保留核心字段
- **好友关系**：双向存储消除 OR 查询

### 6.2 Redis 优化

- **Bitmap**：群聊已读状态，极小空间标记海量用户
- **Hash**：Token 存储用户信息，减少数据库查询
- **异步写入**：登录信息、日志异步更新，不阻塞主流程

### 6.3 缓存策略

- **处罚状态**：5分钟缓存，过期后查库重建
- **未读数**：实时更新，无缓存过期
- **在线状态**：定时任务清理过期状态

---

## 七、API 设计规范

### 7.1 响应格式

```json
{
  "success": true,
  "errorMsg": null,
  "data": {...},
  "total": 100
}
```

### 7.2 认证接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/captcha` | GET | 获取图形验证码 |
| `/api/auth/code` | POST | 发送邮箱验证码 |
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/forget` | POST | 忘记密码 |
| `/api/auth/me` | POST | 获取当前用户 |

---

## 八、开发计划

### Phase 1（第1周）：用户认证
- [ ] User 实体对齐 t_user 表
- [ ] 注册、登录、找回密码功能
- [ ] 登录日志记录
- [ ] Token 鉴权拦截器

### Phase 2（第2周）：用户管理
- [ ] 个人信息管理
- [ ] 隐私设置
- [ ] 好友关系管理
- [ ] 黑名单功能

### Phase 3（第3周）：团队管理
- [ ] 团队创建、查询、修改
- [ ] 成员管理、角色权限
- [ ] 加入申请审批流程

### Phase 4（第4周）：聊天系统
- [ ] 私聊、群聊消息存储
- [ ] 消息状态管理
- [ ] 聊天记录查询
- [ ] WebSocket 实时推送

---

*本文档为 V1.0 核心功能设计，V2.0 将补充多媒体、精细化管理、智能推荐等功能。*
