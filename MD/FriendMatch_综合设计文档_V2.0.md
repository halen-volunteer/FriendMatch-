# FriendMatch 系统综合设计文档

> 基于数据库设计、Redis设计、聊天系统功能点设计的完整系统架构文档
> 版本：V2.0 进阶功能扩展
> 日期：2026-03-19

---

## 一、系统架构概览

### 1.1 核心模块划分

| 模块 | 功能 | 优先级 | V1.0状态 | V2.0状态 |
|------|------|--------|----------|----------|
| 用户认证 | 注册、登录、密码管理、安全审计 | P0 | ✅ 已完成 | 扩展设备管理、二次验证 |
| 用户管理 | 个人信息、隐私设置、好友关系、黑名单 | P0 | ✅ 已完成 | 扩展搜索推荐 |
| 团队管理 | 团队创建、成员管理、角色权限、加入审批 | P0 | ✅ 已完成 | 扩展邀请码、动态、评价 |
| 聊天基础与会话状态 | 私聊、群聊、消息状态、聊天记录 | P0 | ✅ 已完成 | 扩展为基础聊天子模块 |
| 平台治理与安全 | 禁言、封号、违规统计、通知、举报、设备安全 | P1 | ✅ 已完成 | 聚合治理与安全能力 |
| 聊天增强与消息治理 | 多媒体消息、消息管理、消息举报、群公告 | P1 | 规划中 | 新增 |
| 团队精细化 | 角色分级、邀请码、团队动态、评价 | P1 | 规划中 | 新增 |
| 搜索与推荐 | 智能搜索、个性化推荐、搜索历史 | P2 | 规划中 | 新增 |
| 安全补充 | 举报、设备管理、操作日志 | P2 | 规划中 | 新增 |
| 聊天本地存储 | 本地SQLite存储、设备迁移 | P2 | 规划中 | 新增 |

### 1.2 技术栈

- **后端框架**：Spring Boot 4.0 + MyBatis-Plus
- **数据库**：MySQL 8.0（InnoDB）
- **缓存**：Redis 6.0+
- **实时通信**：WebSocket（Spring WebSocket）
- **认证**：JWT Token + Redis Session
- **密码加密**：BCrypt
- **邮件服务**：Spring Mail + QQ SMTP
- **文件存储**：OSS/S3 对象存储（V2.0新增）
- **搜索引擎**：Elasticsearch（V2.0可选）
- **AI审核**：第三方内容安全API（V2.0新增）
- **客户端本地存储**：SQLite / IndexedDB（V2.0新增）

### 1.3 V2.0 核心变化

| 变化点 | 说明 |
|--------|------|
| 聊天记录存储 | 从纯云端存储改为「服务器72小时中转 + 客户端本地持久化」混合模式 |
| 消息内容安全 | 服务器不长期保存消息内容，72小时后清空，违规消息写入审计表 |
| 多媒体消息 | 新增图片、文件、表情包消息类型 |
| 团队精细化 | 新增管理员/嘉宾角色、邀请码、团队动态、团队评价 |
| 搜索推荐 | 新增模糊搜索、相似度排序、个性化推荐 |
| 安全增强 | 新增用户/团队举报、设备管理、操作日志、二次验证 |

---

## 二、数据库设计

### 2.1 用户模块（V1.0继承，V2.0扩展）

#### t_user（用户基础信息表）

**核心字段**（继承V1.0）：
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

**关键索引**：
- `uk_account`：user_account 唯一索引
- `uk_user_email`：user_email 唯一索引
- `idx_global_punish`：(global_punish_type, global_unpunish_time)

#### t_user_login_log（登录日志表，继承V1.0）

记录每次登录的 IP、类型、结果，用于安全审计。

#### t_user_friend（好友关系表，继承V1.0）

- `friend_status`：0-待验证，1-已成为好友，2-已拒绝，3-已拉黑
- 双向存储：A→B 和 B→A 各存一条记录

#### t_user_blacklist（黑名单表，继承V1.0）

#### t_user_device（用户设备表，V2.0新增）

- `device_id`：设备唯一标识（UUID）
- `device_name`：设备名称（如"iPhone 12"）
- `device_type`：1-Web，2-iOS，3-Android，4-Windows，5-Mac
- `device_ip`：设备IP + `device_location`：IP定位地址
- `last_login_time`：最后登录时间
- `is_trusted`：是否信任（0-否，1-是）

#### t_operation_log（操作日志表，V2.0新增）

