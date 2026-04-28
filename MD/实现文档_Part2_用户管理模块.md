# FriendMatch 实现文档 - Part 2：用户管理模块

> 版本：V1.0 | 日期：2026-03-24

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `UserManagementController.java` / `OnlineStatusController.java` |
| Service | `UserManagementService.java` / `UserManagementServiceImpl.java` |
| Mapper | `UserMapper.java` / `UserFriendMapper.java` / `UserBlacklistMapper.java` / `SystemNoticeMapper.java` / `TeamMemberMapper.java` |
| Model | `User.java` / `UserFriend.java` / `UserBlacklist.java` / `SystemNotice.java` |
| DTO | `UserProfileUpdateDTO.java` / `PrivacySettingDTO.java` / `FriendOperationDTO.java` / `BlacklistOperationDTO.java` |
| 调度 | `MuteExpireScheduler.java`（在线状态超时清理） |

---

## 一点五、数据库设计

### t_user_friend 表（好友关系表）

| 字段 | 说明 |
|---|---|
| `user_id` | 发起好友申请的用户 ID |
| `friend_id` | 被添加的好友 ID |
| `friend_remark` | 对好友的备注名（可选）|
| `friend_status` | 0-待验证，1-已成为好友，2-已拒绝，3-已拉黑 |
| `agree_time` | 同意时间 |

**关键索引**：`uk_user_friend_status`（user_id, friend_id, friend_status）、`idx_friend_id`、`idx_friend_status`

**双向存储**：A 添加 B → 存 (A,B,0)；B 同意 → 更新 (A,B,1) 并插入 (B,A,1)

### t_user_blacklist 表（黑名单表）

| 字段 | 说明 |
|---|---|
| `user_id` | 拉黑方用户 ID |
| `black_user_id` | 被拉黑方用户 ID |
| `black_reason` | 拉黑原因（可选）|
| `is_delete` | 0-未解除，1-已解除（软删除，保留历史）|

**关键索引**：`uk_user_black`（user_id, black_user_id, is_delete）、`idx_black_user_id`

---

## 二、个人信息管理

### POST /api/user/profile/update — 编辑个人信息

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userNickname` | String | 否 | 新昵称，3-16位，敏感词检测 |
| `userIntro` | String | 否 | 个人简介，≤512字符，敏感词检测 |
| `userAvatar` | String | 否 | 头像 URL |
| `userTags` | List\<String\> | 否 | 标签列表，≤5个，每个≤20字符 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/user/profile/update
  → 参数校验（昵称3-16位、简介≤512字符、标签≤5个每个≤20字符）
  → 敏感词检测（昵称/简介）
  → userMapper.updateById()（只更新非空字段）
  → 同步更新 Redis token:{token} Hash（昵称/头像/简介/标签）
  → 删除 user_info:{userId} 缓存
  → 查库返回最新 UserFormat
```

- Service：`UserManagementServiceImpl.updateUserProfile()`
- 昵称：3-16 位，敏感词检测（`SensitiveWordBs.contains()`）
- 简介：≤512 字符，敏感词检测
- 标签：≤5 个，每个 ≤20 字符
- `userMapper.updateById()` 只更新非空字段
- 同步更新 Redis Token Hash（从 `RequestContextHolder` 取当前请求 Token），保证 `/api/auth/me` 返回最新数据

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "用户信息已更新",
  "message": null
}
```

**响应示例（昵称包含敏感词）**：
```json
{
  "success": false,
  "data": null,
  "message": "用户名包含违规内容，请更换"
}
```

### GET /api/user/{userId}/profile — 查看用户资料

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Path Variable）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | Long | 是 | 目标用户 ID |

**响应**：`Result.ok(UserVO)`（脱敏用户信息，含好友状态）

**流程图**：
```
GET /api/user/{userId}/profile
  → 查看自己？
      是 → 直接查库返回完整信息（不走缓存）
      否 ↓
  → 查 Redis user_info:{userId}
      命中且 __empty__=1 → 返回"用户不存在"
      命中且正常 → 从缓存构建 User 对象 ↓
      未命中 → 查 t_user
               不存在 → 写 __empty__ 占位（TTL 1min）→ 返回失败
               存在 → 回填 user_info 缓存 ↓
  → isBlacklisted(currentUserId, userId) 检查自己是否拉黑对方
      是 → 返回"用户不存在"
  → isBlacklisted(userId, currentUserId) 检查对方是否拉黑自己
      是 → 返回"用户不存在"
      否 ↓
  → 检查隐私设置 viewInfo
      viewInfo=2 → 查双方 t_team_member 是否有共同团队
                   无共同团队 → 返回"无权查看"
      viewInfo=1 ↓
  → buildSearchUserResponse() 返回脱敏信息
