<script setup>
import { Plus, Search, UserFilled, Lock, Message, Loading } from '@element-plus/icons-vue'
import TagList from '@/components/common/TagList.vue'
import TagInput from '@/components/common/TagInput.vue'
import TeamJoinDialogs from '@/components/team/TeamJoinDialogs.vue'
import { useTeamListPage } from '@/composables/useTeamListPage'

const {
  applyMsg,
  applySubmitting,
  createForm,
  createSubmitting,
  getTeamInitial,
  getTeamIntro,
  getTeamTypeText,
  handleApply,
  handleApplyDialog,
  handleCreate,
  handleJoinPasswordDialog,
  handleJoinPwd,
  joinPwd,
  joinSubmitting,
  keyword,
  load,
  loading,
  openTeamDetail,
  showApply,
  showCreate,
  showJoinPwd,
  teams,
  toast,
} = useTeamListPage()
</script>

<template>
  <div class="page team-list-page page-container-xl">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">团队广场</h2>
      <el-button type="primary" @click="showCreate = true">
        <el-icon><Plus /></el-icon>
        <span>创建团队</span>
      </el-button>
    </div>

    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast page-toast-lg" @close="toast.msg = ''" />

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索团队名称或标签..." @keyup.enter="load" class="search-input">
        <template #append>
          <el-button type="primary" @click="load">
            <el-icon><Search /></el-icon>
            <span>搜索</span>
          </el-button>
        </template>
      </el-input>
    </div>

    <div v-if="loading" class="loading-container loading-center-lg">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <el-empty v-else-if="!teams.length" description="暂无团队" />

    <div v-else class="team-grid">
      <el-card v-for="team in teams" :key="team.id" shadow="hover" class="team-card" @click="openTeamDetail(team.id)">
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
          <el-button v-if="team.joinRule === 1" type="primary" size="small" :disabled="applySubmitting" @click="handleApplyDialog(team)">
            <el-icon><UserFilled /></el-icon>
            申请加入
          </el-button>
          <el-button v-else-if="team.joinRule === 3" type="success" size="small" :disabled="joinSubmitting" @click="handleJoinPasswordDialog(team)">
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

    <el-dialog v-model="showCreate" title="创建团队" width="500px">
      <el-form :model="createForm" class="create-form">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.teamName" maxlength="64" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.teamIntro" type="textarea" rows="3" maxlength="512" placeholder="请输入团队简介" />
        </el-form-item>
        <el-form-item label="标签（逗号分隔）">
          <TagInput v-model="createForm.teamTags" placeholder="输入标签后回车，也可以用逗号一次输入多个标签" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.teamType" class="w-full">
            <el-option :value="1" label="公开" />
            <el-option :value="2" label="私有" />
          </el-select>
        </el-form-item>
        <el-form-item label="加入方式">
          <el-select v-model="createForm.joinRule" class="w-full">
            <el-option :value="1" label="申请审批" />
            <el-option :value="2" label="仅邀请" />
            <el-option :value="3" label="密码加入" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.joinRule === 3" label="入团密码">
          <el-input v-model="createForm.joinPassword" placeholder="请设置入团密码" />
        </el-form-item>
        <el-form-item label="最大人数">
          <el-input-number v-model="createForm.maxMember" :min="1" :max="1000" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer dialog-footer-row">
          <el-button @click="showCreate = false">取消</el-button>
          <el-button type="primary" :loading="createSubmitting" :disabled="createSubmitting" @click="handleCreate">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <TeamJoinDialogs
      v-model:apply-visible="showApply.show"
      v-model:apply-msg="applyMsg"
      v-model:join-visible="showJoinPwd.show"
      v-model:join-pwd="joinPwd"
      :apply-team="showApply.team"
      :apply-submitting="applySubmitting"
      :join-team="showJoinPwd.team"
      :join-submitting="joinSubmitting"
      @submit-apply="handleApply"
      @submit-join="handleJoinPwd"
    />
  </div>
</template>

<style scoped>
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
  gap: 16px;
  align-items: center;
}

.team-avatar {
  border: 3px solid #f0f0f0;
}

.team-info {
  flex: 1;
}

.team-name {
  font-size: 18px;
  font-weight: bold;
  color: #111;
  margin: 0 0 4px 0;
}

.team-meta {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #888;
}

.team-intro {
  font-size: 14px;
  color: #555;
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
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.create-form {
  margin-top: 20px;
}
</style>
