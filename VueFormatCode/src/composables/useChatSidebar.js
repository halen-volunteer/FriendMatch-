import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createTeam } from '@/api/team'
import { getRecentConversations, hideConversation } from '@/api/chat'
import { useChatStore } from '@/stores/chat'

function createDefaultForm() {
  return {
    teamName: '',
    teamIntro: '',
    teamTags: '',
    teamType: 1,
    joinRule: 1,
    joinPassword: '',
    maxMember: 20,
  }
}

export function useChatSidebar() {
  const router = useRouter()
  const route = useRoute()
  const chatStore = useChatStore()
  const keyword = ref('')
  const loading = ref(false)
  const quickMenuVisible = ref(false)
  const showCreateTeam = ref(false)
  const createForm = ref(createDefaultForm())
  const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    conversation: null,
    loading: false,
  })

  const filtered = computed(() => {
    const list = chatStore.conversationList || []
    if (!keyword.value) return list
    return list.filter((conversation) => (
      conversation.name?.includes(keyword.value)
      || conversation.lastMsg?.includes(keyword.value)
    ))
  })

  async function load() {
    loading.value = true
    try {
      const res = await getRecentConversations()
      const conversations = res.code === 200 ? (res.data || []) : []
      chatStore.setConversationList(conversations)
      conversations.forEach((item) => chatStore.setUnread(item.id, item.unreadCount || 0))
    } finally {
      loading.value = false
    }
  }

  function closeContextMenu() {
    contextMenu.value.visible = false
  }

  function isActive(conversation) {
    if (conversation.type === 'private') return route.path === `/chat/private/${conversation.targetId}`
    return route.path === `/chat/team/${conversation.targetId}`
  }

  function goChat(conversation) {
    closeContextMenu()
    if (isActive(conversation)) {
      window.dispatchEvent(new CustomEvent('chat-conversation-reclick', { detail: conversation }))
      return
    }
    if (conversation.type === 'private') {
      router.push(`/chat/private/${conversation.targetId}`)
      return
    }
    router.push(`/chat/team/${conversation.targetId}`)
  }

  function formatTime(value) {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return ''
    const now = new Date()
    const isToday = date.toDateString() === now.toDateString()
    return isToday
      ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString([], { month: 'numeric', day: 'numeric' })
  }

  function openCreateTeam() {
    quickMenuVisible.value = false
    showCreateTeam.value = true
  }

  function openSearchUser() {
    quickMenuVisible.value = false
    router.push('/search?tab=user')
  }

  function openSearchTeam() {
    quickMenuVisible.value = false
    router.push('/search?tab=team')
  }

  function openContextMenu(event, conversation) {
    event.preventDefault()
    event.stopPropagation()
    contextMenu.value = {
      visible: true,
      x: event.clientX,
      y: event.clientY,
      conversation,
      loading: false,
    }
  }

  async function handleHideConversation() {
    const conversation = contextMenu.value.conversation
    if (!conversation?.id || contextMenu.value.loading) return
    contextMenu.value.loading = true
    const res = await hideConversation(conversation.id).catch(() => ({ code: 400 }))
    if (res.code === 200) {
      chatStore.removeConversation(conversation.id)
      closeContextMenu()
      if (isActive(conversation)) {
        router.push('/chat')
      }
    }
    contextMenu.value.loading = false
  }

  async function handleCreateTeam() {
    if (!createForm.value.teamName) return
    const res = await createTeam(createForm.value).catch(() => ({ code: 400 }))
    if (res.code === 200) {
      showCreateTeam.value = false
      createForm.value = createDefaultForm()
    }
  }

  onMounted(load)
  onMounted(() => {
    window.addEventListener('click', closeContextMenu)
  })

  onUnmounted(() => {
    window.removeEventListener('click', closeContextMenu)
  })

  return {
    chatStore,
    contextMenu,
    createForm,
    filtered,
    formatTime,
    goChat,
    handleCreateTeam,
    handleHideConversation,
    isActive,
    keyword,
    loading,
    openContextMenu,
    openCreateTeam,
    openSearchTeam,
    openSearchUser,
    quickMenuVisible,
    showCreateTeam,
  }
}
