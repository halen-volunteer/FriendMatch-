# FriendMatch 详细设计文档 - Part 6：聊天增强与消息治理模块

> 版本：V2.5 | 日期：2026-04-05（更新：对齐聊天模块拆分结构，聚焦消息管理、消息举报与群聊增强能力）

---

## 一、模块概述

本模块是聊天域的增强层，负责聊天基础通信之外的进阶能力，主要包括：

- 多媒体消息配套能力
- 消息编辑 / 撤回
- 消息收藏 / 置顶 / 搜索
- 消息举报 / 申诉 / 管理员审核
- 群公告

本模块不再重复描述聊天基础发送、历史查询、未读统计，这些能力统一归 `Part4`。

---

## 二、当前实现结构

### 2.1 主要服务

- `ChatMessageManageService`
- `ChatReportManageService`
- `ChatNoticeService`
- `ChatSupportService`（提供公共支撑）

### 2.2 功能划分

#### `ChatMessageManageService`

负责：

- 编辑消息
- 撤回消息
- 收藏 / 取消收藏
- 收藏列表
- 置顶 / 取消置顶
- 置顶列表
- 会话内搜索

#### `ChatReportManageService`

负责：

- 举报消息
- 查询举报状态
- 发起申诉
- 管理员查看上下文
- 管理员分页查询举报记录
- 管理员审核消息举报
- 违规后联动处罚

#### `ChatNoticeService`

负责：

- 设置群公告
- 获取群公告

---

## 三、数据库设计

### 3.1 `t_message_collection`（消息收藏表）

**关键字段**：

- `user_id`
- `message_id`
- `collection_note`
- `collection_time`
- `is_delete`

**关键索引**：

- `uk_user_msg (user_id, message_id)`

### 3.2 `t_message_pin`（消息置顶表）

**关键字段**：

- `conversation_id`
- `message_id`
- `pin_user_id`
- `pin_time`
- `pin_order`
- `is_delete`

**关键索引**：

- `uk_conv_msg (conversation_id, message_id)`

### 3.3 `t_message_report`（消息举报表）

**关键字段**：

- `message_id`
- `reporter_id`
- `report_reason`
- `report_content`
- `ai_check_result`
- `ai_check_time`
- `ai_confidence`
- `admin_status`
- `admin_action`
- `admin_note`
- `appeal_count`
- `is_delete`

**关键索引**：

- `idx_message_id`
- `idx_reporter_id`
- `idx_ai_check_result`
- `idx_admin_status`

> 说明：消息举报属于聊天域治理能力，不再归平台通用举报模块描述。

---

## 四、Redis 设计

### 4.1 群公告缓存

```text
Key: group_notice:{conversationId}
Type: String
TTL: 30天
说明：群公告内容由管理员或队长设置，成员读取时优先从 Redis 获取
```

### 4.2 会话最后消息缓存（复用）

```text
Key: last_msg:{conversationId}
Type: Hash
TTL: 30天
说明：用于未读统计预览，也可为收藏 / 搜索列表提供最近消息上下文展示
```

---

## 五、业务流程设计

### 5.1 OSS 预签名上传

接口：`POST /api/oss/presign`

用途：

- 图片上传前获取预签名 URL
- 文件上传前获取预签名 URL
- 表情包上传前获取预签名 URL

流程：

1. 参数校验（文件名、消息类型、大小）
2. 根据 `msgType` 校验扩展名白名单和大小限制
3. 生成 `objectKey`
4. 生成临时 `presignUrl`
5. 返回 `presignUrl + fileUrl + expireAt`

---

### 5.2 编辑消息

接口：`POST /api/chat/message/edit`

流程：

1. 登录校验
2. 查询消息
3. 校验发送者身份
4. 校验消息未撤回
5. 校验发送后 5 分钟内可编辑
6. 更新 `msg_content / is_edited / edit_time / edit_count`
7. 推送 `message_edit` 事件给会话相关方
8. 返回成功

---

### 5.3 撤回消息

接口：`POST /api/chat/message/revoke`

流程：

