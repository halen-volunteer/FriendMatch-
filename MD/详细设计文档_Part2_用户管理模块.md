# FriendMatch 详细设计文档 - Part 2：用户管理模块

> 版本：V1.0 | 日期：2026-03-16
> 更新：2026-03-23 - 在线状态模块并入 Part2（/api/online）

---

## 一、模块概述

用户管理模块负责个人信息管理、隐私设置、好友关系、黑名单以及在线状态管理等功能。支持用户自定义隐私策略，灵活控制资料可见性和消息接收权限。

### 核心特性
- ✅ 个人信息编辑（昵称、头像、简介、标签）
- ✅ 隐私设置（资料可见性、消息接收权限）
- ✅ 好友关系管理（添加、同意、拒绝、拉黑）
- ✅ 黑名单管理（拉黑、解除拉黑）
- ✅ 用户搜索（按账号、昵称、标签搜索）
- ✅ 用户列表浏览（分页展示）
- ✅ 在线状态管理（在线/离开/忙碌/隐身、心跳续期、超时清理）

---

## 二、数据库设计

### 2.1 t_user 表（用户基础信息表）

**隐私设置字段**（JSON格式）：
```json
{
  "view_info": 1,        // 1-所有人可见，2-仅团队成员可见
  "send_msg": 1,         // 1-所有人可发，2-仅团队成员可发，3-需验证
  "search_by_email": 0   // 0-不允许通过邮箱搜索，1-允许
}
```

**默认隐私设置**（数据库触发器自动填充）：
```sql
CREATE TRIGGER trg_t_user_default_privacy 
BEFORE INSERT ON t_user 
FOR EACH ROW 
BEGIN
  IF NEW.privacy_setting IS NULL THEN
    SET NEW.privacy_setting = '{"view_info":1,"send_msg":1,"search_by_email":0}';
  END IF;
END;
```

### 2.2 t_user_friend 表（好友关系表）

**表结构**：
- `id`：关系主键
- `user_id`：发起好友申请的用户ID
- `friend_id`：被添加的好友ID
- `friend_remark`：对好友的备注名（可选）
- `friend_status`：关系状态
  - 0：待验证（对方未同意）
  - 1：已成为好友（双向确认）
  - 2：已拒绝（对方拒绝）
  - 3：已拉黑（对方拉黑了我）
- `create_time`：申请时间
- `agree_time`：同意时间
- `update_time`：更新时间

**关键索引**：
- `uk_user_friend_status`：(user_id, friend_id, friend_status) 唯一索引
- `idx_friend_id`：friend_id 索引（快速查询被申请者）
- `idx_friend_status`：friend_status 索引（按状态查询）

**双向存储设计**：
- A 添加 B 为好友 → 存储 (A, B, 0)
- B 同意 → 更新 (A, B, 1) 并插入 (B, A, 1)
- 查询 A 的好友 → 查询 friend_status=1 且 user_id=A 的记录

### 2.3 t_user_blacklist 表（黑名单表）

**表结构**：
- `id`：黑名单主键
- `user_id`：拉黑方用户ID
- `black_user_id`：被拉黑方用户ID
- `black_reason`：拉黑原因（可选）
- `is_delete`：是否解除拉黑（0-未解除，1-已解除）
- `create_time`：拉黑时间
- `update_time`：更新时间

**关键索引**：
- `uk_user_black`：(user_id, black_user_id, is_delete) 唯一索引
- `idx_black_user_id`：black_user_id 索引

**设计说明**：
- 拉黑后无法收到对方消息
- 被拉黑方无法搜索到拉黑方
- 解除拉黑：is_delete=1（软删除，保留历史记录）

---

## 三、业务流程设计

### 3.1 个人信息管理

**编辑个人信息**：
```
POST /api/user/profile/update
{
  "userNickname": "新昵称",
  "userAvatar": "https://...",
  "userIntro": "个人简介",
  "userTags": "标签1,标签2"
}
```

**流程**：
1. 参数校验（昵称长度、头像URL格式等）
2. 敏感词检测（昵称、简介）
3. 更新 t_user
4. 返回更新后的用户信息

