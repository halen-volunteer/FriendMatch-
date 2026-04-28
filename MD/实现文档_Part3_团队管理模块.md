# FriendMatch 实现文档 - Part 3：团队管理模块

> 版本：V1.0 | 日期：2026-03-24

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `TeamController.java` |
| Service | `TeamService.java` / `TeamServiceImpl.java` |
| Mapper | `TeamMapper.java` / `TeamMemberMapper.java` / `TeamApplyMapper.java` / `SystemNoticeMapper.java` |
| Model | `Team.java` / `TeamMember.java` / `TeamApply.java` |
| DTO | `TeamCreateDTO` / `TeamUpdateDTO` / `TeamApplyDTO` / `TeamAuditDTO` / `TeamMuteDTO` / `TeamMemberOperationDTO` / `TeamInviteDTO` / `TeamJoinByPasswordDTO` |

---

## 一点五、数据库设计

### t_team 表（团队基础信息表）

| 字段 | 说明 |
|---|---|
| `team_name` | 团队名称（1-64字符，允许重名）|
| `creator_id` | 创建人 ID |
| `max_member` | 最大成员数（1-1000，默认200）|
| `team_type` | 1-公开，2-私有 |
| `join_rule` | 1-申请审批，2-仅邀请，3-密码加入 |
| `join_password` | BCrypt 加密的加入密码（仅 join_rule=3 有效）|
| `team_all_mute` | 全员禁言标记（0-正常，1-禁言）|
| `is_delete` | 软删除标记 |

**关键索引**：`idx_creator_id`、`idx_team_type`、`idx_team_name`

### t_team_member 表（团队成员表）

| 字段 | 说明 |
|---|---|
| `team_id` / `user_id` | 团队 ID / 用户 ID |
| `role_type` | 当前实现使用 1-队长，2-管理员，3-普通成员；4-嘉宾仅在 SQL 注释中预留 |
| `team_mute_type` | 团队内禁言状态（0-正常，1-禁言）|
| `team_mute_unpunish_time` | 禁言解除时间 |
| `join_time` / `quit_time` | 加入/退出时间 |
| `is_quit` | 是否已退出（0-否，1-是）|

**关键索引**：`uk_team_user`（team_id, user_id, is_quit）、`idx_user_id`、`idx_role_type`、`idx_team_mute`

### t_team_apply 表（加入申请表）

| 字段 | 说明 |
|---|---|
| `team_id` / `apply_user_id` | 团队 ID / 申请人 ID |
| `audit_user_id` | 审核人 ID（队长/管理员）|
| `apply_msg` | 申请备注（可选）|
| `audit_status` | 0-待审核，1-通过，2-拒绝 |
| `audit_msg` | 审核备注（可选）|

**关键索引**：`idx_team_audit`（team_id, audit_status）、`idx_apply_user`

---

## 二、团队创建与查询

### POST /api/team/create

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamName` | String | 是 | 团队名称，1-64字符 |
| `teamDesc` | String | 否 | 团队简介，≤512字符 |
| `teamTags` | List\<String\> | 否 | 标签列表，≤5个，每个≤20字符 |
| `teamType` | Integer | 是 | 1-公开，2-私有 |
| `joinRule` | Integer | 是 | 1-申请审批，2-仅邀请，3-密码加入 |
| `joinPassword` | String | 条件必填 | `joinRule=3` 时必填，加入密码 |
| `maxMember` | Integer | 否 | 最大成员数，1-1000，默认200 |

**响应**：`Result.ok(teamId)`

**流程图**：
```
POST /api/team/create
  → 参数校验（teamName 1-64字符，teamDesc ≤512字符，标签≤5个每个≤20字符，maxMember 1-1000）
  → 敏感词检测（teamName / teamDesc）
  → joinRule=3 → joinPassword 必填 → BCrypt 加密密码
  → 插入 t_team
  → 插入 t_team_member（userId=创建者, role_type=1 队长）
  → 返回 Result.ok(teamId)
```

- 校验：名称 1-64 字符，简介 ≤512，标签 ≤5 个（每个 ≤20），最大成员数 1-1000
- 敏感词检测（团队名、简介）
- `joinRule=3` 时 BCrypt 加密密码
- 插入 `t_team` + 插入 `t_team_member`（`role_type=1`，创建者为队长）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": 1001,
  "message": null
}
```

**响应示例（团队名含敏感词）**：
```json
{
  "success": false,
  "data": null,
  "message": "团队名称包含敏感词"
}
```

