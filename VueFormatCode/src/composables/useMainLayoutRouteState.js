import { computed } from 'vue'
import { buildConversationId } from '@/utils/websocket'

const OTHER_ROUTE_PREFIXES = ['/devices', '/account-status', '/reports', '/feedback', '/appeals']
const LIST_CONTEXT_ROUTE_PREFIXES = [
  '/chat',
  '/friends',
  '/teams',
  '/recommend',
  '/profile',
  '/notices',
  '/search',
  '/devices',
  '/account-status',
  '/feedback',
  '/reports',
  '/appeals',
]
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

export function useMainLayoutRouteState({ route, authStore }) {
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
    return ['friends', 'requests', 'teams', 'square', 'blacklist'].includes(section) ? section : 'friends'
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

  return {
    contactSection,
    currentConversationId,
    isChatRoute,
    isFriendsRoute,
    isListContextRoute,
    isNoticesRoute,
    isOtherRoute,
    isRecommendRoute,
    middleTitle,
    noticeSection,
    otherSection,
    recommendSection,
    selectedItemId,
  }
}