- `operation_type`：1-登录，2-修改密码，3-修改邮箱，4-解散团队，5-删除好友，6-拉黑用户，7-其他
- `operation_ip`：操作IP + `operation_location`：操作位置
- `operation_result`：0-失败，1-成功

#### t_user_report（用户举报表，V2.0新增）

- `reported_user_id`：被举报用户ID
- `report_reason`：1-骚扰，2-色情，3-暴力，4-广告，5-诈骗，6-其他
- `report_status`：0-待审核，1-已处理，2-驳回
- `admin_action`：1-警告，2-禁言，3-封号，4-无处理

---

### 2.2 团队模块（V1.0继承，V2.0扩展）

#### t_team（团队基础信息表，继承V1.0）

- `team_type`：1-公开，2-私有
- `join_rule`：1-申请审批，2-仅邀请，3-密码加入
- `team_all_mute`：团队全员禁言标记

#### t_team_member（团队成员表，V2.0扩展角色）

**V2.0新增字段**：
- `role_type`：**1-队长，2-管理员（新增），3-普通成员，4-嘉宾（新增）**
- `join_source`：1-直接，2-邀请，3-审批，4-二维码，5-链接，6-密码
- `last_active_time`：最后活跃时间

#### t_team_apply（加入申请表，继承V1.0）

#### t_team_invite_code（邀请码表，V2.0新增）

- `invite_code`：邀请码（唯一）
- `code_type`：1-二维码，2-链接码
- `valid_until`：有效期截止时间（-1永久）
- `max_usage`：最大使用次数（-1无限制）
- `current_usage`：当前使用次数

#### t_team_dynamic（团队动态表，V2.0新增）

- `dynamic_type`：1-活动通知，2-公告，3-成员动态，4-其他
- `is_top`：是否置顶
- `view_count/like_count/comment_count`：互动统计

#### t_team_dynamic_comment（动态评论表，V2.0新增）

- `parent_comment_id`：父评论ID（用于回复嵌套）

#### t_team_rating（团队评价表，V2.0新增）

- `rating_score`：评分（1-5星）
- `rating_tags`：评价标签（活跃、友好、专业等，逗号分隔）
- `team_reply`：队长回复内容

#### t_team_report（团队举报表，V2.0新增）

- `reported_team_id`：被举报团队ID
- `report_reason`：1-违规内容，2-诈骗，3-骚扰，4-广告，5-其他
- `admin_action`：1-警告，2-限制功能，3-解散团队，4-无处理

---

### 2.3 聊天模块（V2.0重大变更）

#### t_chat_message（聊天消息表，V2.0调整）

**核心设计变更**：
- `conversation_id`：会话ID（私聊：min(uid1,uid2)_max(uid1,uid2)；群聊：team_{teamId}）
- `recv_type`：1-私聊，2-群聊
- `msg_type`：**1-文本，2-图片，3-文件，4-表情包（V2.0扩展）**
- `is_revoke`：消息撤回标记
- **`expire_time`（V2.0新增）**：消息内容过期时间（发送时间+72小时）
- **`msg_content`**：V2.0中72小时后自动清空（客户端本地永久存储）

**关键索引**：
- `idx_conversation_create_time`：(conversation_id, create_time)

#### t_chat_message_ext（消息扩展表，V2.0新增）

存储多媒体消息属性：
- `file_name`：文件名称
- `file_size`：文件大小（字节）
- `file_type`：文件类型（jpg、pdf、zip等）
- `file_url`：文件完整URL

#### t_message_read_receipt（消息回执表，继承V1.0）

- `receipt_type`：1-已送达，2-已读

#### t_message_collection（消息收藏表，V2.0新增）

- `user_id`：收藏用户ID
- `message_id`：被收藏消息ID
- `collection_note`：收藏备注

#### t_message_pin（消息置顶表，V2.0新增）

- `conversation_id`：会话ID
- `message_id`：被置顶消息ID
- `pin_order`：置顶顺序

#### t_message_report（消息举报表，V2.0新增）

- `report_reason`：1-色情，2-暴力，3-骚扰，4-广告，5-诈骗，6-其他
- `ai_check_result`：0-待检查，1-违规，2-正常
- `ai_confidence`：AI置信度（0-100）
- `admin_status`：0-待处理，1-已处理，2-驳回

#### t_chat_audit_log（违规消息审计表，V2.0新增）

**永久保留违规消息**（不受72小时清理影响）：
- `msg_id`：关联消息ID
- `sender_id`：发送者ID
- `msg_content`：违规消息内容（永久保留）
- `audit_result`：审核结果