### GET /api/team/list

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamType` | Integer | 否 | 1-公开，2-私有，不传查全部 |
| `sortBy` | String | 否 | 排序字段：`createTime`/`id`，默认 `id` |
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page\<TeamVO\>)`（含成员数统计）

**流程图**：
```
GET /api/team/list
  → 构建查询条件：is_delete=0
  → teamType 非空 → 追加 team_type={teamType} 过滤
  → sortBy=createTime → ORDER BY create_time DESC
  → 否则 ORDER BY id DESC
  → 分页查 t_team
  → 每条附加成员数（SELECT COUNT FROM t_team_member WHERE is_quit=0）
  → 返回 Result.ok(teamList, total)
```

- 按 `is_delete=0`、`team_type` 筛选，支持 `createTime`/`id` 排序
- 分页查询，附带成员数统计

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "teamName": "Java学习小组",
      "teamAvatar": "https://example.com/team/1001.jpg",
      "teamIntro": "一起学习Java",
      "teamTags": "Java,编程",
      "creatorId": 1001,
      "maxMember": 200,
      "teamType": 1,
      "joinRule": 1,
      "teamAllMute": 0,
      "memberCount": 15,
      "isMember": 1
    }
  ],
  "total": 1,
  "message": null
}
```

### GET /api/team/search

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | String | 是 | 搜索类型：`name`-按名称，`tag`-按标签 |
| `keyword` | String | 是 | 搜索关键词 |
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page\<TeamVO\>)`

**流程图**：
```
GET /api/team/search
  → 参数校验（keyword 非空，type 合法）
  → 构建查询：is_delete=0
      type=name → LIKE team_name
      type=tag  → LIKE team_tags
      其他 → 返回"搜索类型无效"
  → 分页查 t_team
  → 返回 Result.ok(teamList, total)
```

- `type=name`：`LIKE team_name`；`type=tag`：`LIKE team_tags`

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "teamName": "Java学习小组",
      "teamTags": "Java,编程",
      "memberCount": 15,
      "isMember": 0
    }
  ],
  "total": 1,
  "message": null
}
```

**响应示例（关键词为空）**：
```json
{
  "success": false,
  "data": null,
  "message": "搜索关键词不能为空"
}
```

### GET /api/team/{teamId}

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Path Variable）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |

**响应**：`Result.ok(TeamDetailVO)`（含成员数、当前用户是否为成员）

**流程图**：
```
GET /api/team/{teamId}
  → 查 t_team（is_delete=0）
      不存在 → 返回失败"团队不存在"
      存在 ↓
  → 查 t_team_member 统计成员数（is_quit=0）
  → 查 t_team_member 判断当前用户是否为成员（is_quit=0）
  → 返回 Result.ok(TeamDetailVO)
```

- 查 `t_team` + 成员数 + 当前用户是否为成员

**响应示例**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "teamName": "Java学习小组",
    "teamAvatar": "https://example.com/team/1001.jpg",
    "teamIntro": "一起学习Java",
    "teamTags": "Java,编程",
    "creatorId": 1001,
    "maxMember": 200,
    "teamType": 1,
    "joinRule": 1,
    "teamAllMute": 0,
    "memberCount": 15,
    "isMember": 1,
    "roleType": 3
  },
  "message": null
}
```

**响应示例（非成员）**：
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "teamName": "Java学习小组",
    "memberCount": 15,
    "isMember": 0,
    "roleType": -1
  },
  "message": null
}
```

### POST /api/team/update

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `teamName` | String | 否 | 新团队名称，1-64字符 |
| `teamDesc` | String | 否 | 新团队简介，≤512字符 |
| `teamTags` | List\<String\> | 否 | 新标签列表 |
| `teamType` | Integer | 否 | 1-公开，2-私有 |
| `joinRule` | Integer | 否 | 1-申请审批，2-仅邀请，3-密码加入 |
| `joinPassword` | String | 否 | 修改加入密码（`joinRule=3` 时生效）|
| `maxMember` | Integer | 否 | 最大成员数 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/update
  → 校验当前用户为队长（role_type=1）
  → 参数校验（teamName/teamDesc/标签格式校验）
  → 敏感词检测（teamName / teamDesc）
  → joinRule=3 且 joinPassword 非空 → BCrypt 加密新密码
  → 更新 t_team（只更新非空字段）
  → 虚拟线程异步：通知所有成员（noticeType=3）
  → 返回 Result.ok()
```

