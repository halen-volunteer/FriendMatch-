# FriendMatch 综合实现文档 - Part 5：平台治理与安全模块

> 版本：V2.6 | 日期：2026-04-05（重写：合并平台治理、举报申诉与设备安全实现总览）

---

## 一、模块总览

本分册统一描述平台级治理与安全实现：

- 通知
- 处罚
- 用户 / 团队举报
- 平台申诉
- 用户反馈
- 设备管理
- 管理员鉴权

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `SystemNoticeController.java` / `PunishController.java` / `ReportController.java` / `AppealController.java` / `FeedbackController.java` / `DeviceController.java` |
| Service | `PunishServiceImpl.java` / `ReportServiceImpl.java` / `AppealServiceImpl.java` / `FeedbackServiceImpl.java` / `DeviceServiceImpl.java` / `AdminAuthServiceImpl.java` |
| Mapper | `SystemNoticeMapper.java` / `UserPunishLogMapper.java` / `UserViolationCountMapper.java` / `UserReportMapper.java` / `TeamReportMapper.java` / `AppealMapper.java` / `UserFeedbackMapper.java` / `UserDeviceMapper.java` / `AdminUserMapper.java` |

---

## 三、核心能力

### 3.1 系统通知

- 未读数
- 列表
- 已读
- 删除
- 实时推送

### 3.2 平台处罚

- 执行处罚
- 撤销处罚
- 处罚记录
- 违规统计

### 3.3 举报与申诉

- 用户举报
- 团队举报
- 平台申诉
- 管理员审核

### 3.4 反馈与设备

- 用户反馈
- 设备绑定
- 设备列表
- 信任设备
- 设备下线与删除

---

## 四、阅读指引

详细设计对应：

- `详细设计文档_Part5_平台治理与安全模块.md`

实现文档对应：

- `实现文档_Part5_平台治理与安全模块.md`