```

- 查看自己：返回完整信息（含 `privacySetting`）
- 双向拉黑检查：`isBlacklisted(currentUserId, userId)`（自己拉黑对方）和 `isBlacklisted(userId, currentUserId)`（对方拉黑自己），任一为真均返回"用户不存在"
- `viewInfo=2`：查询双方 `t_team_member` 判断是否有共同团队
- 返回脱敏 `buildSearchUserResponse()`（含好友状态）

**响应示例（查看他人资料）**：
```json
{
  "success": true,
  "data": {
    "id": 1002,
    "userAccount": "9876543210",
    "userNickname": "李四",
    "userAvatar": "https://example.com/avatar/1002.jpg",
    "userTags": "游戏,音乐",
    "userIntro": "大家好",
    "friendStatus": 1
  },
  "message": null
}
```

**响应示例（被对方拉黑）**：
```json
{
  "success": false,
  "data": null,
  "message": "用户不存在"
}
```

---

## 三、隐私设置

### GET /api/user/privacy

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**：无（读取当前登录用户隐私设置）

**响应**：`Result.ok(PrivacySettingDTO)`

**流程图**：
```
GET /api/user/privacy
  → UserHolder.getUserId() 获取当前用户
  → 查 t_user.privacy_setting（JSON 字段）
  → JSON.parseObject → PrivacySettingDTO
  → privacy_setting 为空 → 返回默认值 {viewInfo:1, sendMsg:1, searchByEmail:0}
  → 返回 Result.ok(PrivacySettingDTO)
```

- `JSON.parseObject(user.getPrivacySetting(), PrivacySettingDTO.class)`
- 设置为空时返回默认：`{viewInfo:1, sendMsg:1, searchByEmail:0}`

**响应示例**：
```json
{
  "success": true,
  "data": {
    "viewInfo": 1,
    "sendMsg": 1,
    "searchByEmail": 0
  },
  "message": null
}
```

### POST /api/user/privacy/update

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `viewInfo` | Integer | 否 | 资料可见性：1-所有人，2-仅团队成员 |
| `sendMsg` | Integer | 否 | 私聊权限：1-所有人，2-团队成员，3-好友 |
| `searchByEmail` | Integer | 否 | 邮箱搜索：0-禁止，1-允许 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/user/privacy/update
  → 枚举值校验（viewInfo:1-2, sendMsg:1-3, searchByEmail:0-1）
  → 查 t_user.privacy_setting（当前设置）
  → 合并非空字段到当前设置
  → JSON.toJSONString() 序列化
  → 写回 t_user.privacy_setting
  → 返回 Result.ok()
```

