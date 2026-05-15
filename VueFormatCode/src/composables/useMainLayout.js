import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNoticeStore } from '@/stores/notice'
import { useMainLayoutContacts } from '@/composables/useMainLayoutContacts'
import { useMainLayoutRealtime } from '@/composables/useMainLayoutRealtime'
import { useMainLayoutRouteState } from '@/composables/useMainLayoutRouteState'

export function useMainLayout() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const noticeStore = useNoticeStore()
  const router = useRouter()
  const route = useRoute()

  const routeState = useMainLayoutRouteState({ route, authStore })
  const contactsState = useMainLayoutContacts({
    route,
    router,
    isFriendsRoute: routeState.isFriendsRoute,
    contactSection: routeState.contactSection,
    selectedItemId: routeState.selectedItemId,
  })
  const realtimeState = useMainLayoutRealtime({
    authStore,
    chatStore,
    contactFriends: contactsState.contactFriends,
    currentConversationId: routeState.currentConversationId,
    isFriendsRoute: routeState.isFriendsRoute,
    joinedTeams: contactsState.joinedTeams,
    loadContactManageData: contactsState.loadContactManageData,
    noticeStore,
    route,
    router,
  })

  return {
    contactLoading: contactsState.contactLoading,
    contactSection: routeState.contactSection,
    contactSections: contactsState.contactSections,
    currentConversationId: routeState.currentConversationId,
    isChatRoute: routeState.isChatRoute,
    isContactItemActive: contactsState.isContactItemActive,
    isFriendsRoute: routeState.isFriendsRoute,
    isListContextRoute: routeState.isListContextRoute,
    isNoticesRoute: routeState.isNoticesRoute,
    isOtherRoute: routeState.isOtherRoute,
    isRecommendRoute: routeState.isRecommendRoute,
    loadContactManageData: contactsState.loadContactManageData,
    logout: realtimeState.logout,
    middleTitle: routeState.middleTitle,
    noticeSection: routeState.noticeSection,
    openContactSection: contactsState.openContactSection,
    otherSection: routeState.otherSection,
    recommendSection: routeState.recommendSection,
    router,
    selectContactItem: contactsState.selectContactItem,
  }
}