---

### 2.4 处罚模块（继承V1.0）

#### t_user_punish_log（处罚主记录表）
- `punish_type`：1-单团队禁言，2-全局禁言，3-永久封号
- `operate_type`：1-系统自动，2-管理员手动

#### t_punish_msg_relation（处罚-违规消息关联表）

- `ai_audit_result`：大模型审核结果

#### t_user_violation_count（违规统计表）

- 用于实现梯度处罚：第1次禁言60分钟→第2次1天→第3次7天→第4次及以上永久封号

---

### 2.5 系统模块（V1.0继承，V2.0扩展）

#### t_system_notice（系统通知表，继承V1.0）

- `notice_type`：1-好友申请，2-好友拒绝，3-入群审批通过，4-入群审批拒绝，5-被移出团队，6-账号异常，7-处罚通知，8-反馈回复

#### t_user_feedback（用户反馈表，继承V1.0）

- `feedback_type`：1-功能问题，2-违规举报，3-处罚申诉，4-其他建议
- `handle_status`：0-待处理，1-处理中，2-已解决，3-已驳回

---

### 2.6 搜索推荐模块（V2.0新增）

#### t_search_history（搜索历史表）

- `search_type`：1-用户搜索，2-团队搜索
- `search_keyword`：搜索关键词
- `search_count`：搜索次数（同一关键词累计）

#### t_search_hot_keyword（热门搜索词表）

- `keyword`：热门关键词
- `rank`：当前排名

#### t_user_recommendation（用户推荐记录表）

- `recommend_reason`：1-标签相似，2-好友关系，3-聊天频繁，4-团队相同
- `recommend_score`：推荐分数（0-100）

#### t_team_recommendation（团队推荐记录表）

- `recommend_reason`：1-标签相似，2-成员重叠，3-热门团队，4-新建团队
- `recommend_score`：推荐分数（0-100）

---

## 三、Redis 设计

### 3.1 核心 Key 设计规范

| 场景 | Key格式 | 数据类型 | TTL | 说明 |
|------|---------|----------|-----|------|
| 用户在线状态 | `user_online` | Hash | 无 | field=userId, value=1-在线/2-离开/3-忙碌/4-隐身 |
| 在线状态过期 | `user_online_ttl` | ZSet | 无 | member=userId, score=过期时间戳 |
| 群聊已读状态 | `msg_read:{msgId}` | Bitmap | 7天 | 标记群成员是否已读 |
| 群聊已送达状态 | `msg_deliver:{msgId}` | Bitmap | 7天 | 标记是否已收到 |
| 用户处罚状态 | `user_punish:{userId}` | String | 5分钟 | 缓存禁言/封号状态 |
| 团队禁言状态 | `team_mute:{teamId}_{userId}` | String | 5分钟 | 用户在指定团队的禁言状态 |
| 团队全员禁言 | `team_all_mute:{teamId}` | String | 5分钟 | 0/1 |
| 会话未读数 | `unread_count:{conversationId}` | Int | 无 | 消息读取后更新 |
| 用户离线消息队列 | `offline_msg:{userId}` | List | 72小时 | V2.0新增：离线期间消息缓冲 |
| 邮箱验证码 | `verify_code:{email}` | String | 5分钟 | 注册/找回密码验证码 |
| 验证码冷却 | `send_limit:{email}` | String | 60秒 | 防止重复发送 |
| 图形验证码 | `captcha:{captchaId}` | String | 1分钟 | 登录图形验证码 |
| 登录Token | `token:{token}` | Hash | 2小时 | 存储用户基础信息 |
| 好友列表缓存 | `friend_list:{userId}` | String | 24小时 | 好友列表缓存 |
| 黑名单缓存 | `blacklist:{userId}` | String | 24小时 | 黑名单缓存 |

### 3.2 V2.0 新增：离线消息队列

**写入逻辑**：接收方离线时，消息写入 Redis List：
```
RPUSH offline_msg:{userId} {msgJson}
EXPIRE offline_msg:{userId} 259200  // 72小时
```

**读取逻辑**：用户上线后调用 `/api/chat/offline-messages`，一次性拉取后清空：
```
LRANGE offline_msg:{userId} 0 -1
DEL offline_msg:{userId}
```

### 3.3 关键操作流程

