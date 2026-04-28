# FriendMatch 实现文档 - Part 7：团队管理精细化与搜索推荐模块

> 版本：V2.2 | 日期：2026-04-03

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `TeamController.java` / `SearchRecommendController.java` |
| Service | `TeamService.java` / `TeamServiceImpl.java` / `SearchRecommendService.java` / `SearchRecommendServiceImpl.java` |
| Mapper | `TeamMapper.java` / `TeamMemberMapper.java` / `TeamApplyMapper.java` / `SearchHistoryMapper.java` / `SearchHotKeywordMapper.java` / `UserRecommendationMapper.java` / `TeamRecommendationMapper.java` / `ChatMessageMapper.java` |
| Model | `TeamMember.java` / `SearchHistory.java` / `SearchHotKeyword.java` / `UserRecommendation.java` / `TeamRecommendation.java` |
| Scheduler | `RecommendRefreshScheduler.java` / `SearchHotKeywordScheduler.java` |

---

## 二、数据库设计（本期范围）

### 2.1 `t_team_member`

- 扩展字段：`role_type`、`join_source`、`invite_user_id`、`join_time`、`last_active_time`
- 角色定义：1-队长，2-管理员，3-普通成员
- 入队来源：1-直接加入，2-邀请加入，3-申请审批

### 2.2 `t_search_history`

- 维度：`user_id + search_type + search_keyword`
- 指标：`search_count`、`last_search_time`

### 2.3 `t_search_hot_keyword`

- 维度：`keyword + search_type`
- 指标：`search_count`、`rank`

### 2.4 `t_user_recommendation` / `t_team_recommendation`

- 推荐记录：`recommend_reason`、`recommend_score`
- 反馈行为：`is_clicked`

---

## 三、团队管理接口（详细）

> 统一失败响应（下同）：

```json
{"success": false, "data": null, "message": "参数不合法"}
```

```json
{"success": false, "data": null, "message": "无权限操作"}
```

### 3.1 POST `/api/team/create`

- 功能：创建团队并自动把创建者加入团队（队长）
- 入参（Body：`TeamCreateDTO`）：`teamName`、`teamType`、`joinRule` 必填

- 流程图：

```text
POST /api/team/create
  → 登录校验
  → 参数校验（团队名、类型、入队规则、人数上限）
  → 敏感词与规则校验（密码入队时校验密码）
  → 写入 t_team
  → 写入 t_team_member（creator role_type=1）
  → 返回团队信息
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.2 GET `/api/team/list`

- 功能：分页查询团队
- 入参（Query）：`page`、`pageSize`、`teamType`、`sort`

- 流程图：

```text
GET /api/team/list
  → 解析分页与筛选参数
  → 构建查询条件（is_delete=0）
  → 分页查询团队基础信息
  → 批量补充成员数/当前用户是否已加入
  → 返回 records + total
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.3 GET `/api/team/search`

- 功能：按名称/标签搜索团队
- 入参（Query）：`keyword`、`type`、`page`、`pageSize`

- 流程图：

```text
GET /api/team/search
  → 校验 keyword 非空
  → 根据 type 选择字段（team_name / team_tags）
  → 执行 LIKE 分页查询
  → 过滤软删记录
  → 返回分页结果
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.4 GET `/api/team/{teamId}`

- 功能：查询团队详情与我的角色
- 入参（Path）：`teamId`

- 流程图：

```text
GET /api/team/{teamId}
  → 校验 teamId
  → 查询团队基础信息
  → 查询当前用户 team_member 关系
  → 统计团队成员数量
  → 组装详情并返回
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.5 POST `/api/team/update`

- 功能：队长更新团队资料
- 入参（Body：`TeamUpdateDTO`）：`teamId` 必填

- 流程图：

```text
POST /api/team/update
  → 登录校验
  → 校验 teamId 与更新字段
  → 校验当前用户是否队长
  → 执行更新（仅非空字段）
  → 返回更新成功
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.6 POST `/api/team/dissolve`

- 功能：解散团队
- 入参（Query）：`teamId`

- 流程图：

```text
POST /api/team/dissolve
  → 登录校验
  → 校验队长权限
  → 软删 t_team
  → 软删或标记退出 t_team_member
  → 异步发送团队解散通知
  → 返回成功
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.7 POST `/api/team/apply`

- 功能：申请入队（审批制）
- 入参（Body：`TeamApplyDTO`）：`teamId`、`applyMsg`

- 流程图：

```text
POST /api/team/apply
  → 登录校验
  → 校验团队规则（join_rule=审批）
  → 校验是否已在队内/是否重复申请
  → 写 t_team_apply（audit_status=0）
  → 通知队长/管理员
  → 返回申请成功
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.8 POST `/api/team/apply/audit`

- 功能：审核入队申请
- 入参（Body：`TeamAuditDTO`）：`applyId`、`auditStatus`、`auditMsg`

- 流程图：

```text
POST /api/team/apply/audit
  → 登录校验
  → 校验审核权限（队长/管理员）
  → 查询申请并校验待审核状态
  → auditStatus=1 时 addMember(joinSource=3)
  → 更新申请审核结果与时间
  → 通知申请人
  → 返回审核结果
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.9 POST `/api/team/join-by-password`

- 功能：密码入队
- 入参（Body：`TeamJoinByPasswordDTO`）：`teamId`、`password`

- 流程图：

