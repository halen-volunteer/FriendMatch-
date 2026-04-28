# part5流程说明

> 模块范围（按当前代码实现）：`ReportServiceImpl` + `PunishServiceImpl` + `AppealServiceImpl` + `DeviceServiceImpl`

---

## 一、模块说明

本流程说明对应 `Part5：平台治理与安全模块`，覆盖：

- 用户举报 / 团队举报
- 平台处罚
- 平台申诉
- 设备管理

---

## 二、核心数据表

### 1. `t_user_report`
- 用户举报主表
- 记录举报人、被举报用户、举报原因、证据、AI 校验结果、管理员处理结果

### 2. `t_team_report`
- 团队举报主表
- 记录举报团队、举报原因、管理员处理信息

### 3. `t_user_punish_log`
- 平台处罚记录
- 记录处罚类型、处罚时长、处罚原因、操作人、撤销状态

### 4. `t_user_violation_count`
- 用户违规累计次数
- 用于梯度处罚判定

### 5. `t_punish_msg_relation`
- 处罚与违规消息关联关系
- 用于追踪处罚来源消息

### 6. `t_appeal`
- 平台申诉表
- 记录申诉轮次、关联举报/处罚、管理员处理结果

### 7. `t_user_device`
- 登录设备管理表
- 记录设备标识、设备信息、IP、地点、信任状态、在线状态

---

## 三、核心流程

## A. 举报流程 `ReportServiceImpl`

### 1. `reportUser(dto)`
1) 校验登录与参数
2) 校验被举报用户存在且不能举报自己
3) 写入 `t_user_report`
4) 异步触发 AI 校验
5) 返回举报结果

### 2. `reportTeam(dto)`
1) 校验登录与参数
2) 校验被举报团队存在
3) 写入 `t_team_report`
4) 返回举报结果

### 3. `getUserReportStatus(reportId)` / `getTeamReportStatus(reportId)`
1) 校验登录与参数
2) 查询举报记录
3) 校验查看权限
4) 返回当前处理状态

### 4. `adminGetUserReportList(...)` / `adminGetTeamReportList(...)`
1) 管理员鉴权
2) 按状态分页查询举报记录
3) 返回待处理 / 已处理列表

### 5. `adminHandleUserReport(dto)` / `adminHandleTeamReport(dto)`
1) 管理员鉴权
2) 校验举报记录处于未处理状态
3) 更新举报处理结果、管理员备注、处理时间
4) 返回处理成功

---

## B. 处罚流程 `PunishServiceImpl`

### 1. `punishUser(dto)`
1) 校验系统自动或管理员手动处罚场景
2) 校验被处罚用户与处罚参数
3) 更新 `t_user_violation_count`
4) 按累计违规次数执行梯度处罚
5) 写入 `t_user_punish_log`
6) 同步 `t_user` 处罚状态
7) 写入 Redis 处罚缓存
8) 发送系统通知
9) 如有关联消息则写 `t_punish_msg_relation`

### 2. `cancelPunish(dto)`
1) 管理员鉴权
2) 校验处罚记录存在且未撤销
3) 更新处罚记录撤销状态
4) 回滚用户处罚状态
5) 删除 Redis 处罚缓存
6) 发送撤销通知

### 3. `getPunishLogs(...)` / `getMyPunishLogs(...)`
- 查询处罚记录列表

### 4. `getViolationCount(userId)`
- 查询违规累计次数

---

## C. 申诉流程 `AppealServiceImpl`

### 1. `submitAppeal(dto)`
1) 校验登录、关联举报/处罚、申诉理由
2) 校验申诉次数上限（最多 3 次）
3) 分配管理员
4) 写入 `t_appeal`
5) 返回申诉结果

### 2. `getMyAppeals(page, pageSize)`
- 查询当前用户申诉记录

### 3. `getPendingAppeals(page, pageSize)`
- 管理员查询待处理申诉

### 4. `handleAppeal(dto)`
1) 管理员鉴权
2) 校验申诉记录存在且待处理
3) 更新申诉结果
4) 若申诉成立则联动撤销处罚
5) 回写关联举报的申诉次数

---

## D. 设备流程 `DeviceServiceImpl`

### 1. `bindDevice(dto)`
1) 校验登录与 `deviceId`
2) 若设备不存在则新增
3) 若已存在则更新设备信息与最近登录时间
4) 返回绑定结果

### 2. `getMyDevices()`
- 查询当前用户设备列表

### 3. `trustDevice(deviceId)`
- 将设备标记为信任设备

### 4. `logoutDevice(deviceId)`
- 将设备状态更新为离线

### 5. `deleteDevice(deviceId)`
- 软删除设备并置离线

---

## 四、缓存说明

### 1. 用户处罚缓存
- Key：`user_punish:{userId}`
- 值格式：`punishType|endTime`

### 2. 团队禁言缓存
- Key：`team_mute:{teamId}_{userId}`
- 用于团队维度禁言控制

---

## 五、边界说明

归 `Part5`：
- 用户举报 / 团队举报
- 平台处罚
- 平台申诉
- 用户反馈
- 设备管理
- 风控增强能力

不归 `Part5`：
- 消息举报、消息申诉、群公告、消息治理闭环 → `Part6`
- 搜索、热词、推荐 → `Part7`