**字段限制**：
- `userNickname`：3-16位，支持中文/字母/数字/下划线
- `userIntro`：0-512字符
- `userTags`：最多5个标签，每个标签最多20字符

### 3.2 隐私设置管理

**查看隐私设置**：
```
GET /api/user/privacy
```

**响应**：
```json
{
  "success": true,
  "data": {
    "view_info": 1,
    "send_msg": 1,
    "search_by_email": 0
  }
}
```

**更新隐私设置**：
```
POST /api/user/privacy/update
{
  "view_info": 2,        // 仅团队成员可见
  "send_msg": 3,         // 需验证
  "search_by_email": 1   // 允许通过邮箱搜索
}
```

**流程**：
1. 参数校验（枚举值校验）
2. 更新 t_user.privacy_setting（JSON）
3. 返回更新后的隐私设置

**隐私策略说明**：
- `view_info=1`：所有人可查看资料（昵称、头像、简介、标签）
- `view_info=2`：仅团队成员可查看资料
- `send_msg=1`：所有人可发起私聊
- `send_msg=2`：仅团队成员可发起私聊
- `send_msg=3`：需对方同意才能发起私聊
- `search_by_email=1`：允许通过邮箱搜索到自己

### 3.3 好友关系管理

**添加好友**：
```
POST /api/friend/add
{
  "friendId": 1002,
  "applyMsg": "我们是同学"  // 可选
}
```

**流程**：
1. 参数校验（friendId 有效性）
2. 检查是否已是好友（friend_status=1）
3. 检查是否已拉黑对方（t_user_blacklist.is_delete=0）
4. 检查对方隐私设置（send_msg）
   - 若 send_msg=3 且未同意，返回"需对方同意"
5. 检查是否已申请（friend_status=0）
6. 插入 t_user_friend（user_id=当前用户，friend_id=目标用户，friend_status=0）
7. 发送系统通知给对方
8. 返回成功

**同意好友申请**：
```
POST /api/friend/agree
{
  "friendId": 1001
}
```

**流程**：
1. 查询申请记录（user_id=1001, friend_id=当前用户, friend_status=0）
2. 更新申请记录为 friend_status=1，agree_time=now()
3. 插入反向记录（user_id=当前用户, friend_id=1001, friend_status=1）
4. 发送系统通知给申请者
5. 返回成功

**拒绝好友申请**：
```
POST /api/friend/reject
{
  "friendId": 1001
}
```

**流程**：
1. 查询申请记录
2. 更新 friend_status=2（已拒绝）
3. 发送系统通知给申请者
4. 返回成功

**删除好友**：
```
POST /api/friend/delete
{
  "friendId": 1002
}
```

**流程**：
1. 查询好友关系（friend_status=1）
2. 删除双向记录（软删除或物理删除）
3. 返回成功

### 3.4 黑名单管理

**拉黑用户**：
```
POST /api/blacklist/add
{
  "blackUserId": 1003,
  "blackReason": "骚扰"  // 可选
}
```

**流程**：
1. 参数校验
2. 检查是否已拉黑（is_delete=0）
3. 插入 t_user_blacklist（is_delete=0）
4. 删除好友关系（如果存在）
5. 返回成功

**解除拉黑**：
```
POST /api/blacklist/remove
{
  "blackUserId": 1003
}
```

**流程**：
1. 查询拉黑记录（is_delete=0）
2. 更新 is_delete=1（软删除）
3. 返回成功

**查看黑名单**：
```
GET /api/blacklist?page=1&pageSize=20
```