1. 登录校验
2. 查询消息
3. 校验发送者身份
4. 校验发送后 5 分钟内可撤回
5. 检查是否存在待处理消息举报
6. 若存在未处理举报，则拒绝撤回
7. 物理删除消息
8. 级联删除关联回执、收藏、置顶、举报记录
9. 推送 `message_revoke` 事件
10. 返回成功

> 说明：当前实现已不采用“仅更新 `is_revoke=1`”的旧方案，而是按消息管理服务中的现实现行策略处理。

---

### 5.4 收藏消息

接口：`POST /api/chat/message/collect`

流程：

1. 登录校验
2. 校验消息存在
3. 检查是否已收藏
4. 写入收藏记录
5. 返回 `collectionId`

### 5.5 取消收藏

接口：`DELETE /api/chat/message/collect/{collectionId}`

流程：

1. 登录校验
2. 校验归属
3. 软删除收藏记录
4. 返回成功

### 5.6 收藏列表

接口：`GET /api/chat/message/collections`

流程：

1. 登录校验
2. 分页查当前用户收藏
3. 关联查询消息内容与发送者
4. 返回列表与总数

---

### 5.7 置顶消息

接口：`POST /api/chat/message/pin`

流程：

1. 登录校验
2. 校验消息所属会话
3. 私聊直接允许；群聊需队长 / 管理员权限
4. 防重复置顶
5. 计算 `pin_order`
6. 写入置顶记录
7. 返回 `pinId`

### 5.8 取消置顶

接口：`DELETE /api/chat/message/pin/{pinId}`

流程：

1. 登录校验
2. 校验置顶记录归属
3. 软删除置顶记录
4. 返回成功

### 5.9 置顶列表

接口：`GET /api/chat/message/pins`

流程：

1. 登录校验
2. 校验会话访问权限
3. 按 `pin_order` 升序返回置顶消息

---

### 5.10 会话内搜索消息

接口：`GET /api/chat/message/search`

流程：

1. 登录校验
2. 参数校验
3. 校验会话访问权限
4. 按 `msg_content LIKE` 检索
5. 过滤已删除 / 已撤回消息
6. 返回分页结果

---

### 5.11 设置群公告

接口：`POST /api/chat/group/notice`

流程：

1. 登录校验
2. 校验 `conversationId` 合法且为群聊
3. 校验当前用户为队长或管理员
4. 校验公告内容非空且不超过 500 字
5. 写入 `group_notice:{conversationId}`
6. 返回成功

### 5.12 获取群公告

接口：`GET /api/chat/group/notice`

流程：

1. 登录校验
2. 校验当前用户属于该团队
3. 读取 Redis 群公告
4. 返回公告内容

---

### 5.13 举报消息

接口：`POST /api/chat/message/report`

流程：

1. 登录校验
2. 参数校验
3. 校验消息存在
4. 检查是否重复举报
5. 写入 `t_message_report`（`ai_check_result=0`）
6. 异步执行 AI 审核
7. AI 审核通过后可自动联动处罚
8. 返回 `reportId`

### 5.14 查询举报状态

接口：`GET /api/chat/message/report/{reportId}`

流程：

1. 登录校验
2. 校验举报归属
3. 返回 AI 检测结果、置信度、管理员处理状态

### 5.15 提交申诉

接口：`POST /api/chat/message/report/{reportId}/appeal`

流程：

1. 登录校验
2. 校验当前用户为举报人或被举报消息发送人
3. 校验申诉次数未超过 3 次
4. 写入申诉信息并增加 `appeal_count`
5. 返回成功

### 5.16 管理员审核消息举报

接口：`POST /api/chat/message/report/admin/handle`

流程：

1. 管理员校验
2. 查询举报记录
3. 查看上下文消息
4. 选择处理结论：维持处罚 / 撤销处罚 / 确认违规
5. 更新举报状态
6. 必要时联动处罚记录与通知
7. 返回成功

---

## 六、与其他分册边界

- `Part4`：聊天基础发送、历史查询、未读状态
- `Part5`：平台级系统通知、处罚、用户举报、团队举报、设备与风控

---

## 七、验收标准

本模块完成后应满足：

- 消息管理能力完整可用
- 消息举报具备“提交 → AI检测 → 管理员审核 → 申诉”完整闭环
- 群公告缓存策略与当前实现一致（30天）
- 文档描述与当前拆分后的服务结构一致
