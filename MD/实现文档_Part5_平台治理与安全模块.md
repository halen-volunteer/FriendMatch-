# FriendMatch 实现文档 - Part 5：平台治理与安全模块

> 版本：V2.6 | 日期：2026-04-05（重写：统一平台治理、举报申诉、反馈与设备管理实现）

---

## 一、模块定位

本模块承载平台级治理与安全能力，统一收敛：

- 系统通知
- 平台处罚
- 违规统计
- 用户举报 / 团队举报
- 平台申诉
- 用户反馈
- 设备管理
- 管理员鉴权

不包含：

- 聊天基础发送 / 已读 / 未读
- 聊天消息治理（消息举报、群公告、消息管理）

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `SystemNoticeController.java` / `PunishController.java` / `ReportController.java` / `AppealController.java` / `FeedbackController.java` / `DeviceController.java` |
| Service | `PunishServiceImpl.java` / `ReportServiceImpl.java` / `AppealServiceImpl.java` / `FeedbackServiceImpl.java` / `DeviceServiceImpl.java` / `AdminAuthServiceImpl.java` |
| Mapper | `SystemNoticeMapper.java` / `UserPunishLogMapper.java` / `UserViolationCountMapper.java` / `PunishMsgRelationMapper.java` / `UserReportMapper.java` / `TeamReportMapper.java` / `AppealMapper.java` / `UserFeedbackMapper.java` / `UserDeviceMapper.java` / `AdminUserMapper.java` |

---

## 三、实现结构

### 3.1 通知实现

负责：

- 通知未读数
- 通知列表
- 批量已读
- 批量删除
- WebSocket 实时通知

### 3.2 处罚实现

负责：

- 执行处罚
- 撤销处罚
- 处罚日志查询
- 违规统计查询
- 缓存刷新

### 3.3 举报实现

负责：

- 用户举报
- 团队举报
- 举报状态查询
- 我的举报列表（通过 `/api/report/my-list` 聚合用户举报与团队举报）
- 管理员处理举报

### 3.4 申诉实现

负责：

- 提交申诉
- 轮次控制（最多 3 次）
- 管理员分配与处理
- 支持用户举报 / 消息举报 / 团队举报三类申诉
- 申诉成立后的联动撤销

### 3.5 反馈实现

负责：

- 用户提交反馈
- 管理员处理反馈
- 反馈通知回写

### 3.6 设备实现

负责：

- 绑定设备
- 设备列表
- 信任设备
- 下线设备
- 删除设备

接口对齐说明：

- `GET /api/user/devices`：返回当前用户设备列表
- 返回字段以 `deviceIp` / `deviceLocation` / `lastLoginTime` / `isActive` / `isTrusted` 为准

---

## 四、数据库实现

主要表：

- `t_admin_user`
- `t_system_notice`
- `t_user_punish_log`
- `t_user_violation_count`
- `t_punish_msg_relation`
- `t_user_report`
- `t_team_report`
- `t_appeal`
- `t_user_feedback`
- `t_user_device`

---

## 五、Redis 实现

主要 Key：

```text
user_punish:{userId}
team_mute:{teamId}_{userId}
```

说明：

- `user_punish` 用于全局禁言 / 封号校验
- `team_mute` 用于团队维度禁言校验
- 通知未读当前以数据库查询为主，可扩展缓存层

---

## 六、核心实现流程

### 6.1 执行处罚

```text
管理员/系统触发
→ 计算违规次数
→ 自动决定处罚等级
→ 写处罚日志
→ 更新用户处罚态
→ 刷新 Redis 缓存
→ 发送处罚通知
```

### 6.2 撤销处罚

```text
管理员发起
→ 查询处罚记录
→ 校验未撤销
→ 更新 is_cancel
→ 回滚处罚状态
→ 删除处罚缓存
→ 发送撤销通知
```

### 6.3 用户 / 团队举报

```text
登录校验
→ 参数校验
→ 写举报记录
→ 可选 AI 检测
→ 进入管理员处理流转
```

### 6.4 平台申诉