**V2.0 消息发送流程**：
1. 前置校验（黑名单、隐私、禁言、敏感词）
2. 写消息记录（设置 expire_time = now + 72h）
3. 敏感词命中 → 同步写 t_chat_audit_log（永久保留）
4. 接收方在线 → WebSocket 实时推送
5. 接收方离线 → 写 Redis 离线消息队列（72h TTL）
6. 返回消息ID给发送方，客户端写本地数据库

**V2.0 消息接收流程**：
1. WebSocket 推送到客户端
2. 客户端写入本地 SQLite/IndexedDB（永久存储）
3. 标记 Bitmap 已送达

**V2.0 历史记录查询**：客户端直接读本地数据库，不请求服务器（无网络查历史）

---

## 四、功能设计（V2.0 进阶）

### 4.1 V1.0 继承功能（已完成）

- ✅ 用户认证（注册/登录/找回密码/图形验证码）
- ✅ 用户管理（资料/隐私/好友/黑名单/通知）
- ✅ 团队管理（创建/搜索/加入/审批/成员/禁言）
- ✅ 聊天系统（私聊/群聊/撤回/已读/WebSocket推送/离线补推）
- ✅ 处罚系统（梯度处罚/撤销/违规统计）
- ✅ 反馈系统（提交/处理/通知）
- ✅ 在线状态（设置/心跳/查询/定时清理）

### 4.2 聊天体验增强（V2.0新增）

**多媒体消息**：
- 图片消息（≤10MB）、文件消息（≤100MB）、表情包消息（≤5MB）
- 文件上传至 OSS，消息正文存 file_url

**消息管理**：
- 收藏消息（写 t_message_collection）
- 置顶消息（写 t_message_pin，每个会话最多5条）
- 搜索历史消息（按关键词全文检索）
- 导出聊天记录（PDF/Excel/TXT，仅限本地数据）

**消息举报（AI联动）**：
```
用户举报消息
    ↓
保存举报记录（t_message_report）
    ↓
异步调用AI内容审核API
    ↓
AI判定违规 → 写违规审计日志 → 触发梯度处罚 → 发送处罚通知
AI判定正常 → 更新举报状态为误报
```

**群聊辅助**：
- 群公告（存 t_group_chat_setting.group_notice，WebSocket推送全员）
- 全员禁言（写 Redis team_all_mute + 数据库）
- 自定义群名（每个用户独立备注，存 t_group_chat_setting）

### 4.3 团队管理精细化（V2.0新增）

**角色分级权限**：

| 权限 | 队长 | 管理员 | 普通成员 | 嘉宾 |
|------|------|--------|----------|------|
| 发消息 | ✅ | ✅ | ✅ | ✅ |
| 审批申请 | ✅ | ✅ | ❌ | ❌ |
| 禁言成员 | ✅ | ✅（不含管理员）| ❌ | ❌ |
| 移除成员 | ✅ | ✅（不含管理员）| ❌ | ❌ |
| 解散团队 | ✅ | ❌ | ❌ | ❌ |
| 转让队长 | ✅ | ❌ | ❌ | ❌ |
| 免疫禁言 | ✅ | ✅ | ❌ | ❌ |

**邀请码管理**：
- 生成二维码/链接码（可设置有效期和使用次数上限）
- 扫码/点链接直接加入
- 队长可禁用/重新生成邀请码

**团队动态**：
- 队长/管理员发布动态（支持Markdown）
- 支持点赞、评论、回复
- 置顶动态

**团队评价**（仅成员可发）：
- 1-5星评分 + 文字评价 + 标签（活跃、友好、专业等）
- 队长可回复评价
- 系统展示平均评分和分布

### 4.4 搜索与推荐（V2.0新增）

**智能搜索**：
- 用户/团队搜索支持模糊匹配
- 相似度排序（精确匹配 > 前缀匹配 > 模糊匹配）
- 搜索历史（按关键词去重，记录次数和最后搜索时间）
- 热门搜索词（定时统计，每小时更新）
- 搜索自动补全建议

**个性化推荐算法**：

用户推荐分数 = 标签相似度×0.4 + 好友关系×0.3 + 聊天频率×0.2 + 团队重叠×0.1

团队推荐分数 = 标签相似度×0.4 + 成员重叠×0.3 + 热度×0.2 + 新建度×0.1

**更新策略**：每天凌晨2点全量更新，推荐结果缓存24小时。

### 4.5 安全与体验补充（V2.0新增）

**举报系统**：
- 用户举报：举报违规用户（含类型选择 + 证据上传）
- 团队举报：举报违规团队
- 防滥用：每用户每天最多举报10次；多次虚假举报计入违规