- 权限：队长（`role_type=1`）
- 敏感词检测，更新 `t_team`，通知所有成员

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "团队信息已更新",
  "message": null
}
```

**响应示例（无权限）**：
```json
{
  "success": false,
  "data": null,
  "message": "仅队长可编辑团队信息"
}
```

---

## 三、加入团队

### POST /api/team/apply — 申请加入

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 目标团队 ID |
| `applyMsg` | String | 否 | 申请备注 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/apply
  → 参数校验（teamId 非空）
  → 查 t_team（is_delete=0）
      不存在 → 返回失败
      存在 ↓
  → 检查是否已是成员（is_quit=0）→ 是则返回"已是团队成员"
  → 检查是否有待审核申请（audit_status=0）→ 有则返回"已有待审核申请"
  → 检查团队是否已满员（当前成员数 >= max_member）→ 满则返回失败
  → 插入 t_team_apply（audit_status=0）
  → 虚拟线程异步：通知所有队长/管理员（noticeType=3）
  → 返回 Result.ok()
```

- 检查团队存在、未是成员（`is_quit=0`）、未有待审核申请、未满员
- 插入 `t_team_apply`（`audit_status=0`）
- 通知所有管理员/队长

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "申请已提交，请等待审核",
  "message": null
}
```

**响应示例（已是成员）**：
```json
{
  "success": false,
  "data": null,
  "message": "已经是团队成员了"
}
```

### POST /api/team/join-by-password — 密码加入

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 目标团队 ID |
| `joinPassword` | String | 是 | 加入密码 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/join-by-password
  → 查 t_team（is_delete=0）
      不存在 → 返回失败
      join_rule != 3 → 返回"该团队不支持密码加入"
      存在 ↓
  → BCryptPasswordEncoder.matches(joinPassword, team.joinPassword)
      不匹配 → 返回"密码错误"
      匹配 ↓
  → 检查是否已是成员（is_quit=0）→ 是则返回"已是团队成员"
  → 检查是否已满员 → 满则返回失败
  → 插入 t_team_member（role_type=3，普通成员）
  → 返回 Result.ok()
```

- 查团队 `join_rule=3`，BCrypt 比对密码
- 检查未是成员、未满员
- 直接插入 `t_team_member`（`role_type=3`）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "加入团队成功",
  "message": null
}
```

**响应示例（密码错误）**：
```json
{
  "success": false,
  "data": null,
  "message": "密码错误"
}
```

### POST /api/team/invite — 邀请加入

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `inviteUserId` | Long | 是 | 被邀请用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/invite
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 查 t_team（is_delete=0）是否存在
  → 检查目标用户是否已是成员（is_quit=0）→ 是则返回失败
  → 检查是否已满员 → 满则返回失败
  → 插入 t_team_member（role_type=3，普通成员）
  → 虚拟线程异步：通知被邀请用户（noticeType=5）
  → 返回 Result.ok()
```

- 权限：队长/管理员
- 检查目标用户非成员、未满员
- 直接插入 `t_team_member`（`role_type=3`）
- 通知被邀请用户

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "邀请成功",
  "message": null
}
```

**响应示例（无权限）**：
```json
{
  "success": false,
  "data": null,
  "message": "仅队长/管理员可邀请成员"
}
```

### POST /api/team/apply/audit — 审批申请

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `applyId` | Long | 是 | 申请记录 ID |
| `auditStatus` | Integer | 是 | 1-通过，2-拒绝 |
| `auditMsg` | String | 否 | 审核备注 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/apply/audit
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 查 t_team_apply（applyId）
      不存在 → 返回失败
      audit_status != 0 → 返回"申请已处理"
      存在 ↓
  → auditStatus=1（通过）：
      检查是否已满员 → 满则返回失败
      插入 t_team_member（role_type=3）
      更新 t_team_apply（audit_status=1, audit_user_id=当前用户）
  → auditStatus=2（拒绝）：
      更新 t_team_apply（audit_status=2）
  → 虚拟线程异步：通知申请者（通过→noticeType=3 / 拒绝→noticeType=4）
  → 返回 Result.ok()
```

- 权限：队长/管理员
- `auditStatus=1`：检查未满员 → 插入 `t_team_member` → 更新申请记录
- `auditStatus=2`：更新申请记录（`audit_status=2`）
- 通知申请者

**响应示例（通过）**：
```json
{
  "success": true,
  "data": "已通过申请",
  "message": null
}
```

**响应示例（拒绝）**：
```json
{
  "success": true,
  "data": "已拒绝申请",
  "message": null
}
```

