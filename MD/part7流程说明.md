# part7流程说明

> 模块范围（按当前代码实现）：`SearchRecommendServiceImpl`

---

## 一、模块说明

本流程说明对应 `Part7：团队管理精细化与搜索推荐模块`，当前重点覆盖：

- 用户搜索
- 团队搜索
- 搜索历史
- 热门关键词
- 搜索建议
- 用户推荐
- 团队推荐

---

## 二、核心数据表

### 1. `t_search_history`
- 搜索历史表
- 记录用户搜索关键词、搜索类型、累计次数、最近搜索时间

### 2. `t_search_hot_keyword`
- 热搜关键词表
- 记录不同类型关键词的热度计数

### 3. `t_user_recommendation`
- 用户推荐结果表
- 记录推荐对象、推荐接收人、推荐原因、推荐分、点击状态

### 4. `t_team_recommendation`
- 团队推荐结果表
- 记录推荐团队、推荐原因、推荐分、点击/加入状态

### 5. 推荐计算依赖表
- `t_user`
- `t_team`
- `t_team_member`
- `t_user_friend`
- `t_user_blacklist`
- `t_chat_message`

---

## 三、核心流程

## A. 搜索流程

### 1. `searchUsers(keyword, page, pageSize)`
1) 校验登录、关键词标准化、分页参数兜底
2) 按昵称/账号/标签模糊查用户
3) 过滤自己与黑名单关系用户
4) 计算匹配分并排序
5) 记录搜索历史与热词
6) 返回搜索结果

### 2. `searchTeams(keyword, page, pageSize)`
1) 校验登录与分页参数
2) 按团队名/标签/简介模糊查团队
3) 计算匹配分并排序
4) 记录搜索历史与热词
5) 返回团队搜索结果

### 3. `getSearchHistory(searchType, limit)`
1) 校验登录与参数
2) 查询 `t_search_history`
3) 按最近搜索时间倒序返回

### 4. `clearSearchHistory()`
1) 校验登录
2) 将当前用户搜索历史软删除
3) 返回清空成功

### 5. `getHotKeywords(searchType, limit)`
1) 先查 Redis 热词缓存
2) 未命中则查 `t_search_hot_keyword`
3) 动态组装排名并回填缓存
4) 返回热词列表

### 6. `suggestSearch(keyword, type, limit)`
1) 校验登录、类型、关键词
2) 按类型查用户或团队候选项
3) 去重并截断
4) 返回联想建议

---

## B. 推荐流程

### 1. `getRecommendUsers(limit)`
1) 校验登录并读取缓存
2) 构建当前用户画像（标签、好友、私聊对象、共同团队）
3) 软删除旧推荐记录
4) 遍历候选用户并计算推荐分
5) 写入 `t_user_recommendation`
6) 排序、截断并写缓存
7) 返回推荐用户列表

### 2. `getRecommendTeams(limit)`
1) 校验登录并读取缓存
2) 准备已加入团队、用户标签、成员重叠集合
3) 软删除旧团队推荐记录
4) 计算标签分、成员重叠分、热度分、新鲜度分
5) 写入 `t_team_recommendation`
6) 排序、截断并写缓存
7) 返回推荐团队列表

### 3. `recordRecommendClick(recommendId, recommendType)`
1) 校验登录与参数
2) 根据类型更新用户推荐或团队推荐点击状态
3) 返回处理结果

### 4. `refreshRecommendForUser(userId)`
1) 删除该用户推荐缓存
2) 主动重建用户推荐与团队推荐
3) 清理线程上下文

### 5. `refreshRecommendForAllUsers()`
- 为所有有效用户重建推荐缓存

---

## 四、评分与缓存

### 1. 用户推荐评分
- 标签分：40
- 共同好友：30
- 聊天重叠：20
- 共同团队：10
- 总分封顶：100

### 2. 团队推荐评分
- 标签分：40
- 成员重叠：30
- 热度分：20
- 新鲜度分：10
- 总分封顶：100

### 3. 热词缓存
- Key：`search:hot:{searchType}:{limit}`
- TTL：约 60 分钟 + 随机偏移

### 4. 推荐缓存
- 用户推荐：`recommend:users:{userId}:{limit}`
- 团队推荐：`recommend:teams:{userId}:{limit}`
- TTL：约 24 小时 + 随机偏移

---

## 五、边界说明

归 `Part7`：
- 搜索历史
- 热门搜索词
- 搜索建议
- 用户推荐
- 团队推荐

不归 `Part7`：
- 用户举报 / 团队举报 / 申诉 / 处罚 / 设备管理 → `Part5`
- 聊天发送、已读未读、消息治理 → `Part4` / `Part6`
