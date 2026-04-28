# 前端 TypeScript 类型定义文档

> 目录对应：`VueFormatCode/src`
> 目的：虽然当前项目是 JS，但建议优先统一“接口类型思维”，即使先不改为 TS，也按本文档维护 JSDoc / 类型注释。
> 当前技术栈：Vue 3 + Vite + Pinia + vue-router + axios

---

## 一、建议新增目录

建议未来在 `src` 下增加：

```text
src/
  types/
    auth.ts
    user.ts
    team.ts
    chat.ts
    notice.ts
    report.ts
    search.ts
    common.ts
```

如果短期不改 TS，也可以先写成：

```text
src/types/
  auth.js
  user.js
  team.js
  chat.js
```

并用 JSDoc 注释类型。

---

## 二、通用类型 `common.ts`

```ts
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  total?: number
}

export interface PageQuery {
  page?: number
  pageSize?: number
}

export interface IdQuery {
  id: number
}
```

结合你当前 `src/api/request.js` 的拦截器，前端最终拿到的是：

```ts
interface RequestResult<T = unknown> {
  code: 200 | 400
  message: string
  data: T
  total?: number
}
```

---

## 三、认证模块类型 `auth.ts`

```ts
export interface LoginForm {
  loginAccount: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export interface RegisterForm {
  userNickname: string
  userEmail: string
  userPassword: string
  emailCode: string
}

export interface AuthUserInfo {
  userId: number
  userNickname: string
  userAvatar: string
  userAccount: string
}

export interface LoginResult {
  token: string
  userId: number
  userNickname: string
  userAvatar: string
  userAccount: string
}
```

### 对应现有 store

`src/stores/auth.js` 当前已使用：
- `token`
- `userInfo`
- `isLoggedIn`
- `userId`
- `userNickname`
- `userAvatar`

因此后端登录结果前端最低需要适配为：

```ts
interface AuthStorePayload {
  token: string
  id?: number
  userId?: number
  userNickname?: string
  userAvatar?: string
  userAccount?: string
}
```

---

## 四、用户模块类型 `user.ts`

```ts
export interface UserProfileVO {
  id: number
  userAccount: string
  userNickname: string
  userAvatar: string
  userTags: string
  userIntro: string
  friendStatus?: number
}

export interface UserProfileUpdateDTO {
  userNickname?: string
  userIntro?: string
  userAvatar?: string
  userTags?: string[]
}

export interface PrivacySettingDTO {
  viewInfo: 1 | 2
  sendMsg: 1 | 2 | 3
  searchByEmail: 0 | 1
}

export interface FriendVO {
  userId: number
  userNickname: string
  userAvatar: string
  userAccount?: string
  friendRemark?: string
  friendStatus: 0 | 1 | 2 | 3
}

export interface BlacklistVO {
  userId: number
  userNickname: string
  userAvatar: string
  blackReason?: string
}
```

---

## 五、团队模块类型 `team.ts`

### 5.1 枚举

```ts
export type TeamRole = 1 | 2 | 3
export type TeamType = 1 | 2
export type JoinRule = 1 | 2 | 3
export type TeamAuditStatus = 0 | 1 | 2
```

> 注意：**没有 `4` 嘉宾**。

### 5.2 团队基础类型

```ts
export interface TeamCardVO {
  id: number
  teamName: string
  teamAvatar: string
  teamIntro: string
  teamTags: string
  creatorId: number
  maxMember: number
  teamType: TeamType
  joinRule: JoinRule
  teamAllMute: 0 | 1
  memberCount: number
  isMember?: 0 | 1
}

export interface TeamDetailVO extends TeamCardVO {
  myRoleType?: TeamRole
}

export interface TeamMemberVO {
  userId: number
  userNickname: string
  userAvatar: string
  roleType: TeamRole
  joinTime?: string
  lastActiveTime?: string
  teamMuteType?: 0 | 1
  teamMuteUnpunishTime?: string | null
}
```

### 5.3 表单类型

```ts
export interface TeamCreateDTO {
  teamName: string
  teamDesc?: string
  teamTags?: string[]
  teamType: TeamType
  joinRule: JoinRule
  joinPassword?: string
  maxMember?: number
}

export interface TeamUpdateDTO {
  teamId: number
  teamName?: string
  teamDesc?: string
  teamTags?: string[]
  teamAvatar?: string
  teamType?: TeamType
  joinRule?: JoinRule
  joinPassword?: string
  maxMember?: number
}
```

---

## 六、聊天模块类型 `chat.ts`

### 6.1 基础枚举

```ts
export type RecvType = 1 | 2
export type MsgType = 1 | 2 | 3 | 4 | 5
export type ReceiptType = 1 | 2
```

### 6.2 消息类型

