# FriendMatch 详细设计文档 - Part 7：团队管理精细化与搜索推荐模块

> 校准版本：V2.0
> 校准日期：2026-04-20
> 校准依据：`TeamController.java`、`SearchRecommendController.java`、`TeamServiceImpl.java`、`SearchRecommendServiceImpl.java`、`SearchQueryService.java`、`SearchRecommendationService.java`、`SQL/friendmatch.sql`

---

## 一、模块范围

Part7 在当前后端中的实际范围包括两部分：

1. 团队管理的精细化能力
2. 搜索、热词、推荐能力

本分册不再将以下内容视为当前已实现能力：

- 邀请码管理
- 二维码 / 链接入队
- 团队动态
- Elasticsearch 检索链路

以上内容如果保留在 SQL 注释或历史设计稿中，应视为预留方案，而不是当前代码事实。

---

## 二、当前实现结构

### 2.1 入口控制器

- `TeamController.java`
- `SearchRecommendController.java`

### 2.2 核心服务

- `TeamServiceImpl.java`
- `SearchRecommendServiceImpl.java`
- `SearchQueryService.java`
- `SearchHistoryService.java`
- `SearchRecommendationService.java`
- `SearchRecommendSupportService.java`

### 2.3 关键调度任务

- `RecommendRefreshScheduler.java`
- `SearchHotKeywordScheduler.java`

---

## 三、数据库设计校准

### 3.1 团队精细化相关表

- `t_team_member`
- `t_team_apply`
- `t_team`

### 3.2 搜索推荐相关表

- `t_search_history`
- `t_search_hot_keyword`
- `t_user_recommendation`
- `t_team_recommendation`

### 3.3 `t_team_member` 字段说明

- `role_type`
  - SQL 预留值：`1-队长`、`2-管理员`、`3-普通成员`、`4-嘉宾`
  - 当前代码实际使用：`1`、`2`、`3`
- `join_source`
  - SQL 预留值：`1-直接加入`、`2-邀请加入`、`3-申请审批`、`4-二维码加入`、`5-链接加入`、`6-密码加入`
  - 当前代码实际使用：`1`、`2`、`3`
- `team_mute_type`
  - 当前代码实际使用：`0-正常`、`1-禁言`

### 3.4 重要校准结论

- “嘉宾角色”在表注释中仍有保留，但业务代码未开放对应操作与权限
- “密码入队”接口已实现，但 `join_source` 当前仍按现有业务写法记录，不单独落库为预留枚举值 `6`
- 当前搜索实现基于 MyBatis-Plus 条件查询和 Redis 缓存，不依赖 Elasticsearch

---

## 四、团队精细化能力

### 4.1 当前已实现接口

- `POST /api/team/member/role/update`
- `POST /api/team/member/role`
- `GET /api/team/members`
- `GET /api/team/{teamId}/members`
- `POST /api/team/transfer-leader`
- `POST /api/team/transfer-captain`
- `POST /api/team/mute-all`
- `POST /api/team/member/mute`
- `POST /api/team/member/unmute`

### 4.2 角色能力边界

- 队长：可转让队长、调整成员角色、移除管理员和普通成员、解散团队
- 管理员：可审批申请、邀请成员、禁言普通成员、移除普通成员
- 普通成员：可查看成员列表、聊天、主动退队

### 4.3 角色调整设计

- 对外兼容两条路径：
  - `POST /api/team/member/role/update`
  - `POST /api/team/member/role`
- 当前可设置值仅支持：
  - `2-管理员`
  - `3-普通成员`
- 不允许直接修改队长角色

### 4.4 队长转让设计

- 当前接口：
  - `POST /api/team/transfer-leader`
  - `POST /api/team/transfer-captain`
- 两条路径最终都调用同一业务逻辑 `transferLeader`
- 转让后：
  - 原队长降为管理员
  - 目标成员升为队长

### 4.5 成员禁言设计

- `POST /api/team/mute-all`：更新 `t_team.team_all_mute`，并同步 Redis `team_all_mute:{teamId}`
- `POST /api/team/member/mute`：更新成员禁言字段，并同步 Redis `team_mute:{teamId}_{userId}`
- `POST /api/team/member/unmute`：清理成员禁言字段与缓存

