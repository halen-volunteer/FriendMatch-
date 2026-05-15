import { onMounted, onUnmounted, watch } from 'vue'
import { getUnreadNoticeCount } from '@/api/user'
import { getRecentConversations } from '@/api/chat'
import { heartbeat, setOnlineStatus, goOffline } from '@/api/online'
import { connectWebSocket, disconnectWebSocket, buildConversationId } from '@/utils/websocket'
import { saveMessageToLocal, markRevokedLocal } from '@/utils/localDb'
import { formatConversationPreview } from '@/utils/chatPreview'

function buildConversationSummary({
  conversationId,
  type,
  targetId,
  name,
  avatar,
  content,
  msgType,
  createTime,
  unreadCount = 0,
}) {
  const preview = formatConversationPreview(msgType, content)
  return {
    id: conversationId,
    conversationId,
    type,
    targetId,
    name,
    avatar: avatar || '',
    lastMsg: preview || '',
    lastMessage: preview || '',
    time: createTime || '',
    lastTime: createTime || '',
    unreadCount,
  }
}

export function useMainLayoutRealtime({
  authStore,
  chatStore,
  contactFriends,
  currentConversationId,
  isFriendsRoute,
  joinedTeams,
  loadContactManageData,
  noticeStore,
  route,
  router,
}) {
  let heartbeatTimer = null

  async function initData() {
    try {
      const [noticeRes, chatRes] = await Promise.all([getUnreadNoticeCount(), getRecentConversations()])

      if (noticeRes.code === 200) {
        noticeStore.setUnreadCount(noticeRes.data)
      }

      if (chatRes.code === 200 && chatRes.data) {
        const conversations = chatRes.data || []
        conversations.forEach((item) => chatStore.setUnread(item.id || item.conversationId, item.unreadCount))
        chatStore.setConversationList(conversations)
      }
    } catch {
      // ignore bootstrap errors and let child pages recover independently
    }
  }

  function handleWsMessage({ type, data }) {
    if (type === 'private_message' || type === 'team_message' || type === 'team_message_summary') {
      const conversationId = type === 'private_message'
        ? buildConversationId(authStore.userId, data.senderId)
        : `team_${data.teamId}`

      // 1. 大群摘要事件只刷新会话摘要和未读，不落本地正文，真正点进会话时再按游标分页拉历史消息。
      if (type !== 'team_message_summary') {
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
      }

      if (conversationId !== chatStore.activeConversationId) {
        chatStore.incrementUnread(conversationId)
      }

      if (type === 'private_message') {
        const friend = contactFriends.value.find((item) => Number(item.friendId) === Number(data.senderId))
        if (friend) {
          chatStore.refreshConversationItem(buildConversationSummary({
            conversationId,
            type: 'private',
            targetId: friend.friendId,
            name: friend.userNickname,
            avatar: friend.userAvatar,
            content: data.content,
            msgType: data.msgType,
            createTime: data.createTime,
            unreadCount: chatStore.unreadMap[conversationId] || 0,
          }))
        }
      } else {
        const team = joinedTeams.value.find((item) => Number(item.id) === Number(data.teamId))
        if (team) {
          chatStore.refreshConversationItem(buildConversationSummary({
            conversationId,
            type: 'team',
            targetId: team.id,
            name: team.teamName,
            avatar: team.teamAvatar,
            content: data.lastMessage ?? data.content,
            msgType: data.msgType,
            createTime: data.createTime,
            unreadCount: chatStore.unreadMap[conversationId] || 0,
          }))
        }
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
    if (value) {
      chatStore.clearUnread(value)
    }
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
    logout,
  }
}
