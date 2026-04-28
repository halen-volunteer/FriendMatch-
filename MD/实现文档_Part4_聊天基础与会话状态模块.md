# FriendMatch 实现文档 - Part 4：聊天基础与会话状态模块

> 版本：V2.6 | 日期：2026-04-05（重写：与当前聊天拆分结构完全对齐）

---

## 一、模块定位

本模块负责聊天域的基础能力，不再承担消息治理与平台治理职责。

核心范围：

- 私聊发送
- 群聊发送
- 私聊历史查询
- 群聊历史查询
- 已读回执
- 未读聚合统计
- 会话级基础缓存

不包含：

- 编辑 / 撤回 / 收藏 / 置顶 / 搜索
- 群公告
- 消息举报 / 申诉 / 审核
- 平台处罚 / 用户举报 / 设备管理

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `ChatController.java` |
| Service | `ChatService.java` / `ChatServiceImpl.java` / `ChatSendService.java` / `ChatReadService.java` / `ChatSupportService.java` |
| Mapper | `ChatMessageMapper.java` / `MessageReadReceiptMapper.java` |
| Model | `ChatMessage.java` / `MessageReadReceipt.java` |
| DTO | `PrivateMsgDTO.java` / `TeamMsgDTO.java` / `MsgReadDTO.java` |
| WebSocket | `ChatWebSocketHandler.java` / `WebSocketConfig.java` |

---

## 三、实现结构

### 3.1 `ChatServiceImpl`

当前实现中，`ChatServiceImpl` 已收敛为门面层，主要职责是：

- 对外统一实现 `ChatService`
- 将基础聊天请求委托给子服务
- 维持接口稳定性

### 3.2 `ChatSendService`

负责：

- `sendPrivateMsg(...)`
- `getPrivateHistory(...)`
- `sendTeamMsg(...)`
- `getTeamHistory(...)`

### 3.3 `ChatReadService`

负责：

- `markMsgRead(...)`
- `getUnreadCount(...)`

### 3.4 `ChatSupportService`

提供基础复用能力：

- 登录态获取
- 会话 ID 构建与解析
- 团队成员查询
- 消息内容序列化
- 回执写入
- 已读处理
- 最后一条消息缓存
- 推送内容组装

---

## 四、数据库实现

### 4.1 `t_chat_message`

承载：

- 私聊消息
- 群聊消息
- 文本 / 图片 / 文件 / 表情包 / @消息内容

关键字段：

- `sender_id`
- `recv_type`
- `recv_id`
- `conversation_id`
- `msg_type`
- `msg_content`
- `is_delete`

### 4.2 `t_message_read_receipt`

承载：

- 送达回执
- 已读回执

关键字段：

- `msg_id`
- `user_id`
- `receipt_type`
- `receipt_time`

---

## 五、Redis 实现

使用到的主要 Key：

```text
msg_read:{msgId}
msg_deliver:{msgId}
unread:{userId}:{conversationId}
last_msg:{conversationId}
user_punish:{userId}
```

说明：

- `unread` 已按“用户 + 会话”维度维护
- `last_msg` 用于未读会话摘要展示
- `user_punish` 在发送阶段参与禁言校验

---

## 六、核心实现流程

### 6.1 私聊发送

```text
鉴权
→ 参数校验
→ 黑名单校验
→ 对方隐私校验
→ 全局禁言校验
→ 敏感词检测
→ 写 t_chat_message
→ 写送达回执
→ 自增 unread:{recipientId}:{conversationId}
→ 刷新 last_msg:{conversationId}
→ WebSocket 推送
```

### 6.2 群聊发送

```text
鉴权
→ 团队成员校验
→ 全局禁言校验
→ 全员禁言 / 个人禁言校验
→ @成员校验
→ 敏感词检测
→ 写 t_chat_message
→ 批量写送达回执
→ 对成员逐个维护 unread
→ 刷新 last_msg
→ WebSocket 广播
→ @通知
```

### 6.3 历史查询

```text
鉴权
→ 构建 conversationId
→ 分页查消息
→ 异步标记已读
→ 返回 records + total
```

### 6.4 标记已读

```text
鉴权
→ 参数校验
→ 批量写已读回执
→ 清空当前用户该会话 unread
→ 群聊写 msg_read Bitmap
→ 返回成功
```

### 6.5 获取未读统计

```text
鉴权
→ SCAN unread:{userId}:*
→ 聚合 totalUnread
→ 读取 last_msg 预览
→ 返回 conversations
```

---

## 七、与其他分册关系

- 详细设计：`详细设计文档_Part4_聊天基础与会话状态模块.md`
- 增强治理：`实现文档_Part6_聊天增强与消息治理模块.md`
- 平台安全：`实现文档_Part5_平台治理与安全模块.md`

---

## 八、实现结论

当前聊天基础链路已从单体大类中完成拆分，具备清晰的职责边界，是后续继续演进聊天模块的稳定底座。
