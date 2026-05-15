<script setup>
import { Search, UserFilled, Lock, Message, Loading } from '@element-plus/icons-vue'
import ConfirmCard from '@/components/common/ConfirmCard.vue'
import TagList from '@/components/common/TagList.vue'
import BlacklistDetailCard from '@/components/friends/BlacklistDetailCard.vue'
import FriendDetailCard from '@/components/friends/FriendDetailCard.vue'
import FriendRequestDetailCard from '@/components/friends/FriendRequestDetailCard.vue'
import TeamDetailCard from '@/components/friends/TeamDetailCard.vue'
import TeamJoinDialogs from '@/components/team/TeamJoinDialogs.vue'
import { useFriendsPage } from '@/composables/useFriendsPage'

const {
  applyMsg,
  closeConfirm,
  confirmState,
  currentBlacklistUser,
  currentFriend,
  currentRequest,
  currentTeam,
  enterTeam,
  getJoinRuleText,
  getTeamInitial,
  getTeamIntro,
  getTeamMemberText,
  getTeamTypeText,
  getUserAccount,
  getUserBio,
  getUserInitial,
  handleAgree,
  handleApply,
  handleApplyDialog,
  handleJoinPasswordDialog,
  handleJoinPwd,
  handleReject,
  handleRemoveBlacklist,
  isBlacklistSection,
  isFriendsSection,
  isRequestsSection,
  isSquareSection,
  isTeamsSection,
  joinPwd,
  joinPwdSubmitting,
  loadSquareTeams,
  loading,
  openTeamDetail,
  requestSubmitting,
  showApply,
  showJoinPwd,
  squareKeyword,
  squareLoading,
  squareTeams,
  submitRemoveBlacklist,
  teamApplySubmitting,
  toast,
  sendMessageToFriend,
} = useFriendsPage()
</script>

