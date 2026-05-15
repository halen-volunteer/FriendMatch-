<script setup>
import ConfirmCard from '@/components/common/ConfirmCard.vue'
import TagList from '@/components/common/TagList.vue'
import TagInput from '@/components/common/TagInput.vue'
import {
  ArrowDown,
  ChatDotRound,
  Check,
  Close,
  Delete,
  Edit,
  Lock,
  Loading,
  Operation,
  SwitchButton,
  UploadFilled,
  UserFilled,
} from '@element-plus/icons-vue'
import { useTeamDetailPage } from '@/composables/useTeamDetailPage'

const {
  activeTab,
  applyMsg,
  applySubmitting,
  canJoin,
  canManageApplies,
  closeConfirm,
  confirmState,
  editForm,
  editMode,
  formatCreateTime,
  getApplyInitial,
  getJoinRuleText,
  getTeamInitial,
  getTeamTypeText,
  handleAudit,
  handleDissolve,
  handleQuit,
  handleSaveEdit,
  joinPassword,
  joinSubmitting,
  loading,
  myRole,
  openApplyDialog,
  openJoinPasswordDialog,
  openTeamChat,
  openTeamMembers,
  openTeamOperation,
  pendingApplies,
  saveDisabled,
  showApplyDialog,
  showJoinPwdDialog,
  submitApplyJoin,
  submitConfirm,
  submitJoinByPassword,
  team,
  teamAvatarInputRef,
  toast,
  triggerTeamAvatarUpload,
  uploadTeamAvatar,
  uploadTeamAvatarText,
  uploadingTeamAvatar,
} = useTeamDetailPage()
</script>

