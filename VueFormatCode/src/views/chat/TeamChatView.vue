<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import {
  sendTeamMsg,
  getTeamHistory,
  revokeMsg,
  readMsgs,
  collectMsg,
  cancelCollect,
  getCollections,
  pinMsg,
  unpinMsg,
  getPinList,
  searchMsg,
  getGroupNotice,
  setGroupNotice,
  editMsg,
} from '@/api/chat'
import { reportMessage } from '@/api/report'
import { getTeamDetail, getTeamMembers } from '@/api/team'
import { getLocalMessages, saveMessageToLocal, deleteLocalMessage, updateLocalStatus, markRevokedLocal, cleanupExpiredMessages, clearFailedMessages } from '@/utils/localDb'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import { More, Document, Search, Top, Star, Delete, Close, UserFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const teamId = route.params.teamId ? Number(route.params.teamId) : undefined
const convId = teamId ? `team_${teamId}` : ''

const messages = ref([])
const team = ref({})
const members = ref([])
const pins = ref([])
const collections = ref([])
const searchKeyword = ref('')
const searchResults = ref([])
const groupNotice = ref('')
const noticeDraft = ref('')
const activePanel = ref('')
const reportModal = ref({ visible: false, msg: null, reason: 3, content: '' })
const collectModal = ref({ visible: false, msg: null, note: '' })
const editModal = ref({ visible: false, msg: null, content: '' })
const myRole = ref(0)
const isMuteAll = ref(false)
const msgListRef = ref(null)
const sending = ref(false)
const loadingPanel = ref(false)
const errorMsg = ref('')
const toast = ref({ msg: '', type: 'success' })
let pollTimer = null
let lastMsgCount = 0
let lastRealtimeAt = 0

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

function escapeHtml(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function renderHighlightedText(text, keyword = '') {
  const safe = escapeHtml(text || '')
  const kw = keyword.trim()
  if (!kw) return safe
  const escapedKw = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(`(${escapedKw})`, 'gi'), '<mark>$1</mark>')
}

async function scrollToBottom(force = false) {
  await nextTick()
  const run = () => {
    if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight
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
  if (messages.value.some(item => String(item.msgId) === String(msgId))) return true
  try {
    const res = await getTeamHistory({ teamId, page: 1, pageSize: 200 })
    if (res.code === 200) {
      const list = normalizeMessages(res.data?.records || res.data || [])
      messages.value = list
      lastMsgCount = list.length
      return list.some(item => String(item.msgId) === String(msgId))
    }
  } catch { /* ignore */ }
  return false
}

async function scrollToMessage(msgId) {
  const exists = await ensureMessageLoaded(msgId)
  if (!exists) {
    showToast('目标消息未在当前历史范围内', 'error')
    return
  }
  await nextTick()
  const el = document.getElementById(`msg-${msgId}`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('msg-highlight')
  setTimeout(() => el.classList.remove('msg-highlight'), 1800)
}

function normalizeMessages(rawList) {
  return (rawList || []).map((item) => ({
    ...item,
    msgId: item.msgId ?? item.id,
    msgContent: item.msgContent ?? item.content,
    senderId: item.senderId ?? item.sender_id,
    isSelf: item.isSelf ?? ((item.senderId ?? item.sender_id) === authStore.userId),
  })).slice().sort((a, b) => {
    if (a.createTime < b.createTime) return -1
    if (a.createTime > b.createTime) return 1
    return (a.msgId || 0) - (b.msgId || 0)
  })
}

function mergeMessages(serverList = [], localList = []) {
  const map = new Map()
  normalizeMessages(serverList).forEach(item => map.set(String(item.msgId), item))
  normalizeMessages(localList).forEach(item => {
    const key = String(item.msgId)
    if (!map.has(key)) map.set(key, item)
    else map.set(key, { ...item, ...map.get(key) })
  })
  return normalizeMessages(Array.from(map.values()))
}

async function loadLocalMessages() {
  try {
    await cleanupExpiredMessages()
    const list = await getLocalMessages(convId, 100)
    if (list?.length) {
      messages.value = mergeMessages(messages.value, list)
      lastMsgCount = messages.value.length
    }
  } catch { /* ignore */ }
}

async function syncMessageToLocal(message) {
  await saveMessageToLocal({ ...message, conversationId: convId }).catch(() => { })
}

async function removeMessageFromLocal(msgId) {
  await deleteLocalMessage(msgId).catch(() => { })
}

function upsertMessage(message) {
  const normalized = normalizeMessages([message])[0]
  if (!normalized) return null
  const idx = messages.value.findIndex(item => String(item.msgId) === String(normalized.msgId))
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], ...normalized }
  } else {
    messages.value = normalizeMessages([...messages.value, normalized])
    lastMsgCount = messages.value.length
  }
  return normalized
}