```text
登录校验
→ 校验申诉资格
→ 校验轮次 <= 3
→ 写申诉记录
→ 分配管理员
→ 回写审核结论
→ 必要时联动撤销处罚
```

### 6.5 搜索推荐联动说明

```text
搜索页：调用 /api/search/users、/api/search/teams、/api/search/history、/api/search/hot-keywords
推荐页：调用 /api/recommend/users、/api/recommend/teams
用户点击推荐卡片时：调用 /api/recommend/click 进行点击回传
```

### 6.6 用户反馈

```text
登录校验
→ 参数校验
→ 写反馈记录
→ 管理员处理
→ 生成反馈回复通知
```

### 6.6 设备管理

```text
登录校验
→ 读取当前用户设备列表（deviceIp / deviceLocation / lastLoginTime / isActive / isTrusted）
→ 设备归属校验
→ 支持信任 / 下线 / 删除
```

### 6.7 通知中心联动说明

```text
通知页通过 /api/notice/list 拉取通知列表
点击单条通知时调用 /api/notice/read
删除通知时调用 /api/notice/delete
全部已读时批量调用 /api/notice/read
```

### 6.8 聊天增强联动说明

```text
聊天列表页通过 /api/chat/unread-count 拉取会话未读数
进入私聊/群聊页后，前端调用 /api/chat/message/read，并按当前最后一条消息ID以 msgIds 数组格式回写已读
聊天消息气泡已接入：撤回、举报、收藏、置顶
举报与收藏已从 prompt 交互升级为正式弹层 UI
聊天页顶部已接入：消息搜索、置顶列表、收藏列表
搜索结果支持点击后滚动定位到原消息并高亮，关键词命中会高亮显示
置顶列表与收藏列表支持点击后定位到原消息
群聊页已接入：群公告查看与管理员编辑
当前活跃会话收到 WebSocket 新消息时，前端直接做消息增量插入；收到撤回事件时，直接做本地撤回状态更新
当前会话发送消息时，前端先插入本地发送中消息，服务端返回后再更新为成功状态，失败则标记失败状态
页面初始化时会优先恢复本地缓存消息，再与服务端历史去重合并，保留发送中/失败状态
发送失败消息支持在气泡区域直接重试，重试成功后完成临时消息与服务端消息替换
上传类失败消息会保留 `fileUrl/fileName/fileSize` 元数据用于重试；失败消息也支持直接删除
聊天页顶部支持一键清理当前会话全部失败消息
本地缓存增加过期清理策略，加载会话本地消息前会清除超期记录，避免 IndexedDB 持续膨胀
聊天图片/文件上传到七牛云后，除用户头像外，默认保留 14 天；到期后定时删除七牛文件，并将消息标记为已过期
管理端与普通用户端已分离：共用 `/api/auth/login` 登录，登录后依据 `t_admin_user` 判定是否进入管理端
管理端当前已接入：治理总览、用户举报管理、团队举报管理、申诉处理、处罚中心
已补充管理员操作审计日志，关键处罚/撤销处罚会写入后台审计表
前端主端与管理端整体视觉已调整为参考微信的浅灰背景、白色卡片、绿色主色的布局体系
群公告保存后，后端通过 WebSocket 广播 `group_notice_update` 给团队成员，前端实时刷新公告内容
前端轮询已降频为弱兜底模式：实时事件活跃时跳过轮询，减少对固定轮询的依赖
收到 system_notice 推送后，会实时增加通知角标
当前前端已接入：未读/已读、撤回、消息发送、历史查询、举报、收藏、置顶、搜索、群公告、管理端治理入口
WebSocket 主动断开（如退出登录、页面卸载）时，前端不会再触发自动重连
```

---

## 七、与其他分册关系

- 详细设计：`详细设计文档_Part5_平台治理与安全模块.md`
- 聊天基础：`实现文档_Part4_聊天基础与会话状态模块.md`
- 聊天治理：`实现文档_Part6_聊天增强与消息治理模块.md`

---

## 八、实现结论

平台治理与安全能力已完成聚合，不再散落在多个分册中，当前结构更适合答辩展示、维护和后续扩展。
