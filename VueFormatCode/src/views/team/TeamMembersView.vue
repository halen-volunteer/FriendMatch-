<script setup>
import ConfirmCard from '@/components/common/ConfirmCard.vue'
import { Bell, Loading, UserFilled } from '@element-plus/icons-vue'
import { useTeamMembersPage } from '@/composables/useTeamMembersPage'

const {
  canDemoteToMember,
  canPromoteToAdmin,
  canTransfer,
  closeConfirm,
  confirmState,
  contextMenu,
  getMemberInitial,
  handleContextMute,
  handleContextRemove,
  handleContextRoleChange,
  handleContextTransfer,
  isRowSubmitting,
  load,
  loading,
  members,
  muteDialog,
  muteDurationOptions,
  openContextMenu,
  openMemberProfile,
  pageAction,
  pageHint,
  roleFilter,
  submitConfirm,
  submitMuteDialog,
  toast,
} = useTeamMembersPage()
</script>

<template>
  <div class="page team-members-page">
    <div class="page-header">
      <h2>成员列表</h2>
      <div class="toolbar-right">
        <el-select v-model="roleFilter" placeholder="按身份筛选" clearable class="role-filter" @change="load">
          <el-option :value="1" label="队长" />
          <el-option :value="2" label="管理员" />
          <el-option :value="3" label="成员" />
        </el-select>
      </div>
    </div>

    <el-alert
      :title="pageHint"
      :type="pageAction === 'transfer' ? 'warning' : 'info'"
      show-icon
      class="page-hint"
      :closable="false"
    />

    <el-alert
      v-if="toast.msg"
      :title="toast.msg"
      :type="toast.type"
      show-icon
      class="toast"
      @close="toast.msg = ''"
    />

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <el-empty v-else-if="!members.length" description="暂无成员" />

    <div v-else class="members-list">
      <div
        v-for="member in members"
        :key="member.userId"
        class="member-item"
        @click="openMemberProfile(member)"
        @contextmenu="openContextMenu($event, member)"
      >
        <el-avatar :size="44" :src="member.userAvatar" class="member-avatar">
          {{ getMemberInitial(member.userNickname) }}
        </el-avatar>

        <div class="member-info">
          <div class="member-main">
            <span class="member-name">{{ member.userNickname || '未命名用户' }}</span>
            <el-tag v-if="member.roleType === 1" type="danger" effect="light" round class="role-tag">
              <el-icon><UserFilled /></el-icon>
              <span>队长</span>
            </el-tag>
            <el-tag v-else-if="member.roleType === 2" type="warning" effect="light" round class="role-tag">
              <el-icon><UserFilled /></el-icon>
              <span>管理员</span>
            </el-tag>
            <el-tag v-else type="success" effect="light" round class="role-tag">
              <el-icon><UserFilled /></el-icon>
              <span>成员</span>
            </el-tag>
            <el-tag v-if="member.isMuted" type="info" effect="plain" round class="mute-tag">
              <el-icon><Bell /></el-icon>
              <span>已禁言</span>
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <teleport to="body">
      <div
        v-if="contextMenu.visible && contextMenu.member"
        class="member-context-menu"
        :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
        @click.stop
      >
        <button
          class="menu-item"
          :disabled="isRowSubmitting(contextMenu.member.isMuted ? 'unmute' : 'mute', contextMenu.member.userId)"
          @click="handleContextMute(contextMenu.member)"
        >
          {{ contextMenu.member.isMuted ? '解除禁言' : '禁言成员' }}
        </button>
        <button
          v-if="canPromoteToAdmin(contextMenu.member)"
          class="menu-item"
          :disabled="isRowSubmitting('role-2', contextMenu.member.userId)"
          @click="handleContextRoleChange(contextMenu.member, 2)"
        >
          设置为管理员
        </button>
        <button
          v-if="canDemoteToMember(contextMenu.member)"
          class="menu-item"
          :disabled="isRowSubmitting('role-3', contextMenu.member.userId)"
          @click="handleContextRoleChange(contextMenu.member, 3)"
        >
          设置为普通用户
        </button>
        <button
          v-if="canTransfer(contextMenu.member)"
          class="menu-item"
          :disabled="isRowSubmitting('transfer', contextMenu.member.userId)"
          @click="handleContextTransfer(contextMenu.member)"
        >
          移交队长
        </button>
        <button
          class="menu-item danger-item"
          :disabled="isRowSubmitting('remove', contextMenu.member.userId)"
          @click="handleContextRemove(contextMenu.member)"
        >
          移出团队
        </button>
      </div>
    </teleport>

    <el-dialog v-model="muteDialog.visible" title="设置禁言时长" width="420px">
      <div class="mute-dialog-content">
        <p class="mute-dialog-tip">成员：{{ muteDialog.userNickname || '该成员' }}</p>
        <el-radio-group v-model="muteDialog.duration" class="mute-duration-group">
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
          <el-button @click="muteDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="submitMuteDialog">确认禁言</el-button>
        </div>
      </template>
    </el-dialog>

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
  </div>
</template>

<style scoped>
.team-members-page {
  max-width: 760px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2329;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.role-filter {
  width: 150px;
}

.page-hint {
  margin-bottom: 14px;
}

.toast {
  margin-bottom: 14px;
}

.loading-container {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.members-list {
  background: #fff;
  border-radius: 18px;
  border: 1px solid rgba(31, 35, 41, 0.08);
  overflow: hidden;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.member-item + .member-item {
  border-top: 1px solid rgba(31, 35, 41, 0.06);
}

.member-item:hover {
  background: #f7f8fa;
}

.member-avatar {
  flex-shrink: 0;
  background: #f0f2f5;
  color: #657180;
}

.member-info {
  min-width: 0;
  flex: 1;
}

.member-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: nowrap;
}

.member-name {
  min-width: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  line-height: 1.35;
  color: #1f2329;
  font-weight: 500;
}

.role-tag,
.mute-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  font-size: 12px;
}

.mute-tag {
  color: #7b8190;
}

.member-context-menu {
  position: fixed;
  z-index: 4200;
  min-width: 164px;
  background: rgba(255, 255, 255, 0.985);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 16px;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.18);
  padding: 8px;
}

.menu-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  color: #222;
}

.menu-item:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.05);
}

.menu-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.danger-item {
  color: #cf3f36;
}

.danger-item:hover:not(:disabled) {
  background: rgba(207, 63, 54, 0.08);
}

.mute-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mute-dialog-tip {
  margin: 0;
  color: #444;
  font-size: 14px;
}

.mute-duration-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 768px) {
  .team-members-page {
    max-width: 100%;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-right {
    justify-content: space-between;
  }

  .role-filter {
    width: 100%;
  }

  .member-name {
    max-width: 130px;
  }
}
</style>
