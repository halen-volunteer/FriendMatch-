<script setup>
import {
  Delete,
  Edit,
  Lock,
  More,
  Operation,
  Search,
  Star,
  Top,
  UserFilled,
  WarningFilled,
} from '@element-plus/icons-vue'
import AvatarWithStatus from '@/components/common/AvatarWithStatus.vue'
import ChatToolPanel from '@/components/chat/ChatToolPanel.vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import ReasonReportDialog from '@/components/chat/ReasonReportDialog.vue'
import TextActionDialog from '@/components/chat/TextActionDialog.vue'
import { escapeHtml } from '@/utils/chatText'
import { useTeamChatPage } from '@/composables/useTeamChatPage'

const {
  MESSAGE_REPORT_REASON_OPTIONS,
  TEAM_REPORT_REASON_OPTIONS,
  actionSubmitting,
  activePanel,
  allMuteDialog,
  canAtAll,
  canManageMuteAll,
  clearAllFailedMessages,
  closeMentionPanel,
  closePanel,
  collectModal,
  collections,
  confirmRemoveMember,
  deleteFailedMessage,
  draftText,
  editModal,
  errorMsg,
  formatMessageDivider,
  getManageMemberBlockReason,
  getMemberActionItems,
  getMessageSenderAvatar,
  getMessageSenderNickname,
  getPanelMessageText,
  groupNotice,
  handleCancelCollect,
  handleCollect,
  handleEdit,
  handleMemberAction,
  handlePin,
  handleReport,
  handleRevoke,
  handleSend,
  handleUnpin,
  loadingPanel,
  memberActionDialog,
  memberRemoveConfirm,
  mentionCandidates,
  mentionKeyword,
  mentionPanelVisible,
  messages,
  msgListRef,
  muteDurationOptions,
  muteState,
  myRole,
  noticeDraft,
  openAllMuteDialog,
  openPanel,
  openTeamDetail,
  openTeamMembers,
  openTeamReportModal,
  openUserProfile,
  panelTitles,
  pins,
  renderPanelHighlightedText,
  reportModal,
  retrySend,
  runSearch,
  saveNotice,
  scrollToMessage,
  searchKeyword,
  searchResults,
  selectMentionTarget,
  setMessageInputRef,
  shouldShowTime,
  submitAllMuteDialog,
  submitCollect,
  submitEdit,
  submitMuteMemberDialog,
  submitReport,
  submitTeamReport,
  team,
  teamDisplayName,
  teamMemberCountText,
  teamReportModal,
  toast,
  toggleMentionPanel,
} = useTeamChatPage()

function renderSearchResultHtml(item) {
  const highlighted = renderPanelHighlightedText(item, searchKeyword.value || '')
  const fallback = escapeHtml(getPanelMessageText(item) || '[消息]')
  const visibleText = String(highlighted || '').replace(/<[^>]+>/g, '').trim()
  return visibleText ? highlighted : fallback
}

function formatSearchResultTime(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN')
}
</script>

