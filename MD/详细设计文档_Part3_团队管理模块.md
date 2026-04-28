# FriendMatch 详细设计文档 - Part 3：团队管理模块

> 版本：V1.0 | 日期：2026-03-16

---

## 一、模块概述

团队管理模块负责团队的创建、查询、成员管理、角色权限、加入审批等功能。支持公开/私有团队、多种加入方式、灵活的角色权限体系。

### 核心特性
- ✅ 团队创建（公开/私有、多种加入方式）
- ✅ 团队查询（列表、搜索、筛选）
- ✅ 成员管理（添加、移除、角色分配）
- ✅ 角色权限（当前实现为队长、管理员、普通成员；嘉宾仅在 SQL 注释中预留）
- ✅ 加入审批（申请审批流程）
- ✅ 禁言管理（全员禁言、指定成员禁言）
- ✅ 团队资料编辑（队长权限）

---

## 二、数据库设计

### 2.1 t_team 表（团队基础信息表）

**字段说明**：
- `id`：团队主键
- `team_name`：团队名称（1-64字符）
- `team_avatar`：团队头像URL
- `team_intro`：团队简介（0-512字符）
- `team_tags`：团队标签（逗号分隔，最多5个）
- `creator_id`：团队创建人ID
- `max_member`：团队最大成员数（默认200）
- `team_type`：团队类型
  - 1：公开团队（所有人可见）
  - 2：私有团队（仅成员可见）
- `join_rule`：加入规则
  - 1：申请审批（需队长/管理员审批）
  - 2：仅邀请（由队长或管理员邀请）
  - 3：密码加入（输入密码直接加入）
- `join_password`：加入密码（仅 join_rule=3 有效，BCrypt加密）
- `team_all_mute`：团队全员禁言标记（0-正常，1-禁言）
- `is_delete`：软删除标记
- `create_time`、`update_time`：时间戳

**关键索引**：
- `idx_creator_id`：creator_id 索引（快速查询用户创建的团队）
- `idx_team_type`：team_type 索引（按类型筛选）
- `idx_team_name`：team_name 索引（按名称搜索）

### 2.2 t_team_member 表（团队成员表）

**字段说明**：
- `id`：成员关联主键
- `team_id`：关联团队ID
- `user_id`：关联用户ID
- `role_type`：角色类型
  - 1：队长（最高权限，可转让、解散）
  - 2：管理员（可审批、禁言，无转让权限）
  - 3：普通成员（可聊天、查看成员）
  - 4：嘉宾（SQL 预留值，当前业务代码未启用）
- `team_mute_type`：团队内禁言状态（0-正常，1-禁言）
- `team_mute_unpunish_time`：禁言解除时间
- `join_time`：加入团队时间
- `quit_time`：退出团队时间
- `is_quit`：是否已退出（0-否，1-是）
- `is_delete`：软删除标记
- `create_time`、`update_time`：时间戳

**关键索引**：
- `uk_team_user`：(team_id, user_id, is_quit) 唯一索引
- `idx_user_id`：user_id 索引（快速查询用户加入的团队）
- `idx_role_type`：role_type 索引（按角色查询）
- `idx_team_mute`：(team_id, team_mute_type, team_mute_unpunish_time)

### 2.3 t_team_apply 表（加入申请表）

**字段说明**：
- `id`：申请主键
- `team_id`：关联团队ID
- `apply_user_id`：申请人ID
- `audit_user_id`：审核人ID（队长/管理员）
- `apply_msg`：申请备注（可选）
- `audit_status`：审核状态
  - 0：待审核
  - 1：通过
  - 2：拒绝
- `audit_msg`：审核备注（可选）
- `audit_time`：审核时间
- `is_delete`：软删除标记
- `create_time`、`update_time`：时间戳

**关键索引**：
- `idx_team_audit`：(team_id, audit_status) 索引（快速查询待审核申请）
- `idx_apply_user`：apply_user_id 索引（快速查询用户的申请）

---

## 三、业务流程设计

### 3.1 团队创建流程

**请求**：
```
POST /api/team/create
{
  "teamName": "前端开发组",
  "teamIntro": "专注前端技术分享",
  "teamTags": "前端,React,Vue",
  "maxMember": 100,
  "teamType": 1,           // 1-公开，2-私有
  "joinRule": 1,           // 1-申请审批，2-仅邀请，3-密码加入
  "joinPassword": ""       // 仅 joinRule=3 时需要
}
```

**流程**：
1. 参数校验
   - 团队名称：1-64字符，不能为空
   - 团队简介：0-512字符
   - 标签：最多5个，每个最多20字符
   - 最大成员数：1-1000
2. 敏感词检测（团队名称、简介）
3. 若 joinRule=3，BCrypt加密密码
4. 插入 t_team
5. 插入 t_team_member（role_type=1，当前用户为队长）
6. 返回团队信息

**关键设计点**：
- 创建者自动成为队长
- 密码加入方式需加密存储
- 团队名称不唯一（允许重名）

### 3.2 团队查询流程

