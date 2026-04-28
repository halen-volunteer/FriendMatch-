<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getTeamMembers, getTeamMembersByRole, getTeamDetail, removeMember, updateMemberRole, updateMemberRoleCompat, transferLeader, transferCaptainCompat, muteMember, unmuteMember, muteAll } from '@/api/team'
import { groupMuteAllCompat, groupMuteMemberCompat, groupUnmuteMemberCompat } from '@/api/chat'
import { UserFilled, Bell, Delete, User, RefreshLeft, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const teamId = route.params.teamId ? Number(route.params.teamId) : undefined
const members = ref([])
const myRole = ref(0)
const isMuteAll = ref(false)
const roleFilter = ref(0)
const loading = ref(true)
const toast = ref({ msg: '', type: 'success' })
const canManageMuteAll = computed(() => myRole.value <= 2 && myRole.value > 0)

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

function canOperate(targetRole) {
  if (myRole.value === 1) return targetRole !== 1
  if (myRole.value === 2) return targetRole >= 3
  return false
}

async function load() {
  loading.value = true
  try {
    const memberPromise = roleFilter.value
      ? getTeamMembersByRole({ teamId, roleType: roleFilter.value, page: 1, pageSize: 100 })
      : getTeamMembers(teamId, { page: 1, pageSize: 100 })
    const [mRes, tRes] = await Promise.all([
      memberPromise,
      getTeamDetail(teamId)
    ])
    if (mRes.code === 200) {
      members.value = (mRes.data?.records || mRes.data || []).map((item) => ({
        ...item,
        roleType: item.roleType ?? item.teamRoleType,
        isMuted: item.isMuted ?? item.teamMuteType === 1
      }))
    }
    if (tRes.code === 200) {
      const detail = tRes.data || {}
      myRole.value = detail.myRoleType || detail.currentUserRole || detail.roleType || 0
      isMuteAll.value = (detail.teamAllMute ?? detail.muteAll) === 1
    }
  } finally {
    loading.value = false
  }
}

async function handleRemove(userId) {
  if (!confirm('确定移除？')) return
  const res = await removeMember({ teamId, userId })
  if (res.code === 200) {
    showToast('已移除')
    load()
  } else {
    showToast(res.message || '失败', 'error')
  }
}

async function handleMute(userId) {
  const [res] = await Promise.all([
    muteMember({ teamId, userId, muteDuration: 60 }),
    groupMuteMemberCompat({ teamId, userId, muteDuration: 60 }).catch(() => {})
  ])
  if (res.code === 200) {
    showToast('已禁言60分钟')
    load()
  } else {
    showToast(res.message || '失败', 'error')
  }
}

async function handleUnmute(userId) {
  const [res] = await Promise.all([
    unmuteMember({ teamId, userId }),
    groupUnmuteMemberCompat({ teamId, userId }).catch(() => {})
  ])
  if (res.code === 200) {
    showToast('已解除禁言')
    load()
  } else {
    showToast(res.message || '失败', 'error')
  }
}

async function handleRoleChange(userId, roleType) {
  const [res] = await Promise.all([
    updateMemberRole({ teamId, userId, roleType }),
    updateMemberRoleCompat({ teamId, userId, roleType }).catch(() => {})
  ])
  if (res.code === 200) {
    showToast('已更新')
    load()
  } else {
    showToast(res.message || '失败', 'error')
  }
}

async function handleTransfer(userId) {
  if (!confirm('确定转让队长？')) return
  const [res] = await Promise.all([
    transferLeader({ teamId, userId }),
    transferCaptainCompat({ teamId, userId }).catch(() => {})
  ])
  if (res.code === 200) {
    showToast('已转让')
    load()
  } else {
    showToast(res.message || '失败', 'error')
  }
}

async function handleMuteAll() {
  const next = !isMuteAll.value
  const [res] = await Promise.all([
    muteAll({ teamId, isMute: next }),
    groupMuteAllCompat({ teamId, isMute: next }).catch(() => {})
  ])
  if (res.code === 200) {
    isMuteAll.value = next
    showToast(next ? '已开启全员禁言' : '已关闭全员禁言')
  } else {
    showToast(res.message || '失败', 'error')
  }
}

onMounted(load)
</script>

<template>
  <div class="page team-members-page">
    <div class="page-header">
      <h2>成员管理</h2>
      <div class="toolbar-right">
        <el-select v-model="roleFilter" placeholder="按角色筛选" clearable class="role-filter" @change="load">
          <el-option :value="1" label="队长" />
          <el-option :value="2" label="管理员" />
          <el-option :value="3" label="成员" />
        </el-select>
        <div v-if="canManageMuteAll" class="mute-all-bar">
          <span>全员禁言</span>
          <el-switch
            v-model="isMuteAll"
            active-text="已开启"
            inactive-text="关闭"
            @change="handleMuteAll"
            class="mute-switch"
          />
        </div>
      </div>
    </div>
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
    <el-list v-else class="members-list">
      <el-list-item v-for="m in members" :key="m.userId" class="member-item">
        <template #prefix>
          <el-avatar :size="48" :src="m.userAvatar" class="member-avatar">
            {{ m.userNickname?.charAt(0) || '用' }}
          </el-avatar>
        </template>
        <div class="member-info">
          <div class="member-main">
            <span class="member-name">{{ m.userNickname }}</span>
            <el-tag v-if="m.roleType === 1" type="danger" effect="dark" class="role-tag">
              <el-icon><UserFilled /></el-icon>
              队长
            </el-tag>
            <el-tag v-else-if="m.roleType === 2" type="warning" effect="dark" class="role-tag">
              <el-icon><UserFilled /></el-icon>
              管理员
            </el-tag>
            <el-tag v-else type="success" effect="dark" class="role-tag">
              <el-icon><User /></el-icon>
              成员
            </el-tag>
            <el-tag v-if="m.isMuted" type="info" effect="light" class="muted-tag">
              <el-icon><Bell /></el-icon>
              禁言中
            </el-tag>
          </div>
          <div class="member-meta">
            <span class="member-account">账号：{{ m.userAccount || '未知' }}</span>
            <span v-if="m.joinTime" class="member-join-time">
              加入时间：{{ new Date(m.joinTime).toLocaleString('zh-CN') }}
            </span>
          </div>
        </div>
        <template #suffix>
          <div v-if="canOperate(m.roleType)" class="member-ops">
            <el-button
              v-if="!m.isMuted"
              type="warning"
              size="small"
              @click="handleMute(m.userId)"
            >
              <el-icon><Bell /></el-icon>
              禁言
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              @click="handleUnmute(m.userId)"
            >
              <el-icon><Bell /></el-icon>
              解禁
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleRemove(m.userId)"
            >
              <el-icon><Delete /></el-icon>
              移除
            </el-button>
            <template v-if="myRole === 1">
              <el-button
                v-if="m.roleType !== 2"
                type="primary"
                size="small"
                @click="handleRoleChange(m.userId, 2)"
              >
                <el-icon><UserFilled /></el-icon>
                设管理
              </el-button>
              <el-button
                v-if="m.roleType === 2"
                type="info"
                size="small"
                @click="handleRoleChange(m.userId, 3)"
              >
                <el-icon><User /></el-icon>
                降为成员
              </el-button>
              <el-button
                type="info"
                size="small"
                @click="handleTransfer(m.userId)"
              >
                <el-icon><RefreshLeft /></el-icon>
                转让队长
              </el-button>
            </template>
          </div>
        </template>
      </el-list-item>
    </el-list>
  </div>
</template>

<style scoped>
.team-members-page { max-width: 860px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role-filter {
  width: 150px;
}
.page-header h2 {
  font-size: 20px;
  font-weight: bold;
  color: #111;
  margin: 0;
}
.mute-all-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #666;
}
.mute-switch {
  --el-switch-on-color: #67c23a;
  --el-switch-off-color: #dcdfe6;
}
.toast { margin-bottom: 20px; }
.loading-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.members-list {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}
.member-item {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
}
.member-item:last-child {
  border-bottom: none;
}
.member-avatar {
  border: 2px solid #f0f0f0;
}
.member-info {
  flex: 1;
  margin-left: 20px;
}
.member-main {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.member-name {
  font-size: 16px;
  font-weight: bold;
  color: #111;
}
.role-tag {
  font-size: 12px;
  padding: 4px 8px;
}
.muted-tag {
  font-size: 12px;
  padding: 4px 8px;
}
.member-meta {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #888;
}
.member-ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>