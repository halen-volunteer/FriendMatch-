import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', () => {
  const unreadMap = ref({}) // { conversationId: count }
  const activeConversationId = ref('')
  const conversationList = ref([])

  const totalUnread = computed(() =>
    Object.values(unreadMap.value).reduce((s, n) => s + n, 0)
  )

  function setUnread(convId, count) { unreadMap.value[convId] = count }
  function clearUnread(convId) { unreadMap.value[convId] = 0 }
  function incrementUnread(convId) {
    unreadMap.value[convId] = (unreadMap.value[convId] || 0) + 1
  }
  function setActiveConversation(id) { activeConversationId.value = id }
  function setConversationList(list) { conversationList.value = list }

  return {
    unreadMap, activeConversationId, conversationList, totalUnread,
    setUnread, clearUnread, incrementUnread, setActiveConversation, setConversationList,
  }
})