- 枚举值校验：`viewInfo` 1-2，`sendMsg` 1-3，`searchByEmail` 0-1
- 读取当前设置 → 合并非空字段 → `JSON.toJSONString()` 写回 `t_user.privacy_setting`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "隐私设置已更新",
  "message": null
}
```

**响应示例（参数无效）**：
```json
{
  "success": false,
  "data": null,
  "message": "viewInfo 值无效"
}
```

**隐私字段枚举含义**：

| 字段 | 值 | 含义 |
|---|---|---|
| `viewInfo` | 1 | 所有人可查看资料 |
| `viewInfo` | 2 | 仅团队成员可查看资料 |
| `sendMsg` | 1 | 所有人可发起私聊 |
| `sendMsg` | 2 | 仅团队成员可发起私聊 |
| `sendMsg` | 3 | 需对方同意（好友）才能发私聊 |
| `searchByEmail` | 0 | 不允许通过邮箱搜索到自己 |
| `searchByEmail` | 1 | 允许通过邮箱搜索到自己 |

---

## 四、好友关系管理

### POST /api/friend/add

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `friendId` | Long | 是 | 目标用户 ID |
| `applyMsg` | String | 否 | 申请附言（可选备注）|

**响应**：`Result.ok()`

**流程图**：
```
POST /api/friend/add
  → 参数校验（friendId 非空，不能添加自己）
  → 检查目标用户是否存在（is_delete=0）
  → 检查是否已是好友（friend_status=1）→ 是则返回失败
  → 检查是否被对方拉黑（t_user_blacklist.is_delete=0）→ 是则返回失败
  → 检查对方隐私 sendMsg=3 时，是否已有待验证申请（friend_status=0）→ 有则返回失败
  → 插入 t_user_friend（friend_status=0，friend_remark=applyMsg）
  → 虚拟线程异步：
      查询当前用户昵称
      sendRealtimeSystemNotice()（noticeType=1，通知对方）
      WebSocket 实时推送
  → 返回 Result.ok()
```

- 检查是否已是好友（`friend_status=1`）
- 检查被对方拉黑（`is_delete=0`）
- 检查对方 `sendMsg` 隐私（`send_msg=3` 时检查是否有待验证申请）
- 插入 `t_user_friend`（`friend_status=0`）
- `sendRealtimeSystemNotice()` 发送通知（`noticeType=1`）+ WebSocket 实时推送

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "好友申请已发送",
  "message": null
}
```

**响应示例（已是好友）**：
```json
{
  "success": false,
  "data": null,
  "message": "已是好友关系"
}
```

### POST /api/friend/agree

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `friendId` | Long | 是 | 申请人用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/friend/agree
  → 查询申请记录（user_id=friendId, friend_id=currentUserId, friend_status=0）
      不存在 → 返回失败"申请记录不存在"
      存在 ↓
  → 更新原记录 friend_status=1，agree_time=now()
  → 插入反向记录（userId=currentUserId, friendId=friendId, friend_status=1）
  → 清除双方好友列表缓存（friend_list:{userId}，friend_list:{friendId}）
  → 虚拟线程异步：
      sendRealtimeSystemNotice()（noticeType=3，通知申请者"申请已通过"）
      WebSocket 实时推送
  → 返回 Result.ok()
```

- 更新原记录 `friend_status=1`，`agree_time=now()`
- 插入反向记录（userId/friendId 互换）
- 清除双方好友列表缓存（`FRIEND_LIST_CACHE_KEY`）
- 发送通知（`noticeType=3`）给申请者

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已同意好友申请",
  "message": null
}
```

**响应示例（申请不存在）**：
```json
{
  "success": false,
  "data": null,
  "message": "申请记录不存在"
}
```

### POST /api/friend/reject

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `friendId` | Long | 是 | 申请人用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/friend/reject
  → 查询申请记录（user_id=friendId, friend_id=currentUserId, friend_status=0）
      不存在 → 返回失败"申请记录不存在"
      存在 ↓
  → 更新 friend_status=2
  → 虚拟线程异步：
      sendRealtimeSystemNotice()（noticeType=2，通知申请者"申请被拒绝"）
      WebSocket 实时推送
  → 返回 Result.ok()
```

- 更新 `friend_status=2`
- 发送通知（`noticeType=2`）给申请者

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已拒绝好友申请",
  "message": null
}
```

