# FriendMatch 综合实现文档 - Part 6：聊天增强与消息治理模块

> 版本：V2.6 | 日期：2026-04-05（补齐：从聊天基础分册中拆出增强层总览）

---

## 一、模块总览

本分册对应 `Part6`，负责聊天域中的增强与治理能力，覆盖：

- 多媒体消息上传配套
- 消息编辑 / 撤回
- 消息收藏 / 置顶 / 搜索
- 群公告
- 消息举报 / 申诉 / 管理员审核

本分册不包含：

- 私聊 / 群聊发送
- 历史记录查询
- 已读 / 未读统计
- 用户举报 / 团队举报 / 设备管理

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `ChatController.java` / `OssController.java` |
| Service | `ChatMessageManageService.java` / `ChatReportManageService.java` / `ChatNoticeService.java` / `AiCheckServiceImpl.java` / `OssServiceImpl.java` |
| Mapper | `MessageCollectionMapper.java` / `MessagePinMapper.java` / `MessageReportMapper.java` |

---

## 三、核心能力

### 3.1 多媒体消息配套

- 上传凭证生成
- 文件类型校验
- 文件大小限制
- 返回可落库 `fileUrl`

### 3.2 消息管理

- 编辑消息
- 撤回消息
- 收藏 / 取消收藏
- 置顶 / 取消置顶
- 会话内搜索

### 3.3 群公告

- 设置群公告
- 获取群公告
- Redis 公告缓存

### 3.4 消息治理

- 举报消息
- AI 检测
- 管理员查看消息上下文
- 管理员处理举报
- 用户发起申诉
- 联动平台处罚

---

## 四、实现结构

- `ChatMessageManageService`：编辑、撤回、收藏、置顶、搜索
- `ChatReportManageService`：举报、申诉、审核、上下文查询
- `ChatNoticeService`：群公告设置与读取
- `OssServiceImpl`：对象存储上传凭证与文件校验

---

## 五、数据与缓存

主要表：

- `t_message_collection`
- `t_message_pin`
- `t_message_report`

主要缓存：

- `group_notice:{conversationId}`
- `last_msg:{conversationId}`

---

## 六、核心流程

### 6.1 OSS 上传流程

```text
前端申请预签名
→ 服务端校验文件类型与大小
→ 返回 presignUrl 与 fileUrl
→ 前端完成对象存储上传
→ 聊天消息引用 fileUrl 发送
```

### 6.2 编辑 / 撤回流程

```text
登录校验
→ 查询消息
→ 校验发送者身份
→ 校验 5 分钟窗口
→ 更新消息或执行撤回
→ 推送消息变更事件
```

### 6.3 收藏 / 置顶 / 搜索流程

```text
校验消息与会话权限
→ 防重复处理
→ 写入收藏 / 置顶记录或执行搜索
→ 返回结果列表
```

### 6.4 消息举报治理流程

```text
提交消息举报
→ 写举报记录
→ 异步 AI 检测
→ 管理员查看上下文
→ 审核处理
→ 必要时联动处罚
→ 支持最多 3 次申诉
```

---

## 七、阅读指引

详细设计对应：

- `详细设计文档_Part6_聊天增强与消息治理模块.md`

实现文档对应：

- `实现文档_Part6_聊天增强与消息治理模块.md`

关联分册：

- `综合实现Part4_聊天基础与会话状态模块.md`
- `综合实现Part5_平台治理与安全模块.md`

---

## 八、实现结论

聊天增强能力已从聊天基础链路中清晰拆分，形成独立的“消息管理 + 消息治理”层，便于继续扩展、维护和答辩展示。
