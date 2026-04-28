import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNoticeStore } from '@/stores/notice'
import { getUnreadNoticeCount, getFriendRequests, getFriendList } from '@/api/user'
import { getUnreadCount } from '@/api/chat'
import { getTeamList } from '@/api/team'
import { heartbeat, setOnlineStatus, goOffline } from '@/api/online'
import { connectWebSocket, disconnectWebSocket, buildConversationId } from '@/utils/websocket'
import { saveMessageToLocal, markRevokedLocal } from '@/utils/localDb'
import { clampText } from '@/utils/format'

const OTHER_ROUTE_PREFIXES = ['/devices', '/account-status', '/reports', '/feedback', '/appeals']
const LIST_CONTEXT_ROUTE_PREFIXES = ['/chat', '/friends', '/teams', '/recommend', '/profile', '/notices', '/search', '/devices', '/account-status', '/feedback', '/reports', '/appeals']
const MIDDLE_TITLE_MAP = [
  { title: '聊天', prefixes: ['/chat'] },
  { title: '通讯录', prefixes: ['/friends'] },
  { title: '队伍', prefixes: ['/teams'] },
  { title: '发现', prefixes: ['/recommend'] },
  { title: '个人资料', prefixes: ['/profile'] },
  { title: '通知', prefixes: ['/notices'] },
  { title: '搜索', prefixes: ['/search'] },
  { title: '其他', prefixes: ['/settings', ...OTHER_ROUTE_PREFIXES, '/reports'] },
]

function normalizeFriends(list = []) {
  return list.map((item) => ({
    ...item,
    friendId: item.friendId ?? item.userId,
    userNickname: item.userNickname ?? item.friendRemark ?? item.nickname ?? '好友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? '',
    userIntro: item.userIntro ?? item.userBio ?? '',
  }))
}

function normalizeRequests(list = []) {
  return list.map((item) => ({
    ...item,
    applicantId: item.applicantId ?? item.userId ?? item.friendId,
    userNickname: item.userNickname ?? item.nickname ?? '新的朋友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? '',
    applyMsg: item.applyMsg ?? item.message ?? '',
  }))
}

function normalizeTeams(list = []) {
  return list.map((item) => ({
    ...item,
    id: item.id ?? item.teamId,
    teamAvatar: item.teamAvatar ?? item.avatar ?? '',
    teamIntro: item.teamIntro ?? item.teamDesc ?? '',
    maxMember: item.maxMember ?? item.maxMemberNum ?? 0,
    memberCount: item.memberCount ?? item.currentMemberCount ?? 0,
  }))
}

