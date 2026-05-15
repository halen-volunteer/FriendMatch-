import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { formatConversationPreview } from '@/utils/chatPreview'

function normalizeConversationItem(item) {
  if (!item) return item
  const rawPreview = item.lastMsg ?? item.lastMessage ?? item.msgContent ?? item.content ?? ''
  const preview = formatConversationPreview(item.msgType, rawPreview)
  return {
    ...item,
    lastMsg: preview || '',
    lastMessage: preview || '',
  }
}

export const useChatStore = defineStore('chat', () => {
  const unreadMap = ref({}) // { conversationId: count }
  const activeConversationId = ref('')
  const conversationList = ref([])

  const totalUnread = computed(() =>
    Object.values(unreadMap.value).reduce((s, n) => s + n, 0)
  )

  function syncConversationUnread(convId, count) {
    conversationList.value = conversationList.value.map((item) => (
      item.id === convId ? { ...item, unreadCount: count } : item
    ))
  }

  function setUnread(convId, count) {
    unreadMap.value[convId] = count
    syncConversationUnread(convId, count)
  }

  function clearUnread(convId) {
    unreadMap.value[convId] = 0
    syncConversationUnread(convId, 0)
  }

  function incrementUnread(convId) {
    unreadMap.value[convId] = (unreadMap.value[convId] || 0) + 1
    syncConversationUnread(convId, unreadMap.value[convId])
  }

  function setActiveConversation(id) { activeConversationId.value = id }
  function setConversationList(list) {
    conversationList.value = Array.isArray(list) ? list.map(normalizeConversationItem) : []
  }
  function removeConversation(conversationId) {
    conversationList.value = conversationList.value.filter(item => item.id !== conversationId)
    if (conversationId in unreadMap.value) {
      unreadMap.value[conversationId] = 0
    }
  }
  function refreshConversationItem(conversation) {
    if (!conversation?.id) return
    const list = [...conversationList.value]
    const index = list.findIndex(item => item.id === conversation.id)
    if (index >= 0) {
      list.splice(index, 1)
    }
    conversationList.value = [normalizeConversationItem(conversation), ...list]
  }

  return {
    unreadMap, activeConversationId, conversationList, totalUnread,
    setUnread, clearUnread, incrementUnread, setActiveConversation, setConversationList, removeConversation, refreshConversationItem,
  }
})
