<script setup>
import { useSideNav } from '@/composables/useSideNav'

const emit = defineEmits(['logout'])

const {
  authStore,
  currentStatusLabel,
  getNavBadgeValue,
  handleLogout,
  handleNavClick,
  icons,
  isActive,
  isStatusActive,
  menuWrapRef,
  navItems,
  openStatusPanel,
  settingsMenuVisible,
  shouldShowNavBadge,
  statusOptions,
  statusPanelVisible,
  toggleSettingsMenu,
  selectOnlineStatus,
} = useSideNav(emit)
</script>

<template>
  <aside class="side-bar">
    <div class="side-bar-top">
      <button type="button" class="avatar-link" :class="{ active: isActive('/profile') }" @click="handleNavClick('/profile')">
        <el-avatar :size="38" :src="authStore.userAvatar || ''" class="avatar-img">
          {{ authStore.userNickname?.charAt(0) || '我' }}
        </el-avatar>
      </button>

      <div class="page">
        <button
          v-for="item in navItems"
          :key="item.name"
          type="button"
          :title="item.label"
          :class="['page-item', { active: isActive(item.path) }]"
          @click="handleNavClick(item.path)"
        >
          <div class="icon-wrap">
            <el-icon class="page-icon">
              <component :is="item.icon" />
            </el-icon>
            <el-badge
              v-if="shouldShowNavBadge(item.name)"
              :value="getNavBadgeValue(item.name)"
              class="unread-badge"
            />
          </div>
          <span class="item-label">{{ item.label }}</span>
        </button>
      </div>
    </div>

    <div ref="menuWrapRef" class="side-bar-bottom settings-wrap">
      <button class="bottom-item settings-trigger" title="设置" type="button" @click.stop="toggleSettingsMenu">
        <el-icon class="page-icon">
          <component :is="icons.Setting" />
        </el-icon>
      </button>

      <transition name="menu-fade">
        <div v-if="settingsMenuVisible" class="settings-menu-card" @click.stop>
          <button class="settings-menu-item primary" type="button" @click="openStatusPanel">
            <div class="menu-item-main">
              <span>在线状态</span>
              <small>{{ currentStatusLabel }}</small>
            </div>
            <el-icon>
              <component :is="icons.ArrowRight" />
            </el-icon>
          </button>
          <button class="settings-menu-item" type="button" @click="handleLogout">
            <span>退出登录</span>
            <el-icon>
              <component :is="icons.SwitchButton" />
            </el-icon>
          </button>
        </div>
      </transition>

      <transition name="menu-fade">
        <div v-if="settingsMenuVisible && statusPanelVisible" class="status-submenu-card" @click.stop>
          <div class="status-submenu-title">切换在线状态</div>
          <button
            v-for="item in statusOptions"
            :key="item.value"
            type="button"
            class="status-option-item"
            :class="{ active: isStatusActive(item.value) }"
            @click="selectOnlineStatus(item.value)"
          >
            <span>{{ item.label }}</span>
            <el-icon v-if="isStatusActive(item.value)">
              <component :is="icons.Check" />
            </el-icon>
          </button>
        </div>
      </transition>
    </div>
  </aside>
</template>

<style scoped>
.side-bar {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  width: var(--layout-side-nav-width);
  min-width: var(--layout-side-nav-width);
  padding: var(--layout-side-nav-padding-top) 0 var(--layout-side-nav-padding-bottom);
  border-right: 1px solid var(--layout-side-nav-border);
  background: var(--layout-side-nav-bg);
}

.side-bar-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.avatar-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--layout-side-nav-avatar-size);
  height: var(--layout-side-nav-avatar-size);
  margin-bottom: var(--layout-side-nav-gap-lg);
  border: none;
  border-radius: var(--layout-side-nav-radius-sm);
  background: transparent;
}

.avatar-link.active,
.avatar-link:hover {
  background: var(--layout-side-nav-active-bg);
}

.avatar-img {
  background: linear-gradient(135deg, #5cba47, #2e9f3f);
  color: #ffffff;
  font-weight: 700;
}

.page,
.side-bar-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--layout-side-nav-gap-sm);
  width: 100%;
}

.page {
  flex: 1;
}

.page-item,
.bottom-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--layout-side-nav-gap-xs);
  width: var(--layout-side-nav-item-size);
  min-height: var(--layout-side-nav-item-size);
  border: none;
  border-radius: var(--layout-side-nav-radius-sm);
  background: transparent;
  color: var(--layout-side-nav-text-muted);
}

.page-item:hover,
.bottom-item:hover,
.page-item.active,
.settings-trigger:hover {
  background: var(--layout-side-nav-hover-bg);
  color: var(--layout-side-nav-text-main);
}

.icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-icon {
  font-size: var(--layout-side-nav-icon-size);
}

.item-label {
  font-size: var(--layout-side-nav-font-xs);
  line-height: 1;
}

.unread-badge {
  position: absolute;
  top: -7px;
  right: -11px;
}

.settings-wrap {
  position: relative;
}

.settings-menu-card,
.status-submenu-card {
  position: absolute;
  bottom: 0;
  left: var(--layout-side-nav-menu-left);
  width: var(--layout-side-nav-menu-width);
  padding: 10px;
  border: 1px solid var(--layout-side-nav-panel-border);
  border-radius: var(--layout-side-nav-radius-xl);
  background: var(--layout-side-nav-panel-bg);
  box-shadow: var(--layout-side-nav-panel-shadow);
  backdrop-filter: blur(8px);
}

.settings-menu-card {
  display: flex;
  flex-direction: column;
  gap: var(--layout-side-nav-gap-sm2);
}

.status-submenu-card {
  left: var(--layout-side-nav-submenu-left);
  width: var(--layout-side-nav-submenu-width);
  padding: 8px;
}

.settings-menu-item,
.status-option-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--layout-side-nav-gap-sm2);
  width: 100%;
  padding: 10px 11px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--layout-side-nav-item-bg);
  color: var(--layout-side-nav-text-main);
  font-size: var(--layout-side-nav-font-md);
  font-weight: 500;
  text-align: left;
  line-height: 1.3;
}

.settings-menu-item.primary {
  background: var(--layout-side-nav-item-primary-bg);
}

.settings-menu-item:hover,
.status-option-item:hover,
.status-option-item.active {
  background: var(--layout-side-nav-item-hover-bg);
}

.status-option-item.active {
  font-weight: 600;
}

.menu-item-main {
  display: flex;
  flex-direction: column;
  gap: var(--layout-side-nav-gap-xs);
}

.menu-item-main small {
  color: var(--layout-side-nav-text-light);
  font-size: 10px;
  font-weight: 500;
}

.status-submenu-title {
  padding: 2px 6px 6px;
  color: var(--layout-side-nav-text-subtle);
  font-size: 11px;
}
</style>
