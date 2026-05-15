import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import {
  cancelCollect,
  collectMsg,
  editMsg,
  getCollections,
  getGroupNotice,
  getPinList,
  getTeamHistory,
  pinMsg,
  readMsgs,
  revokeMsg,
  searchMsg,
  sendTeamMsg,
  setGroupNotice,
  unpinMsg,
} from '@/api/chat'
import {
  getTeamDetail,
  getTeamMembers,
  muteAll,
  muteMember,
  removeMember,
  unmuteMember,
} from '@/api/team'
import { reportMessage, reportTeam } from '@/api/report'
import {
  cleanupExpiredMessages,
  clearFailedMessages,
  deleteLocalMessage,
  getLocalMessages,
  markRevokedLocal,
  saveMessageToLocal,
  updateLocalStatus,
} from '@/utils/localDb'
import { formatConversationPreview } from '@/utils/chatPreview'
import { renderConversationPreviewHighlight } from '@/utils/chatText'
import { useActionSubmitting } from '@/composables/useActionSubmitting'
import { useChatMessageList } from '@/composables/useChatMessageList'
import { useChatPanelState } from '@/composables/useChatPanelState'
import { useToast } from '@/composables/useToast'
import { MESSAGE_REPORT_REASON_OPTIONS, TEAM_REPORT_REASON_OPTIONS } from '@/constants/report'

function normalizeMember(item) {
  return {
    ...item,
    userId: Number(item.userId ?? item.id ?? 0),
    userNickname: item.userNickname ?? item.nickname ?? item.userName ?? '',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    roleType: Number(item.roleType ?? item.teamRoleType ?? 0),
    teamMuteType: Number(item.teamMuteType ?? item.muteType ?? 0),
    teamMuteUnpunishTime: item.teamMuteUnpunishTime ?? item.muteUnpunishTime ?? null,
    isMuted: Number(item.teamMuteType ?? item.muteType ?? 0) === 1,
  }
}

function createEmptyMentionState() {
  return []
}