function markMessageRevoked(msgId) {
  const idx = messages.value.findIndex(item => String(item.msgId) === String(msgId))
  if (idx < 0) return false
  messages.value[idx] = { ...messages.value[idx], isRevoke: true }
  return true
}

function getMemberInfo(userId) {
  return members.value.find(m => String(m.userId) === String(userId)) || {}
}

async function loadMessages() {
  try {
    const res = await getTeamHistory({ teamId, page: 1, pageSize: 50 })
    if (res.code === 200) {
      const list = normalizeMessages(res.data?.records || res.data || [])
      messages.value = mergeMessages(list, messages.value)
      lastMsgCount = messages.value.length
    }
  } catch { /* ignore */ }
}

async function loadTeamInfo() {
  try {
    const [tRes, mRes, nRes] = await Promise.all([
      getTeamDetail(teamId),
      getTeamMembers(teamId, { page: 1, pageSize: 200 }),
      getGroupNotice({ conversationId: convId }).catch(() => ({ code: 400, data: {} })),
    ])
    if (tRes.code === 200) {
      const detail = tRes.data || {}
      team.value = detail
      myRole.value = detail.myRoleType || detail.roleType || 0
      isMuteAll.value = detail.teamAllMute === 1
    }
    if (mRes.code === 200) {
      members.value = (mRes.data?.records || mRes.data || []).map((item) => ({
        ...item,
        userId: item.userId ?? item.id,
        userNickname: item.userNickname ?? item.nickname,
        roleType: item.roleType ?? item.teamRoleType,
        isMuted: item.isMuted ?? item.teamMuteType === 1,
      }))
    }
    if (nRes.code === 200) {
      groupNotice.value = nRes.data?.notice || ''
      noticeDraft.value = groupNotice.value
    }
  } catch { /* ignore */ }
}

async function refreshPins() {
  const res = await getPinList({ conversationId: convId }).catch(() => ({ code: 400, data: [] }))
  if (res.code === 200) pins.value = res.data || []
}

async function refreshCollections() {
  const res = await getCollections({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] }))
  if (res.code === 200) collections.value = res.data || []
}

async function runSearch() {
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    return
  }
  loadingPanel.value = true
  const res = await searchMsg({ conversationId: convId, keyword: searchKeyword.value.trim(), page: 1, pageSize: 50 }).catch(() => ({ code: 400, data: [] }))
  if (res.code === 200) searchResults.value = normalizeMessages(res.data?.records || res.data || [])
  else showToast(res.message || '搜索失败', 'error')
  loadingPanel.value = false
}

async function openPanel(panel) {
  activePanel.value = activePanel.value === panel ? '' : panel
  if (!activePanel.value) return
  loadingPanel.value = true
  if (panel === 'pins') await refreshPins()
  if (panel === 'collections') await refreshCollections()
  if (panel === 'search') searchResults.value = []
  if (panel === 'notice') noticeDraft.value = groupNotice.value
  loadingPanel.value = false
}

const isMuted = () => {
  if (myRole.value <= 2) return false
  if (isMuteAll.value) return true
  const me = getMemberInfo(authStore.userId)
  return !!me.isMuted
}

