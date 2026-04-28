# FriendMatch 综合实现文档 - Part 4：聊天基础与会话状态模块

> 版本：V2.6 | 日期：2026-04-05（修订：收敛为纯 Part4 边界）

---

## 一、模块总览

本分册对应 `Part4`，负责聊天域的基础通信与会话状态能力：

- 私聊发送
- 群聊发送
- 历史记录查询
- 已读回执
- 未读统计
- 会话最后消息缓存

本分册不包含：

- 消息编辑 / 撤回
- 收藏 / 置顶 / 搜索
- 群公告
- 消息举报 / 申诉 / 审核

以上增强与治理能力统一归 `Part6`。

---

## 二、涉及文件

| 层级 | 文件 |
|---|---|
| Controller | `ChatController.java` |
| Service | `ChatServiceImpl.java` / `ChatSendService.java` / `ChatReadService.java` / `ChatSupportService.java` |
| Mapper | `ChatMessageMapper.java` / `MessageReadReceiptMapper.java` |

---

## 三、核心能力

### 3.1 聊天发送

- 私聊发送
- 群聊发送
- 发送前关系 / 权限校验
- 最后一条消息缓存刷新

### 3.2 历史记录

- 私聊历史查询
- 群聊历史查询
- 分页拉取消息
- 基础消息展示字段组装

### 3.3 会话状态

- 标记已读
- 已读回执写入
- 会话未读数统计
- 未读列表查询

---

## 四、实现结构

- `ChatServiceImpl`：门面层，统一对外暴露聊天基础能力
- `ChatSendService`：发送与历史查询
- `ChatReadService`：已读、未读、会话状态
- `ChatSupportService`：基础公共支撑

---

## 五、数据与缓存

主要表：

- `t_chat_message`
- `t_message_read_receipt`

主要缓存：

- `last_msg:{conversationId}`
- `unread:{userId}:{conversationId}`

---

## 六、核心流程

### 6.1 发送消息流程

```text
登录校验
→ 校验接收对象与发送权限
→ 写入 t_chat_message
→ 刷新 last_msg 缓存
→ 推送实时消息事件
```

### 6.2 历史查询流程

```text
登录校验
→ 校验会话权限
→ 按会话分页查询消息
→ 组装返回记录
```

### 6.3 已读 / 未读流程

```text
登录校验
→ 标记消息已读
→ 写入已读状态
→ 更新未读统计
→ 返回未读数或未读列表
```

---

## 七、阅读指引

详细设计对应：

- `详细设计文档_Part4_聊天基础与会话状态模块.md`

实现文档对应：

- `实现文档_Part4_聊天基础与会话状态模块.md`

关联分册：

- `综合实现Part6_聊天增强与消息治理模块.md`

---

## 八、实现结论

聊天基础能力已与消息增强治理能力分层，当前 `Part4` 聚焦“发送 + 历史 + 已读未读 + 会话状态”主链，结构边界清晰。
