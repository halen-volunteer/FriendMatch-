<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import { goOffline } from '@/api/online'
import { useAuthStore } from '@/stores/auth'
import AdminSideNav from './AdminSideNav.vue'

const router = useRouter()
const authStore = useAuthStore()

const adminDisplayName = computed(() => authStore.adminName || authStore.userNickname || '管理员')

function openProfile() {
  router.push('/profile')
}

function logout() {
  goOffline().catch(() => {})
  authStore.clearAuth()
  router.push({ name: 'Login' })
}
</script>

<template>
  <div class="admin-layout">
    <AdminSideNav @logout="logout" />
    <div class="admin-layout__content">
      <header class="admin-layout__header surface-card">
        <div>
          <h1 class="admin-layout__title">管理中心</h1>
          <p class="admin-layout__subtitle">处理举报、申诉、反馈和处罚记录</p>
        </div>
        <el-dropdown>
          <span class="admin-layout__user">
            <el-avatar :size="36" :src="authStore.userAvatar || ''">
              {{ adminDisplayName.charAt(0) }}
            </el-avatar>
            <span class="admin-layout__name">{{ adminDisplayName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openProfile">个人资料</el-dropdown-item>
              <el-dropdown-item divided @click="logout">
                <el-icon><SwitchButton /></el-icon>
                <span>退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <main class="admin-layout__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);
}

.admin-layout__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 20px 20px 0;
  padding: 18px 20px;
}

.admin-layout__title {
  margin: 0;
  color: #303133;
  font-size: 22px;
  font-weight: 700;
}

.admin-layout__subtitle {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.admin-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.admin-layout__name {
  color: #606266;
  font-size: 14px;
}

.admin-layout__main {
  flex: 1;
  overflow: auto;
  padding: 20px;
}
</style>