<template>
  <div class="chat-win">
    <div class="chat-topbar">
      <div class="team-head">
        <div class="team-icon-wrap">
          <AvatarWithStatus :avatar="team.teamAvatar || ''" :size="36">
            {{ team.teamName?.charAt(0) || '团' }}
          </AvatarWithStatus>
        </div>
        <div class="team-meta">
          <span class="chat-name">{{ teamDisplayName }}</span>
          <span class="team-subtitle">{{ teamMemberCountText }}</span>
        </div>
      </div>

      <div class="top-actions">
        <el-dropdown v-if="myRole > 0" trigger="click">
          <el-button type="warning" size="small">
            <el-icon><Operation /></el-icon>
            <span>团队操作</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openTeamDetail">
                <el-icon><Edit /></el-icon>
                <span>{{ myRole === 1 ? '编辑团队资料' : '查看团队资料' }}</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="myRole <= 2" @click="openTeamMembers">
                <el-icon><UserFilled /></el-icon>
                <span>成员权限管理</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="click">
          <el-button type="text" size="small" class="action-btn">
            <el-icon><More /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openPanel('notice')">
                <span>群公告</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('search')">
                <el-icon><Search /></el-icon>
                <span>消息搜索</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('pins')">
                <el-icon><Top /></el-icon>
                <span>消息置顶</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('collections')">
                <el-icon><Star /></el-icon>
                <span>我的收藏</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openTeamReportModal">
                <el-icon><WarningFilled /></el-icon>
                <span>举报团队</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="clearAllFailedMessages">
                <el-icon><Delete /></el-icon>
                <span class="text-danger">清理失败消息</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button type="primary" size="small" @click="openTeamMembers">
          <el-icon><UserFilled /></el-icon>
          <span>成员</span>
        </el-button>
      </div>
    </div>

    <div v-if="activePanel" class="panel-wrap">
      <ChatToolPanel v-if="activePanel === 'notice'" :title="panelTitles.notice" @close="closePanel">
        <el-input
          v-model="noticeDraft"
          type="textarea"
          rows="4"
          :readonly="myRole > 2"
          placeholder="请输入群公告"
          class="notice-editor"
        />
        <div class="panel-item-actions">
          <span class="panel-item-time">当前公告：{{ groupNotice || '暂无公告' }}</span>
          <el-button
            v-if="myRole <= 2"
            type="primary"
            size="small"
            :loading="actionSubmitting.saveNotice"
            @click="saveNotice"
          >
            保存公告
          </el-button>
        </div>
      </ChatToolPanel>

      <ChatToolPanel v-if="activePanel === 'search'" :title="panelTitles.search" @close="closePanel">
        <div class="search-row">
          <el-input
            v-model="searchKeyword"
            placeholder="输入关键词搜索本群消息"
            size="small"
            class="search-input"
            @keyup.enter="runSearch"
          />
          <el-button type="primary" size="small" @click="runSearch">搜索</el-button>
        </div>
        <div v-if="loadingPanel" class="panel-empty">搜索中...</div>
        <el-empty v-else-if="!searchResults.length" description="暂无结果" class="panel-empty" />
        <div v-else class="panel-list">
          <button
            v-for="item in searchResults"
            :key="item.msgId"
            type="button"
            class="panel-item panel-item-clickable search-result-item"
            @click="scrollToMessage(item.msgId)"
          >
            <div class="panel-item-text" v-html="renderSearchResultHtml(item)"></div>
            <span class="panel-item-time">{{ formatSearchResultTime(item.createTime) }}</span>
          </button>
        </div>
      </ChatToolPanel>

      <ChatToolPanel v-if="activePanel === 'pins'" :title="panelTitles.pins" @close="closePanel">
        <div v-if="loadingPanel" class="panel-empty">加载中...</div>
        <el-empty v-else-if="!pins.length" description="暂无置顶消息" class="panel-empty" />
        <div v-else class="panel-list">
          <el-card
            v-for="item in pins"
            :key="item.pinId"
            class="panel-item panel-item-clickable"
            @click="scrollToMessage(item.messageId || item.msgId)"
          >
            <div class="panel-item-text">{{ getPanelMessageText(item) }}</div>
            <div class="panel-item-actions">
              <span class="panel-item-time">#{{ item.pinOrder }}</span>
              <el-button
                type="text"
                size="small"
                class="text-danger"
                :loading="actionSubmitting.unpin"
                @click.stop="handleUnpin(item.pinId)"
              >
                取消置顶
              </el-button>
            </div>
          </el-card>
        </div>
      </ChatToolPanel>

      <ChatToolPanel v-if="activePanel === 'collections'" :title="panelTitles.collections" @close="closePanel">
        <div v-if="loadingPanel" class="panel-empty">加载中...</div>
        <el-empty v-else-if="!collections.length" description="暂无收藏消息" class="panel-empty" />
        <div v-else class="panel-list">
          <el-card
            v-for="item in collections"
            :key="item.collectionId"
            class="panel-item panel-item-clickable"
            @click="scrollToMessage(item.messageId || item.msgId)"
          >
            <div class="panel-item-text">{{ getPanelMessageText(item) }}</div>
            <div class="panel-sub">{{ item.collectionNote || '无备注' }}</div>
            <div class="panel-item-actions">
              <span class="panel-item-time">{{ new Date(item.collectionTime).toLocaleString('zh-CN') }}</span>
              <el-button
                type="text"
                size="small"
                class="text-danger"
                :loading="actionSubmitting.cancelCollect"
                @click.stop="handleCancelCollect(item.collectionId)"
              >
                取消收藏
              </el-button>
            </div>
          </el-card>
        </div>
      </ChatToolPanel>
    </div>

    <el-alert
      v-if="toast.msg"
      :title="toast.msg"
      :type="toast.type"
      show-icon
      class="toast"
      @close="toast.msg = ''"
    />

    <ReasonReportDialog
      v-model="reportModal"
      title="举报消息"
      content-placeholder="补充举报说明，便于管理员判断"
      :options="MESSAGE_REPORT_REASON_OPTIONS"
      :loading="actionSubmitting.report"
      submit-text="提交举报"
      @submit="submitReport"
    />

    <TextActionDialog
      v-model="collectModal"
      title="收藏消息"
      label="收藏备注"
      placeholder="输入收藏备注，方便后续检索"
      :loading="actionSubmitting.collect"
      submit-text="确认收藏"
      field="note"
      @submit="submitCollect"
    />

    <TextActionDialog
      v-model="editModal"
      title="编辑消息"
      :loading="actionSubmitting.edit"
      submit-text="保存"
      :rows="5"
      field="content"
      @submit="submitEdit"
    />

    <ReasonReportDialog
      v-model="teamReportModal"
      title="举报团队"
      content-placeholder="请补充举报说明，便于管理员处理"
      :options="TEAM_REPORT_REASON_OPTIONS"
      :loading="actionSubmitting.teamReport"
      submit-text="提交举报"
      @submit="submitTeamReport"
    />

    <div ref="msgListRef" class="msg-list">
      <el-empty v-if="!messages.length" description="暂无消息" class="empty-tip" />
      <div
        v-for="(msg, index) in messages"
        :id="`msg-${msg.msgId}`"
        :key="msg.msgId"
        class="msg-anchor"
      >
        <div v-if="shouldShowTime(index)" class="msg-divider-time">
          {{ formatMessageDivider(msg.createTime) }}
        </div>
        <MessageBubble
          :msg="msg"
          :is-self="!!msg.isSelf"
          :sender-id="msg.senderId"
          :sender-avatar="getMessageSenderAvatar(msg)"
          :sender-nickname="getMessageSenderNickname(msg)"
          :member-actions="getMemberActionItems(msg)"
          :member-action-block-reason="getManageMemberBlockReason(msg.senderId)"
          @revoke="handleRevoke"
          @report="handleReport"
          @collect="handleCollect"
          @pin="handlePin"
          @retry="retrySend"
          @delete-failed="deleteFailedMessage"
          @edit="handleEdit"
          @member-action="handleMemberAction"
          @member-action-block="(reason) => reason && (toast.msg = reason, toast.type = 'error')"
          @open-profile="openUserProfile"
        />
      </div>
    </div>

    <div v-if="errorMsg" class="error-tip">{{ errorMsg }}</div>

    <el-dialog v-model="memberActionDialog.visible" title="设置禁言时长" width="420px">
      <div class="member-dialog-content">
        <p class="member-dialog-tip">成员：{{ memberActionDialog.userNickname || '该成员' }}</p>
        <el-radio-group v-model="memberActionDialog.duration" class="member-duration-group">
          <el-radio-button
            v-for="option in muteDurationOptions"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="memberActionDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="actionSubmitting.memberMute" @click="submitMuteMemberDialog">
            确认禁言
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="memberRemoveConfirm.visible" title="移出团队" width="420px">
      <p class="member-dialog-tip">确认将 {{ memberRemoveConfirm.userNickname || '该成员' }} 移出团队吗？</p>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="memberRemoveConfirm.visible = false">取消</el-button>
          <el-button type="danger" :loading="memberRemoveConfirm.loading" @click="confirmRemoveMember">
            确认移出
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="allMuteDialog.visible" title="设置全员禁言时长" width="420px">
      <div class="member-dialog-content">
        <p class="member-dialog-tip">请选择本次全员禁言时长</p>
        <el-radio-group v-model="allMuteDialog.duration" class="member-duration-group">
          <el-radio-button
            v-for="option in muteDurationOptions"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="allMuteDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="actionSubmitting.muteAll" @click="submitAllMuteDialog()">
            确认设置
          </el-button>
        </div>
      </template>
    </el-dialog>

    <MessageInput
      :ref="setMessageInputRef"
      :draft-text="draftText"
      :disabled="muteState.disabled"
      :disabled-reason="muteState.reason"
      @update:draft-text="draftText = $event"
      @send="handleSend"
    >
      <template #extra-tools>
        <div class="mention-tool-wrap">
          <button
            type="button"
            class="tool-btn mention-btn"
            :disabled="muteState.disabled || actionSubmitting.send"
            title="选择要@的成员"
            @click.stop="toggleMentionPanel"
          >
            <span class="mention-text">@</span>
          </button>

          <div v-if="mentionPanelVisible" class="mention-popover" @click.stop>
            <div class="mention-search-row">
              <el-input
                v-model="mentionKeyword"
                size="small"
                placeholder="搜索成员"
                clearable
              />
            </div>
            <div class="mention-list">
              <button
                v-for="item in mentionCandidates"
                :key="item.key"
                type="button"
                class="mention-item"
                @click="selectMentionTarget(item)"
              >
                <span class="mention-item-title">{{ item.token }}</span>
                <span class="mention-item-sub">
                  {{ item.key === 'all' && canAtAll ? '通知全体在线成员' : '插入到输入框' }}
                </span>
              </button>
              <div v-if="!mentionCandidates.length" class="mention-empty">没有匹配成员</div>
            </div>
            <div class="mention-footer">
              <el-button size="small" text @click="closeMentionPanel">关闭</el-button>
            </div>
          </div>
        </div>

        <button
          v-if="canManageMuteAll"
          type="button"
          class="tool-btn mute-all-btn"
          :class="{ active: team.teamAllMute === 1 || actionSubmitting.muteAll }"
          :disabled="actionSubmitting.muteAll"
          :title="team.teamAllMute === 1 ? '解除全员禁言' : '设置全员禁言'"
          @click="openAllMuteDialog"
        >
          <el-icon><Lock /></el-icon>
        </button>
      </template>
    </MessageInput>
  </div>