async function handleSend(payload) {
  if (sending.value) return
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
  upsertMessage(optimisticMsg)
  await syncMessageToLocal(optimisticMsg)
  scrollToBottom()
  try {
    const res = await sendTeamMsg({
      teamId,
      msgType: payload.msgType,
      msgContent: payload.msgContent,
      emojiId: payload.emojiId,
      fileUrl: payload.fileUrl,
      fileName: payload.fileName,
      fileSize: payload.fileSize,
    })
    if (res.code === 200) {
      const serverMsg = res.data || {}
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
      upsertMessage(savedMsg)
      await syncMessageToLocal(savedMsg)
      if (!serverMsg.msgId && !serverMsg.id) {
        messages.value = messages.value.filter(item => String(item.msgId) !== String(tempId))
        await removeMessageFromLocal(tempId)
        await loadMessages()
      } else if (String(tempId) !== String(serverMsg.msgId ?? serverMsg.id)) {
        messages.value = messages.value.filter(item => String(item.msgId) !== String(tempId))
        await removeMessageFromLocal(tempId)
      } else {
        await updateLocalStatus(tempId, 1)
      }
      await markConversationRead()
      scrollToBottom()
    } else {
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
      errorMsg.value = res.message || '发送失败'
      setTimeout(() => errorMsg.value = '', 3000)
    }
  } catch (e) {
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
    errorMsg.value = e?.response?.message || e?.message || '网络错误'
    setTimeout(() => errorMsg.value = '', 3000)
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
    retryMsgId: msg.msgId,
  })
}

async function deleteFailedMessage(msg) {
  if (msg.localStatus !== 2) return
  messages.value = messages.value.filter(item => String(item.msgId) !== String(msg.msgId))
  await removeMessageFromLocal(msg.msgId)
}

async function handleRevoke(msg) {
  const res = await revokeMsg(msg.msgId || msg.id)
  if (res.code === 200) {
    await loadMessages()
    showToast('消息已撤回')
  } else {
    showToast(res.message || '撤回失败', 'error')
  }
}

async function handleReport(msg) {
  reportModal.value = { visible: true, msg, reason: 3, content: '' }
}

function handleEdit(msg) {
  editModal.value = { visible: true, msg, content: msg.msgContent || '' }
}

async function submitEdit() {
  const { msg, content } = editModal.value
  if (!msg || !content?.trim()) return
  const res = await editMsg({ msgId: msg.msgId, newContent: content.trim() }).catch(() => ({ code: 400, message: '编辑失败' }))
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
  const res = await reportMessage({ messageId: msg.msgId, reportReason: reason, reportContent: content })
  if (res.code === 200) {
    reportModal.value.visible = false
    showToast('举报已提交')
  } else {
    showToast(res.message || '举报失败', 'error')
  }
}

async function handleCollect(msg) {
  collectModal.value = { visible: true, msg, note: '' }
}

async function submitCollect() {
  const { msg, note } = collectModal.value
  if (!msg) return
  const res = await collectMsg({ messageId: msg.msgId, collectionNote: note })
  if (res.code === 200) {
    collectModal.value.visible = false
    showToast('收藏成功')
    if (activePanel.value === 'collections') await refreshCollections()
  } else {
    showToast(res.message || '收藏失败', 'error')
  }
}