### GET /api/team/apply/pending

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page\<TeamApplyVO\>)`

**流程图**：
```
GET /api/team/apply/pending
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 分页查 t_team_apply
      WHERE team_id=teamId AND audit_status=0
      ORDER BY create_time DESC
  → 关联查申请者用户信息
  → 返回 Result.ok(applyList, total)
```

- 分页查 `t_team_apply`（`audit_status=0`）

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "applyId": 6001,
      "applicantId": 1003,
      "userNickname": "王五",
      "userAvatar": "https://example.com/avatar/1003.jpg",
      "applyMsg": "我对Java很感兴趣，申请加入",
      "createTime": "2026-03-24T10:00:00"
    }
  ],
  "total": 1,
  "message": null
}
```

### POST /api/team/quit — 退出团队

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/quit
  → 查当前用户的 t_team_member（is_quit=0）
      不存在 → 返回"不在该团队中"
      存在 ↓
  → 是队长（role_type=1）→ 返回"队长不可退出，请先转让队长"
  → 更新 t_team_member（is_quit=1, quit_time=now()）
  → 返回 Result.ok()
```

- `TeamServiceImpl.quitTeam()`
- 队长不可直接退出（需先转让）
- 更新 `t_team_member`（`is_quit=1, quit_time=now()`）

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已退出团队",
  "message": null
}
```

**响应示例（队长不可退出）**：
```json
{
  "success": false,
  "data": null,
  "message": "队长不能直接退出，请先转让队长权限或解散团队"
}
```

---

## 四、成员管理

### GET /api/team/{teamId}/members

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Path Variable + Query）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID（路径参数）|
| `page` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**响应**：`Result.ok(Page\<TeamMemberVO\>)`

**流程图**：
```
GET /api/team/{teamId}/members
  → 分页查 t_team_member
      WHERE team_id=teamId AND is_quit=0
  → 批量关联查用户信息（selectBatchIds）
  → 返回 Result.ok(memberList, total)
```

- 分页查 `t_team_member`（`is_quit=0`），关联用户信息

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "userId": 1001,
      "roleType": 1,
      "userNickname": "张三",
      "userAvatar": "https://example.com/avatar/1001.jpg",
      "userIntro": "队长",
      "joinTime": "2026-03-01T08:00:00",
      "teamMuteType": 0,
      "teamMuteUnpunishTime": null
    },
    {
      "userId": 1002,
      "roleType": 3,
      "userNickname": "李四",
      "userAvatar": "https://example.com/avatar/1002.jpg",
      "userIntro": "普通成员",
      "joinTime": "2026-03-05T09:00:00",
      "teamMuteType": 0,
      "teamMuteUnpunishTime": null
    }
  ],
  "total": 15,
  "message": null
}
```

### POST /api/team/member/remove

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `removeUserId` | Long | 是 | 被移除成员的用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/member/remove
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 查目标用户 t_team_member（is_quit=0）
      不存在 → 返回"用户不在团队中"
      role_type=1（队长）→ 返回"不能移除队长"
      操作者为管理员（role_type=2）且目标也是管理员（role_type=2）→ 返回"管理员不能移除其他管理员"
      存在 ↓
  → 更新 t_team_member（is_quit=1, quit_time=now()）
  → 虚拟线程异步：通知被移除用户（noticeType=5）+ 清除禁言缓存
  → 返回 Result.ok()
```

- 权限：队长/管理员；不允许移除队长
- **管理员只能移除普通成员（role_type=3），不能移除其他管理员（role_type=2）**
- **队长可以移除所有人（管理员、普通成员）**
- 更新 `is_quit=1, quit_time=now()`
- 通知被移除用户

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已移除成员",
  "message": null
}
```

**响应示例（不可移除队长）**：
```json
{
  "success": false,
  "data": null,
  "message": "不能移除队长"
}
```

### POST /api/team/member/role/update

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `targetUserId` | Long | 是 | 目标成员用户 ID |
| `roleType` | Integer | 是 | 新角色：2-管理员，3-普通成员 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/member/role/update
  → 校验当前用户为队长（role_type=1）
  → 查目标用户 t_team_member（is_quit=0）
      不存在 → 返回失败
      目标是队长（role_type=1）→ 返回"不允许修改队长角色"
      存在 ↓
  → roleType 校验（2/3 之一）
  → 更新 t_team_member.role_type
  → 返回 Result.ok()
```

