<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import twemoji from 'twemoji'
import AvatarWithStatus from '@/components/common/AvatarWithStatus.vue'

const props = defineProps({ msg: { type: Object, required: true }, isSelf: { type: Boolean, default: false }, senderNickname: { type: String, default: '' }, senderAvatar: { type: String, default: '' }, canPin: { type: Boolean, default: true } })
const emit = defineEmits(['revoke', 'report', 'collect', 'pin', 'retry', 'delete-failed', 'edit'])
const msgTypeLabel = { 3: '[文件]', 4: '[表情]', 5: '[@消息]' }
const previewVisible = ref(false)
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuRef = ref(null)

const showEdit = computed(() => props.isSelf && (props.msg.msgType === 1 || props.msg.msgType === 5) && !props.msg.isRevoke)
const showDeleteFailed = computed(() => props.msg.localStatus === 2)
const showRetry = computed(() => props.msg.localStatus === 2)
const hasPrimaryActions = computed(() => showEdit.value || canRevoke() || showRetry.value)
const hasSecondaryActions = computed(() => !props.msg.isRevoke)
const hasDangerActions = computed(() => showDeleteFailed.value)

function canRevoke() {
  return props.isSelf && !props.msg.isRevoke && Date.now() - new Date(props.msg.createTime).getTime() < 5 * 60 * 1000
}

function parseContent(raw) {
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw || {}
  } catch {
    return raw
  }
}

function messageText() {
  if (props.msg.msgType === 1 || props.msg.msgType === 5) return props.msg.msgContent
  const parsed = parseContent(props.msg.msgContent)
  return typeof parsed === 'string' ? parsed : parsed.caption || parsed.name || parsed.url || ''
}

function imageUrl() {
  const parsed = parseContent(props.msg.msgContent)
  return typeof parsed === 'string' ? parsed : parsed.url || ''
}

function isExpiredFile() {
  const parsed = parseContent(props.msg.msgContent)
  return typeof parsed === 'object' && !!parsed?.expired
}

function fileUrl() {
  const parsed = parseContent(props.msg.msgContent)
  return typeof parsed === 'string' ? parsed : parsed.url || ''
}

function fileName() {
  const parsed = parseContent(props.msg.msgContent)
  if (typeof parsed === 'object' && parsed?.name) return parsed.name
  const url = fileUrl()
  return decodeURIComponent(url.split('/').pop()?.split('?')[0] || '下载文件')
}

function emojiRaw() {
  const parsed = parseContent(props.msg.msgContent)
  if (typeof parsed === 'object' && parsed?.emoji) return parsed.emoji
  if (typeof parsed === 'string' && parsed) return parsed
  return ''
}

function emojiCode() {
  const parsed = parseContent(props.msg.msgContent)
  const raw = emojiRaw()
  if (raw) return twemoji.convert.toCodePoint(raw)
  if (typeof parsed === 'object' && parsed?.emojiId) return parsed.emojiId
  return props.msg.emojiId || '1f600'
}

function emojiSrc() {
  return `https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/svg/${emojiCode()}.svg`
}

function emojiAlt() {
  const parsed = parseContent(props.msg.msgContent)
  return emojiRaw() || (typeof parsed === 'object' ? parsed?.label || parsed?.emojiId : '') || props.msg.emojiId || 'emoji'
}

function openPreview() {
  if (imageUrl()) previewVisible.value = true
}

function closeMenu() {
  menuVisible.value = false
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

function handleMenuAction(action) {
  closeMenu()
  emit(action, props.msg)
}

function handleWindowPointer(event) {
  if (menuRef.value?.contains(event.target)) return
  closeMenu()
}

function handleWindowResize() {
  if (menuVisible.value) positionMenu()
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
    <AvatarWithStatus :avatar="senderAvatar" :size="38" />
    <div class="bubble-col">
      <span v-if="!isSelf" class="sender-name">{{ senderNickname }}</span>

      <template v-if="msg.isRevoke">
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span class="revoked">{{ isSelf ? '你撤回了一条消息' : '对方撤回了一条消息' }}</span>
        </div>
      </template>

      <template v-else-if="msg.msgType === 2">
        <img :src="imageUrl()" alt="图片消息" class="image-msg image-plain" @click="openPreview" />
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

      <template v-else-if="msg.msgType === 3">
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <a :href="fileUrl()" target="_blank" rel="noreferrer" class="file-link">{{ fileName() }}</a>
        </div>
      </template>

      <template v-else>
        <div :class="['bubble', isSelf ? 'bubble-self' : 'bubble-other']">
          <span>{{ msgTypeLabel[msg.msgType] }}</span>
        </div>
      </template>

      <div class="meta-row">
        <span v-if="msg.localStatus === 0" class="msg-status">发送中</span>
      </div>
    </div>

    <teleport to="body">
      <div v-if="menuVisible" ref="menuRef" class="msg-context-menu" @click.stop>
        <div v-if="hasPrimaryActions" class="menu-group">
          <button v-if="showEdit" class="menu-item" @click="handleMenuAction('edit')">编辑</button>
          <button v-if="canRevoke()" class="menu-item" @click="handleMenuAction('revoke')">撤回</button>
          <button v-if="showRetry" class="menu-item" @click="handleMenuAction('retry')">重试发送</button>
        </div>

        <div v-if="hasPrimaryActions && hasSecondaryActions" class="menu-divider"></div>

        <div v-if="hasSecondaryActions" class="menu-group">
          <button class="menu-item" @click="handleMenuAction('report')">举报</button>
          <button class="menu-item" @click="handleMenuAction('collect')">收藏</button>
          <button v-if="canPin" class="menu-item" @click="handleMenuAction('pin')">置顶</button>
        </div>

        <div v-if="(hasPrimaryActions || hasSecondaryActions) && hasDangerActions" class="menu-divider"></div>

        <div v-if="hasDangerActions" class="menu-group danger-group">
          <button class="menu-item danger-item" @click="handleMenuAction('delete-failed')">删除</button>
        </div>
      </div>
    </teleport>
  </div>

  <el-dialog v-model="previewVisible" width="auto" class="image-preview-dialog" append-to-body>
    <img :src="imageUrl()" alt="图片预览" class="preview-image" />
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

.revoked,
.expired-file,
.msg-status {
  color: var(--wx-muted);
  font-size: 11px;
}

.image-msg {
  max-width: 240px;
  max-height: 320px;
  display: block;
  border-radius: 12px;
  cursor: zoom-in;
}

.image-plain {
  background: transparent;
  box-shadow: 0 2px 10px rgba(0, 0, 0, .08);
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

.file-link {
  color: inherit;
  text-decoration: underline;
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
  background: rgba(255, 255, 255, .985);
  border: 1px solid rgba(0, 0, 0, .08);
  border-radius: 16px;
  box-shadow: 0 14px 34px rgba(0, 0, 0, .18);
  padding: 8px;
}

.menu-group {
  display: flex;
  flex-direction: column;
}

.menu-divider {
  height: 1px;
  margin: 6px 4px;
  background: rgba(0, 0, 0, .08);
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
  background: rgba(0, 0, 0, .05);
}

.danger-item {
  color: #cf3f36;
}

.danger-item:hover {
  background: rgba(207, 63, 54, .08);
}

.preview-image {
  max-width: min(88vw, 1100px);
  max-height: 80vh;
  display: block;
  border-radius: 12px;
}
</style>