### POST /api/friend/delete

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `friendId` | Long | 是 | 要删除的好友用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/friend/delete
  → 参数校验（friendId 非空）
  → 验证好友关系存在（friend_status=1）
  → 物理删除双向记录（userId→friendId 和 friendId→userId）
  → 清除双方好友列表缓存（friend_list:{userId}，friend_list:{friendId}）
  → 返回 Result.ok()
```

- 物理删除双向记录
- 清除双方好友列表缓存

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "好友已删除",
  "message": null
}
```

### GET /api/friend/list

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page<FriendVO>)`

**流程图**：
```
GET /api/friend/list
  → 查 Redis friend_list:{userId}（Set）
      命中 ↓
      过滤占位符"0" → 取出好友 ID 列表
      手动分页（subList）
      批量查 t_user（selectBatchIds）
      批量查 t_user_friend（备注 + 同意时间）
      构建并返回 FriendVO 列表
      未命中 ↓
      查 t_user_friend（friend_status=1, user_id=当前用户）
      无结果 → 写占位符"0"（TTL 5min）→ 返回空列表
      有结果 → 写 Set friend_list:{userId}（TTL 24h±2h）
             → 手动分页 → 批量查用户详情 → 返回
```

- Redis Set 缓存（`FRIEND_LIST_CACHE_KEY + userId`，TTL 24h）
- 命中：从 Set 取 ID → 手动分页 → 查用户详情
- 未命中：查库 → 写缓存
- 返回：`friendId / friendRemark / userNickname / userAvatar / userIntro / agreeTime`

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "friendId": 1002,
      "friendRemark": "小李",
      "userNickname": "李四",
      "userAvatar": "https://example.com/avatar/1002.jpg",
      "userIntro": "大家好",
      "agreeTime": "2026-03-01T10:00:00"
    }
  ],
  "total": 1,
  "message": null
}
```

### GET /api/friend/requests

> 文件：`FriendManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page<FriendRequestVO>)`（含申请者昵称、头像、申请备注）

**流程图**：
```
GET /api/friend/requests
  → 分页查 t_user_friend
      WHERE friend_id=当前用户 AND friend_status=0
      ORDER BY create_time DESC
  → 批量查申请者用户信息（selectBatchIds）
  → 构建返回：applicantId/userNickname/userAvatar/userIntro/userTags/applyMsg/createTime
  → 返回 Result.ok(requestList, total)
```

- 查 `t_user_friend`（`friend_id=当前用户, friend_status=0`）
- 返回申请者昵称、头像、申请备注

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "requestId": 5001,
      "applicantId": 1003,
      "userNickname": "王五",
      "userAvatar": "https://example.com/avatar/1003.jpg",
      "userIntro": "你好",
      "userTags": "游戏",
      "applyMsg": "我们在同一个团队，加个好友吧",
      "createTime": "2026-03-24T09:30:00"
    }
  ],
  "total": 1,
  "message": null
}
```

---

## 五、黑名单管理

### POST /api/blacklist/add

> 文件：`BlacklistManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `blackUserId` | Long | 是 | 要拉黑的用户 ID |
| `blackReason` | String | 否 | 拉黑原因 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/blacklist/add
  → 参数校验（blackUserId 非空，不能拉黑自己）
  → 检查是否已拉黑（is_delete=0）→ 是则返回"已拉黑"
  → 插入 t_user_blacklist（is_delete=0）
  → 物理删除双向好友关系记录（t_user_friend）
  → 清除当前用户好友缓存（friend_list:{userId}）
  → 清除对方好友缓存（friend_list:{blackUserId}）
  → 清除当前用户黑名单缓存（blacklist:{userId}）
  → 返回 Result.ok()
```

- 检查已拉黑防重复，插入 `t_user_blacklist`
- 物理删除双向好友关系
- 清除好友缓存（双方）+ 黑名单缓存（当前用户）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已将该用户加入黑名单",
  "message": null
}
```

**响应示例（已拉黑）**：
```json
{
  "success": false,
  "data": null,
  "message": "已将该用户加入黑名单"
}
```