**获取团队列表**：
```
GET /api/team/list?page=1&pageSize=20&teamType=1&sort=createTime
```

**流程**：
1. 参数校验（page、pageSize、teamType）
2. 构建查询条件
   - is_delete=0
   - team_type=teamType（若指定）
3. 分页查询 t_team
4. 对每个团队，查询成员数
5. 返回脱敏团队信息

**搜索团队**：
```
GET /api/team/search?keyword=前端&type=name&page=1&pageSize=20
```

**流程**：
1. 参数校验（keyword 长度、type 枚举值）
2. 根据 type 构建查询条件
   - type=name：按 team_name 模糊搜索
   - type=tag：按 team_tags 搜索
3. 分页查询 t_team
4. 返回脱敏团队信息

**获取团队详情**：
```
GET /api/team/{teamId}
```

**流程**：
1. 查询 t_team
2. 查询成员数
3. 检查当前用户是否为成员
4. 返回团队详情

### 3.3 加入团队流程

**申请加入（公开+申请审批）**：
```
POST /api/team/apply
{
  "teamId": 1001,
  "applyMsg": "我想加入这个团队"
}
```

**流程**：
1. 参数校验
2. 检查团队是否存在
3. 检查是否已是成员（is_quit=0）
4. 检查是否已申请（audit_status=0）
5. 检查成员数是否已满
6. 插入 t_team_apply（audit_status=0）
7. 发送系统通知给队长/管理员
8. 返回成功

**密码加入（公开+密码加入）**：
```
POST /api/team/join-by-password
{
  "teamId": 1001,
  "password": "123456"
}
```

**流程**：
1. 参数校验
2. 查询团队（join_rule=3）
3. BCrypt密码比对
4. 检查是否已是成员
5. 检查成员数是否已满
6. 插入 t_team_member（role_type=3）
7. 返回成功

