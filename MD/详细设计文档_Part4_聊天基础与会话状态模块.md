# FriendMatch 详细设计文档 - Part 4：聊天基础与会话状态模块

> 版本：V2.5 | 日期：2026-04-05（更新：对齐聊天三轮拆分后的实现结构，收敛为聊天基础能力与会话状态）

---

## 一、模块概述

本模块负责聊天域的基础通信能力与会话状态管理，重点覆盖：

- 私聊发送与历史查询
- 群聊发送与历史查询
- 消息送达 / 已读状态
- 未读消息聚合统计
- 会话级基础缓存与推送

本模块不再承载以下增强能力，它们已拆分到其他分册：

- 消息管理（编辑 / 撤回 / 收藏 / 置顶 / 搜索）→ `Part6`
- 消息举报与申诉审核 → `Part6`
- 平台处罚、系统通知、用户/团队举报、设备风控 → `Part5`

---

## 二、当前实现结构

### 2.1 服务分层

当前聊天实现已从单体大类拆分为以下结构：

- `ChatServiceImpl`：聊天门面层，对外统一实现 `ChatService`
- `ChatSendService`：发送与历史查询
- `ChatReadService`：已读、未读统计
- `ChatNoticeService`：群公告
- `ChatMessageManageService`：消息管理增强能力
- `ChatReportManageService`：消息举报、申诉、审核
- `ChatSupportService`：聊天公共支撑能力

### 2.2 本分册重点覆盖范围

本分册只描述以下两块：

1. `ChatSendService`
2. `ChatReadService`

---

## 三、数据库设计

### 3.1 `t_chat_message`（聊天消息表）

**关键字段**：

- `sender_id`：发送人ID
- `recv_type`：1-私聊，2-群聊
- `recv_id`：私聊对方ID或团队ID
- `conversation_id`：会话ID
  - 私聊：`min(uid1, uid2)_max(uid1, uid2)`
  - 群聊：`team_{teamId}`
- `msg_type`：1-文本，2-图片，3-文件，4-表情包，5-@消息
- `msg_content`：消息内容
  - 文本 / @消息：纯文本
  - 图片：`{"url":"...","caption":"..."}`
  - 文件：`{"url":"...","name":"...","size":123}`
  - 表情包：`{"emojiId":"emoji_001"}`
- `is_delete`：软删除标记
- `create_time`、`update_time`

### 3.2 `t_message_read_receipt`（消息回执表）

**关键字段**：

- `msg_id`：消息ID
- `user_id`：用户ID
- `receipt_type`：1-送达，2-已读
- `receipt_time`：回执时间

**关键索引**：

- `uk_msg_user_type (msg_id, user_id, receipt_type)`

---

## 四、Redis 设计

### 4.1 群聊已读 Bitmap

```text
Key: msg_read:{msgId}
Type: Bitmap
说明：按 userId 置位，表示群聊消息是否已读
TTL: 7天
```

### 4.2 群聊已送达 Bitmap

```text
Key: msg_deliver:{msgId}
Type: Bitmap
说明：按 userId 置位，表示群聊消息是否已送达
TTL: 7天
```

### 4.3 会话未读消息数

```text
Key: unread:{userId}:{conversationId}
Type: Int
说明：按“用户 + 会话”维度记录未读数
TTL: 无固定 TTL，消息读取后删除或归零
```

### 4.4 最后一条消息缓存

```text
Key: last_msg:{conversationId}
Type: Hash
说明：缓存会话最后一条消息，用于未读会话预览
TTL: 30天
```

### 4.5 群公告缓存

```text
Key: group_notice:{conversationId}
Type: String
说明：缓存群公告内容
TTL: 30天
```

### 4.6 用户全局处罚缓存

```text
Key: user_punish:{userId}
Type: String
说明：缓存用户全局禁言 / 封号状态
TTL: 5分钟（带随机抖动）
```

---

## 五、业务流程设计

### 5.1 私聊发送流程

接口：`POST /api/chat/private/send`

**流程**：

1. 参数校验（按 `msgType` 校验必填字段）
2. 检查双向黑名单
3. 检查对方隐私设置 `sendMsg`
4. 检查全局禁言
5. 文本消息执行敏感词检测
6. 构建 `conversation_id`
7. 调用 `buildStoredContent()` 序列化 `msg_content`
8. 插入 `t_chat_message`
9. 写送达回执
10. `unread:{recipientId}:{conversationId}` 自增
11. 刷新 `last_msg:{conversationId}`
12. WebSocket 推送给接收方
13. 返回消息ID

### 5.2 群聊发送流程

接口：`POST /api/chat/team/send`

**流程**：

1. 参数校验（按 `msgType` 校验）
2. 校验团队存在与成员资格
3. 检查全局禁言
4. 普通成员检查全员禁言 / 个人禁言
5. `@消息` 校验 `atUsers` 均为团队成员
6. 文本消息执行敏感词检测
7. 构建 `conversation_id = team_{teamId}`
8. 插入 `t_chat_message`
9. 异步为群成员写送达回执
10. 对每个成员维护各自未读数
11. 刷新 `last_msg:{conversationId}`
12. WebSocket 广播群消息（排除发送者）
13. 若存在 `@成员`，异步发送系统通知
14. 返回消息ID

### 5.3 私聊历史查询

接口：`GET /api/chat/private/history`

**流程**：

1. 登录校验
2. 参数校验
3. 构建私聊 `conversation_id`
4. 按会话分页查询消息
5. 异步批量标记已读
6. 返回消息列表与总数

### 5.4 群聊历史查询

接口：`GET /api/chat/team/history`

**流程**：

1. 登录校验
2. 团队成员校验
3. 按 `team_{teamId}` 分页查询消息
4. 异步批量标记已读
5. 返回消息列表与总数

### 5.5 标记已读

接口：`POST /api/chat/message/read`

**流程**：

1. 参数校验
2. 批量插入 `t_message_read_receipt`（`receipt_type=2`）
3. 删除或清空当前用户该会话未读数
4. 若为群聊，写 `msg_read:{msgId}` Bitmap
5. 返回成功

### 5.6 未读消息统计

接口：`GET /api/chat/unread-count`

**流程**：

1. 通过 `SCAN unread:{userId}:*` 获取当前用户所有未读会话
2. 聚合每个会话未读数
3. 读取 `last_msg:{conversationId}` 作为预览
4. 返回总未读数与会话列表

---

## 六、实现归属

### 6.1 `ChatSendService`

负责：

- `sendPrivateMsg(...)`
- `getPrivateHistory(...)`
- `sendTeamMsg(...)`
- `getTeamHistory(...)`

### 6.2 `ChatReadService`

负责：

- `markMsgRead(...)`
- `getUnreadCount(...)`

### 6.3 `ChatSupportService`

复用能力：

- 登录态获取
- 会话ID构建 / 团队ID解析
- 团队成员查询
- 消息内容序列化
- 回执写入
- 已读处理
- 最后一条消息缓存
- 群公告缓存
- 推送内容组装

---

## 七、与其他分册边界

- `Part5`：平台治理与安全（系统通知、处罚、用户/团队举报、设备风控）
- `Part6`：聊天增强与消息治理（消息管理、消息举报、申诉审核、群公告）

---

## 八、验收标准

本模块完成后应满足：

- 支持私聊 / 群聊发送与历史查询
- 支持送达、已读、未读统计
- Redis Key 设计与实现一致
- 文档与当前拆分后的服务结构一致
