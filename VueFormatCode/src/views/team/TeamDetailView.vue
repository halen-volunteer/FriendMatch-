<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTeamDetail, updateTeam, dissolveTeam, quitTeam, getPendingApplyList, auditApply } from '@/api/team'
import { ChatDotRound, UserFilled, Edit, SwitchButton, Delete, Check, Close, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const team = ref(null)
const pendingApplies = ref([])
const activeTab = ref('info')
const loading = ref(true)
const toast = ref({ msg: '', type: 'success' })
const editMode = ref(false)
const editForm = ref({})
const myRole = ref(0)
const teamId = route.params.teamId ? Number(route.params.teamId) : undefined
const canManageApplies = computed(() => myRole.value <= 2 && myRole.value > 0)

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

async function load() {
  loading.value = true
  try {
    const res = await getTeamDetail(teamId)
    if (res.code === 200) {
      const detail = res.data || {}
      team.value = {
        ...detail,
        teamIntro: detail.teamIntro ?? detail.teamDesc ?? '',
        maxMember: detail.maxMember ?? detail.maxMemberNum ?? 0
      }
      myRole.value = detail.myRoleType || detail.roleType || 0
      editForm.value = {
        teamId,
        teamName: detail.teamName,
        teamIntro: detail.teamIntro ?? detail.teamDesc ?? '',
        teamTags: detail.teamTags,
        teamType: detail.teamType,
        joinRule: detail.joinRule,
        maxMember: detail.maxMember ?? detail.maxMemberNum
      }
    } else {
      showToast(res.message || '加载失败', 'error')
    }
  } catch {
    showToast('网络错误', 'error')
  } finally {
    loading.value = false
  }
}

async function loadApplies() {
  if (myRole.value > 2) return
  const res = await getPendingApplyList({ teamId, page: 1, pageSize: 50 })
  if (res.code === 200) {
    pendingApplies.value = res.data?.records || res.data || []
  }
}

async function handleDissolve() {
  if (!confirm('确定解散团队？此操作不可撤销！')) return
  const res = await dissolveTeam(teamId)
  if (res.code === 200) {
    router.push('/teams')
  } else {
    showToast(res.message || '操作失败', 'error')
  }
}

async function handleQuit() {
  if (!confirm('确定退出团队？')) return
  const res = await quitTeam(teamId)
  if (res.code === 200) {
    router.push('/teams')
  } else {
    showToast(res.message || '操作失败', 'error')
  }
}

async function handleSaveEdit() {
  const res = await updateTeam(editForm.value)
  if (res.code === 200) {
    showToast('保存成功')
    editMode.value = false
    load()
  } else {
    showToast(res.message || '保存失败', 'error')
  }
}

async function handleAudit(applyId, status) {
  const res = await auditApply({ applyId, auditStatus: status })
  if (res.code === 200) {
    showToast(status === 1 ? '已通过' : '已拒绝')
    loadApplies()
  } else {
    showToast(res.message || '操作失败', 'error')
  }
}

function openTeamChat() {
  router.push(`/chat/team/${teamId}`)
}

function openTeamMembers() {
  router.push(`/teams/${teamId}/members`)
}

function getTeamInitial(teamName) {
  return teamName?.charAt(0) || '团'
}

function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

function getJoinRuleText(joinRule) {
  if (joinRule === 1) return '申请审批'
  if (joinRule === 2) return '仅邀请'
  return '密码加入'
}

function formatCreateTime(createTime) {
  return createTime ? new Date(createTime).toLocaleString('zh-CN') : '未知'
}

function getApplyInitial(userNickname) {
  return userNickname?.charAt(0) || '用'
}

onMounted(() => {
  load().then(loadApplies)
})
</script>

<template>
  <div class="page team-detail-page">
    <div v-if="loading" class="loading-container loading-center-xl">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <el-empty v-else-if="!team" description="团队不存在" />
    <div v-else-if="team">
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
          <el-avatar :size="80" :src="team.teamAvatar" class="team-avatar">
            {{ getTeamInitial(team.teamName) }}
          </el-avatar>
          <div class="team-basic">
            <h2>{{ team.teamName }}</h2>
            <div class="meta-row">
              <el-tag v-if="myRole === 1" type="danger" effect="dark">队长</el-tag>
              <el-tag v-else-if="myRole === 2" type="warning" effect="dark">管理员</el-tag>
              <el-tag v-else-if="myRole === 3" type="success" effect="dark">成员</el-tag>
              <span class="member-count">{{ team.memberCount }}/{{ team.maxMember }} 人</span>
            </div>
          </div>
        </div>
      </el-card>
      <div class="action-bar">
        <el-button
          v-if="myRole > 0"
          type="primary"
          @click="openTeamChat"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>进入群聊</span>
        </el-button>
        <el-button
          v-if="myRole > 0"
          type="success"
          @click="openTeamMembers"
        >
          <el-icon><UserFilled /></el-icon>
          <span>成员列表</span>
        </el-button>
        <el-button
          v-if="myRole === 1"
          type="info"
          @click="editMode = !editMode"
        >
          <el-icon><Edit /></el-icon>
          <span>编辑团队</span>
        </el-button>
        <el-button
          v-if="myRole >= 2"
          type="warning"
          @click="handleQuit"
        >
          <el-icon><SwitchButton /></el-icon>
          <span>退出团队</span>
        </el-button>
        <el-button
          v-if="myRole === 1"
          type="danger"
          @click="handleDissolve"
        >
          <el-icon><Delete /></el-icon>
          <span>解散团队</span>
        </el-button>
      </div>
      <el-tabs v-model="activeTab" class="team-tabs">
        <el-tab-pane label="详情" name="info">
          <el-card shadow="hover">
            <div v-if="!editMode">
              <p class="team-intro">{{ team.teamIntro || '暂无简介' }}</p>
              <div v-if="team.teamTags" class="team-tags">
                <el-tag v-for="tag in team.teamTags.split(',').filter(Boolean)" :key="tag" size="small">
                  {{ tag }}
                </el-tag>
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
                  <el-descriptions-item label="团队ID">{{ team.id || team.teamId || '未知' }}</el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
            <el-form v-else :model="editForm" class="edit-form">
              <el-form-item label="团队名称" required>
                <el-input v-model="editForm.teamName" placeholder="请输入团队名称" />
              </el-form-item>
              <el-form-item label="简介">
                <el-input
                  v-model="editForm.teamIntro"
                  type="textarea"
                  rows="3"
                  placeholder="请输入团队简介"
                />
              </el-form-item>
              <el-form-item label="标签">
                <el-input v-model="editForm.teamTags" placeholder="请输入团队标签，逗号分隔" />
              </el-form-item>
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
                  <el-option :value="1" label="公开" />
                  <el-option :value="2" label="私有" />
                </el-select>
              </el-form-item>
              <el-form-item label="加入方式">
                <el-select v-model="editForm.joinRule" class="w-full">
                  <el-option :value="1" label="申请审批" />
                  <el-option :value="2" label="仅邀请" />
                  <el-option :value="3" label="密码加入" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <div class="row-actions">
                  <el-button type="primary" @click="handleSaveEdit">保存</el-button>
                  <el-button @click="editMode = false">取消</el-button>
                </div>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>
        <el-tab-pane v-if="canManageApplies" :label="`待审核 (${pendingApplies.length})`" name="apply">
          <el-card shadow="hover">
            <el-empty v-if="!pendingApplies.length" description="暂无待审核申请" />
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
    </div>
  </div>
</template>

<style scoped>
.team-detail-page { max-width: 900px; }
.loading-container {
  min-height: 500px;
}
.team-hero {
  margin-bottom: 20px;
}
.team-header {
  display: flex;
  gap: 24px;
  align-items: center;
}
.team-avatar {
  border: 3px solid #f0f0f0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 28px;
  font-weight: bold;
}
.team-basic h2 {
  font-size: 24px;
  font-weight: bold;
  color: #111;
  margin: 0 0 8px 0;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.member-count {
  font-size: 14px;
  color: #888;
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
  flex-wrap: wrap;
  gap: 8px;
}
.team-details {
  margin-top: 20px;
}
.edit-form {
  margin-top: 20px;
}
.row-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
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
  font-style: italic;
}
.apply-time {
  font-size: 12px;
  color: #888;
}
.apply-actions {
  display: flex;
  gap: 8px;
}
</style>