**响应**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "blackUserId": 1003,
      "blackReason": "骚扰",
      "createTime": "2026-03-16 10:00:00"
    }
  ],
  "total": 1
}
```

### 3.5 用户搜索

**按账号搜索**：
```
GET /api/user/search?keyword=1234567890&type=account
```

**按昵称搜索**：
```
GET /api/user/search?keyword=张三&type=nickname
```

**按标签搜索**：
```
GET /api/user/search?keyword=编程&type=tag
```

**流程**：
1. 参数校验（keyword 长度、type 枚举值）
2. 根据 type 构建查询条件
3. 检查隐私设置（view_info）
4. 检查黑名单（is_delete=0）
5. 分页返回结果

**返回字段**（脱敏）：
```json
{
  "id": 1002,
  "userAccount": "1234567890",
  "userNickname": "张三",
  "userAvatar": "https://...",
  "userIntro": "个人简介",
  "userTags": "标签1,标签2",
  "isFriend": false,        // 是否已是好友
  "friendStatus": 0         // 好友状态（0-待验证，1-已成为好友）
}
```

### 3.6 用户列表浏览

**获取用户列表**：
```
GET /api/user/list?page=1&pageSize=20&sort=createTime
```

**流程**：
1. 分页查询 t_user（is_delete=0）
2. 检查隐私设置（view_info）
3. 检查黑名单
4. 返回脱敏用户信息

### 3.7 在线状态管理（并入 Part2）

**设置在线状态**：
```
POST /api/online/status?status=1
```

**状态值**：
- 1：在线
- 2：离开
- 3：忙碌
- 4：隐身

**心跳续期**：
```
POST /api/online/heartbeat
```
前端建议每2-3分钟调用一次。

**查询在线状态**：
```
GET /api/online/status?userId=1002
```
返回 `status=0` 表示离线。

**主动下线**：
```
POST /api/online/offline
```

**流程**：
1. `status` 接口写入 `user_online` Hash，并更新 `user_online_ttl` ZSet 到期时间
2. `heartbeat` 接口刷新到期时间，若状态不存在默认补为在线
3. `offline` 接口删除 Hash + ZSet 两处数据
4. 定时任务每分钟清理超时用户，防止异常断线导致假在线

---

## 四、隐私保护设计

### 4.1 资料可见性控制

**场景1：查看用户资料**
```
GET /api/user/{userId}/profile
```

**检查逻辑**：
1. 若 userId == 当前用户 → 返回完整信息
2. 若被拉黑（is_delete=0） → 返回"用户不存在"
3. 若 view_info=1 → 返回完整信息
4. 若 view_info=2 → 检查是否在同一团队
   - 是 → 返回完整信息
   - 否 → 返回"无权查看"
5. 若 view_info=3 → 返回"无权查看"

### 4.2 消息接收权限控制

**场景2：发起私聊**
```
POST /api/chat/private/send
{
  "recipientId": 1002,
  "content": "你好"
}
```

**检查逻辑**：
1. 若被拉黑 → 返回"无法发送消息"
2. 若 send_msg=1 → 允许发送
3. 若 send_msg=2 → 检查是否在同一团队
   - 是 → 允许发送
   - 否 → 返回"对方仅接收团队成员消息"
4. 若 send_msg=3 → 检查是否已是好友
   - 是 → 允许发送
   - 否 → 返回"需对方同意"

### 4.3 搜索可见性控制

**场景3：搜索用户**

**检查逻辑**：
1. 若被拉黑 → 搜索结果中不显示
2. 若 view_info=1 → 可被搜索到
3. 若 view_info=2 → 仅在团队成员搜索中显示
4. 若 view_info=3 → 不可被搜索到

---

## 五、API 接口设计

### 5.1 个人信息接口

**编辑个人信息**：
```
POST /api/user/profile/update
Authorization: {token}
Content-Type: application/json

{
  "userNickname": "新昵称",
  "userAvatar": "https://...",
  "userIntro": "个人简介",
  "userTags": "标签1,标签2"
}
```

**获取个人信息**：
```
GET /api/user/{userId}/profile
Authorization: {token}
```

### 5.2 隐私设置接口

**查看隐私设置**：
```
GET /api/user/privacy
Authorization: {token}
```

**更新隐私设置**：
```
POST /api/user/privacy/update
Authorization: {token}
Content-Type: application/json

{
  "view_info": 2,
  "send_msg": 3,
  "search_by_email": 1
}
```

### 5.3 好友关系接口

**添加好友**：
```
POST /api/friend/add
Authorization: {token}
Content-Type: application/json

