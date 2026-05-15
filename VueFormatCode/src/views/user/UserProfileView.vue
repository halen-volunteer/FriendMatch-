<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getUserProfile, addFriend, addBlacklist } from '@/api/user'
import { getUserOnlineStatus } from '@/api/online'
import TagList from '@/components/common/TagList.vue'
import { DocumentCopy, UserFilled, ChatDotRound, Warning, Edit, Loading } from '@element-plus/icons-vue'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const profile = ref(null)
const onlineStatus = ref(0)
const loading = ref(true)
const applyMsg = ref('')
const showApplyDialog = ref(false)
const { toast, showToast } = useToast()
const friendSubmitting = ref(false)
const blacklistSubmitting = ref(false)
const localBlacklisted = ref(false)

const isSelf = computed(() => String(authStore.userId) === String(route.params.userId))
const isFriend = computed(() => Boolean(profile.value?.isFriend))
const hasPendingFriendRequest = computed(() => Number(profile.value?.friendStatus) === 0)
const isBlacklisted = computed(() => localBlacklisted.value)
const hasProfileDetails = computed(() => Boolean(profile.value?.userEmail || profile.value?.userPhone))

async function copyAccount() {
  try {
    await navigator.clipboard.writeText(profile.value?.userAccount || '')
    showToast('账号已复制到剪贴板')
  } catch {
    showToast('复制失败，请手动复制', 'error')
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getUserProfile(route.params.userId)
    profile.value = res.code === 200 ? res.data : null

    const statusRes = await getUserOnlineStatus(route.params.userId)
    if (statusRes.code === 200) {
      onlineStatus.value = Number(statusRes.data?.status || 0)
    }
  } finally {
    loading.value = false
  }
}

async function handleAddFriend() {
  if (friendSubmitting.value || isFriend.value || hasPendingFriendRequest.value) return
  friendSubmitting.value = true
  try {
    const res = await addFriend({
      friendId: route.params.userId,
      applyMsg: applyMsg.value,
    })
    if (res.code === 200) {
      showApplyDialog.value = false
      showToast('申请已发送')
      if (profile.value) {
        profile.value.isFriend = false
        profile.value.friendStatus = 0
      }
    } else {
      showToast(res.message || '操作失败', 'error')
    }
  } finally {
    friendSubmitting.value = false
  }
}

async function handleBlacklist() {
  if (blacklistSubmitting.value || isBlacklisted.value) return
  blacklistSubmitting.value = true
  try {
    const res = await addBlacklist(route.params.userId)
    if (res.code === 200) {
      localBlacklisted.value = true
      showToast('已拉黑该用户')
    } else {
      showToast(res.message || '操作失败', 'error')
    }
  } finally {
    blacklistSubmitting.value = false
  }
}

function getProfileInitial() {
  return profile.value?.userNickname?.charAt(0) || '用'
}

function getUserIntro() {
  return profile.value?.userIntro || '这个人很安静，什么都没有留下。'
}

function openPrivateChat() {
  router.push(`/chat/private/${route.params.userId}`)
}

function openProfileEdit() {
  router.push('/profile')
}

onMounted(load)
</script>

<template>
  <div class="page user-profile-page">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <el-empty v-else-if="!profile" description="该用户已设置隐私或不存在" />
    <div v-else>
      <el-alert
        v-if="toast.msg"
        :title="toast.msg"
        :type="toast.type"
        show-icon
        class="toast"
        @close="toast.msg = ''"
      />

      <el-card class="profile-card" shadow="hover">
        <div class="profile-top">
          <div class="avatar-container">
            <el-avatar :size="90" :src="profile.userAvatar" class="avatar">
              {{ getProfileInitial() }}
            </el-avatar>
            <div class="status-indicator" :class="{ online: onlineStatus === 1 }"></div>
          </div>

          <div class="profile-info">
            <div class="user-header">
              <h3>{{ profile.userNickname }}</h3>
              <el-button type="text" size="small" @click="copyAccount">
                <el-icon><DocumentCopy /></el-icon>
                复制账号
              </el-button>
            </div>
            <div class="user-account">@{{ profile.userAccount }}</div>
            <p class="user-intro">{{ getUserIntro() }}</p>
            <TagList v-if="profile.userTags" :tags="profile.userTags.split(',').filter(Boolean)" />
          </div>
        </div>

        <div v-if="!isSelf" class="actions">
          <el-button
            type="primary"
            :disabled="isFriend || hasPendingFriendRequest || friendSubmitting"
            @click="showApplyDialog = true"
          >
            <el-icon><UserFilled /></el-icon>
            <span>{{ isFriend ? '已是好友' : hasPendingFriendRequest ? '已发送申请' : '添加好友' }}</span>
          </el-button>
          <el-button type="success" @click="openPrivateChat">
            <el-icon><ChatDotRound /></el-icon>
            <span>发消息</span>
          </el-button>
          <el-button
            type="danger"
            :disabled="isBlacklisted || blacklistSubmitting"
            @click="handleBlacklist"
          >
            <el-icon><Warning /></el-icon>
            <span>{{ isBlacklisted ? '已拉黑' : '拉黑' }}</span>
          </el-button>
        </div>

        <div v-else class="actions">
          <el-button type="primary" @click="openProfileEdit">
            <el-icon><Edit /></el-icon>
            <span>编辑资料</span>
          </el-button>
        </div>

        <div v-if="hasProfileDetails" class="profile-details">
          <el-divider content-position="left">资料详情</el-divider>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-if="profile.userEmail" label="邮箱">
              {{ profile.userEmail }}
            </el-descriptions-item>
            <el-descriptions-item v-if="profile.userPhone" label="手机号">
              {{ profile.userPhone }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="showApplyDialog" title="发送好友申请" width="400px">
      <el-form :model="{ applyMsg }">
        <el-form-item label="留言（选填）">
          <el-input v-model="applyMsg" type="textarea" rows="3" placeholder="请输入留言内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer dialog-footer-row">
          <el-button @click="showApplyDialog = false">取消</el-button>
          <el-button type="primary" :loading="friendSubmitting" :disabled="friendSubmitting" @click="handleAddFriend">
            发送申请
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-profile-page {
  max-width: 760px;
}

.loading-container {
  min-height: 500px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast {
  margin-bottom: 20px;
}

.profile-card {
  margin-bottom: 20px;
}

.profile-top {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
  align-items: flex-start;
}

.avatar-container {
  position: relative;
}

.avatar {
  border: 3px solid #f0f0f0;
}

.status-indicator {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #ccc;
  border: 3px solid #fff;
}

.status-indicator.online {
  background: #07c160;
}

.profile-info {
  flex: 1;
}

.user-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.user-header h3 {
  font-size: 24px;
  font-weight: bold;
  color: #111;
  margin: 0;
}

.user-account {
  font-size: 14px;
  color: #888;
  margin-bottom: 12px;
}

.user-intro {
  font-size: 14px;
  color: #555;
  line-height: 1.7;
  margin: 0 0 16px 0;
}

.actions {
  display: flex;
  gap: 12px;
  margin: 20px 0;
  flex-wrap: wrap;
}

.profile-details {
  margin-top: 30px;
}
</style>