### POST /api/blacklist/remove

> 文件：`BlacklistManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `blackUserId` | Long | 是 | 要解除拉黑的用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/blacklist/remove
  → 查询拉黑记录（user_id=当前用户, black_user_id=blackUserId, is_delete=0）
      不存在 → 返回失败"拉黑记录不存在"
      存在 ↓
  → 更新 is_delete=1（软删除，保留历史）
  → 删除黑名单缓存（blacklist:{userId}）
  → 返回 Result.ok()
```

- 更新 `is_delete=1`，清除黑名单缓存

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已解除拉黑",
  "message": null
}
```

**响应示例（记录不存在）**：
```json
{
  "success": false,
  "data": null,
  "message": "拉黑记录不存在"
}
```

### GET /api/blacklist

> 文件：`BlacklistManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page<BlacklistVO>)`

**流程图**：
```
GET /api/blacklist
  → 分页查 t_user_blacklist
      WHERE user_id=当前用户 AND is_delete=0
      ORDER BY create_time DESC
  → 批量查被拉黑用户信息（selectBatchIds）
  → 构建返回：id/blackUserId/userNickname/userAvatar/blackReason/createTime
  → 返回 Result.ok(blacklistData, total)
```

- 分页查 `t_user_blacklist`（`is_delete=0`），关联被拉黑用户信息

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": 2001,
      "blackUserId": 1005,
      "userNickname": "恶意用户",
      "userAvatar": "https://example.com/avatar/1005.jpg",
      "blackReason": "骚扰消息",
      "createTime": "2026-03-20T15:00:00"
    }
  ],
  "total": 1,
  "message": null
}
```

### isBlacklisted()（内部）

**流程图**：
```
isBlacklisted(userId, targetId)
  → 查 Redis blacklist:{userId}（Set）
      命中 → 判断 Set 是否包含 targetId
             包含占位符"0"且仅有占位符 → 空黑名单 → 返回 false
             包含 targetId → 返回 true
             不包含 → 返回 false
      未命中 → 加分布式锁（setIfAbsent）
               查 t_user_blacklist（user_id=userId, is_delete=0）全量
               空黑名单 → 写占位符"0"（TTL 5min）
               非空 → 写 Set（TTL 24h±2h）
               释放锁
               判断 targetId 是否在结果中并返回
```

- Redis Set 缓存（`BLACKLIST_CACHE_KEY + userId`，TTL 24h）
- 缓存不存在时加载全量黑名单；空黑名单写占位符 `"0"` 防缓存穿透

---

## 六、用户搜索与列表

### GET /api/user/search

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | String | 是 | 搜索类型：`account`/`nickname`/`tag`/`email` |
| `keyword` | String | 是 | 搜索关键词 |
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page<UserVO>)`

**流程图**：
```
GET /api/user/search
  → 参数校验（keyword 非空，type 合法）
  → 构建查询条件：is_delete=0
      type=account → LIKE user_account
      type=nickname → LIKE user_nickname
      type=tag → LIKE user_tags
      type=email → LIKE user_email
      其他 → 返回"搜索类型无效"
  → 分页查 t_user（MySQL LIKE 查询）
  → 流式过滤链：
      filter: 排除自己（id != currentUserId）
      filter: 排除自己拉黑的用户（isBlacklisted(currentUserId, user.getId())）
      filter: 排除把自己拉黑的用户（isBlacklisted(user.getId(), currentUserId)）
      filter: canViewUserInfo()（隐私可见性过滤）
      filter: email 搜索开关（canSearchByEmail()）
      peek: cacheUserInfo()（搜索命中用户回写 user_info:{userId} 缓存）
      map: buildSearchUserResponse()（构建脱敏响应）
  → 返回 Result.ok(searchResult, total)