{
  "friendId": 1002,
  "applyMsg": "我们是同学"
}
```

**获取好友列表**：
```
GET /api/friend/list?page=1&pageSize=20
Authorization: {token}
```

**同意好友申请**：
```
POST /api/friend/agree
Authorization: {token}
Content-Type: application/json

{
  "friendId": 1001
}
```

**拒绝好友申请**：
```
POST /api/friend/reject
Authorization: {token}
Content-Type: application/json

{
  "friendId": 1001
}
```

**删除好友**：
```
POST /api/friend/delete
Authorization: {token}
Content-Type: application/json

{
  "friendId": 1002
}
```

### 5.4 黑名单接口

**拉黑用户**：
```
POST /api/blacklist/add
Authorization: {token}
Content-Type: application/json

{
  "blackUserId": 1003,
  "blackReason": "骚扰"
}
```

**解除拉黑**：
```
POST /api/blacklist/remove
Authorization: {token}
Content-Type: application/json

{
  "blackUserId": 1003
}
```

**获取黑名单**：
```
GET /api/blacklist?page=1&pageSize=20
Authorization: {token}
```

### 5.5 用户搜索接口

**搜索用户**：
```
GET /api/user/search?keyword=张三&type=nickname&page=1&pageSize=20
Authorization: {token}
```

**获取用户列表**：
```
GET /api/user/list?page=1&pageSize=20&sort=createTime
Authorization: {token}
```

---

### 5.6 在线状态接口（并入 Part2）

**设置在线状态**：
```
POST /api/online/status?status=1
Authorization: {token}
```

**在线状态心跳**：
```
POST /api/online/heartbeat
Authorization: {token}
```

**查询指定用户在线状态**：
```
GET /api/online/status?userId=1002
Authorization: {token}
```

**主动下线**：
```
POST /api/online/offline
Authorization: {token}
```

---

## 六、数据一致性设计

### 6.1 好友关系一致性

**场景**：A 添加 B 为好友

**操作**：
1. 插入 (A, B, 0)
2. 发送系统通知给 B

**B 同意**：
1. 更新 (A, B, 0) → (A, B, 1)
2. 插入 (B, A, 1)
3. 发送系统通知给 A

**B 拒绝**：
1. 更新 (A, B, 0) → (A, B, 2)
2. 发送系统通知给 A

**A 删除 B**：
1. 删除 (A, B, 1)
2. 删除 (B, A, 1)

### 6.2 黑名单与好友关系一致性

**拉黑用户**：
1. 插入 t_user_blacklist（is_delete=0）
2. 删除好友关系（如果存在）
3. 删除好友申请（如果存在）

**解除拉黑**：
1. 更新 t_user_blacklist（is_delete=1）
2. 不恢复好友关系（需重新申请）

### 6.3 在线状态一致性（并入 Part2）

**状态写入一致性**：
1. `POST /api/online/status` 同时写 `user_online`（Hash）和 `user_online_ttl`（ZSet）
2. `POST /api/online/offline` 同时删除 Hash + ZSet

**超时清理一致性**：
1. `MuteExpireScheduler.cleanExpiredOnlineStatus()` 每分钟执行一次
2. `rangeByScore(user_online_ttl, 0, now)` 找到过期用户
3. `HDEL user_online` 与 `ZREM user_online_ttl` 成对清理

**说明**：
- 在线状态清理与登录态 token 过期不是同一机制
- token 用 Redis `EXPIRE` 自动过期；在线状态用 ZSet+定时任务清理

---

## 七、性能优化

**数据库优化**：
- `uk_user_friend_status` 唯一索引快速查询好友关系
- `idx_friend_id` 索引快速查询被申请者的申请列表
- `uk_user_black` 唯一索引快速查询黑名单

**查询优化**：
- 好友列表查询：`SELECT * FROM t_user_friend WHERE user_id=? AND friend_status=1`
- 黑名单查询：`SELECT * FROM t_user_blacklist WHERE user_id=? AND is_delete=0`

**缓存策略**：
- 用户基础信息缓存（Redis）
- 好友列表缓存（Redis）
- 黑名单缓存（Redis）

---

*Part 2 完成。后续将发布 Part 3（团队管理模块）、Part 4（聊天系统模块）。*
