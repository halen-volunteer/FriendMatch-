<script setup>
import { computed } from 'vue'
import SideNav from './SideNav.vue'
import ChatSidebar from './ChatSidebar.vue'
import { useMainLayout } from '@/composables/useMainLayout'

const {
  contactLoading,
  contactSection,
  contactSections,
  isChatRoute,
  isContactItemActive,
  isFriendsRoute,
  isListContextRoute,
  isNoticesRoute,
  isOtherRoute,
  isRecommendRoute,
  logout,
  middleTitle,
  noticeSection,
  openContactSection,
  otherSection,
  recommendSection,
  router,
  selectContactItem,
} = useMainLayout()

const recommendTabs = [
  { key: 'users', label: '发现用户', path: '/recommend', query: { section: 'users' } },
  { key: 'teams', label: '发现团队', path: '/recommend', query: { section: 'teams' } },
]

const noticeTabs = [
  { key: 'all', label: '全部通知', path: '/notices', query: { section: 'all' } },
  { key: 'friend', label: '好友通知', path: '/notices', query: { section: 'friend' } },
  { key: 'team', label: '团队通知', path: '/notices', query: { section: 'team' } },
  { key: 'system', label: '系统通知', path: '/notices', query: { section: 'system' } },
]

const otherTabs = [
  { key: 'devices', label: '设备管理', path: '/devices' },
  { key: 'account-status', label: '账号状态', path: '/account-status' },
  { key: 'reports', label: '举报中心', path: '/reports' },
  { key: 'feedback', label: '意见反馈', path: '/feedback' },
  { key: 'appeals', label: '申诉记录', path: '/appeals' },
]

const activeContactSection = computed(() => contactSections.value.find((section) => section.key === contactSection.value) || null)
const activeContactItems = computed(() => activeContactSection.value?.items || [])

function navigateToTab(tab) {
  router.replace({
    path: tab.path,
    ...(tab.query ? { query: tab.query } : {}),
  })
}
</script>

<template>
  <div class="desktop-shell">
    <SideNav @logout="logout" />

    <aside v-if="isListContextRoute" class="desktop-middle">
      <ChatSidebar v-if="isChatRoute" />

      <div v-else class="middle-panel">
        <header class="middle-panel__header">
          <h3>{{ middleTitle }}</h3>
          <p v-if="isFriendsRoute">左侧选择联系人、申请或团队，右侧展示对应详情。</p>
          <p v-else-if="isRecommendRoute">根据当前入口切换用户推荐和团队推荐。</p>
          <p v-else-if="isNoticesRoute">按通知类型筛选消息，提升处理效率。</p>
          <p v-else-if="isOtherRoute">设备、账号、举报、反馈和申诉记录都统一收口在这里。</p>
        </header>

        <div v-if="isFriendsRoute" class="middle-panel__body middle-panel__body--spaced">
          <button
            v-for="section in contactSections"
            :key="section.key"
            type="button"
            class="shortcut-item"
            :class="{ active: contactSection === section.key }"
            @click="openContactSection(section.key)"
          >
            {{ section.label }}
          </button>

          <div class="sub-list-wrap">
            <div v-if="contactLoading" class="sub-empty">正在加载联系人数据...</div>
            <div v-else-if="!activeContactItems.length" class="sub-empty">
              {{ activeContactSection?.emptyText }}
            </div>
            <button
              v-for="item in activeContactItems"
              :key="item.id"
              type="button"
              class="sub-item"
              :class="{ active: isContactItemActive(contactSection, item.id) }"
              @click="selectContactItem(contactSection, item.id)"
            >
              <el-avatar :size="34" :src="item.avatar">
                {{ item.title?.charAt(0) || item.fallback }}
              </el-avatar>
              <div class="sub-item-text">
                <span class="sub-title">{{ item.title }}</span>
                <span class="sub-desc">{{ item.description }}</span>
              </div>
            </button>
          </div>
        </div>

        <div v-else-if="isRecommendRoute" class="middle-panel__body">
          <button
            v-for="tab in recommendTabs"
            :key="tab.key"
            type="button"
            class="shortcut-item"
            :class="{ active: recommendSection === tab.key }"
            @click="navigateToTab(tab)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div v-else-if="isNoticesRoute" class="middle-panel__body">
          <button
            v-for="tab in noticeTabs"
            :key="tab.key"
            type="button"
            class="shortcut-item"
            :class="{ active: noticeSection === tab.key }"
            @click="navigateToTab(tab)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div v-else-if="isOtherRoute" class="middle-panel__body">
          <button
            v-for="tab in otherTabs"
            :key="tab.key"
            type="button"
            class="shortcut-item"
            :class="{ active: otherSection === tab.key }"
            @click="navigateToTab(tab)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>
    </aside>

    <section class="desktop-main">
      <main class="desktop-content">
        <RouterView :key="$route.fullPath" />
      </main>
    </section>
  </div>
</template>

<style scoped>
.desktop-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--app-bg);
}

.desktop-middle {
  width: 280px;
  min-width: 280px;
  overflow: hidden;
  border-right: 1px solid var(--border-color);
  background: #ffffff;
}

.middle-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
}

.middle-panel__header {
  padding: 22px 18px 14px;
  border-bottom: 1px solid var(--border-color);
}

.middle-panel__header h3 {
  margin: 0 0 8px;
  color: var(--text-color);
  font-size: 20px;
  line-height: 1.2;
}

.middle-panel__header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.middle-panel__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 12px;
  overflow-y: auto;
}

.middle-panel__body--spaced {
  gap: 10px;
}

.shortcut-item {
  width: 100%;
  padding: 12px 14px;
  border: none;
  border-radius: 10px;
  background: #ffffff;
  color: #333333;
  font-size: 14px;
  text-align: left;
  transition: background-color 0.2s ease;
}

.shortcut-item:hover,
.shortcut-item.active {
  background: #f3f3f3;
}

.sub-list-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 0 4px;
}

.sub-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 12px;
  background: #ffffff;
  text-align: left;
}

.sub-item:hover,
.sub-item.active {
  background: #f1f1f1;
}

.sub-item-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.sub-title,
.sub-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-title {
  color: #2a2a2a;
  font-size: 13px;
}

.sub-desc,
.sub-empty {
  color: #8f8f8f;
  font-size: 12px;
}

.sub-empty {
  padding: 4px 6px 8px;
}

.desktop-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: #f9f9f9;
}

.desktop-content {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