```

- 支持 `type`：`account`（账号模糊）/ `nickname`（昵称模糊）/ `tag`（标签模糊）/ `email`（邮箱，受 `searchByEmail` 隐私开关控制）
- 过滤链：排除自己 → 排除被拉黑 → `canViewUserInfo()` 资料可见性 → email 搜索开关
- `canViewUserInfo()`：`viewInfo=2` 时查双方 TeamMember 判断共同团队

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1002,
      "userAccount": "9876543210",
      "userNickname": "李四",
      "userAvatar": "https://example.com/avatar/1002.jpg",
      "userTags": "游戏,音乐",
      "userIntro": "大家好",
      "friendStatus": 0
    }
  ],
  "total": 1,
  "message": null
}
```

### GET /api/user/list

> 文件：`UserManagementController.java` → `UserManagementServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |
| `sortBy` | String | 否 | 排序字段：`createTime`/`id`，默认 `id` |

**响应**：`Result.ok(Page<UserVO>)`

- 分页查全量用户，支持 `createTime` / `id` 排序
- 同搜索过滤链

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1002,
      "userNickname": "李四",
      "userAvatar": "https://example.com/avatar/1002.jpg",
      "userTags": "游戏",
      "userIntro": "大家好",
      "friendStatus": 1
    }
  ],
  "total": 50,
  "message": null
}
```

---

## 七、在线状态管理

### POST /api/online/status

> 文件：`OnlineStatusController.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | Integer | 是 | 状态值：1-在线，2-离开，3-忙碌，4-隐身 |
| `expireMinutes` | Integer | 否 | 在线状态有效时长（分钟），默认30 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/online/status
  → 参数校验（status 值 1-4）
  → HSET user_online {userId} {status}
  → ZADD user_online_ttl {now + expireMinutes * 60 * 1000} {userId}
  → 返回 Result.ok()
```

- 状态值：1-在线，2-离开，3-忙碌，4-隐身
- 写入 Redis Hash `user_online`（field=userId, value=status）
- 写入 ZSet `user_online_ttl`（score=到期时间戳）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "在线状态已更新",
  "message": null
}
```

### POST /api/online/heartbeat

> 文件：`OnlineStatusController.java`

**请求参数**：无

**响应**：`Result.ok()`

**流程图**：
```
POST /api/online/heartbeat
  → 查 Redis Hash user_online 中是否有 userId
      不存在 → HSET user_online {userId} 1（补写在线状态）
      存在 ↓
  → ZADD user_online_ttl {now + 默认超时} {userId}（刷新到期时间）
  → 返回 Result.ok()
```

- 刷新 ZSet score（延长到期时间）
- 若 Hash 中不存在则补写在线状态

**响应示例**：
```json
{
  "success": true,
  "data": null,
  "message": null
}
```

### GET /api/online/status?userId=xxx

> 文件：`OnlineStatusController.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | Long | 是 | 目标用户 ID |

**响应**：`Result.ok(Map{status})`（0-离线，1-在线，2-离开，3-忙碌，4-隐身）

**流程图**：
```
GET /api/online/status?userId=xxx
  → HGET user_online {userId}
      存在 → 返回 {status: value}
      不存在 → 返回 {status: 0}（离线）
```

- 从 `user_online` Hash 读取状态，不存在返回 `status=0`（离线）

**响应示例（在线）**：
```json
{
  "success": true,
  "data": {
    "userId": 1001,
    "status": 1
  },
  "message": null
}
```

**响应示例（离线）**：
```json
{
  "success": true,
  "data": {
    "userId": 1002,
    "status": 0
  },
  "message": null
}
```

### POST /api/online/offline

> 文件：`OnlineStatusController.java`

**请求参数**：无

**响应**：`Result.ok()`

**流程图**：
```
POST /api/online/offline
  → HDEL user_online {userId}
  → ZREM user_online_ttl {userId}
  → 返回 Result.ok()
```

- 同时删除 Hash field 和 ZSet member

**响应示例**：
```json
{
  "success": true,
  "data": "已下线",
  "message": null
}
```

### MuteExpireScheduler — 超时清理

