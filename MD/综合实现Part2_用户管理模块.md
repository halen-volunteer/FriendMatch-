# FriendMatch 综合实现文档 - Part 2：用户管理模块

> 版本：V2.4（全接口 + 细化流程图 + 完整响应） | 日期：2026-04-03

---

## 一、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `UserManagementController.java` / `FriendManagementController.java` / `BlacklistManagementController.java` |
| Service | `UserManagementService.java` / `UserManagementServiceImpl.java` |
| Mapper | `UserMapper.java` / `UserFriendMapper.java` / `UserBlacklistMapper.java` |

---

## 二、数据库设计

- `t_user_friend`
- `t_user_blacklist`

---

## 三、接口实现详情

### POST /api/user/profile/update — 更新用户资料
**参数**：`userNickname`、`userAvatar`、`userIntro`、`userTags`

**流程图**：
```text
鉴权 → 参数校验 → 敏感词校验 → 更新用户资料 → 同步缓存 → 返回
```

**响应**：
```json
{"success": true, "data": "更新成功", "message": null}
```

### GET /api/user/privacy — 获取隐私设置
**参数**：无

**流程图**：
```text
鉴权 → 查询 privacy_setting → 缺省字段补齐 → 返回
```

**响应**：
```json
{"success": true, "data": {"viewInfo": 1, "sendMsg": 2, "searchByEmail": 0}, "message": null}
```

### POST /api/user/privacy/update — 更新隐私设置
**参数**：`viewInfo`、`sendMsg`、`searchByEmail`

**流程图**：
```text
鉴权 → 范围校验 → 更新 privacy_setting → 清缓存 → 返回
```

**响应**：
```json
{"success": true, "data": "更新成功", "message": null}
```

### GET /api/user/{userId}/profile — 查看用户资料
**参数**：`userId`

**流程图**：
```text
鉴权 → 用户存在校验 → 黑名单校验 → 隐私可见性校验 → 返回可见资料
```

**响应**：
```json
{"success": true, "data": {"id": 1002, "userNickname": "Alice"}, "message": null}
```

### GET /api/user/list — 用户列表
**参数**：`page`、`pageSize`、`sort`

**流程图**：
```text
鉴权 → 分页参数校验 → 查询列表 → 过滤黑名单/自己 → 返回分页
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### GET /api/user/search — 搜索用户
**参数**：`keyword`、`type`、`page`、`pageSize`

**流程图**：
```text
鉴权 → 关键词与类型校验 → 多字段查询 → 黑名单与隐私过滤 → 返回分页
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### POST /api/friend/add — 添加好友
**参数**：`friendId`、`applyMsg`、`friendRemark`

**流程图**：
```text
鉴权 → 目标存在校验 → 自己/黑名单/重复关系校验 → 写申请记录
```

**响应**：
```json
{"success": true, "data": "申请已发送", "message": null}
```

### GET /api/friend/list — 好友列表
**参数**：`page`、`pageSize`

**流程图**：
```text
鉴权 → 查询好友关系 → 组装好友信息 → 返回分页
```
