<script setup>
import { More, Search, Top, Star, Delete, CircleClose, RemoveFilled, WarningFilled } from '@element-plus/icons-vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import ChatToolPanel from '@/components/chat/ChatToolPanel.vue'
import ReasonReportDialog from '@/components/chat/ReasonReportDialog.vue'
import TextActionDialog from '@/components/chat/TextActionDialog.vue'
import AvatarWithStatus from '@/components/common/AvatarWithStatus.vue'
import ConfirmCard from '@/components/common/ConfirmCard.vue'
import { usePrivateChatPage } from '@/composables/usePrivateChatPage'
import { escapeHtml } from '@/utils/chatText'

const {
  MESSAGE_REPORT_REASON_OPTIONS,
  USER_REPORT_REASON_OPTIONS,
  actionSubmitting,
  activePanel,
  authStore,
  clearAllFailedMessages,
  closeConfirm,
  closePanel,
  collectModal,
  collections,
  confirmState,
  deleteFailedMessage,
  editModal,
  errorMsg,
  formatMessageDivider,
  friend,
  friendDisplayAvatar,
  friendDisplayName,
  friendOnlineStatus,
  friendStatusText,
  getPanelMessageText,
  handleBlacklistFriend,
  handleCancelCollectSafe,
  handleCollect,
  handleDeleteFriend,
  handleEdit,
  handlePinSafe,
  handleReport,
  handleRevokeSafe,
  isFriendBlacklisted,
  loadingPanel,
  messages,
  msgListRef,
  openPanel,
  openUserProfile,
  openUserReportModal,
  panelTitles,
  pins,
  privateInputDisabled,
  privateInputDisabledReason,
  renderPanelHighlightedText,
  reportModal,
  retrySend,
  runSearch,
  scrollToMessage,
  searchKeyword,
  searchResults,
  shouldShowTime,
  submitCollectSafe,
  submitConfirm,
  submitEditSafe,
  submitReportSafe,
  submitUserReportSafe,
  toast,
  userReportModal,
  handleSend,
} = usePrivateChatPage()

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
      <div class="chat-user">
        <AvatarWithStatus :avatar="friendDisplayAvatar" :status="friendOnlineStatus" :size="36" />
        <div class="chat-user-meta">
          <span class="chat-name">{{ friendDisplayName }}</span>
          <span class="chat-status">{{ friendStatusText }}</span>
        </div>
      </div>

      <div class="top-actions">
        <el-dropdown>
          <el-button type="text" size="small" class="action-btn">
            <el-icon><More /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
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
              <el-dropdown-item @click="openUserReportModal">
                <el-icon><WarningFilled /></el-icon>
                <span>举报用户</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleDeleteFriend">
                <el-icon><CircleClose /></el-icon>
                <span>删除好友</span>
              </el-dropdown-item>
              <el-dropdown-item :disabled="isFriendBlacklisted" @click="handleBlacklistFriend">
                <el-icon><RemoveFilled /></el-icon>
                <span>{{ isFriendBlacklisted ? '已拉黑好友' : '拉黑好友' }}</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="clearAllFailedMessages">
                <el-icon><Delete /></el-icon>
                <span class="text-danger">清理失败消息</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="activePanel" class="panel-wrap">
      <ChatToolPanel v-if="activePanel === 'search'" :title="panelTitles.search" @close="closePanel">
        <div class="search-row">
          <el-input
            v-model="searchKeyword"
            placeholder="输入关键词搜索当前会话"
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
                @click.stop="handleUnpinSafe(item.pinId)"
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
                @click.stop="handleCancelCollectSafe(item.collectionId)"
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

    <ConfirmCard
      v-model:visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      :confirm-text="confirmState.confirmText"
      :loading="confirmState.loading"
      danger
      @confirm="submitConfirm"
      @cancel="closeConfirm"
    />

    <ReasonReportDialog
      v-model="reportModal"
      title="举报消息"
      content-placeholder="补充举报说明，可选"
      :options="MESSAGE_REPORT_REASON_OPTIONS"
      :loading="actionSubmitting.report"
      submit-text="提交举报"
      @submit="submitReportSafe"
    />

    <TextActionDialog
      v-model="collectModal"
      title="收藏消息"
      label="收藏备注"
      placeholder="输入收藏备注，方便后续检索"
      :loading="actionSubmitting.collect"
      submit-text="确认收藏"
      field="note"
      @submit="submitCollectSafe"
    />

    <TextActionDialog
      v-model="editModal"
      title="编辑消息"
      :loading="actionSubmitting.edit"
      submit-text="保存"
      :rows="5"
      field="content"
      @submit="submitEditSafe"
    />

    <ReasonReportDialog
      v-model="userReportModal"
      title="举报用户"
      content-placeholder="请补充举报说明，可选"
      :options="USER_REPORT_REASON_OPTIONS"
      :loading="actionSubmitting.userReport"
      submit-text="提交举报"
      @submit="submitUserReportSafe"
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
          :sender-id="msg.isSelf ? authStore.userId : (friend.userId || friend.id || friend.friendId)"
          :sender-avatar="msg.isSelf ? authStore.userAvatar : friendDisplayAvatar"
          :sender-nickname="msg.isSelf ? authStore.userNickname : (friend.userNickname || friend.userAccount)"
          @revoke="handleRevokeSafe"
          @report="handleReport"
          @collect="handleCollect"
          @pin="handlePinSafe"
          @retry="retrySend"
          @delete-failed="deleteFailedMessage"
          @edit="handleEdit"
          @open-profile="openUserProfile"
        />
      </div>
    </div>

    <div v-if="errorMsg" class="error-tip">{{ errorMsg }}</div>

    <MessageInput
      :disabled="privateInputDisabled"
      :disabled-reason="privateInputDisabledReason"
      @send="handleSend"
    />
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

.chat-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-name {
  font-weight: 500;
  font-size: 16px;
  color: #222;
}

.chat-status {
  font-size: 12px;
  color: #9ca3af;
}

.top-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.panel-wrap {
  padding: 12px 16px 0;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.search-input,
.modal-input {
  width: 100%;
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
</style>