```ts
export interface ChatMessageVO {
  id: number
  senderId: number
  recvType: RecvType
  recvId: number
  conversationId: string
  msgType: MsgType
  msgContent: string
  isEdited: 0 | 1
  createTime: string
  updateTime?: string
}

export interface PrivateMsgDTO {
  recvUserId: number
  msgType: MsgType
  msgContent: string
}

export interface TeamMsgDTO {
  teamId: number
  msgType: MsgType
  msgContent: string
  atUserIds?: number[]
}

export interface MsgReadDTO {
  conversationId: string
  msgIds: number[]
}
```

### 6.3 会话与未读

```ts
export interface ConversationUnreadVO {
  conversationId: string
  unreadCount: number
  lastMsg?: string
  lastTime?: string
}
```

### 6.4 WebSocket 消息体

```ts
export interface WsMessage<T = unknown> {
  event: string
  data: T
}
```

建议事件名：

```ts
export type WsEvent =
  | 'message_new'
  | 'message_read'
  | 'message_edit'
  | 'message_revoke'
  | 'system_notice'
  | 'group_notice_update'
```

---

## 七、消息增强模块类型 `message.ts`

```ts
export interface MessageCollectDTO {
  messageId: number
  collectionNote?: string
}

export interface MessagePinDTO {
  conversationId: string
  messageId: number
}

export interface MessageReportDTO {
  messageId: number
  reportReason: 1 | 2 | 3 | 4 | 5 | 6
  reportContent?: string
}

export interface GroupNoticeDTO {
  teamId: number
  noticeContent: string
}

export interface OssPresignResult {
  uploadUrl: string
  fileUrl: string
}
```

---

## 八、通知与治理模块类型 `notice.ts` / `report.ts`

```ts
export interface SystemNoticeVO {
  id: number
  userId: number
  noticeType: 1 | 2 | 3 | 4 | 5 | 6
  noticeContent: string
  relatedId?: number
  isRead: 0 | 1
  readTime?: string | null
  createTime: string
}

export interface UserReportDTO {
  reportedUserId: number
  reportReason: 1 | 2 | 3 | 4 | 5 | 6
  reportContent?: string
  reportEvidence?: string
}

export interface TeamReportDTO {
  reportedTeamId: number
  reportReason: 1 | 2 | 3 | 4 | 5
  reportContent?: string
  reportEvidence?: string
}

export interface AppealDTO {
  relatedReportId: number
  relatedReportType: 1 | 2
  appealReason: string
  appealEvidence?: string
}

export interface FeedbackDTO {
  feedbackType: 1 | 2 | 3 | 4
  feedbackTitle?: string
  feedbackContent: string
  feedbackAttachment?: string
  feedbackImg?: string
}
```

---

## 九、搜索推荐类型 `search.ts`

```ts
export type SearchType = 1 | 2

export interface SearchHistoryVO {
  id: number
  searchType: SearchType
  searchKeyword: string
  searchCount: number
  lastSearchTime: string
}

export interface HotKeywordVO {
  keyword: string
  searchType: SearchType
  searchCount: number
  rank: number
}

export interface UserRecommendationVO {
  id: number
  userId: number
  recommendToUserId: number
  recommendReason: 1 | 2 | 3 | 4
  recommendScore: number
  isClicked: 0 | 1
  isAdded: 0 | 1
}

export interface TeamRecommendationVO {
  id: number
  teamId: number
  recommendToUserId: number
  recommendReason: 1 | 2 | 3 | 4
  recommendScore: number
  isClicked: 0 | 1
  isJoined: 0 | 1
}
```

---

## 十、结合你当前项目结构的落地建议

### 10.1 对应 `src/api/`

建议保留并逐步补全：

- `auth.js`
- `user.js`
- `team.js`
- `chat.js`
- `feedback.js`
- `online.js`

建议新增：

- `notice.js`
- `report.js`
- `search.js`
- `recommend.js`
- `device.js`
- `message.js`

### 10.2 对应 `src/stores/`

当前已有：
- `auth.js`
- `chat.js`
- `notice.js`

建议新增：
- `team.js`
- `user.js`
- `search.js`

### 10.3 对应 `src/views/`

当前已有：
- `auth/`
- `chat/`
- `team/`
- `user/`

建议未来补充：

```text
src/views/search/
  SearchView.vue
  RecommendView.vue

src/views/device/
  DeviceListView.vue

src/views/report/
  ReportCenterView.vue
  AppealView.vue
```

---

## 十一、最小结论

你当前前端项目虽然是 JS，但结构已经很适合继续开发。现在最合理的做法不是重构，而是：

1. 先按本文档补全类型思维
2. 用 JSDoc 或类型文件约束 API 返回
3. 在不改技术栈的前提下继续推进页面开发

这已经足够支撑当前前端继续往下做。