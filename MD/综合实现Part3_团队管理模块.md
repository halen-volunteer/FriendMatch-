# FriendMatch 综合实现文档 - Part 3：团队管理模块

> 版本：V2.4（全接口 + 细化流程图 + 完整响应） | 日期：2026-04-03

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `TeamController.java` |
| Service | `TeamService.java` / `TeamServiceImpl.java` |
| Mapper | `TeamMapper.java` / `TeamMemberMapper.java` / `TeamApplyMapper.java` |

---

## 二、数据库设计

- `t_team`
- `t_team_member`
- `t_team_apply`

---

## 三、接口实现详情

### POST /api/team/create — 创建团队
**参数**：`teamName`、`teamType`、`joinRule`、`joinPassword`、`maxMember`

**流程图**：
```text
鉴权 → 参数与规则校验 → 写团队 → 写队长成员关系 → 返回 teamId
```

**响应**：
```json
{"success": true, "data": {"teamId": 2001}, "message": null}
```

### GET /api/team/list — 团队列表
**参数**：`page`、`pageSize`、`teamType`、`sort`

**流程图**：
```text
鉴权 → 分页参数校验 → 按条件分页查询 → 返回
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### GET /api/team/search — 搜索团队
**参数**：`keyword`、`type`、`page`、`pageSize`

**流程图**：
```text
鉴权 → 关键词校验 → 按名称/标签搜索 → 分页返回
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### GET /api/team/{teamId} — 团队详情
**参数**：`teamId`

**流程图**：
```text
鉴权 → 团队存在校验 → 查询团队与成员角色 → 返回
```

**响应**：
```json
{"success": true, "data": {"teamId": 2001, "roleType": 1}, "message": null}
```

### POST /api/team/update — 编辑团队
**参数**：`teamId` + 可更新字段

**流程图**：
```text
鉴权 → 队长权限校验 → 参数校验 → 更新团队信息
```

**响应**：
```json
{"success": true, "data": "更新成功", "message": null}
```

### POST /api/team/dissolve — 解散团队
**参数**：`teamId`

**流程图**：
```text
鉴权 → 队长权限校验 → 标记团队删除 → 处理成员状态 → 返回
```

**响应**：
```json
{"success": true, "data": "解散成功", "message": null}
```

### POST /api/team/apply — 申请入队
**参数**：`teamId`、`applyMsg`

**流程图**：
```text
鉴权 → 入队规则校验 → 重复申请校验 → 写申请记录
```

**响应**：
```json
{"success": true, "data": "申请已提交", "message": null}
```

### POST /api/team/join-by-password — 密码入队
**参数**：`teamId`、`joinPassword`

**流程图**：
```text
鉴权 → 校验团队与口令 → 加入团队 → 返回结果
```
