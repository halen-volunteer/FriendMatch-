# FriendMatch 实现文档 - Part 6：聊天增强与消息治理模块

> 版本：V2.6 | 日期：2026-04-05（重写：统一消息管理、群公告与消息治理实现）

---

## 一、模块定位

本模块承载聊天域增强能力，位于聊天基础能力之上，负责：

- 多媒体消息配套上传
- 编辑 / 撤回
- 收藏 / 置顶 / 搜索
- 群公告
- 消息举报 / 申诉 / 管理员审核

不包含：

- 私聊 / 群聊发送
- 历史查询
- 已读 / 未读统计
- 用户举报 / 团队举报 / 设备管理

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `ChatController.java` / `OssController.java` |
| Service | `ChatMessageManageService.java` / `ChatReportManageService.java` / `ChatNoticeService.java` / `AiCheckServiceImpl.java` / `OssServiceImpl.java` |
| Mapper | `MessageCollectionMapper.java` / `MessagePinMapper.java` / `MessageReportMapper.java` |
| Model | `MessageCollection.java` / `MessagePin.java` / `MessageReport.java` |
| DTO | `MessageCollectDTO.java` / `MessagePinDTO.java` / `MessageReportDTO.java` / `MessageReportHandleDTO.java` / `GroupNoticeDTO.java` / `OssPresignDTO.java` |

---

## 三、实现结构

### 3.1 `ChatMessageManageService`

负责：

- 编辑消息
- 撤回消息
- 收藏 / 取消收藏
- 收藏列表
- 置顶 / 取消置顶
- 置顶列表
- 会话内搜索

### 3.2 `ChatReportManageService`

负责：

- 举报消息
- 查询举报状态
- 提交申诉
- 查询消息上下文
- 管理员分页查询举报列表
- 管理员处理消息举报

### 3.3 `ChatNoticeService`

负责：

- 设置群公告
- 获取群公告

### 3.4 `OssServiceImpl`

负责：

- 生成上传凭证 / 预签名信息
- 校验文件类型和大小
- 返回 `fileUrl`

---

## 四、数据库实现

主要表：

- `t_message_collection`
- `t_message_pin`
- `t_message_report`

---

## 五、Redis 实现

主要 Key：

```text
group_notice:{conversationId}
last_msg:{conversationId}
```

说明：

- `group_notice` 缓存群公告
- `last_msg` 为收藏 / 搜索 / 未读列表等场景提供上下文支撑

---

## 六、核心实现流程

### 6.1 OSS 上传配套

```text
前端申请上传凭证
→ 校验文件类型与大小
→ 返回上传地址和 fileUrl
→ 前端完成对象存储上传
→ 将 fileUrl 传给聊天发送接口
```

### 6.2 编辑消息

```text
登录校验
→ 查询消息
→ 校验发送者身份
→ 校验 5 分钟编辑窗口
→ 更新内容与编辑标记
→ 推送 message_edit
```

### 6.3 撤回消息

```text
登录校验
→ 查询消息
→ 校验发送者身份
→ 校验 5 分钟撤回窗口
→ 检查是否存在待处理举报
→ 删除消息及关联记录
→ 推送 message_revoke
```

### 6.4 收藏 / 置顶 / 搜索

```text
收藏：消息存在校验 → 防重复 → 写收藏记录
置顶：会话权限校验 → 防重复 → 写 pin_order
搜索：会话权限校验 → LIKE 检索 → 过滤无效消息
```

### 6.5 群公告

```text
设置：管理员/队长校验 → 写 Redis group_notice
获取：成员身份校验 → 读 Redis
```

### 6.6 消息举报治理

```text
提交举报
→ 写 t_message_report
→ 异步 AI 检测
→ 管理员查看上下文
→ 审核处理
→ 必要时联动处罚
→ 支持最多 3 次申诉
```

---

## 七、与其他分册关系

- 详细设计：`详细设计文档_Part6_聊天增强与消息治理模块.md`
- 聊天基础：`实现文档_Part4_聊天基础与会话状态模块.md`
- 平台治理：`实现文档_Part5_平台治理与安全模块.md`

---

## 八、实现结论

聊天增强能力已经从聊天基础链路中剥离，形成独立的消息管理与消息治理层，结构清晰，便于继续扩展与答辩呈现。