export function useMainLayout() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const noticeStore = useNoticeStore()
  const router = useRouter()
  const route = useRoute()
  const contactRequests = ref([])
  const contactFriends = ref([])
  const joinedTeams = ref([])
  const squareTeams = ref([])
  const contactLoading = ref(false)
  let heartbeatTimer = null

  const isChatRoute = computed(() => route.path.startsWith('/chat'))
  const isFriendsRoute = computed(() => route.path.startsWith('/friends'))
  const isRecommendRoute = computed(() => route.path.startsWith('/recommend'))
  const isNoticesRoute = computed(() => route.path.startsWith('/notices'))
  const isOtherRoute = computed(() => OTHER_ROUTE_PREFIXES.some((prefix) => route.path.startsWith(prefix)))
  const isListContextRoute = computed(() => LIST_CONTEXT_ROUTE_PREFIXES.some((prefix) => route.path.startsWith(prefix)))

  const currentConversationId = computed(() => {
    if (route.path.startsWith('/chat/private/')) {
      const friendId = Number(route.params.friendId)
      if (!authStore.userId || !friendId) {
        return ''
      }

      return buildConversationId(authStore.userId, friendId)
    }

    if (route.path.startsWith('/chat/team/')) {
      const teamId = Number(route.params.teamId)
      return teamId ? `team_${teamId}` : ''
    }

    return ''
  })

  const middleTitle = computed(() => {
    const match = MIDDLE_TITLE_MAP.find((item) => item.prefixes.some((prefix) => route.path.startsWith(prefix)))
    return match?.title ?? '功能导航'
  })

  const contactSection = computed(() => {
    const section = String(route.query.section || 'friends')
    return ['friends', 'requests', 'teams', 'square'].includes(section) ? section : 'friends'
  })

  const selectedItemId = computed(() => String(route.query.itemId || ''))

  const recommendSection = computed(() => {
    const section = String(route.query.section || 'users')
    return ['users', 'teams'].includes(section) ? section : 'users'
  })

  const noticeSection = computed(() => {
    const section = String(route.query.section || 'all')
    return ['all', 'friend', 'team', 'system'].includes(section) ? section : 'all'
  })

  const otherSection = computed(() => {
    if (route.path.startsWith('/devices')) return 'devices'
    if (route.path.startsWith('/account-status')) return 'account-status'
    if (route.path.startsWith('/reports')) return 'reports'
    if (route.path.startsWith('/feedback')) return 'feedback'
    if (route.path.startsWith('/appeals')) return 'appeals'
    return 'devices'
  })

  const contactSectionMap = computed(() => ({
    friends: {
      key: 'friends',
      label: '好友',
      emptyText: '暂无好友',
      items: contactFriends.value.map((item) => ({
        id: item.friendId,
        avatar: item.userAvatar,
        title: item.userNickname,
        description: clampText(item.userIntro || item.userAccount || '点击查看资料', 22),
        fallback: '友',
      })),
    },
    requests: {
      key: 'requests',
      label: '新的朋友',
      emptyText: '暂无好友申请',
      items: contactRequests.value.map((item) => ({
        id: item.applicantId,
        avatar: item.userAvatar,
        title: item.userNickname,
        description: clampText(item.applyMsg || '新的好友申请', 22),
        fallback: '新',
      })),
    },
    teams: {
      key: 'teams',
      label: '队伍管理',
      emptyText: '暂无已加入队伍',
      items: joinedTeams.value.map((item) => ({
        id: item.id,
        avatar: item.teamAvatar,
        title: item.teamName,
        description: `${item.memberCount || 0} 人`,
        fallback: '队',
      })),
    },
    square: {
      key: 'square',
      label: '队伍广场',
      emptyText: '请从右侧查看广场内容',
      items: squareTeams.value.map((item) => ({
        id: item.id,
        avatar: item.teamAvatar,
        title: item.teamName,
        description: clampText(item.teamIntro || '公开招募中', 22),
        fallback: '广',
      })),
    },
  }))

  const contactSections = computed(() => Object.values(contactSectionMap.value))

  async function initData() {
    try {
      const [noticeRes, chatRes] = await Promise.all([getUnreadNoticeCount(), getUnreadCount()])

      if (noticeRes.code === 200) {
        noticeStore.setUnreadCount(noticeRes.data)
      }

      if (chatRes.code === 200 && chatRes.data) {
        const conversations = chatRes.data.conversations || []
        conversations.forEach((item) => chatStore.setUnread(item.conversationId, item.unreadCount))
        chatStore.setConversationList(conversations)
      }
    } catch {
      // ignore bootstrap errors and let child pages recover independently
    }
  }

  function ensureContactQuery() {
    if (!isFriendsRoute.value) {
      return
    }

    const list = contactSectionMap.value[contactSection.value]?.items || []
    if (!list.length) {
      return
    }

    const exists = list.some((item) => String(item.id) === selectedItemId.value)
    if (!selectedItemId.value || !exists) {
      router.replace({
        path: '/friends',
        query: {
          section: contactSection.value,
          itemId: String(list[0].id),
        },
      })
    }
  }

  async function loadContactManageData() {
    if (!isFriendsRoute.value) {
      return
    }

    contactLoading.value = true

    try {
      const [friendRes, requestRes, teamRes] = await Promise.all([
        getFriendList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
        getFriendRequests({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
        getTeamList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
      ])

      const teamSource = teamRes.data?.records || teamRes.data || []
      contactFriends.value = normalizeFriends(friendRes.data?.records || friendRes.data || [])
      contactRequests.value = normalizeRequests(requestRes.data?.records || requestRes.data || [])
      joinedTeams.value = normalizeTeams(teamSource)
      squareTeams.value = normalizeTeams(teamSource)
      ensureContactQuery()
    } finally {
      contactLoading.value = false
    }
  }

  function openContactSection(section) {
    const list = contactSectionMap.value[section]?.items || []
    const firstId = list[0] ? String(list[0].id) : undefined
    router.replace({
      path: '/friends',
      query: {
        section,
        ...(firstId ? { itemId: firstId } : {}),
      },
    })
  }

  function selectContactItem(section, itemId) {
    router.replace({
      path: '/friends',
      query: { section, itemId: String(itemId) },
    })
  }

  function isContactItemActive(section, itemId) {
    return contactSection.value === section && selectedItemId.value === String(itemId)
  }

  function handleWsMessage({ type, data }) {
    if (type === 'private_message' || type === 'team_message') {
      const conversationId = type === 'private_message' ? buildConversationId(authStore.userId, data.senderId) : `team_${data.teamId}`
      saveMessageToLocal({
        msgId: data.msgId,
        conversationId,
        senderId: data.senderId,
        msgType: data.msgType,
        msgContent: data.content,
        createTime: data.createTime,
        isRevoke: false,
        localStatus: 1,
      })
      window.dispatchEvent(new CustomEvent('chat-ws-message', { detail: { type, data, conversationId } }))

      if (conversationId !== chatStore.activeConversationId) {
        chatStore.incrementUnread(conversationId)
      }
    }

    if (type === 'message_revoke') {
      markRevokedLocal(data.msgId)
      window.dispatchEvent(new CustomEvent('chat-ws-revoke', { detail: data }))
    }

    if (type === 'group_notice_update') {
      window.dispatchEvent(new CustomEvent('group-notice-update', { detail: data }))
    }

    if (type === 'system_notice') {
      noticeStore.increment()
      window.dispatchEvent(new CustomEvent('system-notice', { detail: data }))
    }
  }

  function logout() {
    goOffline().catch(() => {})
    disconnectWebSocket()
    authStore.clearAuth()
    router.push({ name: 'Login' })
  }

  watch(currentConversationId, (value) => {
    chatStore.setActiveConversation(value || '')
  }, { immediate: true })

  watch(() => route.fullPath, () => {
    if (isFriendsRoute.value) {
      loadContactManageData()
    }
  })

  onMounted(async () => {
    await initData()

    if (isFriendsRoute.value) {
      await loadContactManageData()
    }

    await setOnlineStatus(Number(localStorage.getItem('onlineStatus') || '1')).catch(() => {})
    connectWebSocket(handleWsMessage)
    heartbeatTimer = setInterval(() => heartbeat().catch(() => {}), 120000)
  })

  onUnmounted(() => {
    clearInterval(heartbeatTimer)
    goOffline().catch(() => {})
    disconnectWebSocket()
  })

  return {
    contactLoading,
    contactSection,
    contactSections,
    currentConversationId,
    isChatRoute,
    isContactItemActive,
    isFriendsRoute,
    isListContextRoute,
    isNoticesRoute,
    isOtherRoute,
    isRecommendRoute,
    loadContactManageData,
    logout,
    middleTitle,
    noticeSection,
    openContactSection,
    otherSection,
    recommendSection,
    router,
    selectContactItem,
  }
}
