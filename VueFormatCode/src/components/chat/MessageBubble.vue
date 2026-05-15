<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import twemoji from 'twemoji'
import { Document, Download, VideoPlay } from '@element-plus/icons-vue'
import AvatarWithStatus from '@/components/common/AvatarWithStatus.vue'

const props = defineProps({
  msg: { type: Object, required: true },
  isSelf: { type: Boolean, default: false },
  senderNickname: { type: String, default: '' },
  senderAvatar: { type: String, default: '' },
  senderId: { type: [Number, String], default: '' },
  canPin: { type: Boolean, default: true },
  memberActions: { type: Array, default: () => [] },
  memberActionBlockReason: { type: String, default: '' },
})

const emit = defineEmits([
  'revoke',
  'report',
  'collect',
  'pin',
  'retry',
  'delete-failed',
  'edit',
  'member-action',
  'member-action-block',
  'open-profile',
])

const msgTypeLabel = {
  3: '[文件]',
  4: '[表情包]',
  5: '[@消息]',
}

const imagePreviewVisible = ref(false)
const videoPreviewVisible = ref(false)
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuRef = ref(null)
const memberMenuVisible = ref(false)
const memberMenuX = ref(0)
const memberMenuY = ref(0)
const memberMenuRef = ref(null)
const videoDuration = ref('')

const showEdit = computed(() => props.isSelf && (props.msg.msgType === 1 || props.msg.msgType === 5) && !props.msg.isRevoke)
const showDeleteFailed = computed(() => props.msg.localStatus === 2)
const showRetry = computed(() => props.msg.localStatus === 2)
const showReport = computed(() => !props.isSelf && !props.msg.isRevoke && !isPendingLocalMessage())
const hasPrimaryActions = computed(() => showEdit.value || canRevoke() || showRetry.value)
const hasSecondaryActions = computed(() => showReport.value || !props.msg.isRevoke)
const hasDangerActions = computed(() => showDeleteFailed.value)
const showDownloadAction = computed(() => canDownloadMessage())

function canRevoke() {
  return props.isSelf && !props.msg.isRevoke && Date.now() - new Date(props.msg.createTime).getTime() < 5 * 60 * 1000
}

function isTempMessageId(msgId) {
  return String(msgId || '').startsWith('temp_')
}

function isPendingLocalMessage() {
  const localStatus = Number(props.msg?.localStatus ?? 1)
  return isTempMessageId(props.msg?.msgId) || localStatus === 0 || localStatus === 2
}

function parseContent(raw) {
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw || {}
  } catch {
    return raw
  }
}

function parsedContent() {
  return parseContent(props.msg.msgContent)
}

function messageText() {
  if (props.msg.msgType === 1 || props.msg.msgType === 5) return props.msg.msgContent
  const parsed = parsedContent()
  return typeof parsed === 'string' ? parsed : parsed.caption || parsed.name || parsed.url || ''
}

function imageUrl() {
  const parsed = parsedContent()
  return typeof parsed === 'string' ? parsed : parsed.url || ''
}

function isExpiredFile() {
  const parsed = parsedContent()
  return typeof parsed === 'object' && !!parsed?.expired
}

function fileUrl() {
  const parsed = parsedContent()
  return typeof parsed === 'string' ? parsed : parsed.url || ''
}

function fileName() {
  const parsed = parsedContent()
  if (props.msg.fileName) return props.msg.fileName
  if (typeof parsed === 'object' && parsed?.name) return parsed.name
  const url = fileUrl()
  return decodeURIComponent(url.split('/').pop()?.split('?')[0] || '下载文件')
}

