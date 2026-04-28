# FriendMatch 前端开发文档 — Part 5 系统通知与处罚管理模块

> 版本：V1.0 | 日期：2026-03-19

**对应后端文档**：`API文档_Part5_通知与处罚.md`  
**页面路径前缀**：`/views/user/`  
**接口路径前缀**：`/api/notice`、`/api/punish`、`/api/feedback`、`/api/online`  
**认证要求**：所有接口需携带 `Authorization: {token}`

---

## 一、模块概述

| 功能域 | 说明 |
|--------|------|
| 系统通知 | 好友申请、审批结果、处罚通知、反馈回复等系统消息的查看/已读/删除 |
| 处罚与反馈 | 查看自己的处罚记录、提交申诉/反馈、查看处理进展 |
| 在线状态 | 设置在线状态、心跳保活、主动下线 |

**接口一览**：

| 编号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 5.1 | GET | `/api/notice/unread-count` | 获取未读通知数 |
| 5.2 | GET | `/api/notice/list` | 获取通知列表 |
| 5.3 | POST | `/api/notice/read` | 标记已读 |
| 5.4 | POST | `/api/notice/delete` | 删除通知 |
| 5.5 | GET | `/api/punish/my-logs` | 查看我的处罚记录 |
| 5.6 | POST | `/api/feedback/submit` | 提交反馈/申诉 |
| 5.7 | GET | `/api/feedback/my-list` | 查看我的反馈列表 |
| 5.8 | GET | `/api/feedback/detail` | 查看反馈详情 |
| 5.9 | POST | `/api/online/status` | 设置在线状态 |
| 5.10 | POST | `/api/online/heartbeat` | 心跳保活 |
| 5.11 | GET | `/api/online/status` | 查询用户在线状态 |
| 5.12 | POST | `/api/online/offline` | 主动下线 |

---

## 二、页面列表

| 文件 | 路由路径 | 说明 |
|------|----------|------|
| `NoticesView.vue` | `/notices` | 系统通知列表 |
| `FeedbackView.vue` | `/feedback` | 反馈/申诉 + 处罚记录 |

---

## 三、接口封装

**src/api/notice.js**

```js
import request from './request'
export const getUnreadNoticeCount = () => request.get('/api/notice/unread-count')
export const getNoticeList = (params) => request.get('/api/notice/list', { params })
export const readNotices = (ids) => request.post('/api/notice/read', ids)
export const deleteNotices = (ids) => request.post('/api/notice/delete', ids)
```

**src/api/feedback.js**

```js
import request from './request'
export const getMyPunishLogs = (params) => request.get('/api/punish/my-logs', { params })
export const submitFeedback = (data) => request.post('/api/feedback/submit', data)
export const getMyFeedbackList = (params) => request.get('/api/feedback/my-list', { params })
export const getFeedbackDetail = (feedbackId) => request.get('/api/feedback/detail', { params: { feedbackId } })
```

**src/api/online.js**

```js
import request from './request'
export const setOnlineStatus = (status) => request.post('/api/online/status', null, { params: { status } })
export const heartbeat = () => request.post('/api/online/heartbeat')
export const getUserOnlineStatus = (userId) => request.get('/api/online/status', { params: { userId } })
export const goOffline = () => request.post('/api/online/offline')
```

---

## 四、Pinia Store（src/stores/notice.js）

```js
import { ref } from 'vue'
import { defineStore } from 'pinia'
export const useNoticeStore = defineStore('notice', () => {
  const unreadCount = ref(0)
  function setUnreadCount(n) { unreadCount.value = n }
  function increment() { unreadCount.value++ }
  function decrement(n = 1) { unreadCount.value = Math.max(0, unreadCount.value - n) }
  function clear() { unreadCount.value = 0 }
  return { unreadCount, setUnreadCount, increment, decrement, clear }
})
```

初始化：主布局 `onMounted` 时调用 `getUnreadNoticeCount()` 写入 store，侧边栏角标绑定 `noticeStore.unreadCount`。

---

## 五、页面详细设计

### 5.1 系统通知页（NoticesView.vue）

**布局**：顶部操作栏（全部已读/删除已读）+ Tab（全部/未读/已读）+ 通知列表 + 分页

**通知类型映射**：

| noticeType | 文案模板 | 点击跳转 |
|------------|----------|----------|
| 1 | `{nickname} 申请添加你为好友` | `/friends?tab=requests` |
| 2 | `{nickname} 拒绝了你的好友申请` | 无 |
| 3 | `你已成功加入团队「{teamName}」` | `/teams/{teamId}` |
| 4 | `加入「{teamName}」的申请被拒绝` | 无 |
| 5 | `你已被移出团队「{teamName}」` | 无 |
| 6 | 账号异常提醒 | `/feedback` |
| 7 | 你受到了处罚通知 | `/feedback?tab=punish` |
| 8 | 你的反馈已得到回复 | `/feedback?tab=feedback` |

**流程**：
```
挂载 → 调用列表接口 + noticeStore.clear()
点击通知 → readNotices([id]) → 更新本地isRead → 按类型跳转
全部已读 → readNotices(未读ids[]) → 刷新列表
删除 → deleteNotices([id]) → 从列表移除
```

