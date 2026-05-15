import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, UserFilled, BellFilled, Compass, Setting, SwitchButton, Operation, ArrowRight, Check } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNoticeStore } from '@/stores/notice'
import { setOnlineStatus } from '@/api/online'

const OTHER_ROUTE_PREFIXES = ['/account-status', '/reports', '/feedback', '/appeals']

export function useSideNav(emit) {
  const route = useRoute()
  const router = useRouter()
  const chatStore = useChatStore()
  const noticeStore = useNoticeStore()
  const authStore = useAuthStore()
  const settingsMenuVisible = ref(false)
  const statusPanelVisible = ref(false)
  const menuWrapRef = ref(null)
  const currentOnlineStatus = ref(Number(localStorage.getItem('onlineStatus') || '1'))

  const navItems = [
    { name: 'Chat', path: '/chat', label: '聊天', icon: Message },
    { name: 'Friends', path: '/friends', label: '通讯录', icon: UserFilled },
    { name: 'Recommend', path: '/recommend', label: '发现', icon: Compass },
    { name: 'Notices', path: '/notices', label: '通知', icon: BellFilled },
    { name: 'Other', path: '/devices', label: '其他', icon: Operation },
  ]

  const statusOptions = [
    { value: 1, label: '在线' },
    { value: 2, label: '离开' },
    { value: 3, label: '忙碌' },
    { value: 4, label: '隐身' },
  ]

  const currentStatusLabel = computed(() => statusOptions.find((item) => item.value === currentOnlineStatus.value)?.label || '在线')
  const navBadgeMap = computed(() => ({
    Chat: chatStore.totalUnread,
    Notices: noticeStore.unreadCount,
  }))

  function isActive(path) {
    return route.path.startsWith(path) || (path === '/devices' && OTHER_ROUTE_PREFIXES.some((prefix) => route.path.startsWith(prefix)))
  }

  function shouldShowNavBadge(name) {
    return Number(navBadgeMap.value[name] || 0) > 0
  }

  function getNavBadgeValue(name) {
    const count = Number(navBadgeMap.value[name] || 0)
    return count > 99 ? '99+' : count
  }

  function handleNavClick(path) {
    if (!isActive(path)) {
      router.push(path)
    }
  }

  function toggleSettingsMenu() {
    settingsMenuVisible.value = !settingsMenuVisible.value

    if (!settingsMenuVisible.value) {
      statusPanelVisible.value = false
    }
  }

  function openStatusPanel() {
    statusPanelVisible.value = true
  }

  async function selectOnlineStatus(status) {
    currentOnlineStatus.value = status
    localStorage.setItem('onlineStatus', String(status))
    await setOnlineStatus(status).catch(() => {})
    settingsMenuVisible.value = false
    statusPanelVisible.value = false
  }

  function handleOutsideClick(event) {
    if (!menuWrapRef.value?.contains(event.target)) {
      settingsMenuVisible.value = false
      statusPanelVisible.value = false
    }
  }

  function handleLogout() {
    settingsMenuVisible.value = false
    statusPanelVisible.value = false
    emit('logout')
  }

  onMounted(() => {
    document.addEventListener('click', handleOutsideClick)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleOutsideClick)
  })

  return {
    authStore,
    currentStatusLabel,
    getNavBadgeValue,
    handleLogout,
    handleNavClick,
    isActive,
    menuWrapRef,
    navItems,
    openStatusPanel,
    settingsMenuVisible,
    shouldShowNavBadge,
    statusOptions,
    statusPanelVisible,
    toggleSettingsMenu,
    selectOnlineStatus,
    isStatusActive: (status) => currentOnlineStatus.value === status,
    icons: { Setting, SwitchButton, ArrowRight, Check },
  }
}