<template>
  <div class="friends-view-root">
    <div class="page friends-page contact-manage-page">
      <el-alert
        v-if="toast.msg"
        :title="toast.msg"
        :type="toast.type"
        show-icon
        class="toast"
        @close="toast.msg = ''"
      />

      <div v-if="loading" class="detail-empty">加载中...</div>

      <template v-else-if="isFriendsSection">
        <div class="detail-head"><h2>好友</h2></div>
        <div v-if="!currentFriend" class="detail-empty">请选择一个好友</div>
        <FriendDetailCard
          v-else
          :friend="currentFriend"
          :get-user-initial="getUserInitial"
          :get-user-account="getUserAccount"
          :get-user-bio="getUserBio"
          @send-message="sendMessageToFriend"
        />
      </template>

      <template v-else-if="isRequestsSection">
        <div class="detail-head"><h2>新的朋友</h2></div>
        <div v-if="!currentRequest" class="detail-empty">请选择一个好友申请</div>
        <FriendRequestDetailCard
          v-else
          :request="currentRequest"
          :request-submitting="requestSubmitting"
          :get-user-initial="getUserInitial"
          :get-user-account="getUserAccount"
          :get-user-bio="getUserBio"
          @agree="handleAgree"
          @reject="handleReject"
        />
      </template>

      <template v-else-if="isTeamsSection">
        <div class="detail-head"><h2>团队管理</h2></div>
        <div v-if="!currentTeam" class="detail-empty">请选择一个团队</div>
        <TeamDetailCard
          v-else
          :team="currentTeam"
          :get-team-initial="getTeamInitial"
          :get-team-member-text="getTeamMemberText"
          :get-team-intro="getTeamIntro"
          :get-team-type-text="getTeamTypeText"
          :get-join-rule-text="getJoinRuleText"
          @enter-team="enterTeam(currentTeam)"
        />
      </template>

      <template v-else-if="isBlacklistSection">
        <div class="detail-head"><h2>黑名单管理</h2></div>
        <div v-if="!currentBlacklistUser" class="detail-empty">请选择一个黑名单用户</div>
        <BlacklistDetailCard
          v-else
          :user="currentBlacklistUser"
          :loading="confirmState.loading"
          :get-user-initial="getUserInitial"
          :get-user-account="getUserAccount"
          :get-user-bio="getUserBio"
          @remove-blacklist="handleRemoveBlacklist()"
        />
      </template>

      <template v-else-if="isSquareSection">
        <div class="square-shell">
          <div class="page-header square-header">
            <h2>团队广场</h2>
          </div>

          <div class="search-bar">
            <el-input
              v-model="squareKeyword"
              placeholder="搜索团队名称或标签..."
              class="search-input"
              @keyup.enter="loadSquareTeams"
            >
              <template #append>
                <el-button type="primary" @click="loadSquareTeams">
                  <el-icon><Search /></el-icon>
                  <span>搜索</span>
                </el-button>
              </template>
            </el-input>
          </div>

          <div v-if="squareLoading" class="square-loading-container">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <el-empty v-else-if="!squareTeams.length" description="暂无团队" />
          <div v-else class="team-grid">
            <el-card
              v-for="team in squareTeams"
              :key="team.id"
              shadow="hover"
              class="team-card"
              @click="openTeamDetail(team.id)"
            >
              <template #header>
                <div class="team-header">
                  <el-avatar :size="48" :src="team.teamAvatar" class="team-avatar">
                    {{ getTeamInitial(team.teamName) }}
                  </el-avatar>
                  <div class="team-info">
                    <h3 class="team-name">{{ team.teamName }}</h3>
                    <div class="team-meta">
                      <span class="team-members">{{ team.memberCount || 0 }}人</span>
                      <span class="team-type">{{ getTeamTypeText(team.teamType) }}</span>
                    </div>
                  </div>
                </div>
              </template>
              <div class="team-content">
                <div class="team-intro">
                  <span class="content-label">简介：</span>
                  <span class="content-text">{{ getTeamIntro(team.teamIntro) }}</span>
                </div>
                <div class="team-tags-row">
                  <span class="content-label">标签：</span>
                  <TagList :tags="team.teamTags" empty-text="暂无标签" />
                </div>
              </div>
              <div class="team-actions" @click.stop>
                <el-button
                  v-if="team.joinRule === 1"
                  type="primary"
                  size="small"
                  :loading="teamApplySubmitting && showApply.team?.id === team.id"
                  :disabled="teamApplySubmitting"
                  @click="handleApplyDialog(team)"
                >
                  <el-icon><UserFilled /></el-icon>
                  申请加入
                </el-button>
                <el-button
                  v-else-if="team.joinRule === 3"
                  type="success"
                  size="small"
                  :loading="joinPwdSubmitting && showJoinPwd.team?.id === team.id"
                  :disabled="joinPwdSubmitting"
                  @click="handleJoinPasswordDialog(team)"
                >
                  <el-icon><Lock /></el-icon>
                  密码加入
                </el-button>
                <el-button v-else type="info" size="small" disabled>
                  <el-icon><Message /></el-icon>
                  仅邀请
                </el-button>
              </div>
            </el-card>
          </div>
        </div>
      </template>
    </div>

    <TeamJoinDialogs
      v-model:apply-visible="showApply.show"
      v-model:apply-msg="applyMsg"
      v-model:join-visible="showJoinPwd.show"
      v-model:join-pwd="joinPwd"
      :apply-team="showApply.team"
      :apply-submitting="teamApplySubmitting"
      :join-team="showJoinPwd.team"
      :join-submitting="joinPwdSubmitting"
      @submit-apply="handleApply"
      @submit-join="handleJoinPwd"
    />

    <ConfirmCard
      v-model:visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      :confirm-text="confirmState.confirmText"
      :loading="confirmState.loading"
      danger
      @confirm="submitRemoveBlacklist"
      @cancel="closeConfirm"
    />
  </div>
</template>

<style scoped>
.friends-view-root {
  height: 100%;
}

.contact-manage-page {
  max-width: none;
  height: 100%;
  padding: 28px 32px;
  background: #fff;
}

.toast {
  margin-bottom: 16px;
}

.detail-head h2,
.square-header h2 {
  margin: 0 0 18px;
  color: #1f1f1f;
  font-size: 28px;
}

.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 16px;
}

.square-loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  color: #999;
  font-size: 16px;
}

.square-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.square-header,
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-input {
  width: 100%;
}

.team-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(460px, 1fr));
  gap: 20px;
  overflow: auto;
  padding-bottom: 8px;
}

.team-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 20px;
}

.team-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.team-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.team-avatar {
  border: 3px solid #f0f0f0;
}

.team-info {
  flex: 1;
}

.team-name {
  margin: 0 0 4px 0;
  color: #111;
  font-size: 18px;
  font-weight: 700;
}

.team-meta {
  display: flex;
  gap: 12px;
  color: #888;
  font-size: 14px;
}

.team-intro {
  color: #555;
  font-size: 14px;
  line-height: 1.7;
}

.team-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 98px;
}

.team-tags-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.content-label {
  color: #666;
  font-size: 14px;
  font-weight: 600;
}

.content-text {
  color: #444;
}

.team-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