export function useTeamChatPage() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const chatStore = useChatStore()

  const teamId = Number(route.params.teamId || 0)
  const conversationId = `team_${teamId}`

  const team = ref({})
  const members = ref([])
  const messages = ref([])
  const msgListRef = ref(null)
  const messageInputRef = ref(null)
  const draftText = ref('')
  const groupNotice = ref('')
  const noticeDraft = ref('')
  const pins = ref([])
  const collections = ref([])
  const searchKeyword = ref('')
  const searchResults = ref([])
  const errorMsg = ref('')
  const myRole = ref(0)
  const mentionPanelVisible = ref(false)
  const mentionKeyword = ref('')
  const mentionState = ref(createEmptyMentionState())
  const memberActionDialog = ref({ visible: false, userId: null, userNickname: '', duration: 60 })
  const memberRemoveConfirm = ref({ visible: false, userId: null, userNickname: '', loading: false })
  const allMuteDialog = ref({ visible: false, duration: 60 })
  const reportModal = ref({ visible: false, msg: null, reason: 3, content: '' })
  const teamReportModal = ref({ visible: false, reason: 1, content: '' })
  const collectModal = ref({ visible: false, msg: null, note: '' })
  const editModal = ref({ visible: false, msg: null, content: '' })

  const muteDurationOptions = [
    { label: '10分钟', value: 10 },
    { label: '30分钟', value: 30 },
    { label: '1小时', value: 60 },
    { label: '3小时', value: 180 },
    { label: '12小时', value: 720 },
    { label: '24小时', value: 1440 },
  ]

  const { toast, showToast } = useToast()
  const { actionSubmitting, runAction } = useActionSubmitting({
    send: false,
    revoke: false,
    report: false,
    teamReport: false,
    collect: false,
    cancelCollect: false,
    pin: false,
    unpin: false,
    edit: false,
    saveNotice: false,
    memberMute: false,
    memberUnmute: false,
    memberRemove: false,
    muteAll: false,
  })

  const { activePanel, closePanel, loadingPanel, openPanel } = useChatPanelState({
    async notice() {
      await loadGroupNotice()
    },
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

  const {
    findMatchingMessageIndex,
    formatMessageDivider,
    markMessageRevoked,
    mergeMessages,
    normalizeMessages,
    shouldShowTime,
    upsertMessage,
  } = useChatMessageList(messages, authStore.userId)

  const panelTitles = {
    notice: '群公告',
    search: '消息搜索',
    pins: '置顶消息',
    collections: '我的收藏',
  }

  const memberMap = computed(() => {
    const map = new Map()
    members.value.forEach((item) => {
      map.set(Number(item.userId), item)
    })
    return map
  })

  const currentMember = computed(() => memberMap.value.get(Number(authStore.userId || 0)) || null)

  const canManageMuteAll = computed(() => myRole.value > 0 && myRole.value <= 2)

  const teamDisplayName = computed(() => team.value.teamName || '团队会话')

  const teamMemberCountText = computed(() => {
    const count = Number(team.value.teamMemberCount ?? members.value.length ?? 0)
    return `${count} 名成员`
  })

  const muteState = computed(() => {
    if (Number(team.value.teamAllMute || 0) === 1 && myRole.value >= 3) {
      return { disabled: true, reason: '当前团队已开启全员禁言' }
    }
    const member = currentMember.value
    const muteType = Number(member?.teamMuteType ?? 0)
    const muteExpireTime = member?.teamMuteUnpunishTime ? new Date(member.teamMuteUnpunishTime).getTime() : 0
    if (muteType === 1 && (!muteExpireTime || muteExpireTime > Date.now())) {
      return { disabled: true, reason: '您已被禁言' }
    }
    return { disabled: false, reason: '' }
  })

  const canAtAll = computed(() => myRole.value > 0 && myRole.value <= 2)

  const mentionCandidates = computed(() => {
    const keyword = mentionKeyword.value.trim().toLowerCase()
    const list = []

    if (canAtAll.value) {
      const allItem = {
        key: 'all',
        label: '全体成员',
        token: '@全体成员',
        userIds: members.value
          .map((item) => Number(item.userId))
          .filter((id) => id && id !== Number(authStore.userId || 0)),
      }
      if (!keyword || allItem.label.toLowerCase().includes(keyword)) {
        list.push(allItem)
      }
    }

    members.value
      .filter((item) => Number(item.userId) !== Number(authStore.userId || 0))
      .forEach((item) => {
        const label = item.userNickname || `用户${item.userId}`
        if (!keyword || label.toLowerCase().includes(keyword)) {
          list.push({
            key: `user-${item.userId}`,
            label,
            token: `@${label}`,
            userIds: [Number(item.userId)],
          })
        }
      })

    return list
  })

  function getPanelMessageText(item) {
    return formatConversationPreview(item?.msgType, item?.msgContent)
  }

  function renderPanelHighlightedText(item, keyword = '') {
    return renderConversationPreviewHighlight(item, keyword)
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

  function syncMentionState() {
    mentionState.value = mentionState.value.filter((item) => draftText.value.includes(item.token))
  }

  watch(draftText, () => {
    syncMentionState()
  })

  function setMessageInputRef(el) {
    messageInputRef.value = el
  }

  function closeMentionPanel() {
    mentionPanelVisible.value = false
    mentionKeyword.value = ''
  }

  function toggleMentionPanel() {
    if (muteState.value.disabled || actionSubmitting.value.send) return
    mentionPanelVisible.value = !mentionPanelVisible.value
    if (!mentionPanelVisible.value) {
      mentionKeyword.value = ''
    }
  }

  async function selectMentionTarget(target) {
    if (!target) return
    const exists = mentionState.value.some((item) => item.key === target.key)
    if (!exists) {
      mentionState.value = [...mentionState.value, target]
    }
    closeMentionPanel()
    await messageInputRef.value?.insertText(target.token, true)
  }

  async function mentionMember(userId) {
    const target = mentionCandidates.value.find((item) =>
      item.key !== 'all'
      && item.userIds.length === 1
      && Number(item.userIds[0]) === Number(userId),
    )
    if (!target) return
    await selectMentionTarget(target)
  }

  function getActiveAtUsers(text) {
    return Array.from(new Set(
      mentionState.value
        .filter((item) => text.includes(item.token))
        .flatMap((item) => item.userIds)
        .filter((id) => Number(id) && Number(id) !== Number(authStore.userId || 0)),
    ))
  }

  function canOperateMember(targetUserId) {
    const target = memberMap.value.get(Number(targetUserId))
    if (!target) return false
    if (Number(target.userId) === Number(authStore.userId || 0)) return false
    if (target.roleType === 1) return false
    if (myRole.value === 1) return true
    if (myRole.value === 2) return target.roleType >= 3
    return false
  }

  function getMemberActionItems(msg) {
    const senderId = Number(msg?.senderId || 0)
    if (!senderId || senderId === Number(authStore.userId || 0)) {
      return []
    }

    const items = [{ key: 'mention', label: '@该用户' }]
    if (!canOperateMember(senderId)) {
      return items
    }

    const member = memberMap.value.get(senderId)
    if (member?.isMuted || Number(member?.teamMuteType || 0) === 1) {
      items.push({ key: 'unmute', label: '解除禁言' })
    } else {
      items.push({ key: 'mute', label: '禁言成员' })
    }
    items.push({ key: 'remove', label: '移出团队', danger: true })
    return items
  }

  function getManageMemberBlockReason() {
    return ''
  }

  function getMessageSenderNickname(msg) {
    const senderId = Number(msg?.senderId || 0)
    if (senderId === Number(authStore.userId || 0)) {
      return authStore.userNickname || '我'
    }
    return memberMap.value.get(senderId)?.userNickname || `用户${senderId}`
  }

  function getMessageSenderAvatar(msg) {
    const senderId = Number(msg?.senderId || 0)
    if (senderId === Number(authStore.userId || 0)) {
      return authStore.userAvatar || ''
    }
    return memberMap.value.get(senderId)?.userAvatar || ''
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
    if (messages.value.some((item) => String(item.msgId) === String(msgId))) {
      return true
    }
    await loadMessages()
    return messages.value.some((item) => String(item.msgId) === String(msgId))
  }

  async function scrollToMessage(msgId) {
    const exists = await ensureMessageLoaded(msgId)
    if (!exists) {
      showToast('未找到对应消息', 'error')
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
    await cleanupExpiredMessages().catch(() => {})
    const list = await getLocalMessages(conversationId, 120).catch(() => [])
    if (list?.length) {
      messages.value = mergeMessages(messages.value, list)
    }
  }

  async function saveLocalMessage(message) {
    await saveMessageToLocal({ ...message, conversationId }).catch(() => {})
  }

  async function removeLocalMessage(msgId) {
    await deleteLocalMessage(msgId).catch(() => {})
  }

  async function loadTeamDetail() {
    const res = await getTeamDetail(teamId).catch(() => null)
    if (res?.code !== 200) return
    const detail = res.data || {}
    team.value = {
      ...team.value,
      ...detail,
      teamAllMute: Number(detail.teamAllMute ?? detail.muteAll ?? team.value.teamAllMute ?? 0),
    }
    myRole.value = Number(detail.myRoleType ?? detail.currentUserRole ?? detail.roleType ?? 0)
  }

  async function loadMembers() {
    const res = await getTeamMembers(teamId, { page: 1, pageSize: 300 }).catch(() => null)
    if (res?.code !== 200) return
    members.value = (res.data?.records || res.data || []).map(normalizeMember)
    team.value.teamMemberCount = Number(team.value.teamMemberCount || members.value.length)
  }

  async function loadMessages() {
    const res = await getTeamHistory({ teamId, page: 1, pageSize: 100 }).catch(() => null)
    if (res?.code !== 200) return
    const list = normalizeMessages(res.data?.records || res.data || [])
    messages.value = mergeMessages(list, messages.value, { keepOnlyUnsyncedLocal: true })
  }

  async function syncLatestMessages({ forceScroll = false } = {}) {
    await loadMessages()
    await markConversationRead()
    if (forceScroll) {
      scrollToBottom()
    }
  }

  async function loadGroupNotice() {
    const res = await getGroupNotice({ conversationId }).catch(() => null)
    if (res?.code !== 200) return
    groupNotice.value = String(res.data || '')
    noticeDraft.value = groupNotice.value
  }

  async function refreshPins() {
    const res = await getPinList({ conversationId }).catch(() => null)
    pins.value = res?.code === 200 ? (res.data || []) : []
  }

  async function refreshCollections() {
    const res = await getCollections({ page: 1, pageSize: 100 }).catch(() => null)
    collections.value = res?.code === 200 ? (res.data || []) : []
  }

  async function runSearch() {
    const keyword = searchKeyword.value.trim()
    if (!keyword) {
      searchResults.value = []
      return
    }
    const res = await searchMsg({
      conversationId,
      keyword,
      page: 1,
      pageSize: 50,
    }).catch(() => null)
    if (res?.code === 200) {
      searchResults.value = normalizeMessages(res.data?.records || res.data || [])
      return
    }
    showToast(res?.message || '搜索失败', 'error')
  }

  async function markConversationRead() {
    const last = messages.value[messages.value.length - 1]
    const lastMsgId = last?.msgId || last?.id
    if (!lastMsgId) return
    await readMsgs({ conversationId, msgIds: [lastMsgId] }).catch(() => {})
    chatStore.clearUnread(conversationId)
  }

  function refreshConversationSummary(message) {
    const preview = formatConversationPreview(message?.msgType, message?.msgContent)
    chatStore.refreshConversationItem({
      id: conversationId,
      conversationId,
      type: 'team',
      targetId: teamId,
      name: team.value.teamName || '团队会话',
      avatar: team.value.teamAvatar || '',
      lastMsg: preview || '',
      lastMessage: preview || '',
      time: message?.createTime || new Date().toISOString(),
      lastTime: message?.createTime || new Date().toISOString(),
      unreadCount: chatStore.unreadMap[conversationId] || 0,
    })
  }

  async function handleSend(payload) {
    if (actionSubmitting.value.send) return
    if (muteState.value.disabled) {
      errorMsg.value = muteState.value.reason
      setTimeout(() => {
        errorMsg.value = ''
      }, 2500)
      return
    }

    const content = String(payload.msgContent || '').trim()
    const atUsers = payload.msgType === 1 ? getActiveAtUsers(content) : []
    const msgType = payload.msgType === 1 && atUsers.length ? 5 : payload.msgType
    const tempId = `temp_${Date.now()}`
    const optimisticMessage = {
      msgId: tempId,
      senderId: Number(authStore.userId || 0),
      msgType,
      msgContent: payload.msgContent,
      emojiId: payload.emojiId,
      fileUrl: payload.fileUrl,
      fileName: payload.fileName,
      fileSize: payload.fileSize,
      mediaType: payload.mediaType,
      createTime: new Date().toISOString(),
      isSelf: true,
      localStatus: 0,
      isRevoke: false,
    }

    mentionState.value = createEmptyMentionState()

    await runAction('send', async () => {
      upsertMessage(optimisticMessage)
      await saveLocalMessage(optimisticMessage)
      refreshConversationSummary(optimisticMessage)
      scrollToBottom()

      const requestBody = {
        teamId,
        msgType,
        msgContent: payload.msgType === 1 ? content : payload.msgContent,
        emojiId: payload.emojiId,
        mediaType: payload.mediaType,
        fileUrl: payload.fileUrl,
        fileName: payload.fileName,
        fileSize: payload.fileSize,
      }
      if (atUsers.length) {
        requestBody.atUsers = atUsers
      }

      const res = await sendTeamMsg(requestBody).catch((error) => ({
        code: 500,
        message: error?.response?.message || error?.message || '发送失败',
      }))

      if (res.code !== 200) {
        messages.value = messages.value.filter((item) => String(item.msgId) !== String(tempId))
        await removeLocalMessage(tempId)
        errorMsg.value = res.message || '发送失败'
        setTimeout(() => {
          errorMsg.value = ''
        }, 3000)
        return
      }

      const serverMsg = typeof res.data === 'object' && res.data !== null
        ? res.data
        : { msgId: res.data }
      const savedMsg = {
        ...serverMsg,
        msgId: serverMsg.msgId ?? serverMsg.id ?? tempId,
        senderId: serverMsg.senderId ?? Number(authStore.userId || 0),
        msgType: serverMsg.msgType ?? msgType,
        msgContent: serverMsg.msgContent ?? payload.msgContent,
        emojiId: serverMsg.emojiId ?? payload.emojiId,
        fileUrl: serverMsg.fileUrl ?? payload.fileUrl,
        fileName: serverMsg.fileName ?? payload.fileName,
        fileSize: serverMsg.fileSize ?? payload.fileSize,
        mediaType: serverMsg.mediaType ?? payload.mediaType,
        createTime: serverMsg.createTime ?? new Date().toISOString(),
        isSelf: true,
        localStatus: 1,
        isRevoke: false,
      }

      if (String(savedMsg.msgId) !== String(tempId)) {
        messages.value = messages.value.filter((item) => String(item.msgId) !== String(tempId))
        await removeLocalMessage(tempId)
      } else {
        await updateLocalStatus(tempId, 1).catch(() => {})
      }
      upsertMessage(savedMsg)
      await saveLocalMessage(savedMsg)

      if (!serverMsg.msgId && !serverMsg.id) {
        await loadMessages()
      }
      await markConversationRead()
      refreshConversationSummary(savedMsg)
      scrollToBottom()
    })
  }

  async function retrySend(msg) {
    if (msg?.localStatus !== 2) return
    await handleSend({
      msgType: msg.msgType,
      msgContent: msg.msgContent,
      emojiId: msg.emojiId,
      fileUrl: msg.fileUrl,
      fileName: msg.fileName,
      fileSize: msg.fileSize,
      mediaType: msg.mediaType,
    })
  }

  async function deleteFailedMessage(msg) {
    if (msg?.localStatus !== 2) return
    messages.value = messages.value.filter((item) => String(item.msgId) !== String(msg.msgId))
    await removeLocalMessage(msg.msgId)
  }

  async function handleRevoke(msg) {
    await runAction('revoke', async () => {
      const res = await revokeMsg(msg.msgId || msg.id)
      if (res.code !== 200) {
        showToast(res.message || '撤回失败', 'error')
        return
      }
      await loadMessages()
      showToast('消息已撤回')
    })
  }

  function handleReport(msg) {
    const invalidReason = getReportValidationMessage(msg)
    if (invalidReason) {
      showToast(invalidReason, 'error')
      return
    }
    reportModal.value = { visible: true, msg, reason: 3, content: '' }
  }

  async function submitReport() {
    await runAction('report', async () => {
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
      }).catch((error) => ({ code: 500, message: resolveRequestErrorMessage(error, '举报失败') }))
      if (res.code === 200) {
        reportModal.value.visible = false
        showToast('举报已提交')
        return
      }
      showToast(res.message || '举报失败', 'error')
    })
  }

  function openTeamReportModal() {
    teamReportModal.value = { visible: true, reason: 1, content: '' }
  }

  async function submitTeamReport() {
    await runAction('teamReport', async () => {
      const { reason, content } = teamReportModal.value
      const res = await reportTeam({
        reportedTeamId: teamId,
        reportReason: reason,
        reportContent: content,
      }).catch(() => ({ code: 500, message: '举报失败' }))
      if (res.code === 200) {
        teamReportModal.value.visible = false
        showToast('团队举报已提交')
        return
      }
      showToast(res.message || '举报失败', 'error')
    })
  }

  function handleCollect(msg) {
    collectModal.value = { visible: true, msg, note: '' }
  }

  async function submitCollect() {
    await runAction('collect', async () => {
      const { msg, note } = collectModal.value
      if (!msg) return
      const res = await collectMsg({ messageId: msg.msgId, collectionNote: note })
      if (res.code === 200) {
        collectModal.value.visible = false
        showToast('收藏成功')
        if (activePanel.value === 'collections') {
          await refreshCollections()
        }
        return
      }
      showToast(res.message || '收藏失败', 'error')
    })
  }

  async function handleCancelCollect(collectionId) {
    await runAction('cancelCollect', async () => {
      const res = await cancelCollect(collectionId)
      if (res.code === 200) {
        showToast('已取消收藏')
        await refreshCollections()
        return
      }
      showToast(res.message || '取消收藏失败', 'error')
    })
  }

  async function handlePin(msg) {
    await runAction('pin', async () => {
      const res = await pinMsg({ conversationId, messageId: msg.msgId })
      if (res.code === 200) {
        showToast('置顶成功')
        if (activePanel.value === 'pins') {
          await refreshPins()
        }
        return
      }
      showToast(res.message || '置顶失败', 'error')
    })
  }

  async function handleUnpin(pinId) {
    await runAction('unpin', async () => {
      const res = await unpinMsg(pinId)
      if (res.code === 200) {
        showToast('已取消置顶')
        await refreshPins()
        return
      }
      showToast(res.message || '取消置顶失败', 'error')
    })
  }

  function handleEdit(msg) {
    editModal.value = { visible: true, msg, content: msg.msgContent || '' }
  }

  async function submitEdit() {
    await runAction('edit', async () => {
      const { msg, content } = editModal.value
      if (!msg || !String(content || '').trim()) return
      const res = await editMsg({
        msgId: msg.msgId,
        newContent: String(content).trim(),
      }).catch(() => ({ code: 500, message: '编辑失败' }))
      if (res.code === 200) {
        editModal.value.visible = false
        await loadMessages()
        showToast('消息已编辑')
        return
      }
      showToast(res.message || '编辑失败', 'error')
    })
  }

  function openMuteMemberDialog(userId) {
    const member = memberMap.value.get(Number(userId))
    if (!member) return
    memberActionDialog.value = {
      visible: true,
      userId: Number(userId),
      userNickname: member.userNickname || '',
      duration: 60,
    }
  }

  async function submitMuteMemberDialog() {
    await runAction('memberMute', async () => {
      const { userId, duration } = memberActionDialog.value
      if (!userId) return
      const res = await muteMember({ teamId, userId, muteDuration: duration }).catch(() => ({
        code: 500,
        message: '禁言失败',
      }))
      if (res.code === 200) {
        memberActionDialog.value.visible = false
        showToast(`已禁言 ${duration} 分钟`)
        await loadMembers()
        return
      }
      showToast(res.message || '禁言失败', 'error')
    })
  }

  async function unmuteTarget(userId) {
    await runAction('memberUnmute', async () => {
      const res = await unmuteMember({ teamId, userId }).catch(() => ({
        code: 500,
        message: '解除禁言失败',
      }))
      if (res.code === 200) {
        showToast('已解除禁言')
        await loadMembers()
        return
      }
      showToast(res.message || '解除禁言失败', 'error')
    })
  }

  async function confirmRemoveMember() {
    if (memberRemoveConfirm.value.loading || !memberRemoveConfirm.value.userId) return
    memberRemoveConfirm.value.loading = true
    try {
      const res = await removeMember({ teamId, userId: memberRemoveConfirm.value.userId }).catch(() => ({
        code: 500,
        message: '移出团队失败',
      }))
      if (res.code === 200) {
        showToast('成员已移出团队')
        memberRemoveConfirm.value = { visible: false, userId: null, userNickname: '', loading: false }
        await loadMembers()
        return
      }
      showToast(res.message || '移出团队失败', 'error')
    } finally {
      memberRemoveConfirm.value.loading = false
    }
  }

  function handleMemberAction({ action, msg }) {
    const senderId = Number(msg?.senderId || 0)
    if (!senderId) return
    if (action === 'mention') {
      mentionMember(senderId)
      return
    }
    if (!canOperateMember(senderId)) {
      showToast('没有权限', 'error')
      return
    }
    if (action === 'mute') {
      openMuteMemberDialog(senderId)
      return
    }
    if (action === 'unmute') {
      unmuteTarget(senderId)
      return
    }
    if (action === 'remove') {
      const member = memberMap.value.get(senderId)
      memberRemoveConfirm.value = {
        visible: true,
        userId: senderId,
        userNickname: member?.userNickname || '',
        loading: false,
      }
    }
  }

  function openAllMuteDialog() {
    if (!canManageMuteAll.value) return
    if (Number(team.value.teamAllMute || 0) === 1) {
      submitAllMuteDialog(false)
      return
    }
    allMuteDialog.value.visible = true
  }

  async function submitAllMuteDialog(forceMute) {
    await runAction('muteAll', async () => {
      const isMute = typeof forceMute === 'boolean' ? forceMute : true
      const res = await muteAll({
        teamId,
        isMute,
        muteDuration: allMuteDialog.value.duration,
      }).catch(() => ({ code: 500, message: '操作失败' }))
      if (res.code === 200) {
        allMuteDialog.value.visible = false
        team.value.teamAllMute = isMute ? 1 : 0
        showToast(isMute ? '已开启全员禁言' : '已解除全员禁言')
        await loadTeamDetail()
        return
      }
      showToast(res.message || '操作失败', 'error')
    })
  }

  async function saveNotice() {
    await runAction('saveNotice', async () => {
      const res = await setGroupNotice({
        conversationId,
        notice: String(noticeDraft.value || '').trim(),
      }).catch(() => ({ code: 500, message: '保存失败' }))
      if (res.code === 200) {
        groupNotice.value = String(noticeDraft.value || '').trim()
        showToast('群公告已保存')
        return
      }
      showToast(res.message || '保存失败', 'error')
    })
  }

  async function clearAllFailedMessages() {
    messages.value = messages.value.filter((item) => item.localStatus !== 2)
    await clearFailedMessages(conversationId).catch(() => {})
    showToast('已清理失败消息')
  }

  function openTeamMembers() {
    router.push(`/teams/${teamId}/members`)
  }

  function openTeamDetail() {
    router.push(`/teams/${teamId}`)
  }

  function openUserProfile(payload) {
    const targetUserId = Number(payload?.senderId || 0)
    if (!targetUserId) return
    if (targetUserId === Number(authStore.userId || 0)) {
      router.push('/profile')
      return
    }
    router.push(`/profile/${targetUserId}`)
  }

  function handleRealtimeMessage(event) {
    if (event.detail?.conversationId !== conversationId) return
    lastRealtimeAt = Date.now()
    const incoming = event.detail?.data || {}
    const appended = upsertMessage({
      msgId: incoming.msgId,
      senderId: incoming.senderId,
      msgType: incoming.msgType,
      msgContent: incoming.content,
      createTime: incoming.createTime,
      isRevoke: false,
      localStatus: 1,
    })
    refreshConversationSummary(appended)
    saveLocalMessage(appended)
    markConversationRead()
    scrollToBottom()
  }

  function handleRealtimeRevoke(event) {
    lastRealtimeAt = Date.now()
    if (!markMessageRevoked(event.detail?.msgId)) return
    markRevokedLocal(event.detail?.msgId)
  }

  function handleGroupNoticeUpdate() {
    loadGroupNotice()
  }

  function handleConversationReclick(event) {
    if (event.detail?.id !== conversationId) return
    loadMessages().then(() => {
      markConversationRead()
      scrollToBottom(true)
    })
  }

  function handleOuterClick(event) {
    if (!mentionPanelVisible.value) return
    const target = event.target
    if (target?.closest?.('.mention-tool-wrap')) return
    closeMentionPanel()
  }

  let pollTimer = null
  let lastRealtimeAt = 0

  onMounted(async () => {
    chatStore.setActiveConversation(conversationId)
    window.addEventListener('chat-ws-message', handleRealtimeMessage)
    window.addEventListener('chat-ws-revoke', handleRealtimeRevoke)
    window.addEventListener('group-notice-update', handleGroupNoticeUpdate)
    window.addEventListener('chat-conversation-reclick', handleConversationReclick)
    window.addEventListener('click', handleOuterClick)

    await Promise.all([
      loadLocalMessages(),
      loadTeamDetail(),
      loadMembers(),
      loadMessages(),
      loadGroupNotice(),
    ])
    await markConversationRead()
    await scrollToBottom(true)

    pollTimer = setInterval(async () => {
      if (Date.now() - lastRealtimeAt < 15000) return
      await Promise.all([loadMessages(), loadTeamDetail(), loadMembers()])
    }, 8000)
  })

  onUnmounted(() => {
    chatStore.setActiveConversation('')
    window.removeEventListener('chat-ws-message', handleRealtimeMessage)
    window.removeEventListener('chat-ws-revoke', handleRealtimeRevoke)
    window.removeEventListener('group-notice-update', handleGroupNoticeUpdate)
    window.removeEventListener('chat-conversation-reclick', handleConversationReclick)
    window.removeEventListener('click', handleOuterClick)
    clearInterval(pollTimer)
  })

  watch(messages, () => {
    const last = messages.value[messages.value.length - 1]
    if (last) {
      refreshConversationSummary(last)
    }
  }, { deep: true })

  return {
    MESSAGE_REPORT_REASON_OPTIONS,
    TEAM_REPORT_REASON_OPTIONS,
    actionSubmitting,
    activePanel,
    allMuteDialog,
    authStore,
    canAtAll,
    canManageMuteAll,
    clearAllFailedMessages,
    closeMentionPanel,
    closePanel,
    collectModal,
    collections,
    confirmRemoveMember,
    deleteFailedMessage,
    draftText,
    editModal,
    errorMsg,
    formatMessageDivider,
    getManageMemberBlockReason,
    getMemberActionItems,
    getMessageSenderAvatar,
    getMessageSenderNickname,
    getPanelMessageText,
    groupNotice,
    handleCancelCollect,
    handleCollect,
    handleEdit,
    handleMemberAction,
    handlePin,
    handleReport,
    handleRevoke,
    handleSend,
    handleUnpin,
    loadingPanel,
    memberActionDialog,
    memberRemoveConfirm,
    mentionCandidates,
    mentionKeyword,
    mentionPanelVisible,
    messageInputRef,
    messages,
    msgListRef,
    muteDurationOptions,
    muteState,
    myRole,
    noticeDraft,
    openAllMuteDialog,
    openPanel,
    openTeamDetail,
    openTeamMembers,
    openTeamReportModal,
    openUserProfile,
    panelTitles,
    pins,
    renderPanelHighlightedText,
    reportModal,
    retrySend,
    runSearch,
    saveNotice,
    scrollToMessage,
    searchKeyword,
    searchResults,
    selectMentionTarget,
    setMessageInputRef,
    shouldShowTime,
    submitAllMuteDialog,
    submitCollect,
    submitEdit,
    submitMuteMemberDialog,
    submitReport,
    submitTeamReport,
    team,
    teamDisplayName,
    teamMemberCountText,
    teamReportModal,
    toast,
    toggleMentionPanel,
  }
}
