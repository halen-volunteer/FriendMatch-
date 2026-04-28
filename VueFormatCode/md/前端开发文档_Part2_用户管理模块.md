# FriendMatch 前端开发文档 — Part 2 用户管理模块

> 版本：V1.0 | 日期：2026-03-19

**对应后端文档**：`API文档_Part2_用户管理.md`  
**页面路径前缀**：`/views/user/`  
**接口路径前缀**：`/api/user`、`/api/friend`、`/api/blacklist`、`/api/notice`  
**认证要求**：所有接口需携带 `Authorization: {token}`

---

## 目录

1. [模块概述](#一模块概述)
2. [页面列表](#二页面列表)
3. [接口封装](#三接口封装)
4. [页面详细设计](#四页面详细设计)
   - [4.1 个人资料页](#41-个人资料页-myprofileviewvue)
   - [4.2 他人资料页](#42-他人资料页-userprofileviewvue)
   - [4.3 好友列表页](#43-好友列表页-friendsviewvue)
   - [4.4 黑名单页](#44-黑名单页-blacklistviewvue)
   - [4.5 系统通知页](#45-系统通知页-noticesviewvue)
5. [公共组件](#五公共组件)
6. [路由配置](#六路由配置)
7. [注意事项](#七注意事项)

---

## 一、模块概述

用户管理模块涵盖个人信息管理、隐私设置、好友关系（添加/同意/拒绝/删除）、黑名单管理以及系统通知的查看与处理。

**接口一览**：

| 编号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 2.1 | POST | `/api/user/profile/update` | 更新个人资料 |
| 2.2 | GET | `/api/user/privacy` | 查看隐私设置 |
| 2.3 | POST | `/api/user/privacy/update` | 更新隐私设置 |
| 2.4 | GET | `/api/user/{userId}/profile` | 查看他人资料 |
| 2.5 | GET | `/api/user/list` | 获取用户列表 |
| 2.6 | GET | `/api/user/search` | 搜索用户 |
| 2.7 | POST | `/api/friend/add` | 添加好友 |
| 2.8 | GET | `/api/friend/list` | 获取好友列表 |
| 2.9 | GET | `/api/friend/requests` | 获取好友申请列表 |
| 2.10 | POST | `/api/friend/agree` | 同意好友申请 |
| 2.11 | POST | `/api/friend/reject` | 拒绝好友申请 |
| 2.12 | POST | `/api/friend/delete` | 删除好友 |
| 2.13 | POST | `/api/blacklist/add` | 拉黑用户 |
| 2.14 | POST | `/api/blacklist/remove` | 解除拉黑 |
| 2.15 | GET | `/api/blacklist` | 获取黑名单列表 |
| 2.16 | GET | `/api/notice/unread-count` | 获取未读通知数 |
| 2.17 | GET | `/api/notice/list` | 获取通知列表 |
| 2.18 | POST | `/api/notice/read` | 标记通知已读 |
| 2.19 | POST | `/api/notice/delete` | 删除通知 |

---

## 二、页面列表

| 文件 | 路由路径 | 说明 |
|------|----------|------|
| `MyProfileView.vue` | `/profile` | 查看/编辑自己的资料与隐私设置 |
| `UserProfileView.vue` | `/profile/:userId` | 查看他人资料，可添加好友或拉黑 |
| `FriendsView.vue` | `/friends` | 好友列表 + 好友申请处理 |
| `BlacklistView.vue` | `/blacklist` | 黑名单列表与解除拉黑 |
| `NoticesView.vue` | `/notices` | 系统通知列表，标记已读/删除 |

---

## 三、接口封装

文件路径：`src/api/user.js`

```js
import request from './request'

// ── 用户资料 ──────────────────────────────────────────────────────

/**
 * 2.1 更新个人资料
 * @param {Object} data
 * @param {string} [data.userNickname] 昵称（3-16位）
 * @param {string} [data.userAvatar]   头像URL
 * @param {string} [data.userIntro]    简介（≤512字符）
 * @param {string} [data.userTags]     标签（逗号分隔，≤5个，每个≤20字符）
 */
export const updateProfile = (data) => request.post('/api/user/profile/update', data)

/**
 * 2.2 查看隐私设置
 * 响应字段：viewInfo（1-所有人 2-仅团队成员），sendMsg（1/2/3），searchByEmail（0/1）
 */
export const getPrivacy = () => request.get('/api/user/privacy')

/**
 * 2.3 更新隐私设置
 * @param {Object} data
 * @param {number} [data.viewInfo]       资料可见性：1-所有人，2-仅团队成员
 * @param {number} [data.sendMsg]        消息接收：1-所有人，2-仅团队成员，3-需验证
 * @param {number} [data.searchByEmail]  邮箱搜索：0-不允许，1-允许
 */
export const updatePrivacy = (data) => request.post('/api/user/privacy/update', data)

/**
 * 2.4 查看他人资料（根据隐私设置自动过滤）
 * @param {number} userId 目标用户ID
 */
export const getUserProfile = (userId) => request.get(`/api/user/${userId}/profile`)

/**
 * 2.5 获取用户列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=20]
 * @param {string} [params.sort='id']
 */
export const getUserList = (params) => request.get('/api/user/list', { params })

/**
 * 2.6 搜索用户
 * @param {Object} params
 * @param {string} params.keyword   搜索关键词
 * @param {string} [params.type='nickname'] account / nickname / tag
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=20]
 */
export const searchUser = (params) => request.get('/api/user/search', { params })

// ── 好友管理 ──────────────────────────────────────────────────────

/**
 * 2.7 添加好友
 * @param {Object} data
 * @param {number} data.friendId       目标用户ID
 * @param {string} [data.applyMsg]     申请备注
 * @param {string} [data.friendRemark] 好友备注名
 */
export const addFriend = (data) => request.post('/api/friend/add', data)

/**
 * 2.8 获取好友列表
 * 响应字段：friendId, friendRemark, userNickname, userAvatar, userIntro, agreeTime
 */
export const getFriendList = (params) => request.get('/api/friend/list', { params })

/**
 * 2.9 获取好友申请列表（别人发来的申请）
 * 响应字段：applicantId, userNickname, userAvatar, userTags, applyMsg, createTime
 */
export const getFriendRequests = (params) => request.get('/api/friend/requests', { params })

/** 2.10 同意好友申请 */
export const agreeFriend = (friendId) => request.post('/api/friend/agree', { friendId })

/** 2.11 拒绝好友申请 */
export const rejectFriend = (friendId) => request.post('/api/friend/reject', { friendId })

/** 2.12 删除好友（双向删除） */
export const deleteFriend = (friendId) => request.post('/api/friend/delete', { friendId })

// ── 黑名单 ────────────────────────────────────────────────────────

/** 2.13 拉黑用户 */
export const addBlacklist = (blackUserId) => request.post('/api/blacklist/add', { blackUserId })

/** 2.14 解除拉黑 */
export const removeBlacklist = (blackUserId) => request.post('/api/blacklist/remove', { blackUserId })

/** 2.15 获取黑名单列表 */
export const getBlacklist = (params) => request.get('/api/blacklist', { params })

// ── 系统通知 ──────────────────────────────────────────────────────

/** 2.16 获取未读通知数 */
export const getUnreadNoticeCount = () => request.get('/api/notice/unread-count')

/**
 * 2.17 获取通知列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=20]
 * @param {number} [params.isRead]  0-未读，1-已读，不传=全部
 */
export const getNoticeList = (params) => request.get('/api/notice/list', { params })

/** 2.18 标记通知已读 @param {number[]} ids 通知ID数组 */
export const readNotices = (ids) => request.post('/api/notice/read', ids)

/** 2.19 删除通知 @param {number[]} ids 通知ID数组 */
export const deleteNotices = (ids) => request.post('/api/notice/delete', ids)
```

---

## 四、页面详细设计

### 4.1 个人资料页（MyProfileView.vue）

**路由**：`/profile`  
**文件**：`src/views/user/MyProfileView.vue`

#### 展示内容

- 头像（点击可上传更换）
- 用户账号（只读，不可修改）
- 用户昵称（可编辑，3-16位）
- 个人简介（可编辑，≤512字符）
- 用户标签（可编辑，最多5个，每个≤20字符）
- 隐私设置（可编辑）：资料可见性、消息接收设置、是否允许邮箱搜索

#### 业务逻辑流程

```
页面挂载
    ↓
调用 GET /api/auth/me 获取当前用户完整信息
调用 GET /api/user/privacy 获取隐私设置
    ↓
用户修改资料，点击「保存」
    ↓
调用 POST /api/user/profile/update
    → 成功：更新 authStore.updateUserInfo()，刷新页面展示
    → 失败：展示错误提示
    ↓
用户修改隐私设置，点击「保存隐私设置」
    ↓
调用 POST /api/user/privacy/update
    → 成功：提示保存成功
```

#### 隐私设置选项映射

| 字段 | 选项值 | 展示文案 |
|------|--------|----------|
| viewInfo | 1 | 所有人可见 |
| viewInfo | 2 | 仅团队成员可见 |
| sendMsg | 1 | 所有人可发消息 |
| sendMsg | 2 | 仅团队成员可发消息 |
| sendMsg | 3 | 需要验证才能发消息 |
| searchByEmail | 1 | 允许通过邮箱搜索到我 |
| searchByEmail | 0 | 不允许通过邮箱搜索 |

---

### 4.2 他人资料页（UserProfileView.vue）

**路由**：`/profile/:userId`  
**文件**：`src/views/user/UserProfileView.vue`

#### 展示内容

- 头像、昵称、账号、简介、标签
- 在线状态徽标
- 操作按钮区（根据关系动态展示）

#### 操作按钮逻辑

| 关系状态 | 展示按钮 |
|----------|----------|
| 非好友、未申请 | 「添加好友」「拉黑」|
| 已发送申请、待对方确认 | 「申请中」（禁用）|
| 已是好友 | 「发消息」「删除好友」「拉黑」|
| 已被拉黑 | 「已拉黑」→ 点击解除拉黑 |
| 查看自己的主页 | 「编辑资料」（跳转 /profile）|

#### 业务逻辑流程

```
页面挂载
    ↓
从路由参数读取 userId
调用 GET /api/user/{userId}/profile
    → 成功：展示资料（后端已按隐私设置过滤）
    → 失败/无权查看：展示「该用户已设置隐私」
    ↓
点击「添加好友」
    ↓
弹出输入框（申请留言可选）
    ↓
调用 POST /api/friend/add
    → 成功：按钮变为「申请中」
    → 失败（被对方拉黑/隐私限制）：展示具体原因
    ↓
点击「发消息」→ 跳转 /chat/private/{userId}
```

---

### 4.3 好友列表页（FriendsView.vue）

**路由**：`/friends`  
**文件**：`src/views/user/FriendsView.vue`

#### 页面布局

顶部 Tab 切换：**好友列表** | **好友申请**（Tab标题显示未处理申请数角标）

**好友列表 Tab**：
- 搜索框（本地过滤好友昵称/备注）
- 好友卡片列表：头像、昵称、备注、简介、在线状态
- 每张卡片操作：「发消息」「删除好友」

**好友申请 Tab**：
- 申请卡片列表：头像、昵称、标签、申请留言、申请时间
- 每张卡片操作：「同意」「拒绝」

#### 业务逻辑流程

```
页面挂载
    ↓
调用 GET /api/friend/list（好友列表）
调用 GET /api/friend/requests（好友申请）
    ↓
点击「同意」
    ↓
调用 POST /api/friend/agree
    → 成功：将该申请从列表移除，好友列表增加该用户
    ↓
点击「拒绝」
    ↓
调用 POST /api/friend/reject
    → 成功：将该申请从列表移除
    ↓
点击「删除好友」
    ↓
弹出确认弹窗
    ↓
调用 POST /api/friend/delete
    → 成功：从好友列表移除
```

---

### 4.4 黑名单页（BlacklistView.vue）

**路由**：`/blacklist`  
**文件**：`src/views/user/BlacklistView.vue`

#### 展示内容

- 黑名单用户卡片：头像、昵称、拉黑时间
- 操作：「解除拉黑」（点击后弹确认弹窗）

#### 业务逻辑流程

```
页面挂载 → 调用 GET /api/blacklist
    ↓
点击「解除拉黑」→ 确认弹窗
    ↓
调用 POST /api/blacklist/remove
    → 成功：从列表中移除该用户
```

---

### 4.5 系统通知页（NoticesView.vue）

**路由**：`/notices`  
**文件**：`src/views/user/NoticesView.vue`

#### 展示内容

- 顶部操作栏：「全部标为已读」「删除已读通知」
- 通知列表（按时间倒序）：通知类型图标、通知文案、时间、已读状态
- 分页加载更多

#### 通知类型映射

| noticeType | 图标建议 | 展示文案示例 |
|------------|----------|----------|
| 1 | 👥 | `{nickname} 申请添加你为好友` |
| 2 | ❌ | `{nickname} 拒绝了你的好友申请` |
| 3 | ✅ | `你已成功加入团队「{teamName}」` |
| 4 | ❌ | `你加入团队「{teamName}」的申请被拒绝` |
| 5 | 🚪 | `你已被移出团队「{teamName}」` |
| 6 | ⚠️ | `账号异常提醒` |
| 7 | 🔇 | `你受到了处罚通知` |
| 8 | 💬 | `你的反馈已得到回复` |

#### 业务逻辑流程

```
页面挂载
    ↓
调用 GET /api/notice/list（默认全部，page=1）
调用 noticeStore.setUnreadCount(0)（进入页面后清零角标）
    ↓
点击单条通知 → 调用 POST /api/notice/read（[noticeId]）
    → 若 noticeType=1（好友申请）→ 跳转 /friends?tab=requests
    → 若 noticeType=3/4/5（团队相关）→ 跳转对应团队页
    ↓
点击「全部标为已读」
    ↓
收集所有未读通知ID，调用 POST /api/notice/read（ids[]）
    → 成功：刷新列表
    ↓
点击「删除」→ 调用 POST /api/notice/delete（[noticeId]）
```

---

## 五、公共组件

### UserCard.vue

**路径**：`src/components/common/UserCard.vue`  
**用途**：好友列表、搜索结果、团队成员列表中复用的用户信息卡片。

```vue
<script setup>
defineProps({
  user: {
    type: Object, // { userId, userNickname, userAvatar, userIntro, userTags, onlineStatus }
    required: true,
  },
  actions: {
    type: Array,  // [{ label, handler, type }] type: 'primary'|'danger'|'default'
    default: () => [],
  },
})
</script>

<template>
  <div class="user-card">
    <AvatarWithStatus :avatar="user.userAvatar" :status="user.onlineStatus" />
    <div class="user-info">
      <span class="nickname">{{ user.userNickname }}</span>
      <p class="intro">{{ user.userIntro }}</p>
      <TagList :tags="user.userTags?.split(',')" />
    </div>
    <div class="actions">
      <button
        v-for="action in actions"
        :key="action.label"
        :class="action.type"
        @click="action.handler(user)"
      >{{ action.label }}</button>
    </div>
  </div>
</template>
```

### AvatarWithStatus.vue

**路径**：`src/components/common/AvatarWithStatus.vue`  
**用途**：显示头像 + 右下角在线状态圆点。

```vue
<script setup>
defineProps({
  avatar: String,
  status: { type: Number, default: 0 }, // 0-离线 1-在线 2-离开 3-忙碌 4-隐身
  size: { type: Number, default: 40 },
})

const statusColor = { 0: '#999', 1: '#52c41a', 2: '#faad14', 3: '#f5222d', 4: '#d9d9d9' }
</script>

<template>
  <div style="position:relative;display:inline-block">
    <img :src="avatar" :width="size" :height="size" style="border-radius:50%;object-fit:cover" />
    <span
      v-if="status !== 0"
      :style="{ background: statusColor[status] }"
      style="position:absolute;bottom:1px;right:1px;width:10px;height:10px;border-radius:50%;border:2px solid #fff"
    />
  </div>
</template>
```

### TagList.vue

**路径**：`src/components/common/TagList.vue`  
**用途**：渲染用户/团队标签列表。

```vue
<script setup>
defineProps({
  tags: { type: Array, default: () => [] },
  max: { type: Number, default: 5 },
})
</script>

<template>
  <div class="tag-list">
    <span v-for="tag in tags.slice(0, max)" :key="tag" class="tag">{{ tag }}</span>
    <span v-if="tags.length > max" class="tag tag--more">+{{ tags.length - max }}</span>
  </div>
</template>
```

---

## 六、路由配置

在 `src/router/index.js` 的主布局子路由中添加：

```js
{ path: 'profile',          name: 'MyProfile',   component: () => import('@/views/user/MyProfileView.vue') },
{ path: 'profile/:userId',  name: 'UserProfile', component: () => import('@/views/user/UserProfileView.vue') },
{ path: 'friends',          name: 'Friends',     component: () => import('@/views/user/FriendsView.vue') },
{ path: 'blacklist',        name: 'Blacklist',   component: () => import('@/views/user/BlacklistView.vue') },
{ path: 'notices',          name: 'Notices',     component: () => import('@/views/user/NoticesView.vue') },
```

---

## 七、注意事项

1. **隐私策略前端配合**：查看他人资料时，后端已根据隐私设置过滤返回字段，前端无需二次处理，直接渲染接口返回内容即可；若接口返回空数据，统一展示「该用户已设置隐私」占位。

2. **好友申请状态管理**：好友申请列表需在进入 `/friends?tab=requests` 时重新拉取，不依赖本地缓存，确保数据实时性。

3. **未读通知角标**：侧边导航「通知」菜单项需展示未读数角标，数据来自 `useNoticeStore().unreadCount`。App 挂载后应调用 `GET /api/notice/unread-count` 初始化，进入通知页后清零。

4. **删除好友副作用**：删除好友后，对应的私聊会话仍可查看历史消息，但无法再发送新消息（后端会校验好友关系），前端需在聊天输入框处给出提示。

5. **黑名单影响**：拉黑用户后，对方将无法向你发送消息、查看你的资料，前端仅需在拉黑成功后刷新当前页面状态，无需额外处理。

6. **标签格式**：用户标签后端以逗号分隔字符串存储（如 `"Java,后端,游戏"`），前端展示时调用 `.split(',')` 转为数组；提交时将数组 `.join(',')` 转回字符串。

---

*本文档对应后端 `API文档_Part2_用户管理.md`，如后端接口有变更请同步更新本文档。*