function fileSizeText() {
  const parsed = parsedContent()
  const size = Number(parsed?.size ?? props.msg.fileSize ?? 0)
  if (!size || Number.isNaN(size)) return '文件'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(size >= 10 * 1024 ? 0 : 1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(size >= 10 * 1024 * 1024 ? 0 : 1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function fileExt() {
  const name = fileName()
  const ext = name.includes('.') ? name.split('.').pop() : ''
  return (ext || 'file').slice(0, 4).toUpperCase()
}

function lowerFileExt() {
  return fileExt().toLowerCase()
}

function fileTypeTheme() {
  const ext = lowerFileExt()
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'excel'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  if (['pdf'].includes(ext)) return 'pdf'
  if (['zip', 'rar', '7z'].includes(ext)) return 'zip'
  if (['txt', 'md'].includes(ext)) return 'text'
  if (['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv'].includes(ext)) return 'video'
  return 'default'
}

function isVideoMessage() {
  if (props.msg.msgType !== 3 || isExpiredFile()) return false
  const parsed = parsedContent()
  const mediaType = typeof parsed === 'object' ? String(parsed?.mediaType || parsed?.type || '').toLowerCase() : ''
  if (mediaType.startsWith('video')) return true
  return ['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv'].includes(lowerFileExt())
}

function emojiRaw() {
  const parsed = parsedContent()
  if (typeof parsed === 'object' && parsed?.emoji) return parsed.emoji
  if (typeof parsed === 'string' && parsed) return parsed
  return ''
}

function emojiCode() {
  const parsed = parsedContent()
  const raw = emojiRaw()
  if (raw) return twemoji.convert.toCodePoint(raw)
  if (typeof parsed === 'object' && parsed?.emojiId) return parsed.emojiId
  return props.msg.emojiId || '1f600'
}

function emojiSrc() {
  return `https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/svg/${emojiCode()}.svg`
}

function emojiAlt() {
  const parsed = parsedContent()
  return emojiRaw() || (typeof parsed === 'object' ? parsed?.label || parsed?.emojiId : '') || props.msg.emojiId || 'emoji'
}

function openImagePreview() {
  if (imageUrl()) imagePreviewVisible.value = true
}

function openVideoPreview() {
  if (fileUrl()) videoPreviewVisible.value = true
}

function formatDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return ''
  const total = Math.round(seconds)
  const minute = Math.floor(total / 60)
  const second = total % 60
  return `${minute}:${String(second).padStart(2, '0')}`
}

function handleVideoMetadataLoaded(event) {
  videoDuration.value = formatDuration(event.target?.duration)
}

function closeMenu() {
  menuVisible.value = false
  memberMenuVisible.value = false
}

function positionMenu() {
  const menuEl = menuRef.value
  if (!menuEl) return
  const padding = 12
  const rect = menuEl.getBoundingClientRect()
  const maxX = window.innerWidth - rect.width - padding
  const maxY = window.innerHeight - rect.height - padding
  menuX.value = Math.min(Math.max(padding, menuX.value), Math.max(padding, maxX))
  menuY.value = Math.min(Math.max(padding, menuY.value), Math.max(padding, maxY))
  menuEl.style.left = `${menuX.value}px`
  menuEl.style.top = `${menuY.value}px`
}

function openContextMenu(event) {
  event.preventDefault()
  event.stopPropagation()
  menuX.value = event.clientX + 8
  menuY.value = event.clientY + 8
  menuVisible.value = true
  requestAnimationFrame(positionMenu)
}

function positionMemberMenu() {
  const menuEl = memberMenuRef.value
  if (!menuEl) return
  const padding = 12
  const rect = menuEl.getBoundingClientRect()
  const maxX = window.innerWidth - rect.width - padding
  const maxY = window.innerHeight - rect.height - padding
  memberMenuX.value = Math.min(Math.max(padding, memberMenuX.value), Math.max(padding, maxX))
  memberMenuY.value = Math.min(Math.max(padding, memberMenuY.value), Math.max(padding, maxY))
  menuEl.style.left = `${memberMenuX.value}px`
  menuEl.style.top = `${memberMenuY.value}px`
}

function openAvatarMenu(event) {
  if (props.memberActionBlockReason) {
    event.preventDefault()
    event.stopPropagation()
    closeMenu()
    emit('member-action-block', props.memberActionBlockReason)
    return
  }
  if (!props.memberActions.length) return
  event.preventDefault()
  event.stopPropagation()
  memberMenuX.value = event.clientX + 8
  memberMenuY.value = event.clientY + 8
  memberMenuVisible.value = true
  menuVisible.value = false
  requestAnimationFrame(positionMemberMenu)
}

function openSenderProfile(event) {
  event.stopPropagation()
  emit('open-profile', {
    senderId: props.senderId,
    isSelf: props.isSelf,
  })
}

async function downloadByBlob(url, name) {
  const response = await fetch(url, { mode: 'cors' })
  if (!response.ok) {
    throw new Error('download failed')
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = name
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
}

function downloadByAnchor(url, name) {
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.target = '_blank'
  link.rel = 'noreferrer'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function canDownloadMessage() {
  if (props.msg.isRevoke || isExpiredFile()) return false
  return props.msg.msgType === 2 || props.msg.msgType === 3
}

async function downloadMessageFile() {
  const url = props.msg.msgType === 2 ? imageUrl() : fileUrl()
  if (!url) return
  const name = props.msg.msgType === 2 ? fileName() || 'image' : fileName()
  try {
    await downloadByBlob(url, name)
  } catch {
    downloadByAnchor(url, name)
  }
}

function handleMenuAction(action) {
  closeMenu()
  emit(action, props.msg)
}

function handleMemberAction(action) {
  closeMenu()
  emit('member-action', { action, msg: props.msg })
}

async function handleDownloadAction() {
  closeMenu()
  await downloadMessageFile()
}

function handleWindowPointer(event) {
  if (menuRef.value?.contains(event.target)) return
  closeMenu()
}

function handleWindowResize() {
  if (menuVisible.value) positionMenu()
  if (memberMenuVisible.value) positionMemberMenu()
}

function handleWindowKeydown(event) {
  if (event.key === 'Escape') closeMenu()
}

onMounted(() => {
  window.addEventListener('click', handleWindowPointer)
  window.addEventListener('scroll', handleWindowResize, true)
  window.addEventListener('resize', handleWindowResize)
  window.addEventListener('keydown', handleWindowKeydown)
})

onUnmounted(() => {
  window.removeEventListener('click', handleWindowPointer)
  window.removeEventListener('scroll', handleWindowResize, true)
  window.removeEventListener('resize', handleWindowResize)
  window.removeEventListener('keydown', handleWindowKeydown)
})
</script>

<template>
  <div :class="['bubble-row', { self: isSelf }]" @contextmenu="openContextMenu">
    <div class="avatar-action-wrap" @click="openSenderProfile" @contextmenu="openAvatarMenu">
      <AvatarWithStatus :avatar="senderAvatar" :size="38" />
    </div>
    <div class="bubble-col">
      <button v-if="!isSelf" type="button" class="sender-name sender-name-btn" @click="openSenderProfile">
        {{ senderNickname }}
      </button>

      <template v-if="msg.isRevoke">
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span class="revoked">{{ isSelf ? '你撤回了一条消息' : '对方撤回了一条消息' }}</span>
        </div>
      </template>

      <template v-else-if="msg.msgType === 2">
        <button type="button" class="media-card image-card" @click="openImagePreview">
          <img :src="imageUrl()" alt="图片消息" class="image-msg image-plain" />
        </button>
      </template>

      <template v-else-if="msg.msgType === 1 || msg.msgType === 5">
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span>{{ messageText() }}</span>
        </div>
      </template>

      <template v-else-if="msg.msgType === 4">
        <div :class="['bubble emoji-bubble', isSelf ? 'bubble-self' : 'bubble-other']" :title="emojiAlt()">
          <img :src="emojiSrc()" :alt="emojiAlt()" class="emoji-msg" />
        </div>
      </template>

      <template v-else-if="isExpiredFile()">
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span class="expired-file">该文件已过期</span>
        </div>
      </template>

      <template v-else-if="isVideoMessage()">
        <button type="button" class="video-card" @click="openVideoPreview">
          <video
            :src="fileUrl()"
            class="video-thumb"
            preload="metadata"
            muted
            playsinline
            @loadedmetadata="handleVideoMetadataLoaded"
          ></video>
          <span class="video-overlay">
            <span class="video-play">
              <el-icon><VideoPlay /></el-icon>
            </span>
          </span>
          <span v-if="videoDuration" class="video-duration">{{ videoDuration }}</span>
        </button>
      </template>

      <template v-else-if="msg.msgType === 3">
        <button
          type="button"
          :class="[
            'bubble',
            'file-bubble',
            isSelf ? 'file-bubble-self' : 'file-bubble-other',
          ]"
          @click="handleDownloadAction"
        >
          <span class="file-main">
            <span class="file-name">{{ fileName() }}</span>
            <span class="file-meta">{{ fileSizeText() }}</span>
            <span class="file-divider"></span>
            <span class="file-source">
              <el-icon class="file-source-icon"><Document /></el-icon>
              <span>文件消息，点击下载</span>
            </span>
          </span>
          <span class="file-side">
            <span :class="['file-type-badge', `file-type-${fileTypeTheme()}`]">{{ fileExt() }}</span>
            <span class="file-download">
              <el-icon><Download /></el-icon>
            </span>
          </span>
        </button>
      </template>

      <template v-else>
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span>{{ msgTypeLabel[msg.msgType] }}</span>
        </div>
      </template>

      <div class="meta-row">
        <span v-if="msg.localStatus === 0" class="msg-status">发送中</span>
        <span v-if="msg.localStatus === 2" class="msg-status danger">发送失败</span>
      </div>
    </div>

    <teleport to="body">
      <div v-if="menuVisible" ref="menuRef" class="msg-context-menu" @click.stop>
        <div v-if="hasPrimaryActions" class="menu-group">
          <button v-if="showEdit" class="menu-item" @click="handleMenuAction('edit')">编辑</button>
          <button v-if="canRevoke()" class="menu-item" @click="handleMenuAction('revoke')">撤回</button>
          <button v-if="showRetry" class="menu-item" @click="handleMenuAction('retry')">重试发送</button>
        </div>

        <div v-if="hasPrimaryActions && (hasSecondaryActions || showDownloadAction)" class="menu-divider"></div>

        <div v-if="hasSecondaryActions || showDownloadAction" class="menu-group">
          <button v-if="showDownloadAction" class="menu-item" @click="handleDownloadAction">下载</button>
          <button v-if="showReport" class="menu-item" @click="handleMenuAction('report')">举报</button>
          <button v-if="hasSecondaryActions" class="menu-item" @click="handleMenuAction('collect')">收藏</button>
          <button v-if="hasSecondaryActions && canPin" class="menu-item" @click="handleMenuAction('pin')">置顶</button>
        </div>

        <div v-if="(hasPrimaryActions || hasSecondaryActions || showDownloadAction) && hasDangerActions" class="menu-divider"></div>

        <div v-if="hasDangerActions" class="menu-group danger-group">
          <button class="menu-item danger-item" @click="handleMenuAction('delete-failed')">删除</button>
        </div>
      </div>
    </teleport>

    <teleport to="body">
      <div v-if="memberMenuVisible" ref="memberMenuRef" class="msg-context-menu" @click.stop>
        <div class="menu-group">
          <button
            v-for="item in memberActions"
            :key="item.key"
            class="menu-item"
            :class="{ 'danger-item': item.danger }"
            @click="handleMemberAction(item.key)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </teleport>
  </div>

  <el-dialog
    v-model="imagePreviewVisible"
    width="auto"
    class="image-preview-dialog"
    modal-class="media-preview-modal image-preview-modal"
    append-to-body
  >
    <img :src="imageUrl()" alt="图片预览" class="preview-image" />
  </el-dialog>

  <el-dialog
    v-model="videoPreviewVisible"
    width="min(92vw, 980px)"
    class="video-preview-dialog"
    modal-class="media-preview-modal video-preview-modal"
    append-to-body
  >
    <video :src="fileUrl()" class="preview-video" controls autoplay playsinline></video>
  </el-dialog>
</template>

<style scoped>
.bubble-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.bubble-row.self {
  flex-direction: row-reverse;
}

.avatar-action-wrap {
  display: inline-flex;
  flex-shrink: 0;
  cursor: pointer;
}

.bubble-col {
  display: flex;
  flex-direction: column;
  max-width: 62%;
  align-items: flex-start;
}

.bubble-row.self .bubble-col {
  align-items: flex-end;
}

.sender-name {
  font-size: 11px;
  color: var(--wx-muted);
  margin-bottom: 4px;
}

.sender-name-btn {
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.bubble {
  position: relative;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.45;
  word-break: break-all;
  white-space: pre-wrap;
  max-width: 100%;
}

.bubble-other {
  background: #ffffff;
  color: #111;
  border-top-left-radius: 4px;
}

.bubble-self {
  background: #95ec69;
  color: #111;
  border-top-right-radius: 4px;
}

.file-bubble.file-bubble-self,
.file-bubble.file-bubble-other {
  background: #f5f6f8;
  color: #1f2329;
  border-radius: 12px;
}

.bubble-other::before,
.bubble-self::before {
  content: '';
  position: absolute;
  top: 10px;
  width: 7px;
  height: 7px;
  transform: rotate(45deg);
}

.bubble-other::before {
  left: -3px;
  background: #ffffff;
}

.bubble-self::before {
  right: -3px;
  background: #95ec69;
}

.file-bubble::before {
  display: none;
}

.revoked,
.expired-file,
.msg-status {
  color: var(--wx-muted);
  font-size: 11px;
}

.msg-status.danger {
  color: #d84a4a;
}

.media-card,
.video-card,
.file-bubble {
  border: none;
  cursor: pointer;
}

.media-card,
.video-card {
  padding: 0;
  background: transparent;
}

.image-card {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.image-msg {
  max-width: 240px;
  max-height: 320px;
  display: block;
  border-radius: 12px;
}

.image-plain {
  background: transparent;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.emoji-bubble {
  padding: 8px 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  min-height: 36px;
}

.emoji-msg {
  width: 20px;
  height: 20px;
  object-fit: contain;
  display: block;
}

.video-card {
  position: relative;
  width: min(260px, 60vw);
  border-radius: 16px;
  overflow: hidden;
  background: #111;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.14);
}

.video-thumb {
  display: block;
  width: 100%;
  height: 320px;
  object-fit: cover;
  background: #1b1b1b;
}

.video-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.18));
}

.video-play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #121212;
  font-size: 28px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.video-duration {
  position: absolute;
  right: 10px;
  bottom: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
}

.file-bubble {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 12px;
  min-width: 240px;
  max-width: 320px;
  text-decoration: none;
  padding: 12px 14px 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: background-color 0.18s ease, box-shadow 0.18s ease;
}

.file-bubble:hover {
  background: #eef0f3;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.file-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  justify-content: flex-start;
  gap: 4px;
  text-align: left;
}

.file-name {
  font-size: 13px;
  line-height: 1.5;
  color: #20242a;
  word-break: break-all;
  white-space: normal;
  font-weight: 500;
}

.file-meta {
  font-size: 12px;
  color: #8a919b;
}

.file-divider {
  display: block;
  width: 100%;
  height: 1px;
  margin-top: 4px;
  background: #e1e5ea;
}

.file-source {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #8d95a0;
  font-size: 12px;
}

.file-source-icon {
  font-size: 13px;
}

.file-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  flex-shrink: 0;
}

.file-type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 52px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
  box-shadow: inset 0 -10px 0 rgba(0, 0, 0, 0.08);
}

.file-type-word {
  background: linear-gradient(180deg, #3a84ff 0%, #2e6de0 100%);
}

.file-type-excel {
  background: linear-gradient(180deg, #25b864 0%, #1f9a54 100%);
}

.file-type-ppt {
  background: linear-gradient(180deg, #ff8a3d 0%, #e46f25 100%);
}

.file-type-pdf {
  background: linear-gradient(180deg, #ff5a5f 0%, #d94347 100%);
}

.file-type-zip {
  background: linear-gradient(180deg, #9b7bff 0%, #7a5ee0 100%);
}

.file-type-text {
  background: linear-gradient(180deg, #5f6c7b 0%, #47515c 100%);
}

.file-type-video {
  background: linear-gradient(180deg, #434a54 0%, #23272d 100%);
}

.file-type-default {
  background: linear-gradient(180deg, #6c7a89 0%, #55616c 100%);
}

.file-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: #e9edf2;
  color: #6a7480;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
  min-height: 16px;
}

.msg-context-menu {
  position: fixed;
  z-index: 4000;
  min-width: 148px;
  background: rgba(255, 255, 255, 0.985);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 16px;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.18);
  padding: 8px;
}

.menu-group {
  display: flex;
  flex-direction: column;
}

.menu-divider {
  height: 1px;
  margin: 6px 4px;
  background: rgba(0, 0, 0, 0.08);
}

.menu-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  color: #222;
}

.menu-item:hover {
  background: rgba(0, 0, 0, 0.05);
}

.danger-item {
  color: #cf3f36;
}

.danger-item:hover {
  background: rgba(207, 63, 54, 0.08);
}

.preview-image {
  max-width: min(88vw, 1100px);
  max-height: 80vh;
  display: block;
  border-radius: 12px;
}

.preview-video {
  width: min(88vw, 920px);
  max-height: 78vh;
  display: block;
  border-radius: 12px;
  background: #000;
}

:global(.media-preview-modal .el-dialog) {
  margin: 4vh auto 0 !important;
  width: fit-content !important;
  max-width: 92vw;
}

:global(.media-preview-modal .el-dialog__body) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px !important;
}

:global(.media-preview-modal.image-preview-modal .el-dialog__body) {
  min-width: min(88vw, 1100px);
}

@media (max-width: 768px) {
  .bubble-col {
    max-width: 78%;
  }

  .video-card {
    width: min(220px, 72vw);
  }

  .video-thumb {
    height: 280px;
  }

  .file-bubble {
    min-width: 220px;
    max-width: 280px;
  }
}
</style>