</template>

<style scoped>
.chat-win {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f9f9f9;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  padding: 0 18px;
  background: #ffffff;
  border-bottom: 1px solid #dddddd;
}

.team-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.team-icon-wrap {
  flex-shrink: 0;
}

.team-icon-wrap :deep(.avatar-status-wrap) {
  display: flex;
}

.chat-name {
  font-weight: 500;
  font-size: 16px;
  color: #222;
}

.team-subtitle {
  font-size: 12px;
  color: #9ca3af;
}

.top-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.panel-wrap {
  padding: 12px 16px 0;
}

.notice-editor,
.search-input,
.modal-input {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.panel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 188px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.panel-item {
  border: 1px solid rgba(123, 117, 34, 0.1);
  border-radius: 14px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.92);
}

.panel-item-clickable {
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.panel-item-clickable:hover {
  transform: translateY(-1px);
  border-color: var(--color-accent2-2);
  box-shadow: 0 8px 24px rgba(32, 14, 51, 0.08);
}

.search-result-item {
  width: 100%;
  padding: 10px;
  border-width: 1px;
  border-style: solid;
  background: rgba(255, 255, 255, 0.92);
  text-align: left;
}

.panel-item-text {
  font-size: 13px;
  color: var(--wx-text);
  line-height: 1.55;
  word-break: break-word;
  overflow-wrap: anywhere;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.panel-sub,
.panel-item-time {
  font-size: 11px;
  color: var(--wx-muted);
}

.panel-item-time {
  display: block;
  margin-top: 6px;
  white-space: nowrap;
}

.panel-item-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.panel-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--wx-muted);
  font-size: 13px;
}