- 权限：队长；不允许修改队长角色
- 更新 `t_team_member.role_type`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "成员角色已更新",
  "message": null
}
```

### POST /api/team/transfer-leader

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `newLeaderId` | Long | 是 | 新队长用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/transfer-leader
  → 校验当前用户为队长（role_type=1）
  → 查目标用户 t_team_member（is_quit=0）
      不存在 → 返回"目标用户不在团队中"
      存在 ↓
  → 更新当前队长 role_type=2（管理员）
  → 更新目标用户 role_type=1（队长）
  → 虚拟线程异步：通知新队长（noticeType=3）
  → 返回 Result.ok()
```

- 权限：队长
- 当前队长 → `role_type=2`；目标用户 → `role_type=1`
- 通知新队长

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "队长权限已转让给【李四】",
  "message": null
}
```

### POST /api/team/dissolve

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/dissolve
  → 校验当前用户为队长（role_type=1）
  → 查 t_team（is_delete=0）是否存在
  → 更新 t_team.is_delete=1
  → 批量更新所有成员 t_team_member.is_quit=1（is_quit=0 的成员）
  → 虚拟线程异步：通知所有成员（noticeType=4）+ 清除全员禁言缓存
  → 返回 Result.ok()
```

- 权限：队长
- `t_team.is_delete=1`；所有成员 `is_quit=1`
- 通知所有成员

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "团队已解散",
  "message": null
}
```

**响应示例（无权限）**：
```json
{
  "success": false,
  "data": null,
  "message": "仅队长可解散团队"
}
```

---

## 五、禁言管理

### POST /api/team/mute-all

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `muteAll` | Integer | 是 | 0-关闭全员禁言，1-开启全员禁言 |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/mute-all
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → muteAll 值校验（0 或 1）
  → 更新 t_team.team_all_mute={muteAll}
  → 返回 Result.ok()
```

- 权限：队长/管理员
- 更新 `t_team.team_all_mute`（0/1）

**响应示例（开启全员禁言）**：
```json
{
  "success": true,
  "data": "已开启全员禁言",
  "message": null
}
```

**响应示例（解除全员禁言）**：
```json
{
  "success": true,
  "data": "已解除全员禁言",
  "message": null
}
```

### POST /api/team/member/mute

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `muteUserId` | Long | 是 | 被禁言成员用户 ID |
| `muteDuration` | Integer | 是 | 禁言时长（分钟）|

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/member/mute
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 查目标用户 t_team_member（is_quit=0）
      不存在 → 返回"用户不在团队中"
      目标是队长（role_type=1）→ 返回"不允许禁言队长"
      存在 ↓
  → 更新 t_team_member:
      team_mute_type=1
      team_mute_unpunish_time=now() + muteDuration 分钟
  → 返回 Result.ok()
```

- 权限：队长/管理员；不允许禁言队长
- 更新 `t_team_member.team_mute_type=1`，`team_mute_unpunish_time=now()+muteDuration`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已禁言该成员 30 分钟",
  "message": null
}
```

**响应示例（不可禁言队长）**：
```json
{
  "success": false,
  "data": null,
  "message": "不允许禁言队长"
}
```

### POST /api/team/member/unmute

> 文件：`TeamController.java` → `TeamServiceImpl.java`

**请求参数**（Body JSON）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `teamId` | Long | 是 | 团队 ID |
| `unmuteUserId` | Long | 是 | 被解除禁言的成员用户 ID |

**响应**：`Result.ok()`

**流程图**：
```
POST /api/team/member/unmute
  → 校验当前用户为队长/管理员（role_type IN 1,2）
  → 查目标用户 t_team_member（is_quit=0）
  → 更新 t_team_member:
      team_mute_type=0
      team_mute_unpunish_time=NULL
  → 返回 Result.ok()
```

- 更新 `team_mute_type=0`，`team_mute_unpunish_time=NULL`

**响应示例（成功）**：
```json
{
  "success": true,
  "data": "已解除禁言",
  "message": null
}
```

**响应示例（用户未被禁言）**：
```json
{
  "success": false,
  "data": null,
  "message": "该用户当前未被禁言"
}
```

---

## 六、权限检查实现

```
// 内部辅助方法
getMember(teamId, userId)  -> 查 t_team_member（is_quit=0），返回 TeamMember 对象，不存在返回 null
isLeader(teamId, userId)   -> role_type=1
isAdmin(teamId, userId)    -> role_type IN (1,2)
isMember(teamId, userId)   -> is_quit=0
isMuted(teamId, userId)    -> team_mute_type=1 AND team_mute_unpunish_time > now()
```

### 角色权限矩阵