---

### 5.2 反馈与处罚页（FeedbackView.vue）

**路由**：`/feedback`（支持 `?tab=punish` / `?tab=feedback` 直达指定 Tab）

#### Tab 1：提交反馈

| 字段 | 校验规则 |
|------|----------|
| feedbackType | 必填：1-功能问题/2-违规举报/3-处罚申诉/4-其他建议 |
| feedbackContent | 必填，≤2000字符 |
| feedbackImg | 选填，图片URL逗号分隔 |

限制：同一用户每天最多提交 10 条。提交成功后切换到「我的反馈」Tab。

#### Tab 2：我的反馈

**handleStatus 映射**：

| 值 | 文案 | 颜色 |
|----|------|------|
| 0 | 待处理 | 灰 |
| 1 | 处理中 | 蓝 |
| 2 | 已解决 | 绿 |
| 3 | 已驳回 | 红 |

列表展示：类型文字、内容摘要（前50字）、状态徽标、提交时间。点击列表项弹出详情弹窗，展示完整内容和管理员回复（`handleContent`）。

**feedbackType 映射**：

| 值 | 文案 |
|----|------|
| 1 | 功能问题 |
| 2 | 违规举报 |
| 3 | 处罚申诉 |
| 4 | 其他建议 |

#### Tab 3：处罚记录

**punishType 映射**：

| 值 | 文案 | 颜色 |
|----|------|------|
| 1 | 团队禁言 | 橙 |
| 2 | 全局禁言 | 红 |
| 3 | 永久封号 | 深红 |

列表展示：处罚类型、原因、生效时间、解除时间（永久封号显示「永久」）、是否已撤销。

**梯度处罚说明**（展示在 Tab 顶部提示区）：

```
第1次违规 → 全局禁言 60 分钟
第2次违规 → 全局禁言 1 天
第3次违规 → 全局禁言 7 天
第4次及以上 → 永久封号
```

若用户需申诉：点击「申诉」→ 切换至「提交反馈」Tab 并预设 `feedbackType = 3`。

---

## 六、在线状态管理

### 6.1 状态说明

| status | 说明 | 圆点颜色 |
|--------|------|----------|
| 0 | 离线 | 灰色 |
| 1 | 在线 | 绿色 |
| 2 | 离开 | 黄色 |
| 3 | 忙碌 | 红色 |
| 4 | 隐身 | 灰色（他人看到离线）|

### 6.2 心跳维护（MainLayout.vue）

```js
import { heartbeat, setOnlineStatus, goOffline } from '@/api/online'
import { onMounted, onUnmounted } from 'vue'

let timer = null

onMounted(async () => {
  await setOnlineStatus(1)                          // 上线设为在线
  timer = setInterval(() => heartbeat(), 120000)    // 每2分钟心跳
})

onUnmounted(async () => {
  clearInterval(timer)
  await goOffline()                                 // 主动下线
})
```

### 6.3 状态切换（顶部栏下拉）

```vue
<select @change="(e) => setOnlineStatus(+e.target.value)">
  <option value="1">在线</option>
  <option value="2">离开</option>
  <option value="3">忙碌</option>
  <option value="4">隐身</option>
</select>
```

### 6.4 查询他人在线状态

在用户资料页、好友列表、聊天窗口顶部需展示在线状态时调用：

```js
const { data } = await getUserOnlineStatus(userId)
// data: { userId, status }  传给 AvatarWithStatus 组件的 :status prop
```

---

## 七、路由配置

```js
// 主布局子路由
{ path: 'notices',  name: 'Notices',  component: () => import('@/views/user/NoticesView.vue') },
{ path: 'feedback', name: 'Feedback', component: () => import('@/views/user/FeedbackView.vue') },
```

---

## 八、注意事项

1. **进入通知页即清零角标**：调用 `noticeStore.clear()` 清零侧边栏数字，进页面后再调用接口标记已读，不要等接口返回才清零，避免用户感知延迟。

2. **通知 noticeType 跳转**：noticeType=1（好友申请）点击后跳转 `/friends?tab=requests`，需在 `FriendsView` 中读取 `query.tab` 初始化激活的 Tab。

3. **feedbackType 预设**：从处罚记录 Tab 点击「申诉」时，通过路由 query 或事件传递 `feedbackType=3` 预填表单，提升体验。

4. **心跳间隔**：后端在线状态 TTL 为 5 分钟，前端每 **2 分钟**心跳一次，留有余量防止网络抖动导致误判离线。

5. **隐身状态**：status=4 时，他人调用查询接口返回 status=0（离线），仅用户本人在顶部栏看到「隐身」状态，`AvatarWithStatus` 对自己展示实际状态，对他人查询结果展示即可。

6. **处罚记录与封号**：若用户 `global_punish_type=2`（封号），登录时后端会直接拒绝，前端无需单独处理，在登录接口失败时展示「账号已被封禁，如有异议请联系客服」即可。

---

*本文档对应后端 `API文档_Part5_通知与处罚.md`，如接口变更请同步更新。*

