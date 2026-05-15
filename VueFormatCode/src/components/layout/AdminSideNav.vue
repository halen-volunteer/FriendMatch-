<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Grid, User, CollectionTag, HelpFilled, Warning, Message } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

defineEmits(['logout'])

const route = useRoute()
useAuthStore()

const navItems = computed(() => {
  return [
    { path: '/admin/dashboard', icon: Grid, label: '总览', visible: true },
    { path: '/admin/reports', icon: User, label: '用户举报', visible: true },
    { path: '/admin/message-reports', icon: Message, label: '消息举报', visible: true },
    { path: '/admin/team-reports', icon: CollectionTag, label: '队伍举报', visible: true },
    { path: '/admin/appeals', icon: HelpFilled, label: '申诉处理', visible: true },
    { path: '/admin/punish', icon: Warning, label: '处罚中心', visible: true },
    { path: '/admin/feedback', icon: Message, label: '反馈管理', visible: true },
  ].filter((item) => item.visible)
})

function isActive(path) {
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <nav class="admin-nav surface-card">
    <div class="admin-nav__logo">
      <h2>管理中心</h2>
      <p>后台工作台</p>
    </div>
    <ul class="admin-nav__list">
      <li v-for="item in navItems" :key="item.path" :class="['admin-nav__item', { 'is-active': isActive(item.path) }]">
        <RouterLink :to="item.path" class="admin-nav__link">
          <el-icon class="admin-nav__icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.admin-nav {
  display: flex;
  flex-direction: column;
  width: 220px;
  min-width: 220px;
  margin: 20px 0 20px 20px;
  padding: 20px 0;
}

.admin-nav__logo {
  padding: 0 20px 18px;
  border-bottom: 1px solid var(--border-color);
}

.admin-nav__logo h2 {
  margin: 0;
  color: var(--brand-primary);
  font-size: 20px;
  font-weight: 700;
}

.admin-nav__logo p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.admin-nav__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 16px 10px 0;
  list-style: none;
}

.admin-nav__link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  color: #606266;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.admin-nav__item.is-active .admin-nav__link,
.admin-nav__link:hover {
  background: #ecf5ff;
  color: var(--brand-primary);
}

.admin-nav__icon {
  font-size: 18px;
}
</style>
