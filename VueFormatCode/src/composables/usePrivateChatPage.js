import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import {
  sendPrivateMsg,
  getPrivateHistory,
  revokeMsg,
  readMsgs,
  collectMsg,
  cancelCollect,
  getCollections,
  pinMsg,
  unpinMsg,
  getPinList,
  searchMsg,
  editMsg,
  hideConversation,
} from '@/api/chat'
import { reportMessage, reportUser } from '@/api/report'
import { addBlacklist, deleteFriend, getBlacklist, getUserProfile } from '@/api/user'
import { getUserOnlineStatus } from '@/api/online'
import {
  getLocalMessages,
  saveMessageToLocal,
  deleteLocalMessage,
  updateLocalStatus,
  markRevokedLocal,
  cleanupExpiredMessages,
  clearFailedMessages,
} from '@/utils/localDb'
import { formatConversationPreview } from '@/utils/chatPreview'
import { useActionSubmitting } from '@/composables/useActionSubmitting'
import { useChatPanelState } from '@/composables/useChatPanelState'
import { useToast } from '@/composables/useToast'
import { useChatMessageList } from '@/composables/useChatMessageList'
import { renderConversationPreviewHighlight } from '@/utils/chatText'
import { MESSAGE_REPORT_REASON_OPTIONS, USER_REPORT_REASON_OPTIONS } from '@/constants/report'