- 每分钟执行 `cleanExpiredOnlineStatus()`
- `ZSet.rangeByScore(user_online_ttl, 0, now)` 找过期用户
- 成对清理：`HDEL user_online` + `ZREM user_online_ttl`

> **注意**：在线状态超时清理与登录 Token 过期是两套独立机制。Token 通过 Redis `EXPIRE` 自动过期（2小时滑动续期）；在线状态通过 ZSet 记录到期时间戳 + 定时任务主动清理，两者互不干扰。

---

## 八、系统通知

通知接口由 `UserManagementServiceImpl` 实现，Controller 在 `UserManagementController`。

| 接口 | 说明 |
|---|---|
| `GET /api/notice/unread-count` | 查 `t_system_notice`（`is_read=0, is_delete=0`）的 count |
| `GET /api/notice/list` | 分页查，支持 `isRead` 过滤，按 `create_time` 倒序 |
| `POST /api/notice/read` | 批量 `LambdaUpdateWrapper` 置 `is_read=1`，记录 `read_time` |
| `POST /api/notice/delete` | 批量软删除（`is_delete=1`） |

**流程图（GET /api/notice/unread-count）**：
```
GET /api/notice/unread-count
  → SELECT COUNT(*) FROM t_system_notice
      WHERE user_id=当前用户 AND is_read=0 AND is_delete=0
  → 返回 Result.ok(unreadCount)
```

**流程图（GET /api/notice/list）**：
```
GET /api/notice/list?page=&pageSize=&isRead=
  → 构建分页查询条件：user_id=当前用户, is_delete=0
  → isRead 非空 → 追加 is_read={isRead} 过滤
  → ORDER BY create_time DESC
  → 返回 Result.ok(noticeList, total)
```

**流程图（POST /api/notice/read）**：
```
POST /api/notice/read
  → 参数校验（noticeIds 非空）
  → LambdaUpdateWrapper:
      WHERE user_id=当前用户 AND id IN (noticeIds)
      SET is_read=1, read_time=now()
  → 返回 Result.ok()
```

**流程图（POST /api/notice/delete）**：
```
POST /api/notice/delete
  → 参数校验（noticeIds 非空）
  → LambdaUpdateWrapper:
      WHERE user_id=当前用户 AND id IN (noticeIds)
      SET is_delete=1
  → 返回 Result.ok()
```

**响应示例（GET /api/notice/unread-count）**：
```json
{
  "success": true,
  "data": 5,
  "message": null
}
```

**响应示例（GET /api/notice/list）**：
```json
{
  "success": true,
  "data": [
    {
      "id": 3001,
      "noticeType": 1,
      "noticeContent": "用户王五申请添加你为好友",
      "relatedId": 1003,
      "isRead": 0,
      "createTime": "2026-03-24T09:30:00"
    }
  ],
  "total": 5,
  "message": null
}
```

**响应示例（POST /api/notice/read）**：
```json
{
  "success": true,
  "data": "已标记为已读",
  "message": null
}
```

**响应示例（POST /api/notice/delete）**：
```json
{
  "success": true,
  "data": "通知已删除",
  "message": null
}
```

### sendRealtimeSystemNotice()（内部）

- 入库 `systemNoticeMapper.insert()`
- `ChatWebSocketHandler.sendToUser()` WebSocket 实时推送
- 推送格式：`{type:"system_notice", data:{noticeId, noticeType, noticeContent, relatedId}}`

### 通知类型（noticeType）

| 值 | 含义 | 触发来源 |
|---|---|---|
| 1 | 好友申请 | `addFriend()` |
| 2 | 好友申请拒绝 | `rejectFriend()` |
| 3 | 好友申请通过 / 入群审批通过 | `agreeFriend()` / `auditApply()` |
| 4 | 被移除团队 | `removeMember()` |
| 5 | 被邀请入团队 | `inviteMember()` |
| 6 | 处罚通知 | `punishUser()` |
| 7 | 处罚撤销通知 / AI 举报审核结果通知 / 管理员举报处理结果通知 | `cancelPunish()` / `reportMsg()` / `adminHandleMsgReport()` |
| 8 | 反馈回复通知 | `handleFeedback()` |
| 9 | @提醒通知 | `sendTeamMsg()`（msgType=5 时）|