<template>
  <div class="page team-detail-page">
    <div v-if="loading" class="loading-container loading-center-xl">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <el-empty v-else-if="!team" description="团队不存在" />

    <div v-else>
      <el-alert
        v-if="toast.msg"
        :title="toast.msg"
        :type="toast.type"
        show-icon
        class="toast page-toast-lg"
        @close="toast.msg = ''"
      />

      <el-card class="team-hero" shadow="hover">
        <div class="team-header">
          <div class="avatar-editor">
            <el-avatar :size="88" :src="editMode ? (editForm.teamAvatar || team.teamAvatar) : team.teamAvatar" class="team-avatar">
              {{ getTeamInitial(team.teamName) }}
            </el-avatar>
            <el-button
              v-if="editMode && myRole === 1"
              type="primary"
              size="small"
              class="avatar-upload-btn"
              :loading="uploadingTeamAvatar"
              @click="triggerTeamAvatarUpload"
            >
              <el-icon><UploadFilled /></el-icon>
              <span>{{ uploadTeamAvatarText }}</span>
            </el-button>
            <input
              ref="teamAvatarInputRef"
              type="file"
              hidden
              accept="image/*"
              @change="uploadTeamAvatar"
            />
          </div>

          <div class="team-basic">
            <div class="title-row">
              <h2>{{ team.teamName }}</h2>
              <el-tag v-if="myRole === 1" type="danger" effect="dark">队长</el-tag>
              <el-tag v-else-if="myRole === 2" type="warning" effect="dark">管理员</el-tag>
              <el-tag v-else-if="myRole === 3" type="success" effect="dark">成员</el-tag>
            </div>
            <div class="meta-row">
              <span class="member-count">{{ team.memberCount }}/{{ team.maxMember }} 人</span>
              <span class="dot">·</span>
              <span>{{ getTeamTypeText(team.teamType) }}</span>
              <span class="dot">·</span>
              <span>{{ getJoinRuleText(team.joinRule) }}</span>
            </div>
            <p class="hero-intro">{{ team.teamIntro || '这个团队还没有填写简介。' }}</p>
          </div>
        </div>
      </el-card>

      <div class="action-bar">
        <el-button v-if="myRole > 0" type="primary" @click="openTeamChat">
          <el-icon><ChatDotRound /></el-icon>
          <span>进入群聊</span>
        </el-button>
        <el-button v-if="myRole > 0" type="success" @click="openTeamMembers">
          <el-icon><UserFilled /></el-icon>
          <span>成员列表</span>
        </el-button>

        <el-dropdown v-if="myRole > 0" trigger="click">
          <el-button type="warning">
            <el-icon><Operation /></el-icon>
            <span>团队操作</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="myRole === 1" @click="editMode = !editMode">
                <el-icon><Edit /></el-icon>
                <span>{{ editMode ? '收起资料编辑' : '编辑团队资料' }}</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="myRole > 1" @click="handleQuit">
                <el-icon><SwitchButton /></el-icon>
                <span>退出团队</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="myRole === 1" divided @click="handleDissolve">
                <el-icon><Delete /></el-icon>
                <span class="text-danger">解散团队</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <template v-if="canJoin">
          <el-button v-if="team.joinRule === 1" type="primary" @click="openApplyDialog">
            <el-icon><UserFilled /></el-icon>
            <span>申请加入团队</span>
          </el-button>
          <el-button v-else-if="team.joinRule === 3" type="success" @click="openJoinPasswordDialog">
            <el-icon><Lock /></el-icon>
            <span>密码加入团队</span>
          </el-button>
          <el-button v-else type="info" disabled>
            <el-icon><Lock /></el-icon>
            <span>该团队仅支持邀请加入</span>
          </el-button>
        </template>
      </div>

      <el-tabs v-model="activeTab" class="team-tabs">
        <el-tab-pane label="团队资料" name="info">
          <el-card shadow="hover">
            <div v-if="!editMode">
              <p class="team-intro">{{ team.teamIntro || '暂无简介' }}</p>
              <div class="team-tags">
                <TagList :tags="team.teamTags" empty-text="暂无标签" />
              </div>
              <div class="team-details">
                <el-divider content-position="left">团队信息</el-divider>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="团队类型">
                    {{ getTeamTypeText(team.teamType) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="加入方式">
                    {{ getJoinRuleText(team.joinRule) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="创建时间">
                    {{ formatCreateTime(team.createTime) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="团队 ID">
                    {{ team.id || team.teamId || '未知' }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>

            <el-form v-else :model="editForm" class="edit-form" label-position="top">
              <div class="edit-grid">
                <el-form-item label="团队名称" required>
                  <el-input v-model="editForm.teamName" maxlength="64" placeholder="请输入团队名称" />
                </el-form-item>
                <el-form-item label="团队头像">
                  <el-input v-model="editForm.teamAvatar" placeholder="可上传头像，也可直接粘贴图片地址" />
                </el-form-item>
              </div>

              <el-form-item label="简介">
                <el-input
                  v-model="editForm.teamIntro"
                  type="textarea"
                  rows="4"
                  maxlength="512"
                  show-word-limit
                  placeholder="请输入团队简介"
                />
              </el-form-item>

              <el-form-item label="标签">
                <TagInput v-model="editForm.teamTags" placeholder="输入标签后回车，也可以用逗号一次输入多个标签" />
              </el-form-item>

              <div class="edit-grid">
                <el-form-item label="最大人数">
                  <el-input-number
                    v-model="editForm.maxMember"
                    :min="1"
                    :max="1000"
                    class="w-full"
                  />
                </el-form-item>
                <el-form-item label="团队类型">
                  <el-select v-model="editForm.teamType" class="w-full">
                    <el-option :value="1" label="公开团队" />
                    <el-option :value="2" label="私有团队" />
                  </el-select>
                </el-form-item>
              </div>

              <div class="edit-grid">
                <el-form-item label="加入方式">
                  <el-select v-model="editForm.joinRule" class="w-full">
                    <el-option :value="1" label="申请审批" />
                    <el-option :value="2" label="仅邀请" />
                    <el-option :value="3" label="密码加入" />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="editForm.joinRule === 3" label="加入密码" required>
                  <el-input
                    v-model="editForm.joinPassword"
                    type="password"
                    show-password
                    maxlength="32"
                    placeholder="请输入新的加入密码"
                  />
                </el-form-item>
              </div>

              <el-form-item>
                <div class="row-actions">
                  <el-button
                    type="primary"
                    :disabled="saveDisabled"
                    :loading="uploadingTeamAvatar"
                    @click="handleSaveEdit"
                  >
                    保存资料
                  </el-button>
                  <el-button @click="editMode = false">取消</el-button>
                </div>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane v-if="canManageApplies" :label="`待审批 (${pendingApplies.length})`" name="apply">
          <el-card shadow="hover">
            <el-empty v-if="!pendingApplies.length" description="暂无待审批申请" />
            <el-list v-else class="apply-list">
              <el-list-item v-for="apply in pendingApplies" :key="apply.applyId" class="apply-item">
                <template #prefix>
                  <el-avatar :size="48" :src="apply.userAvatar" class="apply-avatar">
                    {{ getApplyInitial(apply.userNickname) }}
                  </el-avatar>
                </template>
                <div class="apply-info">
                  <div class="apply-name">{{ apply.userNickname }}</div>
                  <div v-if="apply.applyMsg" class="apply-msg">“{{ apply.applyMsg }}”</div>
                  <div class="apply-time">
                    申请时间：{{ formatCreateTime(apply.createTime) }}
                  </div>
                </div>
                <template #suffix>
                  <div class="apply-actions">
                    <el-button type="success" size="small" @click="handleAudit(apply.applyId, 1)">
                      <el-icon><Check /></el-icon>
                      通过
                    </el-button>
                    <el-button type="danger" size="small" @click="handleAudit(apply.applyId, 2)">
                      <el-icon><Close /></el-icon>
                      拒绝
                    </el-button>
                  </div>
                </template>
              </el-list-item>
            </el-list>
          </el-card>
        </el-tab-pane>
      </el-tabs>

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

      <el-dialog v-model="showApplyDialog" title="申请加入团队" width="420px">
        <el-form :model="{ applyMsg }">
          <el-form-item label="申请留言">
            <el-input
              v-model="applyMsg"
              type="textarea"
              rows="4"
              maxlength="200"
              show-word-limit
              placeholder="可以简单说明一下你为什么想加入这个团队"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="row-actions">
            <el-button @click="showApplyDialog = false">取消</el-button>
            <el-button type="primary" :loading="applySubmitting" @click="submitApplyJoin">提交申请</el-button>
          </div>
        </template>
      </el-dialog>

      <el-dialog v-model="showJoinPwdDialog" title="密码加入团队" width="420px">
        <el-form :model="{ joinPassword }">
          <el-form-item label="加入密码" required>
            <el-input
              v-model="joinPassword"
              type="password"
              show-password
              maxlength="32"
              placeholder="请输入团队加入密码"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="row-actions">
            <el-button @click="showJoinPwdDialog = false">取消</el-button>
            <el-button type="primary" :loading="joinSubmitting" @click="submitJoinByPassword">确认加入</el-button>
          </div>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
.team-detail-page {
  max-width: 960px;
}

.loading-container {
  min-height: 500px;
}

.team-hero {
  margin-bottom: 20px;
}

.team-header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.avatar-editor {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.team-avatar {
  border: 3px solid #f0f0f0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 30px;
  font-weight: bold;
}

.avatar-upload-btn {
  min-width: 108px;
}

.team-basic {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.team-basic h2 {
  font-size: 26px;
  font-weight: bold;
  color: #111;
  margin: 0;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: #666;
  font-size: 14px;
  flex-wrap: wrap;
}

.member-count {
  font-weight: 600;
}

.dot {
  color: #b0b0b0;
}

.hero-intro {
  margin: 14px 0 0;
  color: #555;
  line-height: 1.8;
}

.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.team-tabs {
  margin-bottom: 20px;
}

.team-intro {
  font-size: 14px;
  color: #555;
  line-height: 1.7;
  margin-bottom: 16px;
}

.team-tags {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.content-label {
  color: #555;
  font-size: 14px;
  font-weight: 600;
}

.team-details {
  margin-top: 20px;
}

.edit-form {
  margin-top: 12px;
}

.edit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.row-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.apply-list {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.apply-item {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.apply-item:last-child {
  border-bottom: none;
}

.apply-avatar {
  border: 2px solid #f0f0f0;
}

.apply-info {
  flex: 1;
  margin-left: 16px;
}

.apply-name {
  font-size: 16px;
  font-weight: bold;
  color: #111;
  margin-bottom: 4px;
}

.apply-msg {
  font-size: 14px;
  color: #555;
  margin-bottom: 4px;
}

.apply-time {
  font-size: 12px;
  color: #888;
}

.apply-actions {
  display: flex;
  gap: 8px;
}

.text-danger {
  color: #d9363e;
}

@media (max-width: 768px) {
  .team-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .title-row,
  .meta-row,
  .row-actions,
  .action-bar {
    justify-content: center;
  }

  .edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