async function handlePin(msg) {
  const res = await pinMsg({ conversationId: convId, messageId: msg.msgId })
  if (res.code === 200) {
    showToast('置顶成功')
    if (activePanel.value === 'pins') await refreshPins()
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

async function saveNotice() {
  const res = await setGroupNotice({ conversationId: convId, notice: noticeDraft.value })
  if (res.code === 200) {
    groupNotice.value = noticeDraft.value
    showToast('群公告已更新')
  } else {
    showToast(res.message || '群公告保存失败', 'error')
  }
}

async function clearAllFailedMessages() {
  messages.value = messages.value.filter(item => item.localStatus !== 2)
  await clearFailedMessages(convId)
  showToast('已清理失败消息')
}

async function markConversationRead() {
  if (!convId || !messages.value.length) return
  const last = messages.value[messages.value.length - 1]
  const lastMsgId = last?.msgId || last?.id
  if (!lastMsgId) return
  await readMsgs({ conversationId: convId, msgIds: [lastMsgId] }).catch(() => { })
  chatStore.clearUnread(convId)
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
    createTime: incoming?.createTime,
    isRevoke: false,
  })
  markConversationRead()
  if (activePanel.value === 'search' && searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    if ((appended?.msgContent || '').toLowerCase().includes(keyword)) {
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
    searchResults.value = searchResults.value.map(item =>
      String(item.msgId) === String(event.detail?.msgId) ? { ...item, isRevoke: true } : item
    )
  }
}

function shouldShowTime(index) {
  if (index === 0) return true
  const current = messages.value[index]
  const prev = messages.value[index - 1]
  if (!current?.createTime || !prev?.createTime) return false
  return new Date(current.createTime).getTime() - new Date(prev.createTime).getTime() > 10 * 60 * 1000
}

function formatMessageDivider(time) {
  return time ? new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
}

function handleRealtimeGroupNotice(event) {
  if (event.detail?.conversationId !== convId) return
  lastRealtimeAt = Date.now()
  groupNotice.value = event.detail.notice || ''
  noticeDraft.value = groupNotice.value
  if (activePanel.value === 'notice') showToast('群公告已实时更新')
}

function openTeamMembers() {
  router.push(`/teams/${teamId}/members`)
}

onMounted(async () => {
  chatStore.setActiveConversation(convId)
  window.addEventListener('chat-ws-message', handleRealtimeMessage)
  window.addEventListener('chat-ws-revoke', handleRealtimeRevoke)
  window.addEventListener('group-notice-update', handleRealtimeGroupNotice)
  await Promise.all([loadLocalMessages(), loadMessages(), loadTeamInfo()])
  await markConversationRead()
  await scrollToBottom(true)
  // 轮询新消息
  pollTimer = setInterval(async () => {
    if (Date.now() - lastRealtimeAt < 15000) return
    try {
      const res = await getTeamHistory({ teamId, page: 1, pageSize: 50 })
      if (res.code === 200) {
        const list = normalizeMessages(res.data?.records || res.data || [])
        if (list.length !== lastMsgCount) {
          messages.value = list
          lastMsgCount = list.length
          await markConversationRead()
          scrollToBottom()
        }
      }
    } catch { /* ignore */ }
  }, 8000)
})

onUnmounted(() => {
  chatStore.setActiveConversation('')
  window.removeEventListener('chat-ws-message', handleRealtimeMessage)
  window.removeEventListener('chat-ws-revoke', handleRealtimeRevoke)
  window.removeEventListener('group-notice-update', handleRealtimeGroupNotice)
  clearInterval(pollTimer)
})
</script>

<template>
  <div class="chat-win">
    <div class="chat-topbar">
      <div class="team-head">
        <div class="team-icon">{{ team.teamName?.charAt(0) }}</div>
        <span class="chat-name">{{ team.teamName }}</span>
      </div>
      <div class="top-actions">
        <el-dropdown>
          <el-button type="text" size="small" class="action-btn">
            <el-icon>
              <More />
            </el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openPanel('notice')">
                <el-icon>
                  <Document />
                </el-icon>
                <span>群公告</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('search')">
                <el-icon>
                  <Search />
                </el-icon>
                <span>搜索消息</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('pins')">
                <el-icon>
                  <Top />
                </el-icon>
                <span>消息置顶</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openPanel('collections')">
                <el-icon>
                  <Star />
                </el-icon>
                <span>我的收藏</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="clearAllFailedMessages">
                <el-icon>
                  <Delete />
                </el-icon>
                <span class="text-danger">清理失败消息</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="primary" size="small" @click="openTeamMembers">
          <el-icon>
            <UserFilled />
          </el-icon>
          <span>成员</span>
        </el-button>
      </div>
    </div>

    <div v-if="activePanel" class="panel-wrap">
      <div v-if="activePanel === 'notice'" class="tool-panel">
        <div class="panel-head">
          <strong>群公告</strong>
          <el-button type="text" size="small" @click="activePanel = ''">
            <el-icon>
              <Close />
            </el-icon>
          </el-button>
        </div>
        <el-input v-model="noticeDraft" type="textarea" rows="4" :readonly="myRole > 2" placeholder="暂无群公告"
          class="notice-editor" />
        <div class="panel-item-actions">
          <span class="panel-item-time">当前公告：{{ groupNotice || '暂无' }}</span>
          <el-button v-if="myRole <= 2" type="primary" size="small" @click="saveNotice">保存公告</el-button>
        </div>
      </div>

      <div v-if="activePanel === 'search'" class="tool-panel">
        <div class="panel-head">
          <strong>消息搜索</strong>
          <el-button type="text" size="small" @click="activePanel = ''">
            <el-icon>
              <Close />
            </el-icon>
          </el-button>
        </div>
        <div class="search-row">
          <el-input v-model="searchKeyword" placeholder="输入关键词搜索本群消息" size="small" @keyup.enter="runSearch"
            class="search-input" />
          <el-button type="primary" size="small" @click="runSearch">搜索</el-button>
        </div>
        <el-loading v-if="loadingPanel" element-loading-text="搜索中..." class="panel-loading" />
        <el-empty v-else-if="!searchResults.length" description="暂无结果" class="panel-empty" />
        <div v-else class="panel-list">
          <el-card v-for="item in searchResults" :key="item.msgId" class="panel-item"
            @click="scrollToMessage(item.msgId)">
            <div class="panel-item-text" v-html="renderHighlightedText(item.msgContent, searchKeyword)"></div>
            <span class="panel-item-time">{{ new Date(item.createTime).toLocaleString('zh-CN') }}</span>
          </el-card>
        </div>
      </div>

      <div v-if="activePanel === 'pins'" class="tool-panel">
        <div class="panel-head">
          <strong>会话置顶</strong>
          <el-button type="text" size="small" @click="activePanel = ''">
            <el-icon>
              <Close />
            </el-icon>
          </el-button>
        </div>
        <el-loading v-if="loadingPanel" element-loading-text="加载中..." class="panel-loading" />
        <el-empty v-else-if="!pins.length" description="暂无置顶消息" class="panel-empty" />
        <div v-else class="panel-list">
          <el-card v-for="item in pins" :key="item.pinId" class="panel-item"
            @click="scrollToMessage(item.messageId || item.msgId)">
            <div class="panel-item-text">{{ item.msgContent }}</div>
            <div class="panel-item-actions">
              <span class="panel-item-time">#{{ item.pinOrder }}</span>
              <el-button type="text" size="small" class="text-danger"
                @click.stop="handleUnpin(item.pinId)">取消置顶</el-button>
            </div>
          </el-card>
        </div>
      </div>

      <div v-if="activePanel === 'collections'" class="tool-panel">
        <div class="panel-head">
          <strong>我的收藏</strong>
          <el-button type="text" size="small" @click="activePanel = ''">
            <el-icon>
              <Close />
            </el-icon>
          </el-button>
        </div>
        <el-loading v-if="loadingPanel" element-loading-text="加载中..." class="panel-loading" />
        <el-empty v-else-if="!collections.length" description="暂无收藏消息" class="panel-empty" />
        <div v-else class="panel-list">
          <el-card v-for="item in collections" :key="item.collectionId" class="panel-item"
            @click="scrollToMessage(item.messageId || item.msgId)">
            <div class="panel-item-text">{{ item.msgContent }}</div>
            <div class="panel-sub">{{ item.collectionNote || '无备注' }}</div>
            <div class="panel-item-actions">
              <span class="panel-item-time">{{ new Date(item.collectionTime).toLocaleString('zh-CN') }}</span>
              <el-button type="text" size="small" class="text-danger"
                @click.stop="handleCancelCollect(item.collectionId)">取消收藏</el-button>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <el-message v-if="toast.msg" :message="toast.msg" :type="toast.type" duration="3000" show-close />
    <el-dialog v-model="reportModal.visible" title="举报消息" width="460px" @close="reportModal.visible = false">
      <div class="form-group">
        <label>举报原因</label>
        <el-select v-model="reportModal.reason" class="modal-input">
          <el-option :value="1" label="色情" />
          <el-option :value="2" label="暴力" />
          <el-option :value="3" label="骚扰" />
          <el-option :value="4" label="广告" />
          <el-option :value="5" label="诈骗" />
          <el-option :value="6" label="其他" />
        </el-select>
      </div>
      <div class="form-group">
        <label>补充说明</label>
        <el-input v-model="reportModal.content" type="textarea" rows="4" placeholder="补充举报说明（可选）" class="modal-input" />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="reportModal.visible = false">取消</el-button>
          <el-button type="primary" @click="submitReport">提交举报</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog v-model="collectModal.visible" title="收藏消息" width="460px" @close="collectModal.visible = false">
      <div class="form-group">
        <label>收藏备注</label>
        <el-input v-model="collectModal.note" type="textarea" rows="4" placeholder="输入收藏备注，方便后续检索"
          class="modal-input" />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="collectModal.visible = false">取消</el-button>
          <el-button type="primary" @click="submitCollect">确认收藏</el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog v-model="editModal.visible" title="编辑消息" width="460px">
      <el-input v-model="editModal.content" type="textarea" rows="5" class="modal-input" />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editModal.visible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">保存</el-button>
        </span>
      </template>
    </el-dialog>
    <div class="msg-list" ref="msgListRef">
      <el-empty v-if="!messages.length" description="暂无消息" class="empty-tip" />
      <div v-for="(msg, index) in messages" :key="msg.msgId" :id="`msg-${msg.msgId}`" class="msg-anchor">
        <div v-if="shouldShowTime(index)" class="msg-divider-time">{{ formatMessageDivider(msg.createTime) }}</div>
        <MessageBubble :msg="msg" :is-self="!!msg.isSelf" :sender-avatar="getMemberInfo(msg.senderId).userAvatar || ''"
          :sender-nickname="getMemberInfo(msg.senderId).userNickname || String(msg.senderId)" @revoke="handleRevoke"
          @report="handleReport" @collect="handleCollect" @pin="handlePin" @retry="retrySend"
          @delete-failed="deleteFailedMessage" @edit="handleEdit" />
      </div>
    </div>
    <div v-if="errorMsg" class="error-tip">{{ errorMsg }}</div>
    <MessageInput :disabled="isMuted() || sending" @send="handleSend" />
  </div>
</template>

<style scoped>
.chat-win {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f9f9f9;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  padding: 0 18px;
  background: #ffffff;
  border-bottom: 1px solid #dddddd;
}

.team-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #7b61ff, #9d7bff);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}

.chat-name {
  font-weight: 500;
  font-size: 16px;
  color: #222;
  flex: 1;
}

.top-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.tool-btn,
.mini-btn {
  padding: 7px 12px;
  background: #f3f3f3;
  border: 1px solid #e4e4e4;
  border-radius: 999px;
  color: #666;
  cursor: pointer;
  font-size: 12px;
}

.danger-lite {
  background: #fff1f0;
  color: #d4380d;
}

.ghost-btn,
.btn-members {
  padding: 7px 12px;
  background: #f3f3f3;
  border: 1px solid #e4e4e4;
  border-radius: 999px;
  color: #666;
  cursor: pointer;
  font-size: 12px;
}

.panel-wrap {
  padding: 12px 16px 0;
}

.tool-panel {
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 14px;
  padding: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, .04);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-head strong {
  color: var(--wx-text);
}

.panel-close {
  border: none;
  background: rgba(255, 245, 114, .18);
  font-size: 18px;
  color: var(--wx-muted);
  cursor: pointer;
  width: 28px;
  height: 28px;
  border-radius: 999px;
}

.notice-editor,
.search-input,
.modal-input {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.form-group label {
  font-size: 13px;
  color: var(--wx-text-2);
}

.panel-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
}

.panel-item {
  border: 1px solid rgba(123, 117, 34, .1);
  border-radius: 14px;
  padding: 10px;
  background: rgba(255, 255, 255, .92);
}

.panel-item-clickable {
  cursor: pointer;
  transition: transform .15s ease, border-color .15s ease, box-shadow .15s ease;
}

.panel-item-clickable:hover {
  transform: translateY(-1px);
  border-color: var(--color-accent2-2);
  box-shadow: 0 8px 24px rgba(32, 14, 51, .08);
}

.panel-item-text {
  font-size: 13px;
  color: var(--wx-text);
  word-break: break-all;
}

.panel-sub,
.panel-item-time {
  font-size: 11px;
  color: var(--wx-muted);
}

.panel-item-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.text-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: var(--color-complement-2);
  font-size: 12px;
}

.text-btn.danger {
  color: var(--color-accent2-4);
}

.panel-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--wx-muted);
  font-size: 13px;
}

.toast {
  margin: 10px 16px 0;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f5f5f5;
}

.msg-anchor {
  border-radius: 14px;
  transition: background-color .25s ease, box-shadow .25s ease;
}

.msg-anchor.msg-highlight,
.msg-highlight {
  background: rgba(188, 84, 172, .1);
  box-shadow: 0 0 0 1px rgba(188, 84, 172, .28);
}

:deep(mark) {
  background: rgba(255, 245, 114, .72);
  color: var(--color-primary-5);
  padding: 0 2px;
  border-radius: 4px;
}

.empty-tip {
  text-align: center;
  color: var(--wx-muted);
  font-size: 13px;
  padding: 40px 0;
}

.msg-divider-time {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 10px 0 12px;
  font-size: 11px;
  color: #a0a0a0;
}

.error-tip {
  text-align: center;
  color: var(--color-accent2-4);
  font-size: 13px;
  padding: 8px;
  background: rgba(188, 84, 172, .12);
}
</style>