```text
POST /api/team/join-by-password
  → 登录校验
  → 校验团队 join_rule=3
  → BCrypt 校验入队密码
  → 校验是否已在队内
  → addMember(joinSource=1)
  → 返回加入成功
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.10 POST `/api/team/invite`

- 功能：邀请用户入队
- 入参（Body：`TeamInviteDTO`）：`teamId`、`userId`

- 流程图：

```text
POST /api/team/invite
  → 登录校验
  → 校验邀请权限（队长/管理员）
  → 校验目标用户存在且未在队内
  → addMember(joinSource=2, inviteUserId=当前用户)
  → 通知被邀请用户
  → 返回邀请成功
```


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 3.11 其他成员管理接口

- `POST /api/team/member/remove`：移除成员（需权限 + 角色约束）
- `POST /api/team/member/role/update`：调整角色（2/3）
- `POST /api/team/transfer-leader`：转让队长（事务更新新旧队长）
- `POST /api/team/mute-all`：全员禁言开关
- `POST /api/team/member/mute`：成员禁言
- `POST /api/team/member/unmute`：解除成员禁言
- `POST /api/team/quit`：主动退队
- `GET /api/team/members` / `GET /api/team/{teamId}/members`：成员分页查询

> 以上接口均具备：参数校验 → 权限校验 → 状态校验 → 执行更新 → 返回结果。

---

## 四、搜索接口（详细）

### 4.1 GET `/api/search/users`

- 入参（Query）：`keyword`、`page`、`pageSize`

- 流程图：

```text
GET /api/search/users
  → 登录校验
  → 校验关键词
  → 用户维度模糊查询（昵称/账号/标签）
  → 黑名单互斥过滤
  → 计算相似度分并排序
  → 写搜索历史与热词
  → 返回分页结果
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 4.2 GET `/api/search/teams`

- 入参（Query）：`keyword`、`page`、`pageSize`

- 流程图：

```text
GET /api/search/teams
  → 登录校验
  → 校验关键词
  → 团队维度模糊查询（名称/标签/简介）
  → 计算成员规模修正分
  → 综合评分排序
  → 写搜索历史与热词
  → 返回分页结果
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 4.3 GET `/api/search/suggest`

- 入参（Query）：`keyword`、`type`、`limit`

- 流程图：

```text
GET /api/search/suggest
  → 校验 keyword/type
  → 按 type 选择用户或团队数据源
  → 生成前缀匹配候选词
  → 去重 + 截断 limit
  → 返回联想词列表
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 4.4 GET `/api/search/history`

- 入参（Query）：`searchType`、`limit`

- 流程图：

```text
GET /api/search/history
  → 登录校验
  → 校验 searchType
  → 按 userId + searchType 查询历史
  → 按 last_search_time 倒序
  → limit 截断并返回
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 4.5 DELETE `/api/search/history`

- 入参：无

- 流程图：

```text
DELETE /api/search/history
  → 登录校验
  → 定位当前用户历史记录
  → 批量软删除（is_delete=1）
  → 返回清空成功
```


**Success response example**:

```json
{"success": true, "data": "deleted", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 4.6 GET `/api/search/hot-keywords`

- 入参（Query）：`searchType`、`limit`

- 流程图：

```text
GET /api/search/hot-keywords
  → 校验 searchType/limit
  → 读取 Redis 热词缓存
      命中：直接返回
      未命中：查库 + 排序 + rank 计算
  → 写回缓存（带随机过期）
  → 返回榜单
```

---


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
## 五、推荐接口（详细）

### 5.1 GET `/api/recommend/users`

- 入参（Query）：`limit`

- 流程图：

```text
GET /api/recommend/users
  → 登录校验
  → 读取 recommend:users 缓存
      命中：直接返回
      未命中：计算多维得分
  → 评分维度：标签/共同好友/聊天对象/共同团队
  → 写 t_user_recommendation
  → 按分数排序并截断
  → 回写缓存并返回
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 5.2 GET `/api/recommend/teams`

- 入参（Query）：`limit`

- 流程图：

```text
GET /api/recommend/teams
  → 登录校验
  → 读取 recommend:teams 缓存
      命中：直接返回
      未命中：计算团队推荐分
  → 评分维度：标签相似/成员重叠/团队热度/新建加成
  → 写 t_team_recommendation
  → 按分数排序并截断
  → 回写缓存并返回
```


**Success response example**:

```json
{"success": true, "data": {}, "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
### 5.3 POST `/api/recommend/click`

- 入参（Body：`RecommendClickDTO`）：`recommend_id`、`recommend_type`

- 流程图：

```text
POST /api/recommend/click
  → 登录校验
  → 校验 recommend_id/type
  → type=1 更新用户推荐 is_clicked
  → type=2 更新团队推荐 is_clicked
  → 返回记录成功
```

---


**Success response example**:

```json
{"success": true, "data": "ok", "message": null}
```

**Failure response example**:

```json
{"success": false, "data": null, "message": "invalid params or no permission"}
```
## 六、缓存与调度

### 6.1 缓存策略

- 热词缓存：`search:hot:{type}:{limit}`，TTL 60 分钟 + 随机抖动
- 推荐缓存：
  - `recommend:users:{userId}:{limit}`
  - `recommend:teams:{userId}:{limit}`
  - TTL 24 小时 + 随机 0~5 小时

### 6.2 定时任务

- `SearchHotKeywordScheduler`：每小时刷新热词排名并清理缓存
- `RecommendRefreshScheduler`：凌晨全量刷新 + 小时级刷新

### 6.3 事件触发刷新

- 同意好友后：刷新双方推荐
- 成员入队后：刷新该成员推荐

---

## 七、验收结论

- Part7（不含后续优化）已实现完成
- 接口文档已按 Part1 模板补齐：参数、流程、边界说明
- 构建通过：`mvn compile`
