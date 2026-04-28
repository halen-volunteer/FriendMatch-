# FriendMatch 综合实现文档 - Part 7：团队管理精细化与搜索推荐模块

> 版本：V2.4（全接口 + 细化流程图 + 完整响应） | 日期：2026-04-03

---

## 一、模块总览

本分册对应 `Part7`，当前重点覆盖：

- 团队搜索
- 用户搜索
- 搜索历史与热词
- 搜索建议
- 用户推荐
- 团队推荐

> 说明：综合实现分册当前尚未单列 `Part6`，聊天增强与消息治理能力主要体现在 `综合实现Part4_聊天基础与消息体验模块.md` 与对应实现文档中。

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `SearchRecommendController.java` |
| Service | `SearchRecommendService.java` / `SearchRecommendServiceImpl.java` |
| Mapper | `SearchHistoryMapper.java` / `SearchHotKeywordMapper.java` / `UserRecommendationMapper.java` / `TeamRecommendationMapper.java` |

---

## 三、数据库设计

- `t_search_history`
- `t_search_hot_keyword`
- `t_user_recommendation`
- `t_team_recommendation`

---

## 四、接口实现详情

### GET /api/search/users — 搜索用户
**参数**：`keyword`、`page`、`pageSize`

**流程图**：
```text
鉴权 → 关键词校验 → 用户匹配 → 黑名单/可见性过滤 → 排序分页 → 写历史与热词
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### GET /api/search/teams — 搜索团队
**参数**：`keyword`、`page`、`pageSize`

**流程图**：
```text
鉴权 → 关键词校验 → 团队匹配 → 排序分页 → 写历史与热词
```

**响应**：
```json
{"success": true, "data": {"records": [], "total": 0}, "message": null}
```

### GET /api/search/history — 搜索历史
**参数**：`searchType`、`limit`

**流程图**：
```text
鉴权 → searchType校验 → 查询当前用户历史 → 按时间倒序返回
```

**响应**：
```json
{"success": true, "data": [{"keyword": "王者", "searchCount": 3}], "message": null}
```

### GET /api/search/hot-keywords — 热词榜
**参数**：`searchType`、`limit`

**流程图**：
```text
searchType校验 → 查询热词表 → 按 rank 返回前N条
```

**响应**：
```json
{"success": true, "data": [{"keyword": "开黑", "rank": 1}], "message": null}
```

### DELETE /api/search/history — 清空历史
**参数**：无

**流程图**：
```text
鉴权 → 删除当前用户全部搜索历史 → 返回
```

**响应**：
```json
{"success": true, "data": "清空成功", "message": null}
```

### GET /api/search/suggest — 搜索联想
**参数**：`keyword`、`type`、`limit`

**流程图**：
```text
鉴权 → 参数校验 → 按类型提取联想词 → 去重并截断
```

**响应**：
```json
{"success": true, "data": ["王者荣耀", "王者战队"], "message": null}
```

### GET /api/recommend/users — 用户推荐
**参数**：`limit`

**流程图**：
```text
鉴权 → 读取缓存 → 未命中计算推荐分 → 回写缓存与推荐记录 → 返回
```

**响应**：
```json
{"success": true, "data": [{"userId": 1002, "score": 88}], "message": null}
```

### GET /api/recommend/teams — 团队推荐
**参数**：`limit`

**流程图**：
```text
鉴权 → 读取缓存 → 未命中计算推荐分 → 回写缓存与推荐记录 → 返回
```

**响应**：
```json
{"success": true, "data": [{"teamId": 2001, "score": 90}], "message": null}
```

### POST /api/recommend/click — 推荐点击回传
**参数**：`recommendId`、`recommendType`

**流程图**：
```text
鉴权 → 参数校验 → 更新推荐记录点击状态与时间 → 返回
```

**响应**：
```json
{"success": true, "data": "记录成功", "message": null}
```