**设备管理**：
- 查看登录设备列表（设备名、类型、IP、位置、最后登录时间）
- 信任设备（减少二次验证频率）
- 远程下线指定设备
- 异地登录自动告警通知

**二次验证**：
- 重要操作（解散团队、修改密码、修改邮箱）需邮箱验证码二次确认
- 验证码5分钟有效，单次操作绑定

**操作审计日志**：
- 记录所有敏感操作（登录/修改密码/解散团队/拉黑/删好友）
- 保存操作IP和位置，支持用户自查

### 4.6 聊天记录本地存储（V2.0新增）

**设计哲学**：参考微信模式，消息持久化在客户端本地，服务器仅做中转和72小时缓冲。

**客户端本地数据库（SQLite/IndexedDB）**：

| 字段 | 说明 |
|------|------|
| msg_id | 消息ID（服务器返回）|
| conversation_id | 会话ID |
| msg_content | 消息内容（永久本地存储）|
| local_status | 0-发送中，1-已送达，2-发送失败 |

**消息流程**：
1. 发送方：先写本地（status=0）→ 发服务器 → 更新本地（status=1）
2. 接收方：WebSocket推送 → 写本地数据库 → 刷新UI
3. 历史查询：直接读本地，无需请求服务器

**设备迁移（类微信）**：
- 方式一（推荐）：旧设备开启HTTP服务器，新设备同局域网扫码下载加密数据库
- 方式二（备选）：云端中转，24小时有效迁移码，不经服务器明文

---

## 五、安全设计

### 5.1 认证与授权

- **Token鉴权**：基于 Redis Hash 存储用户信息，请求头 `Authorization: {token}`
- **拦截器**：每次请求验证Token，滑动续期（TTL 2小时）
- **密码加密**：BCrypt（工作因子12）
- **封号检测**：登录时检测 global_punish_type=2，直接拒绝

### 5.2 处罚系统

**梯度处罚**（基于 total_violation_num）：

| 违规次数 | 处罚 |
|---------|------|
| 第1次 | 全局禁言 60 分钟 |
| 第2次 | 全局禁言 1440 分钟（1天）|
| 第3次 | 全局禁言 10080 分钟（7天）|
| ≥第4次 | 永久封号 |

**处罚类型**：
- 单团队禁言：仅限制该团队发消息
- 全局禁言：限制所有地方发消息（允许登录）
- 永久封号：拒绝登录

**AI联动（V2.0）**：消息举报触发AI审核 → 违规自动写审计表 → 管理员确认后执行梯度处罚

### 5.3 内容安全（V2.0新增）

- **发送拦截**：文本消息实时敏感词检测
- **举报审核**：用户举报 → AI判定 → 管理员二次确认
- **违规留证**：违规消息写 t_chat_audit_log 永久保留（不受72h清理影响）
- **文件安全**：验证MIME类型、限制大小、禁止可执行文件

### 5.4 数据安全

- **软删除**：所有核心表含 is_delete 字段
- **无物理外键**：业务层保证数据一致性
- **消息内容最小化（V2.0）**：服务器消息内容72小时后清空，仅保留元数据
- **登录日志**：记录所有登录尝试（IP、类型、结果）

---

## 六、性能优化

### 6.1 数据库优化

- **消息查询**：`idx_conversation_create_time` 复合索引
- **好友关系**：双向存储消除 OR 查询
- **消息内容定时清理**：每小时清空 expire_time < now() 的消息内容

### 6.2 Redis 优化

- **Bitmap**：群聊已读/已送达状态，极小空间标记海量用户
- **Hash**：Token存储用户信息，减少数据库查询
- **List**：离线消息队列，72小时TTL自动过期
- **ZSet**：在线状态过期控制，定时清理
- **异步写入**：登录信息、回执、日志异步写入，不阻塞主流程

### 6.3 缓存策略

- **处罚状态**：5分钟缓存，过期后查库重建
- **未读数**：实时更新，无缓存过期
- **在线状态**：每分钟定时清理过期条目
- **好友/黑名单**：24小时缓存
- **推荐结果（V2.0）**：24小时缓存，每天凌晨全量更新

### 6.4 文件上传优化（V2.0）

- 客户端MD5秒传检测（相同文件不重复上传）
- 大文件分片上传
- OSS直传（绕过后端，降低带宽压力）
- 定期清理未关联消息的孤立文件

---

## 七、API 设计规范

### 7.1 响应格式

```json
{
  "code": 200,
  "message": "ok",
  "data": {},
  "total": 0
}
```