.toast {
  margin: 10px 16px 0;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f5f5f5;
}

.msg-anchor {
  border-radius: 14px;
  transition: background-color 0.25s ease, box-shadow 0.25s ease;
}

.msg-anchor.msg-highlight,
.msg-highlight {
  background: rgba(188, 84, 172, 0.1);
  box-shadow: 0 0 0 1px rgba(188, 84, 172, 0.28);
}

:deep(mark) {
  background: rgba(255, 245, 114, 0.72);
  color: var(--color-primary-5);
  padding: 0 2px;
  border-radius: 4px;
}

.empty-tip {
  text-align: center;
  color: var(--wx-muted);
  font-size: 13px;
  padding: 40px 0;
}

.msg-divider-time {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 10px 0 12px;
  font-size: 11px;
  color: #a0a0a0;
}

.error-tip {
  color: #d32f2f;
  font-size: 12px;
  padding: 4px 18px 0;
}

.member-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.member-dialog-tip {
  margin: 0;
  color: #444;
  font-size: 14px;
  line-height: 1.7;
}

.member-duration-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mention-tool-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  outline: none;
  border-radius: 8px;
  background: transparent;
  color: #555e68;
  font-size: 17px;
  box-shadow: none;
  transition: background-color 0.18s ease, color 0.18s ease;
  cursor: pointer;
}

.tool-btn:hover {
  background: #f2f4f7;
  color: #2f353c;
}

.tool-btn:focus,
.tool-btn:focus-visible,
.tool-btn:active {
  outline: none;
  border: none;
  box-shadow: none;
}

.tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mention-btn,
.mute-all-btn {
  position: relative;
}

.mention-text {
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.mention-popover {
  position: absolute;
  left: -8px;
  bottom: calc(100% + 12px);
  width: 264px;
  padding: 10px;
  border-radius: 16px;
  border: 1px solid #dde2e8;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.12);
  z-index: 30;
}

.mention-search-row {
  margin-bottom: 8px;
}

.mention-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 208px;
  overflow-y: auto;
}

.mention-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  outline: none;
  border-radius: 12px;
  background: #f7f8fa;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.mention-item:hover {
  background: #eef1f4;
}

.mention-item:focus,
.mention-item:focus-visible,
.mention-item:active {
  outline: none;
  border: none;
  box-shadow: none;
}

.mention-item-title {
  font-size: 14px;
  color: #1f2329;
  font-weight: 500;
}

.mention-item-sub,
.mention-empty {
  font-size: 12px;
  color: #8b93a1;
}

.mention-empty {
  padding: 14px 6px 6px;
  text-align: center;
}

.mention-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.mute-all-btn.active {
  color: #d84a4a;
  background: #fdf0f0;
}
</style>
