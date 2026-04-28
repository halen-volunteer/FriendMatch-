# FriendMatch 详细设计文档 - Part 5：平台治理与安全模块

> 版本：V2.5 | 日期：2026-04-05（更新：合并原 Part5 与 Part8，统一通知、处罚、举报、申诉、设备与风控设计）

---

## 一、模块概述

本模块负责平台级治理与安全能力，覆盖：

- 系统通知
- 用户处罚与处罚撤销
- 违规统计
- 用户举报 / 团队举报
- 平台申诉机制
- 用户反馈
- 设备管理
- 后续风控扩展（二次验证、操作审计）

与聊天域边界说明：

- 消息举报、消息申诉、管理员审核消息上下文 → 归 `Part6`
- 用户举报、团队举报、平台处罚、系统通知、设备风控 → 归 `Part5`

---

## 二、当前模块边界

### 2.1 本模块负责

- 平台通用通知能力
- 平台通用处罚能力
- 平台通用举报与申诉能力
- 设备安全与登录设备管理
- 安全增强预留能力

### 2.2 本模块不负责

- 私聊 / 群聊发送与历史 → `Part4`
- 消息编辑 / 撤回 / 收藏 / 置顶 / 搜索 → `Part6`
- 消息举报治理闭环 → `Part6`

---

## 三、数据库设计

### 3.1 `t_system_notice`（系统通知表）

**关键字段**：

- `user_id`
- `notice_type`
- `notice_content`
- `related_id`
- `is_read`
- `read_time`
- `is_delete`

**通知类型建议统一为**：

- 1：好友申请通知
- 2：好友申请被拒绝
- 3：入群审批通过
- 4：入群审批拒绝
- 5：被移出团队
- 6：账号异常
- 7：处罚通知
- 8：反馈回复
- 9：消息 @提醒通知

### 3.2 `t_user_punish_log`（处罚记录表）

**关键字段**：

- `punish_user_id`
- `punish_type`（1-单团队禁言，2-全局禁言，3-永久封号）
- `team_id`
- `punish_reason`
- `punish_duration`
- `punish_start_time`
- `punish_end_time`
- `operate_type`
- `operate_user_id`
- `is_cancel`
- `cancel_time`
- `cancel_user_id`

### 3.3 `t_user_violation_count`（违规统计表）

**关键字段**：

- `user_id`
- `total_violation_num`
- `latest_violation_time`
- `reset_time`

### 3.4 `t_punish_msg_relation`（处罚-消息关联表）

**关键字段**：

- `punish_log_id`
- `msg_id`
- `ai_audit_result`
- `create_time`

> 说明：该表仍保留在平台治理域，因为处罚是平台共用能力，可能由消息举报、用户举报等多个治理入口触发。

### 3.5 `t_user_report`（用户举报表）

**关键字段**：

- `reporter_id`
- `reported_user_id`
- `report_reason`
- `report_content`
- `report_evidence`
- `ai_check_result`
- `ai_check_time`
- `ai_confidence`
- `report_status`
- `admin_action`
- `admin_note`
- `admin_id`
- `process_time`
- `appeal_count`

### 3.6 `t_team_report`（团队举报表）

**关键字段**：

- `reporter_id`
- `reported_team_id`
- `report_reason`
- `report_content`
- `report_evidence`
- `report_status`
- `admin_action`
- `admin_note`
- `admin_id`
- `process_time`

### 3.7 `t_appeal`（申诉表）

**关键字段**：

- `appellant_id`
- `appellant_type`
- `related_report_id`
- `related_report_type`
- `related_punish_id`
- `appeal_round`
- `appeal_reason`
- `appeal_evidence`
- `appeal_status`
- `admin_id`
- `admin_reply`
- `process_time`

> `related_report_type` 说明：
>
>- 1：用户举报
>- 2：消息举报
>- 3：团队举报

### 3.8 `t_user_feedback`（用户反馈表）

**关键字段**：

- `user_id`
- `feedback_type`
- `feedback_content`
- `feedback_img`
- `handle_user_id`
- `handle_status`
- `handle_content`
- `handle_time`

### 3.9 `t_user_device`（用户设备表）

**关键字段**：

- `user_id`
- `device_id`
- `device_name`
- `device_type`
- `device_os`
- `device_browser`
- `device_ip`
- `device_location`
- `last_login_time`
- `is_trusted`
- `is_active`
- `is_delete`

### 3.10 预留表

以下能力继续作为后续优化预留：

- `t_user_verification`：二次验证
- `t_operation_log`：敏感操作审计
- `t_team_rating`：团队评价

---

## 四、Redis 设计

### 4.1 用户全局处罚缓存

```text
Key: user_punish:{userId}
Type: String
说明：缓存全局禁言 / 封号状态
TTL: 5分钟（可带随机抖动）
```