---

## 五、搜索能力设计

### 5.1 当前已实现接口

- `GET /api/search/users`
- `GET /api/search/teams`
- `GET /api/search/history`
- `DELETE /api/search/history`
- `GET /api/search/hot-keywords`
- `GET /api/search/suggest`

### 5.2 搜索实现方式

- 用户搜索：基于 `user_nickname`、`user_account`、`user_tags` 的模糊匹配
- 团队搜索：基于 `team_name`、`team_tags`、`team_intro` 的模糊匹配
- 搜索联想：
  - 用户联想返回昵称或账号
  - 团队联想返回团队名

### 5.3 搜索类型约定

- `searchType=1`：用户搜索
- `searchType=2`：团队搜索
- `type=1`：用户联想
- `type=2`：团队联想

### 5.4 搜索历史与热词

- 搜索历史持久化到 `t_search_history`
- 热词持久化到 `t_search_hot_keyword`
- Redis 热词缓存 Key：`search:hot:{searchType}:{limit}`
- `SearchHotKeywordScheduler` 会定时刷新热点缓存

---

## 六、推荐能力设计

### 6.1 当前已实现接口

- `GET /api/recommend/users`
- `GET /api/recommend/teams`
- `POST /api/recommend/click`

### 6.2 推荐类型约定

- `recommendType=1`：用户推荐
- `recommendType=2`：团队推荐

### 6.3 用户推荐主要依据

- 标签相似
- 共同好友
- 私聊关系接近
- 共同团队

### 6.4 团队推荐主要依据

- 标签相似
- 共同成员
- 热门团队
- 新建团队

### 6.5 缓存设计

- 用户推荐缓存：`recommend:users:{userId}:{limit}`
- 团队推荐缓存：`recommend:teams:{userId}:{limit}`
- 支持按用户维度前缀删除，便于局部刷新

### 6.6 刷新设计

- `refreshRecommendForUser(Long userId)`：单用户刷新
- `refreshRecommendForAllUsers()`：全量刷新
- 定时任务与行为触发刷新并存

---

## 七、接口清单

### 7.1 团队精细化接口

- `GET /api/team/members?teamId={teamId}&roleType={roleType}&page=1&pageSize=20`
- `GET /api/team/{teamId}/members?page=1&pageSize=20`
- `POST /api/team/member/role/update`
- `POST /api/team/member/role`
- `POST /api/team/transfer-leader`
- `POST /api/team/transfer-captain`
- `POST /api/team/mute-all`
- `POST /api/team/member/mute`
- `POST /api/team/member/unmute`

### 7.2 搜索推荐接口

- `GET /api/search/users`
- `GET /api/search/teams`
- `GET /api/search/history`
- `DELETE /api/search/history`
- `GET /api/search/hot-keywords`
- `GET /api/search/suggest`
- `GET /api/recommend/users`
- `GET /api/recommend/teams`
- `POST /api/recommend/click`

---

## 八、未纳入当前实现的历史设计项

以下内容在旧版文档中出现过，但当前后端代码未实现：

- `POST /api/team/invite-code/generate`
- `GET /api/team/invite-codes`
- `POST /api/team/invite-code/disable`
- `POST /api/team/join-by-code`
- `POST /api/team/dynamic/publish`
- `GET /api/team/dynamics`
- `POST /api/team/dynamic/like`
- `POST /api/team/dynamic/comment`
- `POST /api/team/dynamic/pin`
- Elasticsearch 索引、Mapping、查询 DSL

如需继续保留这些内容，建议统一放入“后续版本规划”或“预留能力设计”分册，不再与当前实现混写。

---

## 九、结论

Part7 当前已经具备“团队角色分级、成员权限细化、搜索历史、热词、个性化推荐”完整能力链。文档校准后，当前版本的事实边界如下：

- 团队角色已实现到“队长 / 管理员 / 普通成员”
- 搜索采用数据库模糊查询与 Redis 缓存
- 推荐采用关系与标签混合评分
- 邀请码、团队动态、Elasticsearch 仍属于预留方案，不应作为已实现能力描述