| 操作 | 队长 | 管理员 | 普通成员 |
|---|---|---|---|
| 查看成员列表 | ✓ | ✓ | ✓ |
| 发送消息 | ✓ | ✓ | ✓ |
| 审批加入申请 | ✓ | ✓ | ✗ |
| 移除成员（含管理员）| ✓ | ✗ | ✗ |
| 移除普通成员 | ✓ | ✓ | ✗ |
| 禁言成员（含管理员）| ✓ | ✗ | ✗ |
| 禁言普通成员 | ✓ | ✓ | ✗ |
| 全员禁言 | ✓ | ✓ | ✗ |
| 修改成员角色 | ✓ | ✗ | ✗ |
| 转让队长权限 | ✓ | ✗ | ✗ |
| 编辑团队信息 | ✓ | ✗ | ✗ |
| 解散团队 | ✓ | ✗ | ✗ |

> **说明**：管理员可移除/禁言普通成员（role_type=3），但**不能**对其他管理员（role_type=2）执行移除或禁言操作；数据库注释中虽保留 `4-嘉宾`，当前代码并未开放该角色。

发送禁言检测链（发消息时）：
1. 全局禁言：`t_user.global_punish_type=1 AND global_unpunish_time > now()`
2. 全员禁言：`t_team.team_all_mute=1`（队长/管理员豁免）
3. 个人禁言：`t_team_member.team_mute_type=1 AND team_mute_unpunish_time > now()`

---

## 七、系统通知发送（各操作触发的 noticeType）

所有通知均通过 `sendRealtimeSystemNotice()` 实现：先写入 `t_system_notice` 表，再通过 `ChatWebSocketHandler.sendToUser()` WebSocket 实时推送。用户离线时通知不丢失，下次登录后可通过 `GET /api/notice/list` 查询。

| 操作 | noticeType | 通知接收方 | 通知内容示例 |
|---|---|---|---|
| 申请加入团队 | 3 | 所有队长/管理员 | "用户 XX 申请加入团队" |
| 审批通过 | 3 | 申请人 | "你的加入申请已通过" |
| 审批拒绝 | 4 | 申请人 | "你的加入申请被拒绝" |
| 邀请加入 | 5 | 被邀请用户 | "你已被邀请加入团队" |
| 移除成员 | 5 | 被移除用户 | "你已被移出团队" |
| 转让队长 | 3 | 新队长 | "你已成为团队队长" |
| 解散团队 | 4 | 所有成员 | "团队已解散" |
| 编辑团队信息 | 3 | 所有成员 | "团队信息已更新" |

WebSocket 推送格式：
```json
{
  "type": "system_notice",
  "data": {
    "noticeId": 1001,
    "noticeType": 3,
    "noticeContent": "你的加入申请已通过",
    "relatedId": 1001
  }
}
```

---

## 八、兼容路径说明（Part6 复用）

`ChatController` 中 `/api/chat/group/mute-all`、`/api/chat/group/mute-member`、`/api/chat/group/unmute-member` 三个接口直接调用 `TeamService` 中对应方法，与 `/api/team/mute-*` 完全共用同一套实现逻辑，无重复代码。

---

## 九、异步操作说明

本模块中系统通知发送均通过**虚拟线程**（`Thread.ofVirtual().start()`）异步执行，不阻塞主事务流程。

### 异步化原则

- **主事务内执行**：团队/成员数据写库、满员校验、角色校验等**同步执行**
- **虚拟线程内执行**：系统通知发送（`sendNotice()` / `sendNoticeToAdmins()`）**异步执行**
- `removeMember()` 中禁言缓存清除（`stringRedisTemplate.delete`）与通知一并放入虚拟线程，属于非关键路径

### 各方法异步点汇总

| 方法 | 异步内容 | 说明 |
|---|---|---|
| `applyTeam()` | 通知所有管理员/队长（申请审核通知）| 申请记录写库后异步推送 |
| `inviteMember()` | 通知被邀请用户（noticeType=3）| 成员写库后异步推送 |
| `auditApply()` | 通知申请者（通过/拒绝）| 审批状态更新同步，通知异步 |
| `removeMember()` | 通知被移除用户 + 清除禁言缓存 | 成员标记退出同步，通知和缓存清除异步 |
| `dissolveTeam()` | 通知所有成员 + 清除全员禁言缓存 | 解散标记同步，通知和缓存清除异步 |

### 通知丢失保障

`sendNotice()` 内部先写入 `t_system_notice` 表再推送 WebSocket，用户离线时推送静默跳过，通知不丢失。