### 7.2 认证方式

```
Authorization: {token}
```

WebSocket连接：`ws://{host}/ws?token={token}`

### 7.3 V2.0 新增接口汇总

| 模块 | 新增接口数 | 路径前缀 |
|------|-----------|----------|
| 多媒体消息 | 3 | /api/chat/message/ |
| 消息管理 | 7 | /api/chat/message/ |
| 消息举报 | 2 | /api/chat/message/report |
| 群聊辅助 | 5 | /api/chat/group/ |
| 邀请码管理 | 4 | /api/team/invite-code/ |
| 团队动态 | 5 | /api/team/dynamic/ |
| 团队评价 | 4 | /api/team/rating/ |
| 智能搜索 | 5 | /api/search/ |
| 个性化推荐 | 3 | /api/recommend/ |
| 用户举报 | 3 | /api/report/ |
| 设备管理 | 4 | /api/user/device/ |
| 操作日志 | 1 | /api/user/operation-logs |
| 离线消息拉取 | 1 | /api/chat/offline-messages |
| **合计** | **47** | — |

---

## 八、开发计划

### V1.0（已完成）

- ✅ Phase 1：用户认证
- ✅ Phase 2：用户管理（资料/好友/黑名单/通知）
- ✅ Phase 3：团队管理（创建/加入/审批/成员/禁言）
- ✅ Phase 4：聊天系统（私聊/群聊/WebSocket/离线补推）
- ✅ Phase 5：处罚管理 + 用户反馈 + 在线状态

### V2.0（规划中）

- [ ] Phase 6：聊天体验增强（多媒体/收藏/置顶/举报/群聊辅助）
- [ ] Phase 7：团队精细化（角色分级/邀请码/动态/评价）+ 搜索推荐
- [ ] Phase 8：安全补充（举报/设备管理/操作日志/二次验证）
- [ ] Phase 9：客户端本地存储改造（SQLite + 设备迁移）

### 工作量估算

| Phase | 工作量 | 说明 |
|-------|--------|------|
| Phase 6 | 40小时 | 多媒体需对接OSS，AI审核异步 |
| Phase 7 | 48小时 | 推荐算法较复杂 |
| Phase 8 | 24小时 | 相对独立 |
| Phase 9 | 74小时 | 客户端改造工作量最大 |
| **合计** | **186小时** | **约23个工作日** |

---

## 九、V2.0 数据库表汇总

### V1.0 继承表（9张）

| 表名 | 说明 |
|------|------|
| t_user | 用户基础信息 |
| t_user_login_log | 登录日志 |
| t_user_friend | 好友关系 |
| t_user_blacklist | 黑名单 |
| t_team | 团队信息 |
| t_team_member | 团队成员 |
| t_team_apply | 加入申请 |
| t_chat_message | 聊天消息（V2.0调整expire_time）|
| t_message_read_receipt | 消息回执 |
| t_user_punish_log | 处罚记录 |
| t_punish_msg_relation | 处罚-消息关联 |
| t_user_violation_count | 违规统计 |
| t_system_notice | 系统通知 |
| t_user_feedback | 用户反馈 |

### V2.0 新增表（17张）

| 表名 | 模块 | 说明 |
|------|------|------|
| t_user_device | 用户 | 设备管理 |
| t_operation_log | 用户 | 操作审计日志 |
| t_user_report | 用户 | 用户举报 |
| t_team_invite_code | 团队 | 邀请码 |
| t_team_dynamic | 团队 | 团队动态 |
| t_team_dynamic_comment | 团队 | 动态评论 |
| t_team_rating | 团队 | 团队评价 |
| t_team_report | 团队 | 团队举报 |
| t_chat_message_ext | 聊天 | 多媒体消息扩展 |
| t_message_collection | 聊天 | 消息收藏 |
| t_message_pin | 聊天 | 消息置顶 |
| t_message_report | 聊天 | 消息举报 |
| t_chat_audit_log | 聊天 | 违规消息审计 |
| t_group_chat_setting | 聊天 | 群聊设置 |
| t_search_history | 搜索 | 搜索历史 |
| t_search_hot_keyword | 搜索 | 热门搜索词 |
| t_user_recommendation | 推荐 | 用户推荐记录 |
| t_team_recommendation | 推荐 | 团队推荐记录 |

**总计：V2.0 共 31 张表**

---

*本文档为 V2.0 完整进阶功能设计，覆盖 V1.0 全部已实现功能及 V2.0 扩展规划。*