> **注意**：noticeType=7 同时复用于三种场景（撤销处罚、AI审核结果、管理员举报处理），前端可通过 `noticeContent` 内容区分具体场景。

### WebSocket 推送格式（system_notice）

所有系统通知统一使用以下格式通过 WebSocket 实时推送：

```json
{
  "type": "system_notice",
  "data": {
    "noticeId": 1001,
    "noticeType": 1,
    "noticeContent": "用户张三申请添加你为好友",
    "relatedId": 1002
  }
}
```

用户离线时不会丢失通知（已写入 `t_system_notice` 表），下次登录后通过 `GET /api/notice/list` 查询。

---

## 九、Redis Key 汇总

| Key 模板 | 类型 | TTL | 用途 |
|---|---|---|---|
| `friend_list:{userId}` | Set | 24h ± 2h 随机 | 好友 ID 列表缓存（空列表写占位符 `"0"`，TTL 5min）|
| `blacklist:{userId}` | Set | 24h ± 2h 随机 | 黑名单 ID 列表缓存（空列表写占位符 `"0"`，TTL 5min）|
| `user_info:{userId}` | Hash | 5min ± 1min 随机 | 用户基础信息缓存，不存在用户写 `__empty__=1` 占位 |
| `user_online` | Hash | — | 在线状态（userId→status）|
| `user_online_ttl` | ZSet | — | 在线状态到期时间 |

---

## 十、异步操作说明

本模块中所有系统通知发送均通过**虚拟线程**（`Thread.ofVirtual().start()`）异步执行，不阻塞主事务流程。

### 异步化原则

- **主事务内执行**：数据写库（好友关系、黑名单、Redis 缓存清除）等需要事务保证的操作**同步执行**
- **虚拟线程内执行**：系统通知构建、`sendRealtimeSystemNotice()`（写 `t_system_notice` + WebSocket 推送）**异步执行**
- **昵称等字符串**：在启动虚拟线程前于主线程中提前查询并捕获，避免虚拟线程内持有 MyBatis SqlSession 引用

### 各方法异步点汇总

| 方法 | 异步内容 | 说明 |
|---|---|---|
| `addFriend()` | 好友申请通知（noticeType=1）| 申请记录写库后异步推送 |
| `agreeFriend()` | 好友通过通知（noticeType=3）| 缓存清除同步，通知异步 |
| `rejectFriend()` | 好友拒绝通知（noticeType=2）| 状态更新同步，通知异步 |

### 通知丢失保障

通知先写入 `t_system_notice` 表再推送 WebSocket，用户离线时 WebSocket 推送静默跳过，通知不丢失。用户上线后通过 `GET /api/notice/list` 拉取历史通知。

---

## 十一、用户信息缓存说明

### 缓存 Key：`user_info:{userId}`

类型为 Redis Hash，存储用户基础信息供搜索和资料查看复用，避免重复查库。

| 场景 | 行为 |
|---|---|
| `searchUser()` 返回结果 | `.peek(cacheUserInfo)` 将过滤后的命中用户批量回写缓存 |
| `getUserProfile()` 查看他人资料 | 优先读缓存，命中直接返回；未命中查库后回填 |
| `updateUserProfile()` 修改资料 | 主动删除 `user_info:{userId}`，保证一致性 |
| 查询不存在用户 | 写 `__empty__=1` 占位符，TTL 1 分钟，防穿透 |

**TTL**：5 分钟 ± 随机1分钟抖动，防雪崩。

**注意**：查看自己的资料（`getUserProfile` 传入自己的 userId）时直接查库返回完整信息（含隐私字段），不走缓存。