### 4.2 团队成员禁言缓存

```text
Key: team_mute:{teamId}_{userId}
Type: String
说明：缓存单团队禁言状态
TTL: 5分钟（可带随机抖动）
```

### 4.3 通知未读聚合（可选扩展）

```text
Key: notice_unread:{userId}
Type: Int
说明：可选的通知未读数缓存层；当前以数据库查询为主
TTL: 短 TTL 或按事件更新
```

---

## 五、业务流程设计

### 5.1 系统通知流程

典型通知场景包括：

- 好友申请
- 入群审批
- 被移出团队
- 处罚通知
- 反馈回复
- @提醒通知

基础流程：

1. 业务模块产生命中事件
2. 写入 `t_system_notice`
3. 若用户在线则 WebSocket 推送
4. 用户查询通知列表或未读数
5. 用户标记已读 / 删除

---

### 5.2 平台处罚流程

处罚规则：

- 第1次违规：禁言 1 小时
- 第2次违规：禁言 1 天
- 第3次违规：禁言 7 天
- 第4次及以上：永久封号

流程：

1. 举报或审核流程确认违规
2. 查询 `t_user_violation_count`
3. 计算处罚等级
4. 写入 `t_user_punish_log`
5. 更新用户或团队成员处罚状态
6. 删除或刷新 Redis 处罚缓存
7. 发送处罚通知

---

### 5.3 撤销处罚流程

流程：

1. 管理员发起撤销
2. 校验处罚记录存在且未撤销
3. 更新 `is_cancel=1`
4. 回滚用户 / 团队维度处罚状态
5. 删除 Redis 中处罚缓存
6. 发送撤销通知
7. 返回成功

---

### 5.4 用户举报流程

接口：`POST /api/report/user`

流程：

1. 登录校验
2. 校验被举报用户存在
3. 防重复 / 防恶意刷举报
4. 写入 `t_user_report`
5. 可选异步 AI 检测
6. 进入管理员处理队列
7. 举报人可通过 `GET /api/report/my-list?page=1&pageSize=20` 查看自己的举报记录聚合列表

---

### 5.5 团队举报流程

接口：`POST /api/report/team`

流程：

1. 登录校验
2. 校验团队存在
3. 写入 `t_team_report`
4. 进入管理员处理队列
5. 返回成功

---

### 5.6 平台申诉流程

接口：`POST /api/appeal/submit`

关键入参：

- `relatedReportId`
- `relatedReportType`（1-用户举报，2-消息举报，3-团队举报）
- `appellantType`
- `appealReason`
- `relatedPunishId`（可选）
- `appealEvidence`（可选）

流程：

1. 登录校验
2. 校验当前用户是否有申诉资格
3. 校验申诉轮次不超过 3 次
4. 写入 `t_appeal`
5. 为当前轮次分配不同管理员
6. 管理员审核后写回结论
7. 若申诉成立，可联动撤销处罚

---

### 5.7 用户反馈流程

接口：`POST /api/feedback/submit`

流程：

1. 登录校验
2. 参数校验
3. 写入 `t_user_feedback`
4. 管理员处理并填写回复
5. 生成反馈回复通知

---

### 5.8 设备管理流程

典型能力：

- 设备绑定
- 登录设备列表
- 信任设备
- 设备下线
- 删除设备

流程：

1. 登录或主动绑定时写入 / 更新 `t_user_device`
2. 设备列表返回字段以 `deviceIp`、`deviceLocation`、`lastLoginTime`、`isActive`、`isTrusted` 为准
3. 高风险设备可标记非信任
4. 用户可主动下线或删除设备

---

### 5.9 搜索推荐流程

典型能力：

- 用户搜索
- 团队搜索
- 搜索历史
- 热门关键词
- 推荐用户 / 推荐团队
- 推荐点击回传

流程：

1. 搜索页调用 `/api/search/users`、`/api/search/teams`
2. 辅助区读取 `/api/search/history` 与 `/api/search/hot-keywords`
3. 推荐页调用 `/api/recommend/users` 与 `/api/recommend/teams`
4. 用户点击推荐卡片时调用 `/api/recommend/click`
5. 后端基于推荐记录ID回写点击状态

---

### 5.10 通知与聊天联动流程

典型能力：

- 通知列表
- 通知未读数
- 单条/批量已读
- 通知删除
- 会话未读数
- 进入会话后的消息已读回写
- 消息举报 / 收藏 / 置顶
- 消息搜索与结果定位
- 置顶 / 收藏定位
- 搜索关键词高亮
- 群公告查看 / 编辑
- 正式弹层交互
- WebSocket 实时刷新与通知角标联动
- 群公告广播
- 弱兜底轮询
- 当前会话消息增量插入
- 发送消息乐观插入
- 本地缓存恢复与去重合并
- 失败消息重试
- 失败消息删除
- 上传消息元数据恢复
- 失败消息批量清理
- 本地缓存过期清理
- 七牛文件 14 天过期删除
- 管理端登录分流
- 管理员举报/申诉/处罚入口