export function usePrivateChatPage() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const chatStore = useChatStore()

  const friendId = route.params.friendId ? Number(route.params.friendId) : undefined
  const convId = friendId
    ? `${Math.min(authStore.userId, friendId)}_${Math.max(authStore.userId, friendId)}`
    : ''

  const messages = ref([])
  const friend = ref({})
  const pins = ref([])
  const collections = ref([])
  const searchKeyword = ref('')
  const searchResults = ref([])
  const reportModal = ref({ visible: false, msg: null, reason: 3, content: '' })
  const userReportModal = ref({ visible: false, reason: 1, content: '' })
  const collectModal = ref({ visible: false, msg: null, note: '' })
  const editModal = ref({ visible: false, msg: null, content: '' })
  const msgListRef = ref(null)
  const sending = ref(false)
  const errorMsg = ref('')
  const myBlacklist = ref([])
  const friendOnlineStatus = ref(0)
  const friendProfileLoaded = ref(false)
  const confirmState = ref({
    visible: false,
    title: '',
    message: '',
    confirmText: '确认',
    action: '',
    loading: false,
  })

  const { toast, showToast } = useToast()
  const { actionSubmitting, runAction } = useActionSubmitting({
    revoke: false,
    edit: false,
    report: false,
    userReport: false,
    collect: false,
    pin: false,
    unpin: false,
    cancelCollect: false,
  })

  let pollTimer = null
  let lastMsgCount = 0
  let lastRealtimeAt = 0

  const {
    formatMessageDivider,
    markMessageRevoked,
    mergeMessages,
    normalizeMessages,
    shouldShowTime,
    upsertMessage,
  } = useChatMessageList(messages, authStore.userId)

  const { activePanel, closePanel, loadingPanel, openPanel } = useChatPanelState({
    async pins() {
      await refreshPins()
    },
    async collections() {
      await refreshCollections()
    },
    search() {
      searchResults.value = []
    },
  })

  const panelTitles = {
    search: '消息搜索',
    pins: '会话置顶',
    collections: '我的收藏',
  }

  const isFriendBlacklisted = computed(() => {
    if (!friendId) return false
    return myBlacklist.value.some((item) => Number(item.blackUserId ?? item.userId) === Number(friendId))
  })

  const isPrivateSendForbiddenByProfile = computed(() => {
    return friendProfileLoaded.value && friend.value?.canSendPrivateMessage === false
  })

  const privateInputDisabled = computed(() => {
    return !friendProfileLoaded.value || sending.value || isFriendBlacklisted.value || isPrivateSendForbiddenByProfile.value
  })

  const privateInputDisabledReason = computed(() => {
    if (isFriendBlacklisted.value) return '当前好友已被拉黑，无法发送消息'
    if (isPrivateSendForbiddenByProfile.value) {
      return friend.value?.sendPrivateMessageReason || '当前无法发送消息'
    }
    return ''
  })

  const currentConversation = computed(() => {
    return (chatStore.conversationList || []).find((item) =>
      item.id === convId || (item.type === 'private' && Number(item.targetId) === Number(friendId)),
    ) || null
  })

  const friendDisplayName = computed(() => {
    return friend.value.userNickname || friend.value.userAccount || currentConversation.value?.name || '好友'
  })

  const friendStatusText = computed(() => {
    return friendOnlineStatus.value === 1 ? '在线' : '离线'
  })

  const friendDisplayAvatar = computed(() => {
    return friend.value.userAvatar || currentConversation.value?.avatar || ''
  })

  function getPanelMessageText(item) {
    return formatConversationPreview(item?.msgType, item?.msgContent)
  }

  function renderPanelHighlightedText(item, keyword = '') {
    return renderConversationPreviewHighlight(item, keyword)
  }

  function isBusinessRejectedError(error) {
    return typeof error?.response?.code === 'number' && !('status' in (error?.response || {}))
  }

  function isAmbiguousSendError(error) {
    const code = String(error?.code || '')
    const message = String(error?.message || '').toLowerCase()
    return code === 'ECONNABORTED'
      || code === 'ERR_NETWORK'
      || message.includes('timeout')
      || message.includes('network')
  }

  function resolveRequestErrorMessage(error, fallback = '操作失败') {
    return error?.response?.message || error?.message || fallback
  }

  function getReportValidationMessage(msg) {
    if (!msg) return '消息不存在'
    if (msg.isSelf) return '不能举报自己发送的消息'
    const msgIdText = String(msg.msgId ?? msg.id ?? '').trim()
    const localStatus = Number(msg.localStatus ?? 1)
    if (!msgIdText || msgIdText.startsWith('temp_') || localStatus === 0 || localStatus === 2) {
      return '该消息尚未稳定入库，暂时不能举报'
    }
    return ''
  }

  async function scrollToBottom(force = false) {
    await nextTick()
    const run = () => {
      if (msgListRef.value) {
        msgListRef.value.scrollTop = msgListRef.value.scrollHeight
      }
    }
    run()
    if (force) {
      requestAnimationFrame(() => {
        run()
        requestAnimationFrame(run)
      })
    }
  }

  async function ensureMessageLoaded(msgId) {
    if (messages.value.some((item) => String(item.msgId) === String(msgId))) return true
    try {
      const res = await getPrivateHistory({ friendId, page: 1, pageSize: 200 })
      if (res.code === 200) {
        const list = normalizeMessages(res.data?.records || res.data || [])
        messages.value = list
        lastMsgCount = list.length
        return list.some((item) => String(item.msgId) === String(msgId))
      }
    } catch {
      // ignore
    }
    return false
  }

  async function scrollToMessage(msgId) {
    const exists = await ensureMessageLoaded(msgId)
    if (!exists) {
      showToast('目标消息不在当前历史范围内', 'error')
      return
    }
    await nextTick()
    const el = document.getElementById(`msg-${msgId}`)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.add('msg-highlight')
    setTimeout(() => el.classList.remove('msg-highlight'), 1800)
  }

  async function loadLocalMessages() {
    try {
      await cleanupExpiredMessages()
      const list = await getLocalMessages(convId, 100)
      if (list?.length) {
        messages.value = mergeMessages(messages.value, list)
        lastMsgCount = messages.value.length
      }
    } catch {
      // ignore
    }
  }

  async function syncMessageToLocal(message) {
    await saveMessageToLocal({ ...message, conversationId: convId }).catch(() => {})
  }

  async function removeMessageFromLocal(msgId) {
    await deleteLocalMessage(msgId).catch(() => {})
  }

  function refreshConversationSummary(message) {
    const existingConversation = currentConversation.value
    const preview = formatConversationPreview(message?.msgType, message?.msgContent)
    chatStore.refreshConversationItem({
      id: convId,
      conversationId: convId,
      type: 'private',
      targetId: friendId,
      name: friend.value.userNickname || friend.value.userAccount || existingConversation?.name || '好友',
      avatar: friend.value.userAvatar || existingConversation?.avatar || '',
      lastMsg: preview || '',
      lastMessage: preview || '',
      time: message?.createTime || new Date().toISOString(),
      lastTime: message?.createTime || new Date().toISOString(),
      unreadCount: chatStore.unreadMap[convId] || 0,
    })
  }

  function removeCurrentConversationFromStore() {
    chatStore.removeConversation(convId)
  }

  async function loadMessages() {
    try {
      const res = await getPrivateHistory({ friendId, page: 1, pageSize: 50 })
      if (res.code === 200) {
        const list = normalizeMessages(res.data?.records || res.data || [])
        messages.value = mergeMessages(list, messages.value, { keepOnlyUnsyncedLocal: true })
        lastMsgCount = messages.value.length
      }
    } catch {
      // ignore
    }
  }

  async function syncLatestMessages({ forceScroll = false } = {}) {
    try {
      const res = await getPrivateHistory({ friendId, page: 1, pageSize: 50 })
      if (res.code !== 200) return
      const list = normalizeMessages(res.data?.records || res.data || [])
      const merged = mergeMessages(list, messages.value, { keepOnlyUnsyncedLocal: true })
      const changed = merged.length !== messages.value.length
        || merged.some((item, index) => String(item.msgId) !== String(messages.value[index]?.msgId))

      if (!changed) {
        lastMsgCount = merged.length
        return
      }

      messages.value = merged
      lastMsgCount = merged.length
      await markConversationRead()
      if (forceScroll) {
        scrollToBottom()
      }
    } catch {
      // ignore
    }
  }

  async function loadFriend() {
    try {
      const res = await getUserProfile(friendId)
      if (res.code === 200) {
        const profile = res.data || {}
        if (typeof profile.canSendPrivateMessage !== 'boolean') {
          const isFriend = Boolean(profile.isFriend) || Number(profile.friendStatus) === 1
          const sendMsgMode = Number(profile.privacySetting?.sendMsg || 1)
          if (sendMsgMode === 3 && !isFriend) {
            profile.canSendPrivateMessage = false
            profile.sendPrivateMessageReason = profile.sendPrivateMessageReason || '需要先成为好友才能发送消息'
          }
        }
        if (!profile.userAvatar && currentConversation.value?.avatar) {
          profile.userAvatar = currentConversation.value.avatar
        }
        if (!profile.userNickname && currentConversation.value?.name) {
          profile.userNickname = currentConversation.value.name
        }
        friend.value = profile
      }
    } catch {
      // ignore
    } finally {
      friendProfileLoaded.value = true
    }
  }

  async function loadFriendOnlineStatus() {
    if (!friendId) return
    try {
      const res = await getUserOnlineStatus(friendId)
      if (res.code === 200) {
        const status = Number(res.data?.status || 0)
        friendOnlineStatus.value = status === 1 ? 1 : 0
      }
    } catch {
      // ignore
    }
  }

  async function loadBlacklistState() {
    try {
      const res = await getBlacklist({ page: 1, pageSize: 200 })
      if (res.code === 200) {
        myBlacklist.value = res.data?.records || res.data || []
      }
    } catch {
      // ignore
    }
  }

  async function refreshPins() {
    const res = await getPinList({ conversationId: convId }).catch(() => ({ code: 400, data: [] }))
    if (res.code === 200) {
      pins.value = res.data || []
    }
  }

  async function refreshCollections() {
    const res = await getCollections({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] }))
    if (res.code === 200) {
      collections.value = res.data || []
    }
  }

  async function runSearch() {
    if (!searchKeyword.value.trim()) {
      searchResults.value = []
      return
    }
    loadingPanel.value = true
    const res = await searchMsg({
      conversationId: convId,
      keyword: searchKeyword.value.trim(),
      page: 1,
      pageSize: 50,
    }).catch(() => ({ code: 400, data: [] }))

    if (res.code === 200) {
      searchResults.value = normalizeMessages(res.data?.records || res.data || [])
    } else {
      showToast(res.message || '搜索失败', 'error')
    }
    loadingPanel.value = false
  }

  async function handleSend(payload) {
    if (sending.value || privateInputDisabled.value) {
      if (privateInputDisabled.value && privateInputDisabledReason.value) {
        errorMsg.value = privateInputDisabledReason.value
        setTimeout(() => {
          errorMsg.value = ''
        }, 3000)
      }
      return
    }

    sending.value = true
    errorMsg.value = ''
    const tempId = payload.retryMsgId || `temp_${Date.now()}`
    const optimisticMsg = {
      msgId: tempId,
      senderId: authStore.userId,
      msgType: payload.msgType,
      msgContent: payload.msgContent,
      emojiId: payload.emojiId,
      fileUrl: payload.fileUrl,
      fileName: payload.fileName,
      fileSize: payload.fileSize,
      createTime: new Date().toISOString(),
      isSelf: true,
      localStatus: 0,
      isRevoke: false,
    }

    try {
      upsertMessage(optimisticMsg)
      await syncMessageToLocal(optimisticMsg)
      scrollToBottom()

      const requestMsgContent = payload.msgType === 2 || payload.msgType === 3 || payload.msgType === 4
        ? ''
        : payload.msgContent

      const res = await sendPrivateMsg({
        recipientId: friendId,
        msgType: payload.msgType,
        msgContent: requestMsgContent,
        emojiId: payload.emojiId,
        mediaType: payload.mediaType,
        fileUrl: payload.fileUrl,
        fileName: payload.fileName,
        fileSize: payload.fileSize,
      })

      if (res.code === 200) {
        const serverMsg = typeof res.data === 'object' && res.data !== null
          ? res.data
          : { msgId: res.data }
        const savedMsg = {
          ...serverMsg,
          msgId: serverMsg.msgId ?? serverMsg.id ?? tempId,
          senderId: serverMsg.senderId ?? authStore.userId,
          msgType: serverMsg.msgType ?? payload.msgType,
          msgContent: serverMsg.msgContent ?? payload.msgContent,
          emojiId: serverMsg.emojiId ?? payload.emojiId,
          fileUrl: serverMsg.fileUrl ?? payload.fileUrl,
          fileName: serverMsg.fileName ?? payload.fileName,
          fileSize: serverMsg.fileSize ?? payload.fileSize,
          createTime: serverMsg.createTime ?? new Date().toISOString(),
          isSelf: true,
          localStatus: 1,
          isRevoke: false,
        }

        if (String(savedMsg.msgId) !== String(tempId)) {
          messages.value = messages.value.filter((item) => String(item.msgId) !== String(tempId))
          await removeMessageFromLocal(tempId)
        }
        upsertMessage(savedMsg)
        await syncMessageToLocal(savedMsg)

        if (!serverMsg.msgId && !serverMsg.id) {
          await loadMessages()
        } else {
          await updateLocalStatus(tempId, 1)
        }

        await markConversationRead()
        refreshConversationSummary(savedMsg)
        scrollToBottom()
      } else {
        messages.value = messages.value.filter((item) => String(item.msgId) !== String(tempId))
        await removeMessageFromLocal(tempId)
        errorMsg.value = res.message || '发送失败'
        setTimeout(() => {
          errorMsg.value = ''
        }, 3000)
      }
    } catch (error) {
      if (isBusinessRejectedError(error)) {
        messages.value = messages.value.filter((item) => String(item.msgId) !== String(tempId))
        await removeMessageFromLocal(tempId)
        errorMsg.value = error?.response?.message || error?.message || '发送失败'
        setTimeout(() => {
          errorMsg.value = ''
        }, 3000)
        await loadFriend()
        await loadBlacklistState()
        return
      }

      if (isAmbiguousSendError(error)) {
        await syncLatestMessages({ forceScroll: true })
        const tempStillExists = messages.value.some((item) => String(item.msgId) === String(tempId))
        if (!tempStillExists) {
          await removeMessageFromLocal(tempId)
          scrollToBottom()
          return
        }
      }

      const failedMsg = {
        msgId: tempId,
        senderId: authStore.userId,
        msgType: payload.msgType,
        msgContent: payload.msgContent,
        emojiId: payload.emojiId,
        fileUrl: payload.fileUrl,
        fileName: payload.fileName,
        fileSize: payload.fileSize,
        createTime: new Date().toISOString(),
        isSelf: true,
        localStatus: 2,
        isRevoke: false,
      }
      upsertMessage(failedMsg)
      await syncMessageToLocal({ ...failedMsg, conversationId: convId })
      errorMsg.value = error?.response?.message || error?.message || '网络错误'
      setTimeout(() => {
        errorMsg.value = ''
      }, 3000)
    } finally {
      sending.value = false
    }
  }

  async function retrySend(msg) {
    if (sending.value || msg.localStatus !== 2) return
    await syncMessageToLocal({ ...msg, localStatus: 0 })
    upsertMessage({ ...msg, localStatus: 0 })
    await handleSend({
      msgType: msg.msgType,
      msgContent: msg.msgContent,
      emojiId: msg.emojiId,
      fileUrl: msg.fileUrl,
      fileName: msg.fileName,
      fileSize: msg.fileSize,
      mediaType: msg.mediaType,
      retryMsgId: msg.msgId,
    })
  }

  async function deleteFailedMessage(msg) {
    if (msg.localStatus !== 2) return
    messages.value = messages.value.filter((item) => String(item.msgId) !== String(msg.msgId))
    await removeMessageFromLocal(msg.msgId)
  }

  async function handleRevoke(msg) {
    const res = await revokeMsg(msg.msgId || msg.id)
    if (res.code === 200) {
      markMessageRevoked(msg.msgId || msg.id)
      await markRevokedLocal(msg.msgId || msg.id)
      await loadMessages()
      showToast('消息已撤回')
    } else {
      showToast(res.message || '撤回失败', 'error')
    }
  }

  function handleReport(msg) {
    const invalidReason = getReportValidationMessage(msg)
    if (invalidReason) {
      showToast(invalidReason, 'error')
      return
    }
    reportModal.value = { visible: true, msg, reason: 3, content: '' }
  }

  function handleEdit(msg) {
    editModal.value = { visible: true, msg, content: msg.msgContent || '' }
  }

  async function submitEdit() {
    const { msg, content } = editModal.value
    if (!msg || !content?.trim()) return
    const res = await editMsg({
      msgId: msg.msgId,
      newContent: content.trim(),
    }).catch(() => ({ code: 400, message: '编辑失败' }))

    if (res.code === 200) {
      upsertMessage({ ...msg, msgContent: content.trim() })
      editModal.value.visible = false
      showToast('消息已编辑')
    } else {
      showToast(res.message || '编辑失败', 'error')
    }
  }

  async function submitReport() {
    const { msg, reason, content } = reportModal.value
    if (!msg || !reason) return
    const invalidReason = getReportValidationMessage(msg)
    if (invalidReason) {
      showToast(invalidReason, 'error')
      return
    }
    const res = await reportMessage({
      messageId: msg.msgId,
      reportReason: reason,
      reportContent: content,
    }).catch((error) => ({
      code: 400,
      message: resolveRequestErrorMessage(error, '举报失败'),
    }))

    if (res.code === 200) {
      reportModal.value.visible = false
      showToast('举报已提交')
    } else {
      showToast(res.message || '举报失败', 'error')
    }
  }

  function openUserReportModal() {
    if (!friendId) return
    userReportModal.value = { visible: true, reason: 1, content: '' }
  }

  async function submitUserReport() {
    if (!friendId || !userReportModal.value.reason) return
    const res = await reportUser({
      reportedUserId: friendId,
      reportReason: userReportModal.value.reason,
      reportContent: userReportModal.value.content,
    }).catch(() => ({ code: 400, message: '举报失败' }))

    if (res.code === 200) {
      userReportModal.value.visible = false
      showToast('用户举报已提交')
    } else {
      showToast(res.message || '举报失败', 'error')
    }
  }

  function handleCollect(msg) {
    collectModal.value = { visible: true, msg, note: '' }
  }

  async function submitCollect() {
    const { msg, note } = collectModal.value
    if (!msg) return
    const res = await collectMsg({ messageId: msg.msgId, collectionNote: note })
    if (res.code === 200) {
      collectModal.value.visible = false
      showToast('收藏成功')
      if (activePanel.value === 'collections') {
        await refreshCollections()
      }
    } else {
      showToast(res.message || '收藏失败', 'error')
    }
  }

  async function handlePin(msg) {
    const res = await pinMsg({ conversationId: convId, messageId: msg.msgId })
    if (res.code === 200) {
      showToast('置顶成功')
      if (activePanel.value === 'pins') {
        await refreshPins()
      }
    } else {
      showToast(res.message || '置顶失败', 'error')
    }
  }

  async function handleUnpin(pinId) {
    const res = await unpinMsg(pinId)
    if (res.code === 200) {
      showToast('已取消置顶')
      await refreshPins()
    } else {
      showToast(res.message || '取消置顶失败', 'error')
    }
  }

  async function handleCancelCollect(collectionId) {
    const res = await cancelCollect(collectionId)
    if (res.code === 200) {
      showToast('已取消收藏')
      await refreshCollections()
    } else {
      showToast(res.message || '取消收藏失败', 'error')
    }
  }

  async function clearAllFailedMessages() {
    messages.value = messages.value.filter((item) => item.localStatus !== 2)
    await clearFailedMessages(convId)
    showToast('已清理失败消息')
  }

  async function handleRevokeSafe(msg) {
    await runAction('revoke', () => handleRevoke(msg))
  }

  async function submitEditSafe() {
    await runAction('edit', submitEdit)
  }

  async function submitReportSafe() {
    await runAction('report', submitReport)
  }

  async function submitUserReportSafe() {
    await runAction('userReport', submitUserReport)
  }

  async function submitCollectSafe() {
    await runAction('collect', submitCollect)
  }

  async function handlePinSafe(msg) {
    await runAction('pin', () => handlePin(msg))
  }

  async function handleUnpinSafe(pinId) {
    await runAction('unpin', () => handleUnpin(pinId))
  }

  async function handleCancelCollectSafe(collectionId) {
    await runAction('cancelCollect', () => handleCancelCollect(collectionId))
  }

  async function markConversationRead() {
    if (!convId || !messages.value.length) return
    const last = messages.value[messages.value.length - 1]
    const lastMsgId = last?.msgId || last?.id
    if (!lastMsgId) return
    await readMsgs({ conversationId: convId, msgIds: [lastMsgId] }).catch(() => {})
    chatStore.clearUnread(convId)
  }

  function handleDeleteFriend() {
    if (!friendId) return
    confirmState.value = {
      visible: true,
      title: '删除好友',
      message: '确认删除该好友吗？删除后会移出当前会话列表，但聊天记录仍可能保留在历史记录中。',
      confirmText: '确认删除',
      action: 'delete-friend',
      loading: false,
    }
  }

  async function executeDeleteFriend() {
    if (!friendId) return
    const res = await deleteFriend(friendId).catch(() => ({ code: 400, message: '删除好友失败' }))
    if (res.code === 200) {
      await hideConversation(convId).catch(() => ({ code: 400 }))
      removeCurrentConversationFromStore()
      showToast('已删除好友')
      closeConfirm()
      setTimeout(() => {
        router.push({ path: '/friends', query: { section: 'friends' } })
      }, 300)
    } else {
      showToast(res.message || '删除好友失败', 'error')
    }
  }

  function handleBlacklistFriend() {
    if (!friendId) return
    if (isFriendBlacklisted.value) {
      showToast('该好友已在黑名单中', 'error')
      return
    }
    confirmState.value = {
      visible: true,
      title: '拉黑好友',
      message: '确认将该好友加入黑名单吗？加入后你们将无法继续正常消息互动。',
      confirmText: '确认拉黑',
      action: 'blacklist-friend',
      loading: false,
    }
  }

  async function executeBlacklistFriend() {
    if (!friendId) return
    const res = await addBlacklist(friendId).catch(() => ({ code: 400, message: '拉黑好友失败' }))
    if (res.code === 200) {
      await hideConversation(convId).catch(() => ({ code: 400 }))
      removeCurrentConversationFromStore()
      showToast('已拉黑该好友')
      myBlacklist.value = [...myBlacklist.value, { blackUserId: friendId }]
      closeConfirm()
      setTimeout(() => {
        router.push({ path: '/friends', query: { section: 'blacklist' } })
      }, 300)
    } else {
      showToast(res.message || '拉黑好友失败', 'error')
    }
  }

  function closeConfirm() {
    confirmState.value = {
      visible: false,
      title: '',
      message: '',
      confirmText: '确认',
      action: '',
      loading: false,
    }
  }

  async function submitConfirm() {
    if (confirmState.value.loading) return
    confirmState.value.loading = true
    try {
      if (confirmState.value.action === 'delete-friend') {
        await executeDeleteFriend()
        return
      }
      if (confirmState.value.action === 'blacklist-friend') {
        await executeBlacklistFriend()
      }
    } finally {
      confirmState.value.loading = false
    }
  }

  function handleRealtimeMessage(event) {
    if (event.detail?.conversationId !== convId) return

    lastRealtimeAt = Date.now()
    const incoming = event.detail?.data
    const appended = upsertMessage({
      msgId: incoming?.msgId,
      senderId: incoming?.senderId,
      msgType: incoming?.msgType,
      msgContent: incoming?.content,
      content: incoming?.content,
      fileUrl: incoming?.fileUrl,
      fileName: incoming?.fileName,
      fileSize: incoming?.fileSize,
      emojiId: incoming?.emojiId,
      createTime: incoming?.createTime,
      isRevoke: false,
    })

    lastMsgCount = messages.value.length
    refreshConversationSummary(appended)
    markConversationRead()
    if (activePanel.value === 'search' && searchKeyword.value.trim()) {
      const keyword = searchKeyword.value.trim().toLowerCase()
      if (String(appended?.msgContent || '').toLowerCase().includes(keyword)) {
        searchResults.value = normalizeMessages([...searchResults.value, appended])
      }
    }
    scrollToBottom()
  }

  function handleRealtimeRevoke(event) {
    lastRealtimeAt = Date.now()
    const changed = markMessageRevoked(event.detail?.msgId)
    if (!changed) return
    markRevokedLocal(event.detail?.msgId)
    if (activePanel.value === 'search' && searchKeyword.value.trim()) {
      searchResults.value = searchResults.value.map((item) =>
        String(item.msgId) === String(event.detail?.msgId) ? { ...item, isRevoke: true } : item,
      )
    }
  }

  async function handleConversationReclick(event) {
    if (event.detail?.id !== convId) return
    await loadMessages()
    await markConversationRead()
    await scrollToBottom(true)
  }

  async function handleWindowFocus() {
    await syncLatestMessages({ forceScroll: true })
    await loadFriendOnlineStatus()
  }

  async function handleVisibilityChange() {
    if (document.hidden) return
    await syncLatestMessages({ forceScroll: true })
    await loadFriendOnlineStatus()
  }

  function openUserProfile(payload) {
    const targetUserId = Number(payload?.senderId || 0)
    if (!targetUserId) return
    if (String(targetUserId) === String(authStore.userId)) {
      router.push('/profile')
      return
    }
    router.push(`/profile/${targetUserId}`)
  }

  onMounted(async () => {
    chatStore.setActiveConversation(convId)
    window.addEventListener('chat-ws-message', handleRealtimeMessage)
    window.addEventListener('chat-ws-revoke', handleRealtimeRevoke)
    window.addEventListener('chat-conversation-reclick', handleConversationReclick)
    window.addEventListener('focus', handleWindowFocus)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    await Promise.all([
      loadLocalMessages(),
      loadMessages(),
      loadFriend(),
      loadBlacklistState(),
      loadFriendOnlineStatus(),
    ])
    await markConversationRead()
    await scrollToBottom(true)

    pollTimer = setInterval(async () => {
      if (Date.now() - lastRealtimeAt < 6000) return
      await syncLatestMessages({ forceScroll: true })
      await loadFriendOnlineStatus()
    }, 2500)
  })

  onUnmounted(() => {
    chatStore.setActiveConversation('')
    window.removeEventListener('chat-ws-message', handleRealtimeMessage)
    window.removeEventListener('chat-ws-revoke', handleRealtimeRevoke)
    window.removeEventListener('chat-conversation-reclick', handleConversationReclick)
    window.removeEventListener('focus', handleWindowFocus)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    clearInterval(pollTimer)
  })

  return {
    MESSAGE_REPORT_REASON_OPTIONS,
    USER_REPORT_REASON_OPTIONS,
    actionSubmitting,
    activePanel,
    authStore,
    clearAllFailedMessages,
    closeConfirm,
    closePanel,
    collectModal,
    collections,
    confirmState,
    deleteFailedMessage,
    editModal,
    errorMsg,
    formatMessageDivider,
    friend,
    friendDisplayAvatar,
    friendDisplayName,
    friendOnlineStatus,
    friendStatusText,
    getPanelMessageText,
    handleBlacklistFriend,
    handleCancelCollectSafe,
    handleCollect,
    handleDeleteFriend,
    handleEdit,
    handlePinSafe,
    handleReport,
    handleRevokeSafe,
    isFriendBlacklisted,
    loadingPanel,
    messages,
    msgListRef,
    openPanel,
    openUserProfile,
    openUserReportModal,
    panelTitles,
    pins,
    privateInputDisabled,
    privateInputDisabledReason,
    renderPanelHighlightedText,
    reportModal,
    retrySend,
    runSearch,
    scrollToMessage,
    searchKeyword,
    searchResults,
    shouldShowTime,
    submitCollectSafe,
    submitConfirm,
    submitEditSafe,
    submitReportSafe,
    submitUserReportSafe,
    toast,
    userReportModal,
    handleSend,
  }
}
