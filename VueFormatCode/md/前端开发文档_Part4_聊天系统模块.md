# FriendMatch 前端开发文档 — Part 4 聊天系统模块

> 版本：V1.0 | 日期：2026-03-19

**对应后端文档**：`API文档_Part4_聊天系统.md`  
**页面路径前缀**：`/views/chat/`  
**接口路径前缀**：`/api/chat`  
**认证要求**：所有接口需携带 `Authorization: {token}`

---

## 目录

1. [模块概述](#一模块概述)
2. [页面列表](#二页面列表)
3. [接口封装](#三接口封装)
4. [WebSocket 集成](#四websocket-集成)
5. [本地消息存储](#五本地消息存储)
6. [页面详细设计](#六页面详细设计)
7. [组件规划](#七组件规划)
8. [Pinia Store](#八pinia-store)
9. [路由配置](#九路由配置)
10. [注意事项](#十注意事项)

---

## 一、模块概述

聊天系统模块实现私聊和群聊的完整功能，包括消息发送/撤回/已读、未读数统计、WebSocket 实时推送及本地 IndexedDB 持久化存储。核心设计：服务器消息内容 72 小时后清空，客户端通过 IndexedDB 永久保存，历史消息优先读本地。

**接口一览**：

| 编号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 4.1 | POST | `/api/chat/private/send` | 发送私聊消息 |
| 4.2 | GET | `/api/chat/private/history` | 查询私聊历史 |
| 4.3 | POST | `/api/chat/team/send` | 发送群聊消息 |
| 4.4 | GET | `/api/chat/team/history` | 查询群聊历史 |
| 4.5 | POST | `/api/chat/message/revoke` | 撤回消息（5分钟内）|
| 4.6 | POST | `/api/chat/message/read` | 标记已读 |
| 4.7 | GET | `/api/chat/unread-count` | 获取所有会话未读数 |

**WebSocket**：`ws://{host}/ws?token={token}`

---

## 二、页面列表

| 文件 | 路由路径 | 说明 |
|------|----------|------|
| `ChatView.vue` | `/chat` | 会话列表页 |
| `PrivateChatView.vue` | `/chat/private/:friendId` | 私聊窗口 |
| `TeamChatView.vue` | `/chat/team/:teamId` | 群聊窗口 |

---

## 三、接口封装

文件路径：`src/api/chat.js`

```js
import request from './request'

// 4.1 发送私聊消息
// data: { recipientId, msgType: 1文本|2图片|3文件|4表情包, msgContent }
export const sendPrivateMsg = (data) => request.post('/api/chat/private/send', data)

// 4.2 查询私聊历史（按 createTime 倒序）
// params: { friendId, page?, pageSize? }
export const getPrivateHistory = (params) =>
  request.get('/api/chat/private/history', { params })

// 4.3 发送群聊消息
// data: { teamId, msgType, msgContent, atUsers?: number[] }
export const sendTeamMsg = (data) => request.post('/api/chat/team/send', data)

// 4.4 查询群聊历史
// params: { teamId, page?, pageSize? }
export const getTeamHistory = (params) =>
  request.get('/api/chat/team/history', { params })

// 4.5 撤回消息（只能撤回自己的，5分钟内有效）
export const revokeMsg = (msgId) => request.post('/api/chat/message/revoke', { msgId })

// 4.6 标记消息已读
// data: { conversationId, msgIds: number[] }
export const readMsgs = (data) => request.post('/api/chat/message/read', data)

// 4.7 获取所有会话未读数
export const getUnreadCount = () => request.get('/api/chat/unread-count')
```

---

## 四、WebSocket 集成

### 4.1 连接规范

- 地址：`ws://{host}/ws?token={token}`
- 同一用户新连接建立时旧连接自动断开
- 上线后服务端补推离线消息（每会话最多20条，`offline: true`）

### 4.2 管理工具（src/utils/websocket.js）

```js
import { useAuthStore } from '@/stores/auth'

const WS_BASE = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8080'
let ws = null
let heartbeatTimer = null
let reconnectTimer = null
let reconnectCount = 0

export function connectWebSocket(onMessage) {
  const { token } = useAuthStore()
  if (!token) return
  ws = new WebSocket(`${WS_BASE}/ws?token=${token}`)
  ws.onopen = () => {
    reconnectCount = 0
    heartbeatTimer = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN)
        ws.send(JSON.stringify({ type: 'heartbeat' }))
    }, 30000)
  }
  ws.onmessage = (e) => onMessage(JSON.parse(e.data))
  ws.onclose = () => {
    clearInterval(heartbeatTimer)
    if (reconnectCount < 5) {
      reconnectTimer = setTimeout(() => { reconnectCount++; connectWebSocket(onMessage) }, 3000 * (reconnectCount + 1))
    }
  }
}

export function disconnectWebSocket() {
  clearInterval(heartbeatTimer)
  clearTimeout(reconnectTimer)
  ws?.close()
  ws = null
}
```

### 4.3 推送消息类型

| type | 说明 | 关键字段 |
|------|------|----------|
| `private_message` | 收到私聊消息 | msgId, senderId, msgType, content, createTime, offline |
| `team_message` | 收到群聊消息 | msgId, teamId, senderId, msgType, content, createTime, offline |
| `message_revoke` | 消息撤回通知 | msgId, conversationId |

### 4.4 主布局注册消息处理

```js
// 在 MainLayout.vue 的 onMounted 中调用
import { connectWebSocket } from '@/utils/websocket'
import { useChatStore } from '@/stores/chat'
import { saveMessageToLocal, markRevokedLocal } from '@/utils/localDb'
import { useAuthStore } from '@/stores/auth'

const chatStore = useChatStore()
const authStore = useAuthStore()

connectWebSocket(({ type, data }) => {
  if (type === 'private_message' || type === 'team_message') {
    const convId = type === 'private_message'
      ? buildConversationId(authStore.userId, data.senderId)
      : `team_${data.teamId}`
    saveMessageToLocal({ msgId: data.msgId, conversationId: convId, senderId: data.senderId,
      msgType: data.msgType, msgContent: data.content, createTime: data.createTime,
      isRevoke: false, localStatus: 1 })
    if (convId !== chatStore.activeConversationId)
      chatStore.incrementUnread(convId)
  }
  if (type === 'message_revoke') markRevokedLocal(data.msgId)
})

export function buildConversationId(uid1, uid2) {
  return [uid1, uid2].sort((a, b) => a - b).join('_')
}
```

---

## 五、本地消息存储（src/utils/localDb.js）

```js
const DB_NAME = 'friendmatch_chat'
const STORE = 'messages'
let db = null

export function openDb() {
  return new Promise((resolve, reject) => {
    if (db) return resolve(db)
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = (e) => {
      const s = e.target.result.createObjectStore(STORE, { keyPath: 'msgId' })
      s.createIndex('by_conv', 'conversationId')
    }
    req.onsuccess = (e) => { db = e.target.result; resolve(db) }
    req.onerror = reject
  })
}

export async function saveMessageToLocal(msg) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).put(msg)
    tx.oncomplete = res; tx.onerror = rej
  })
}

export async function getLocalMessages(conversationId, limit = 20) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const req = d.transaction(STORE, 'readonly')
      .objectStore(STORE).index('by_conv').getAll(IDBKeyRange.only(conversationId))
    req.onsuccess = (e) => {
      const list = e.target.result.sort((a, b) => b.createTime.localeCompare(a.createTime))
      res(list.slice(0, limit))
    }
    req.onerror = rej
  })
}

export async function markRevokedLocal(msgId) {
  const d = await openDb()
  const tx = d.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  const req = store.get(msgId)
  req.onsuccess = (e) => { const m = e.target.result; if (m) { m.isRevoke = true; store.put(m) } }
}
```

### 本地字段说明

| 字段 | 说明 |
|------|------|
| msgId | 服务端消息ID（主键）|
| conversationId | 会话ID |
| senderId | 发送者ID |
| msgType | 1文本/2图片/3文件/4表情包 |
| msgContent | 消息内容（永久存储）|
| createTime | ISO时间字符串 |
| isRevoke | 是否已撤回 |
| localStatus | 0-发送中/1-已送达/2-发送失败 |

---

## 六、页面详细设计（占位，待补充）

### 6.1 会话列表页（ChatView.vue）

**路由**：`/chat`

- 展示所有会话（好友私聊 + 已加入团队群聊），按最后消息时间倒序
- 每条会话：头像、名称、最后一条消息预览、未读数角标、时间
- 顶部搜索框（本地过滤）

```
页面挂载
    ↓
调用 GET /api/chat/unread-count → 写入 chatStore.unreadMap
并行调用好友列表 + 我的团队列表，合并为会话列表
    ↓
点击私聊会话 → 跳转 /chat/private/{friendId}
点击群聊会话 → 跳转 /chat/team/{teamId}
```

---

### 6.2 私聊窗口（PrivateChatView.vue）

**路由**：`/chat/private/:friendId`

#### 页面布局

- 顶部栏：对方头像、昵称、在线状态
- 消息区：气泡列表（自己右对齐，对方左对齐），支持上滑加载更多
- 底部输入区：文本输入框 + 发送按钮 + 消息类型切换

#### 业务逻辑流程

```
页面挂载
    ↓
chatStore.setActiveConversation(conversationId)
优先读本地 IndexedDB（getLocalMessages）展示历史
    ↓
调用 POST /api/chat/message/read 标记已读，清零未读数
    ↓
用户发送消息
    ↓
生成临时 msgId，写本地（localStatus=0），UI立即展示
    ↓
调用 POST /api/chat/private/send
    → 成功：用服务端 msgId 更新本地（localStatus=1）
    → 失败：更新本地（localStatus=2），显示重试按钮
    ↓
WebSocket 收到对方消息 → saveMessageToLocal → 追加到消息列表
    ↓
收到 message_revoke → markRevokedLocal → 气泡替换为「消息已撤回」
    ↓
页面离开时 chatStore.setActiveConversation('')
```

#### 撤回消息

- 长按/右键自己发送的消息气泡，弹出操作菜单
- 仅展示「撤回」选项（5分钟内）
- 调用 `POST /api/chat/message/revoke { msgId }`
- 成功后本地更新 `isRevoke = true`，气泡替换为「你撤回了一条消息」

---

### 6.3 群聊窗口（TeamChatView.vue）

**路由**：`/chat/team/:teamId`

与私聊窗口逻辑基本一致，差异点：

| 差异 | 说明 |
|------|------|
| 消息展示 | 每条消息需展示发送者昵称和头像 |
| @用户 | 输入 `@` 触发成员列表选择，选中后追加到 msgContent，atUsers 数组传参 |
| 发送检查 | 全员禁言时普通成员/嘉宾输入框禁用，队长/管理员不受限 |
| 会话ID | `team_{teamId}` |

```
页面挂载
    ↓
chatStore.setActiveConversation(`team_${teamId}`)
读本地消息（getLocalMessages）→ 展示
调用 GET /api/team/{teamId}（获取全员禁言状态和当前角色）
    ↓
发送消息逻辑同私聊，接口换为 POST /api/chat/team/send
```

---

## 七、组件规划

### MessageBubble.vue

**路径**：`src/components/chat/MessageBubble.vue`

```vue
<script setup>
defineProps({
  msg: Object,   // { msgId, senderId, msgType, msgContent, createTime, isRevoke, localStatus }
  isSelf: Boolean,
  senderNickname: String,
  senderAvatar: String,
})
const emit = defineEmits(['revoke'])

const msgTypeLabel = { 2: '[图片]', 3: '[文件]', 4: '[表情]' }
</script>

<template>
  <div :class="['bubble-wrap', isSelf ? 'self' : 'other']">
    <img v-if="!isSelf" :src="senderAvatar" class="avatar" />
    <div class="bubble">
      <span v-if="!isSelf" class="nickname">{{ senderNickname }}</span>
      <p v-if="msg.isRevoke" class="revoked">{{ isSelf ? '你撤回了一条消息' : '对方撤回了一条消息' }}</p>
      <p v-else-if="msg.msgType === 1">{{ msg.msgContent }}</p>
      <p v-else>{{ msgTypeLabel[msg.msgType] }}</p>
      <span class="time">{{ msg.createTime }}</span>
      <span v-if="msg.localStatus === 2" class="fail">发送失败</span>
    </div>
    <img v-if="isSelf" :src="senderAvatar" class="avatar" />
  </div>
</template>
```

### MessageInput.vue

**路径**：`src/components/chat/MessageInput.vue`

```vue
<script setup>
import { ref } from 'vue'
const emit = defineEmits(['send'])
const text = ref('')
const disabled = defineProps({ disabled: Boolean })

function handleSend() {
  if (!text.value.trim() || disabled.disabled) return
  emit('send', { msgType: 1, msgContent: text.value.trim() })
  text.value = ''
}
</script>

<template>
  <div class="msg-input-bar">
    <textarea v-model="text" :disabled="disabled.disabled"
      placeholder="输入消息..."
      @keydown.enter.exact.prevent="handleSend" />
    <button :disabled="disabled.disabled || !text.trim()" @click="handleSend">发送</button>
  </div>
</template>
```

### ConversationItem.vue

**路径**：`src/components/chat/ConversationItem.vue`

```vue
<script setup>
defineProps({
  name: String,
  avatar: String,
  lastMsg: String,
  unreadCount: { type: Number, default: 0 },
  time: String,
})
</script>

<template>
  <div class="conv-item">
    <div class="avatar-wrap">
      <img :src="avatar" />
      <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </div>
    <div class="conv-info">
      <span class="name">{{ name }}</span>
      <span class="time">{{ time }}</span>
      <p class="last-msg">{{ lastMsg }}</p>
    </div>
  </div>
</template>
```

---

## 八、Pinia Store（src/stores/chat.js）

```js
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', () => {
  const unreadMap = ref({})          // { conversationId: count }
  const activeConversationId = ref('')
  const conversationList = ref([])   // 会话列表

  const totalUnread = computed(() =>
    Object.values(unreadMap.value).reduce((s, n) => s + n, 0)
  )

  function setUnread(convId, count) { unreadMap.value[convId] = count }
  function clearUnread(convId) { unreadMap.value[convId] = 0 }
  function incrementUnread(convId) {
    unreadMap.value[convId] = (unreadMap.value[convId] || 0) + 1
  }
  function setActiveConversation(id) { activeConversationId.value = id }

  return { unreadMap, activeConversationId, conversationList, totalUnread,
    setUnread, clearUnread, incrementUnread, setActiveConversation }
})
```

---

## 九、路由配置

```js
// 主布局子路由
{ path: 'chat',                    name: 'Chat',        component: () => import('@/views/chat/ChatView.vue') },
{ path: 'chat/private/:friendId',  name: 'PrivateChat', component: () => import('@/views/chat/PrivateChatView.vue') },
{ path: 'chat/team/:teamId',       name: 'TeamChat',    component: () => import('@/views/chat/TeamChatView.vue') },
```

---

## 十、注意事项

1. **历史消息优先读本地**：进入聊天窗口时先调用 `getLocalMessages` 展示本地数据，同时在后台请求服务端最新消息做增量同步，避免白屏等待。

2. **发送消息乐观更新**：消息发出前先写本地（`localStatus=0`）并渲染，成功后更新状态，失败时展示重试按钮，提升体验。

3. **activeConversationId**：进入聊天页时设置，离开时清空，用于判断 WebSocket 新消息是否需要增加未读数角标。

4. **撤回时间限制**：只有 5 分钟内的消息才展示撤回选项，前端通过 `(Date.now() - new Date(msg.createTime).getTime()) < 5 * 60 * 1000` 判断。

5. **msgType=1 才做敏感词检测**：图片/文件/表情包消息后端不做文本检测，前端无需处理，直接发送。

6. **群聊禁言豁免**：roleType ≤ 2（队长/管理员）不受全员禁言和个人禁言影响，前端需根据团队详情接口返回的 `currentUserRole` 控制输入框是否禁用。

7. **WebSocket 断线重连**：最多重连 5 次，间隔递增（3s, 6s, 9s...），重连成功后调用 `GET /api/chat/unread-count` 同步最新未读数。

8. **离线消息补推**：上线后服务端自动推送 `offline: true` 的消息，前端统一走 WebSocket 消息处理逻辑即可，无需单独接口。

---

*本文档对应后端 `API文档_Part4_聊天系统.md`，如接口变更请同步更新。*