流程：

1. 通知页调用 `/api/notice/list`
2. 点击通知或全部已读时调用 `/api/notice/read`
3. 删除通知时调用 `/api/notice/delete`
4. 聊天列表页调用 `/api/chat/unread-count`
5. 进入私聊/群聊页后调用 `/api/chat/message/read`
6. 前端以 `msgIds` 数组格式提交当前最后一条已读消息ID
7. 消息气泡通过正式弹层支持调用 `/api/chat/message/report`、`/api/chat/message/collect`、`/api/chat/message/pin`
8. 聊天页顶部支持调用 `/api/chat/message/search`、`/api/chat/message/pins`、`/api/chat/message/collections`
9. 搜索结果点击后，前端滚动定位到目标消息并做短暂高亮，命中关键词用高亮样式渲染
10. 置顶列表、收藏列表点击后，前端也可定位到原消息
11. 群聊页支持调用 `/api/chat/group/notice` 查看与更新群公告
12. 群公告更新后，后端通过 WebSocket 广播 `group_notice_update` 给团队全部成员
13. 当前活跃会话收到 WebSocket 新消息时，前端直接增量插入到消息列表；收到撤回事件时，前端直接标记本地消息为撤回
14. 当前会话发送消息时，前端先插入 `localStatus=0` 的临时消息，接口成功后更新为成功消息，失败则更新为 `localStatus=2`
15. 页面初始化时，前端先从 IndexedDB 恢复当前会话本地消息，再与服务端历史按 `msgId` 去重合并
16. 发送失败消息时，用户可在消息气泡点击“重试发送”，前端复用原消息内容再次发送，并在成功后完成映射替换
17. 上传类失败消息会保留 `fileUrl`、`fileName`、`fileSize` 元数据，以便重试时继续提交完整文件信息
18. 用户可直接删除失败消息，前端同时移除页面状态与本地缓存记录
19. 用户可在聊天页顶部一键清理当前会话全部失败消息
20. 前端在读取本地消息前会清理超出保留周期的缓存记录，控制 IndexedDB 体积增长
21. 聊天图片/文件上传到七牛云后，头像除外，默认保留 14 天；后端定时任务会删除七牛文件并把消息内容标记为 `expired=true`
22. 当前端读取到 `expired=true` 的图片/文件消息时，显示“该文件已过期”而不是继续访问原链接
23. 登录后后端基于 `t_admin_user` 补充 `isAdmin/adminId/adminName`，前端根据该字段分流到普通端或管理端
24. 管理端当前接入 `/api/report/admin/user/list`、`/api/report/admin/user/handle`、`/api/report/admin/team/list`、`/api/report/admin/team/handle`、`/api/appeal/pending`、`/api/appeal/handle`、`/api/punish/execute`、`/api/punish/cancel`、`/api/punish/logs`、`/api/punish/violation-count`
25. 管理端提供治理总览页面，聚合待处理用户举报、团队举报、申诉数量，作为后台默认首页
26. 管理端已补充最小管理员操作审计日志，处罚执行与撤销会写入 `t_admin_audit_log`
27. 前端普通用户端和管理端的视觉风格已统一调整为接近微信的布局语言：浅灰背景、白卡片、绿色主按钮、左侧会话导航
28. 仅在实时事件不足时，前端才使用弱兜底轮询补齐，减少整列表重拉
29. 收到 `system_notice` 推送后，前端实时增加通知未读角标
30. 前端基于会话ID清空本地未读计数
31. WebSocket 主动关闭时不再自动重连，避免登出后残留连接

---

## 六、与其他分册边界

- `Part4`：聊天基础能力、会话状态
- `Part6`：消息管理、消息举报、群公告等聊天增强治理能力
- 原 `Part8` 内容已并入本分册

---

## 七、文档合并说明

为减少重复与边界冲突，自本版本起：

- 原 `Part5_系统通知与处罚管理模块`
- 原 `Part8_安全与体验补充模块`

统一合并为：

- `Part5_平台治理与安全模块`

这样处理后：

- 平台通用治理能力集中描述
- 聊天消息治理保留在 `Part6`
- 设备与风控不再散落于单独分册

---

## 八、验收标准

本模块完成后应满足：

- 系统通知、处罚、举报、申诉、反馈、设备管理边界清晰
- 与聊天消息治理的职责分工明确
- 不再与 `Part6` 出现大段内容重复
