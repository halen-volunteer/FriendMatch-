# FriendMatch 前端开发文档 — Part 3 团队管理模块

> 版本：V1.0 | 日期：2026-03-19

**对应后端文档**：`API文档_Part3_团队管理.md`  
**页面路径前缀**：`/views/team/`  
**接口路径前缀**：`/api/team`  
**认证要求**：所有接口需携带 `Authorization: {token}`

---

## 目录

1. [模块概述](#一模块概述)
2. [页面列表](#二页面列表)
3. [接口封装](#三接口封装)
4. [页面详细设计](#四页面详细设计)
   - [4.1 团队广场页](#41-团队广场页-teamlistviewvue)
   - [4.2 团队详情页](#42-团队详情页-teamdetailviewvue)
   - [4.3 成员管理页](#43-成员管理页-teammembersviewvue)
5. [公共组件](#五公共组件)
6. [权限控制](#六权限控制)
7. [路由配置](#七路由配置)
8. [注意事项](#八注意事项)

---

## 一、模块概述

团队管理模块涵盖团队的创建、搜索、查看、加入（申请/密码/邀请）、审批流程、成员管理（移除/角色/禁言）以及解散团队等完整生命周期功能。前端需根据当前用户的 `roleType` 动态控制操作按钮的显示与隐藏。

**接口一览**：

| 编号 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 3.1 | POST | `/api/team/create` | 登录用户 | 创建团队 |
| 3.2 | GET | `/api/team/list` | 登录用户 | 获取团队列表 |
| 3.3 | GET | `/api/team/search` | 登录用户 | 搜索团队 |
| 3.4 | GET | `/api/team/{teamId}` | 登录用户 | 获取团队详情 |
| 3.5 | POST | `/api/team/update` | 队长 | 编辑团队 |
| 3.6 | POST | `/api/team/dissolve` | 队长 | 解散团队 |
| 3.7 | POST | `/api/team/apply` | 登录用户 | 申请加入 |
| 3.8 | POST | `/api/team/join-by-password` | 登录用户 | 密码加入 |
| 3.9 | POST | `/api/team/invite` | 队长/管理员 | 邀请用户 |
| 3.10 | POST | `/api/team/quit` | 成员 | 退出团队 |
| 3.11 | GET | `/api/team/apply/pending` | 队长/管理员 | 获取待审核申请 |
| 3.12 | POST | `/api/team/apply/audit` | 队长/管理员 | 审批申请 |
| 3.13 | GET | `/api/team/{teamId}/members` | 登录用户 | 获取成员列表 |
| 3.14 | POST | `/api/team/member/remove` | 队长/管理员 | 移除成员 |
| 3.15 | POST | `/api/team/member/role/update` | 队长 | 修改成员角色 |
| 3.16 | POST | `/api/team/transfer-leader` | 队长 | 转让队长 |
| 3.17 | POST | `/api/team/mute-all` | 队长/管理员 | 全员禁言/解除 |
| 3.18 | POST | `/api/team/member/mute` | 队长/管理员 | 禁言指定成员 |
| 3.19 | POST | `/api/team/member/unmute` | 队长/管理员 | 解除成员禁言 |

---

## 二、页面列表

| 文件 | 路由路径 | 说明 |
|------|----------|------|
| `TeamListView.vue` | `/teams` | 团队广场：浏览/搜索/加入团队 |
| `TeamDetailView.vue` | `/teams/:teamId` | 团队详情：查看信息、编辑、解散、退出 |
| `TeamMembersView.vue` | `/teams/:teamId/members` | 成员管理：禁言、移除、角色调整 |

---

## 三、接口封装

文件路径：`src/api/team.js`

```js
import request from './request'

// ── 团队 CRUD ──────────────────────────────────────────────────────

// 3.1 创建团队
// data: { teamName, teamIntro?, teamTags?, teamType, joinRule, teamPassword?, maxMemberNum }
export const createTeam = (data) => request.post('/api/team/create', data)

// 3.2 获取团队列表
// params: { page?, pageSize?, teamType?, sort? }
export const getTeamList = (params) => request.get('/api/team/list', { params })

// 3.3 搜索团队
// params: { keyword, type?: 'name'|'tag', page?, pageSize? }
export const searchTeam = (params) => request.get('/api/team/search', { params })

// 3.4 获取团队详情（含 currentUserRole 字段）
export const getTeamDetail = (teamId) => request.get(`/api/team/${teamId}`)

// 3.5 编辑团队（队长）
// data: { teamId, teamName?, teamIntro?, teamTags?, teamType?, joinRule?, teamPassword?, maxMemberNum? }
export const updateTeam = (data) => request.post('/api/team/update', data)

// 3.6 解散团队（队长）
export const dissolveTeam = (teamId) =>
  request.post('/api/team/dissolve', null, { params: { teamId } })

// ── 加入团队 ──────────────────────────────────────────────────────

// 3.7 申请加入（joinRule=1 申请审批）
export const applyTeam = (data) => request.post('/api/team/apply', data)

// 3.8 密码加入（joinRule=3）
export const joinByPassword = (data) => request.post('/api/team/join-by-password', data)

// 3.9 邀请用户（队长/管理员）
export const inviteUser = (data) => request.post('/api/team/invite', data)

// 3.10 退出团队（队长需先转让）
export const quitTeam = (teamId) =>
  request.post('/api/team/quit', null, { params: { teamId } })

// ── 审批流程 ──────────────────────────────────────────────────────

// 3.11 获取待审核申请列表
export const getPendingApplyList = (params) =>
  request.get('/api/team/apply/pending', { params })

// 3.12 审批申请
// data: { applyId, auditStatus: 1通过|2拒绝, rejectReason? }
export const auditApply = (data) => request.post('/api/team/apply/audit', data)

// ── 成员管理 ──────────────────────────────────────────────────────

// 3.13 获取成员列表
export const getTeamMembers = (teamId, params) =>
  request.get(`/api/team/${teamId}/members`, { params })

// 3.14 移除成员
export const removeMember = (data) => request.post('/api/team/member/remove', data)

// 3.15 修改成员角色（队长）roleType: 2-管理员 3-普通成员 4-嘉宾
export const updateMemberRole = (data) => request.post('/api/team/member/role/update', data)

// 3.16 转让队长
export const transferLeader = (data) => request.post('/api/team/transfer-leader', data)

// ── 禁言管理 ──────────────────────────────────────────────────────

// 3.17 全员禁言/解除 data: { teamId, muteAll: 1|0 }
export const muteAll = (data) => request.post('/api/team/mute-all', data)

// 3.18 禁言指定成员 data: { teamId, userId, muteDuration（分钟）}
export const muteMember = (data) => request.post('/api/team/member/mute', data)

// 3.19 解除指定成员禁言
export const unmuteMember = (data) => request.post('/api/team/member/unmute', data)
```

---

## 四、页面详细设计

### 4.1 团队广场页（TeamListView.vue）

**路由**：`/teams`  
**文件**：`src/views/team/TeamListView.vue`

#### 页面布局

- 顶部：搜索栏（关键词输入 + 搜索类型切换：名称/标签）+ 「创建团队」按钮
- 筛选栏：团队类型（全部/公开/私有）
- 团队卡片网格：每卡片展示团队名、简介、标签、成员数/上限、加入规则标识
- 底部分页/加载更多

#### 加入规则标识

| joinRule | 按钮文案 | 按钮行为 |
|----------|----------|----------|
| 1 | 申请加入 | 弹出留言输入框，调用 `/api/team/apply` |
| 2 | 仅邀请 | 按钮置灰禁用 |
| 3 | 密码加入 | 弹出密码输入框，调用 `/api/team/join-by-password` |

#### 创建团队表单字段

| 字段 | 校验规则 |
|------|----------|
| teamName | 必填，1-64字符 |
| teamIntro | 选填，≤512字符 |
| teamTags | ≤5个标签，每个≤20字符 |
| teamType | 必填：1-公开/2-私有 |
| joinRule | 必填：1-申请审批/2-仅邀请/3-密码加入 |
| teamPassword | joinRule=3时必填 |
| maxMemberNum | 必填，1-1000 |

---

### 4.2 团队详情页（TeamDetailView.vue）

**路由**：`/teams/:teamId`  
**文件**：`src/views/team/TeamDetailView.vue`

#### 展示内容

- 团队名称、简介、标签、类型、加入规则
- 成员数/最大成员数
- 当前用户角色徽标（队长/管理员/普通成员/嘉宾）
- 操作按钮区（按权限显隐）
- Tab：「群聊入口」「成员列表」「待审核申请」（仅队长/管理员可见）

#### 操作按钮权限矩阵

| 按钮 | 队长(1) | 管理员(2) | 成员(3) | 嘉宾(4) | 非成员 |
|------|---------|-----------|---------|---------|--------|
| 编辑团队资料 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 解散团队 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 退出团队 | ❌ | ✅ | ✅ | ✅ | ❌ |
| 进入群聊 | ✅ | ✅ | ✅ | ✅ | ❌ |
| 申请/加入 | ❌ | ❌ | ❌ | ❌ | ✅ |

#### 业务逻辑流程

```
页面挂载
    ↓
调用 GET /api/team/{teamId}（含 currentUserRole）
    ↓
根据 currentUserRole 渲染对应按钮
    ↓
点击「解散团队」→ 二次确认弹窗
    ↓
调用 POST /api/team/dissolve → 成功后跳转 /teams
    ↓
点击「退出团队」→ 确认弹窗
    ↓
调用 POST /api/team/quit → 成功后跳转 /teams
    ↓
点击「进入群聊」→ 跳转 /chat/team/{teamId}
```

#### 待审核申请 Tab（仅 roleType ≤ 2 可见）

- 调用 `GET /api/team/apply/pending?teamId={teamId}`
- 每条展示：申请人头像、昵称、标签、留言、申请时间
- 「通过」「拒绝」操作，拒绝时弹出原因输入框
- 调用 `POST /api/team/apply/audit { applyId, auditStatus: 1|2, rejectReason? }`

---

### 4.3 成员管理页（TeamMembersView.vue）

**路由**：`/teams/:teamId/members`  
**文件**：`src/views/team/TeamMembersView.vue`

#### 展示内容

- 全员禁言开关（仅 roleType ≤ 2 可见）
- 成员列表：头像、昵称、角色徽标、禁言状态标签、加入时间
- 每行操作按钮（按当前用户权限显示）

#### 成员操作权限

| 操作 | 队长对管理员 | 队长对普通/嘉宾 | 管理员对普通/嘉宾 |
|------|------------|----------------|------------------|
| 移除 | ✅ | ✅ | ✅ |
| 禁言 | ✅ | ✅ | ✅ |
| 升为管理员 | ✅ | ✅ | ❌ |
| 降为成员 | ✅ | ✅ | ❌ |
| 转让队长 | ✅ | ✅ | ❌ |

#### 禁言时长预设

| 选项 | 分钟数 |
|------|--------|
| 10分钟 | 10 |
| 1小时 | 60 |
| 1天 | 1440 |
| 7天 | 10080 |
| 自定义 | 用户输入 |

---

## 五、公共组件

### TeamCard.vue

**路径**：`src/components/common/TeamCard.vue`

```vue
<script setup>
defineProps({
  team: Object, // { teamId, teamName, teamIntro, teamTags, joinRule, memberCount, maxMemberNum }
})
const emit = defineEmits(['apply', 'join-by-password'])
const joinRuleLabel = { 1: '申请加入', 2: '仅邀请', 3: '密码加入' }
</script>

<template>
  <div class="team-card">
    <h3>{{ team.teamName }}</h3>
    <p>{{ team.teamIntro }}</p>
    <TagList :tags="team.teamTags?.split(',')" />
    <span>{{ team.memberCount }}/{{ team.maxMemberNum }} 人</span>
    <button :disabled="team.joinRule === 2"
      @click="team.joinRule === 1 ? emit('apply', team) : emit('join-by-password', team)">
      {{ joinRuleLabel[team.joinRule] }}
    </button>
  </div>
</template>
```

### RoleBadge.vue

**路径**：`src/components/common/RoleBadge.vue`

```vue
<script setup>
const props = defineProps({ roleType: Number })
const roleMap = {
  1: { label: '队长', color: '#f5a623' },
  2: { label: '管理员', color: '#4a90e2' },
  3: { label: '成员', color: '#888' },
  4: { label: '嘉宾', color: '#b0b0b0' },
}
</script>
<template>
  <span :style="{ background: roleMap[props.roleType]?.color, color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' }">
    {{ roleMap[props.roleType]?.label }}
  </span>
</template>
```

---

## 六、权限工具函数

文件路径：`src/utils/teamPermission.js`

```js
// 是否可审批申请（队长/管理员）
export const canAudit = (roleType) => roleType <= 2

// 是否可移除/禁言目标成员
export function canOperateMember(myRole, targetRole) {
  if (myRole === 1) return targetRole !== myRole  // 队长可操作所有人（除自己）
  if (myRole === 2) return targetRole >= 3         // 管理员只能操作普通成员/嘉宾
  return false
}

// 是否可解散
export const canDissolve = (roleType) => roleType === 1

// 是否可转让队长/修改角色
export const canManageRole = (roleType) => roleType === 1
```

---

## 七、路由配置

```js
// 主布局子路由
{ path: 'teams',                 name: 'Teams',       component: () => import('@/views/team/TeamListView.vue') },
{ path: 'teams/:teamId',         name: 'TeamDetail',  component: () => import('@/views/team/TeamDetailView.vue') },
{ path: 'teams/:teamId/members', name: 'TeamMembers', component: () => import('@/views/team/TeamMembersView.vue') },
```

---

## 八、注意事项

1. **currentUserRole 每次进页面重新取**：不缓存角色，每次进入团队详情/成员页都重新调用接口，防止权限状态过期。

2. **队长退出限制**：`roleType === 1` 时隐藏「退出」按钮，提示「请先转让队长权限后再退出」。

3. **解散二次确认**：建议弹窗中要求用户输入团队名称后才能点击确认，防止误操作。

4. **禁言豁免**：队长和管理员免疫禁言，前端在全员禁言状态下仍允许其发送消息（后端同样豁免）。

5. **joinRule=2 仅邀请**：团队广场展示但不可主动加入，按钮禁用；入群只能通过管理员调用「邀请」接口。

6. **标签提交格式**：同用户标签，数组 `.join(',')` 后提交，展示时 `.split(',')` 转为数组。

---

*本文档对应后端 `API文档_Part3_团队管理.md`，如接口变更请同步更新。*
 