**邀请加入（私有+仅邀请）**：
```
POST /api/team/invite
{
  "teamId": 1001,
  "userId": 1002
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 检查当前用户是否为队长/管理员
3. 检查目标用户是否已是成员
4. 检查成员数是否已满
5. 插入 t_team_member（role_type=3）
6. 发送系统通知给被邀请用户
7. 返回成功

### 3.4 审批流程

**审批加入申请**：
```
POST /api/team/apply/audit
{
  "applyId": 1,
  "auditStatus": 1,        // 1-通过，2-拒绝
  "auditMsg": "欢迎加入"
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 查询申请记录
3. 检查当前用户是否为队长/管理员
4. 若 auditStatus=1（通过）：
   - 检查成员数是否已满
   - 插入 t_team_member（role_type=3）
   - 更新 t_team_apply（audit_status=1）
5. 若 auditStatus=2（拒绝）：
   - 更新 t_team_apply（audit_status=2）
6. 发送系统通知给申请者
7. 返回成功

### 3.5 成员管理流程

**获取成员列表**：
```
GET /api/team/{teamId}/members?page=1&pageSize=20
```

**流程**：
1. 查询 t_team_member（team_id=teamId, is_quit=0）
2. 分页返回成员信息

**移除成员**：
```
POST /api/team/member/remove
{
  "teamId": 1001,
  "userId": 1002
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 检查当前用户是否为队长/管理员
3. 检查目标用户是否为成员
4. 不允许移除队长
5. 更新 t_team_member（is_quit=1, quit_time=now()）
6. 发送系统通知给被移除用户
7. 返回成功

**修改成员角色**：
```
POST /api/team/member/role/update
{
  "teamId": 1001,
  "userId": 1002,
  "roleType": 2            // 2-管理员，3-普通成员
}
```

**流程**（队长操作）：
1. 参数校验
2. 检查当前用户是否为队长
3. 不允许修改队长角色
4. 更新 t_team_member.role_type
5. 返回成功

**转让队长权限**：
```
POST /api/team/transfer-leader
{
  "teamId": 1001,
  "userId": 1002
}
```

**流程**（队长操作）：
1. 参数校验
2. 检查目标用户是否为成员
3. 更新当前队长为管理员（role_type=2）
4. 更新目标用户为队长（role_type=1）
5. 发送系统通知给新队长
6. 返回成功

**解散团队**：
```
POST /api/team/dissolve
{
  "teamId": 1001
}
```

**流程**（队长操作，二次确认）：
1. 参数校验
2. 检查当前用户是否为队长
3. 更新 t_team（is_delete=1）
4. 更新所有成员（is_quit=1）
5. 发送系统通知给所有成员
6. 返回成功

### 3.6 禁言管理流程

**全员禁言**：
```
POST /api/team/mute-all
{
  "teamId": 1001,
  "isMute": true
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 检查当前用户是否为队长/管理员
3. 更新 t_team.team_all_mute
4. 返回成功

**禁言指定成员**：
```
POST /api/team/member/mute
{
  "teamId": 1001,
  "userId": 1002,
  "muteDuration": 60       // 禁言时长（分钟）
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 检查当前用户是否为队长/管理员
3. 不允许禁言队长
4. 更新 t_team_member
   - team_mute_type=1
   - team_mute_unpunish_time=now() + muteDuration
5. 返回成功

**解除禁言**：
```
POST /api/team/member/unmute
{
  "teamId": 1001,
  "userId": 1002
}
```

**流程**（队长/管理员操作）：
1. 参数校验
2. 检查当前用户是否为队长/管理员
3. 更新 t_team_member
   - team_mute_type=0
   - team_mute_unpunish_time=NULL
4. 返回成功

### 3.7 团队资料编辑流程

**编辑团队信息**：
```
POST /api/team/update
{
  "teamId": 1001,
  "teamName": "新团队名称",
  "teamIntro": "新简介",
  "teamTags": "新标签1,新标签2",
  "maxMember": 150
}
```

**流程**（队长操作）：
1. 参数校验
2. 检查当前用户是否为队长
3. 敏感词检测
4. 更新 t_team
5. 发送系统通知给所有成员
6. 返回成功

---

## 四、权限控制设计

### 4.1 角色权限矩阵

| 操作 | 队长 | 管理员 | 普通成员 |
|---|---|---|---|
| 查看成员列表 | ✓ | ✓ | ✓ |
| 发送消息 | ✓ | ✓ | ✓ |
| 审批加入申请 | ✓ | ✓ | ✗ |
| 移除成员 | ✓ | ✓ | ✗ |
| 禁言成员 | ✓ | ✓ | ✗ |
| 修改成员角色 | ✓ | ✗ | ✗ |
| 转让队长权限 | ✓ | ✗ | ✗ |
| 编辑团队信息 | ✓ | ✗ | ✗ |
| 解散团队 | ✓ | ✗ | ✗ |
| 全员禁言 | ✓ | ✓ | ✗ |

> 说明：`嘉宾` 仍出现在 `friendmatch.sql` 的字段注释中，但当前 `TeamServiceImpl` 未开放嘉宾角色的写入、转化与权限矩阵。

### 4.2 权限检查实现

**统一权限检查方法**：
```java
// 检查是否为队长
boolean isLeader(Long teamId, Long userId)

// 检查是否为管理员或队长
boolean isAdmin(Long teamId, Long userId)

// 检查是否为成员
boolean isMember(Long teamId, Long userId)

// 检查是否被禁言
boolean isMuted(Long teamId, Long userId)
```

---

## 五、API 接口设计

### 5.1 团队创建接口

```
POST /api/team/create
Authorization: {token}
Content-Type: application/json

{
  "teamName": "前端开发组",
  "teamIntro": "专注前端技术分享",
  "teamTags": "前端,React,Vue",
  "maxMember": 100,
  "teamType": 1,
  "joinRule": 1,
  "joinPassword": ""
}
```

### 5.2 团队查询接口

```
GET /api/team/list?page=1&pageSize=20&teamType=1
Authorization: {token}

GET /api/team/search?keyword=前端&type=name&page=1&pageSize=20
Authorization: {token}

GET /api/team/{teamId}
Authorization: {token}
```

### 5.3 加入团队接口

```
POST /api/team/apply
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "applyMsg": "我想加入这个团队"
}

POST /api/team/join-by-password
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "password": "123456"
}

POST /api/team/invite
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002
}
```

### 5.4 审批接口

```
POST /api/team/apply/audit
Authorization: {token}
Content-Type: application/json

{
  "applyId": 1,
  "auditStatus": 1,
  "auditMsg": "欢迎加入"
}
```

### 5.5 成员管理接口

```
GET /api/team/{teamId}/members?page=1&pageSize=20
Authorization: {token}

POST /api/team/member/remove
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002
}

POST /api/team/member/role/update
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002,
  "roleType": 2
}

POST /api/team/transfer-leader
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002
}

POST /api/team/dissolve
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001
}
```

### 5.6 禁言管理接口

```
POST /api/team/mute-all
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "isMute": true
}

POST /api/team/member/mute
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002,
  "muteDuration": 60
}

POST /api/team/member/unmute
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "userId": 1002
}
```

### 5.7 团队资料编辑接口

```
POST /api/team/update
Authorization: {token}
Content-Type: application/json

{
  "teamId": 1001,
  "teamName": "新团队名称",
  "teamIntro": "新简介",
  "teamTags": "新标签1,新标签2",
  "maxMember": 150
}
```

---

## 六、性能优化

**数据库优化**：
- `idx_creator_id`：快速查询用户创建的团队
- `idx_team_type`：按类型筛选团队
- `uk_team_user`：快速查询用户在团队中的角色

**查询优化**：
- 成员列表查询：`SELECT * FROM t_team_member WHERE team_id=? AND is_quit=0`
- 待审核申请查询：`SELECT * FROM t_team_apply WHERE team_id=? AND audit_status=0`

**缓存策略**：
- 团队基础信息缓存（Redis）
- 成员列表缓存（Redis）
- 禁言状态缓存（Redis）

---

*Part 3 完成。后续将发布 Part 4（聊天系统模块）。*
