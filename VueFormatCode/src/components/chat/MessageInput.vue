<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import twemoji from 'twemoji'
import { ChatDotRound, FolderOpened, PictureFilled, Promotion } from '@element-plus/icons-vue'
import { presignUpload } from '@/api/oss'
import { uploadToQiniu, isLargeUpload } from '@/utils/qiniuUpload'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  disabledReason: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  draftText: { type: String, default: '' },
})

const emit = defineEmits(['send', 'update:draftText'])

const text = ref(props.draftText || '')
const uploading = ref(false)
const uploadText = ref('')
const emojiPanelVisible = ref(false)
const textareaRef = ref(null)
const imageInputRef = ref(null)
const fileInputRef = ref(null)
const customHeight = ref(138)
const isDragOver = ref(false)
const isResizing = ref(false)
const resizeStartY = ref(0)
const resizeStartHeight = ref(138)

const MIN_INPUT_HEIGHT = 138
const MAX_INPUT_HEIGHT = 320

const emojiList = [
  { value: '🙂', label: '微笑' },
  { value: '😋', label: '开心' },
  { value: '😄', label: '大笑' },
  { value: '😮', label: '惊讶' },
  { value: '😍', label: '喜欢' },
  { value: '🥳', label: '庆祝' },
  { value: '🤔', label: '思考' },
  { value: '😑', label: '无语' },
  { value: '😤', label: '生气' },
  { value: '👍', label: '点赞' },
  { value: '🙏', label: '感谢' },
  { value: '🎉', label: '庆贺' },
  { value: '🔥', label: '火热' },
  { value: '❤️', label: '爱心' },
  { value: '🐷', label: '小猪' },
  { value: '🌙', label: '月亮' },
]

watch(
  () => props.draftText,
  (value) => {
    if (value !== text.value) {
      text.value = value || ''
      adjustTextareaHeight()
    }
  },
)

watch(text, (value) => {
  emit('update:draftText', value)
})

const canSendTextMessage = computed(() => {
  return !props.disabled && !uploading.value && !!text.value.trim() && text.value.length <= 2000
})

const sendButtonText = computed(() => (uploading.value ? '上传中' : '发送'))

const inputPlaceholder = computed(() => {
  if (props.disabled) return props.disabledReason || ''
  if (uploading.value) return uploadText.value || ''
  return props.placeholder
})

async function adjustTextareaHeight() {
  await nextTick()
  const textarea = textareaRef.value
  if (!textarea) return
  textarea.style.height = '0px'
  const nextHeight = Math.min(Math.max(textarea.scrollHeight, customHeight.value), MAX_INPUT_HEIGHT)
  textarea.style.height = `${nextHeight}px`
}

function focusTextarea() {
  nextTick(() => {
    textareaRef.value?.focus()
  })
}

async function insertText(insertValue, withTrailingSpace = false) {
  const textarea = textareaRef.value
  const safeValue = withTrailingSpace ? `${insertValue} ` : insertValue
  if (!textarea) {
    text.value += safeValue
    await adjustTextareaHeight()
    focusTextarea()
    return
  }

  const start = textarea.selectionStart ?? text.value.length
  const end = textarea.selectionEnd ?? text.value.length
  text.value = `${text.value.slice(0, start)}${safeValue}${text.value.slice(end)}`
  await adjustTextareaHeight()
  await nextTick()
  const nextCursor = start + safeValue.length
  textarea.setSelectionRange(nextCursor, nextCursor)
  textarea.focus()
}

function setDraft(nextValue) {
  text.value = nextValue || ''
  adjustTextareaHeight()
}

function clearDraft() {
  text.value = ''
  adjustTextareaHeight()
}

function handleInput() {
  adjustTextareaHeight()
}

function handleSend() {
  if (!text.value.trim() || props.disabled || uploading.value) return
  emit('send', { msgType: 1, msgContent: text.value.trim() })
  clearDraft()
}

function handleSendEmoji(emoji) {
  if (props.disabled || uploading.value) return
  emit('send', {
    msgType: 4,
    emojiId: twemoji.convert.toCodePoint(emoji.value),
    msgContent: JSON.stringify({ emoji: emoji.value, label: emoji.label }),
  })
  emojiPanelVisible.value = false
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

function toggleEmojiPanel() {
  if (props.disabled || uploading.value) return
  emojiPanelVisible.value = !emojiPanelVisible.value
}

function openImagePicker() {
  if (props.disabled || uploading.value) return
  imageInputRef.value?.click()
}

function openFilePicker() {
  if (props.disabled || uploading.value) return
  fileInputRef.value?.click()
}

function getDragFile(event) {
  const files = Array.from(event.dataTransfer?.files || [])
  if (!files.length) return null
  return files[0]
}

function handleDragEnter(event) {
  const file = getDragFile(event)
  if (!file || props.disabled || uploading.value) return
  event.preventDefault()
  isDragOver.value = true
}

function handleDragOver(event) {
  const file = getDragFile(event)
  if (!file || props.disabled || uploading.value) return
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
  isDragOver.value = true
}

function handleDragLeave(event) {
  const nextTarget = event.relatedTarget
  if (nextTarget && event.currentTarget?.contains?.(nextTarget)) return
  isDragOver.value = false
}

async function handleDrop(event) {
  const file = getDragFile(event)
  isDragOver.value = false
  if (!file || props.disabled || uploading.value) return
  event.preventDefault()
  await uploadAndSendFile(file)
}

function handleResizeMove(event) {
  if (!isResizing.value) return
  const deltaY = resizeStartY.value - event.clientY
  customHeight.value = Math.min(MAX_INPUT_HEIGHT, Math.max(MIN_INPUT_HEIGHT, resizeStartHeight.value + deltaY))
  adjustTextareaHeight()
}

function stopResize() {
  if (!isResizing.value) return
  isResizing.value = false
  window.removeEventListener('mousemove', handleResizeMove)
  window.removeEventListener('mouseup', stopResize)
  document.body.classList.remove('chat-input-resizing')
}

function startResize(event) {
  if (event.button !== 0) return
  isResizing.value = true
  resizeStartY.value = event.clientY
  resizeStartHeight.value = customHeight.value
  window.addEventListener('mousemove', handleResizeMove)
  window.addEventListener('mouseup', stopResize)
  document.body.classList.add('chat-input-resizing')
}

async function uploadAndSendFile(file) {
  uploading.value = true
  uploadText.value = '准备上传...'

  try {
    const isImage = file.type.startsWith('image/')
    const isVideo = file.type.startsWith('video/')
    const msgType = isImage ? 2 : 3
    const presignRes = await presignUpload({
      fileName: file.name,
      msgType,
      fileSize: file.size,
    })

    if (!presignRes?.data?.uploadUrl || !presignRes?.data?.uploadToken || !presignRes?.data?.key || !presignRes?.data?.fileUrl) {
      throw new Error('上传凭证不完整')
    }

    uploadText.value = isLargeUpload(file.size) ? '分片上传中 0%' : '上传中 0%'

    await uploadToQiniu({
      file,
      uploadUrl: presignRes.data.uploadUrl,
      uploadToken: presignRes.data.uploadToken,
      key: presignRes.data.key,
      onProgress: (progress) => {
        uploadText.value = `${isLargeUpload(file.size) ? '分片上传中' : '上传中'} ${progress}%`
      },
    })

    emit('send', {
      msgType,
      msgContent: JSON.stringify({
        url: presignRes.data.fileUrl,
        name: file.name,
        size: file.size,
        mediaType: file.type || (isVideo ? 'video/*' : isImage ? 'image/*' : 'file/*'),
      }),
      mediaType: file.type || (isVideo ? 'video/*' : isImage ? 'image/*' : 'file/*'),
      fileUrl: presignRes.data.fileUrl,
      fileName: file.name,
      fileSize: file.size,
    })

    uploadText.value = ''
  } catch (error) {
    uploadText.value = error?.response?.message || error?.message || '上传失败'
    setTimeout(() => {
      uploadText.value = ''
    }, 3000)
  } finally {
    uploading.value = false
  }
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  if (!file || props.disabled) return
  await uploadAndSendFile(file)
  event.target.value = ''
}

defineExpose({
  clearDraft,
  focusTextarea,
  insertText,
  setDraft,
})

onBeforeUnmount(() => {
  stopResize()
})
</script>

<template>
  <div class="msg-input-bar">
    <div v-if="emojiPanelVisible" class="emoji-panel">
      <button
        v-for="emoji in emojiList"
        :key="emoji.value"
        type="button"
        class="emoji-item"
        :disabled="disabled || uploading"
        :title="emoji.label"
        @click="handleSendEmoji(emoji)"
      >
        <span class="emoji-char">{{ emoji.value }}</span>
      </button>
    </div>

    <div v-if="disabled && disabledReason" class="input-disabled-tip">
      <span class="tip-dot"></span>
      <span>{{ disabledReason }}</span>
    </div>

    <div class="composer-shell" :class="{ disabled }">
      <div
        class="resize-handle"
        :class="{ active: isResizing }"
        title="按住后上下拖动，调整输入框高度"
        @mousedown.prevent="startResize"
      >
        <span></span>
      </div>

      <div
        class="composer-main"
        @dragenter="handleDragEnter"
        @dragover="handleDragOver"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
      >
        <div v-if="isDragOver" class="drag-mask">
          <div class="drag-mask-inner">
            <strong>松开即可发送</strong>
            <span>支持拖拽图片和文件到这里</span>
          </div>
        </div>

        <textarea
          ref="textareaRef"
          v-model="text"
          :disabled="disabled || uploading"
          :placeholder="inputPlaceholder"
          :maxlength="2000"
          class="msg-textarea"
          rows="5"
          @input="handleInput"
          @keydown="handleKeydown"
        />
      </div>

      <div class="composer-footer">
        <div class="left-actions">
          <button
            type="button"
            class="tool-btn"
            :disabled="disabled || uploading"
            title="表情"
            @click="toggleEmojiPanel"
          >
            <el-icon><ChatDotRound /></el-icon>
          </button>

          <button
            type="button"
            class="tool-btn"
            :disabled="disabled || uploading"
            title="图片"
            @click="openImagePicker"
          >
            <el-icon><PictureFilled /></el-icon>
          </button>

          <button
            type="button"
            class="tool-btn"
            :disabled="disabled || uploading"
            title="文件"
            @click="openFilePicker"
          >
            <el-icon><FolderOpened /></el-icon>
          </button>

          <slot name="extra-tools"></slot>

          <input
            ref="imageInputRef"
            type="file"
            accept="image/*"
            :disabled="disabled || uploading"
            hidden
            @change="handleFileChange"
          />

          <input
            ref="fileInputRef"
            type="file"
            :disabled="disabled || uploading"
            hidden
            @change="handleFileChange"
          />

          <span v-if="uploading" class="upload-state">{{ uploadText }}</span>
        </div>

        <div class="right-actions">
          <span v-if="!uploading" class="char-count" :class="{ warn: text.length > 1800 }">
            {{ `${text.length}/2000` }}
          </span>
          <button class="send-btn" :disabled="!canSendTextMessage" @click="handleSend">
            <el-icon><Promotion /></el-icon>
            <span>{{ sendButtonText }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-input-bar {
  position: relative;
  padding: 10px 12px 12px;
  border-top: 1px solid #ebeef2;
  background: #f7f8fa;
}

.input-disabled-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid rgba(245, 108, 108, 0.12);
  border-radius: 12px;
  background: #fff8f8;
  color: #d74c4c;
  font-size: 13px;
  line-height: 1.5;
}

.tip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.composer-shell {
  background: #ffffff;
  border: 1px solid #dde2e8;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  overflow: visible;
}

.composer-shell.disabled {
  background: #fafafa;
}

.resize-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 12px;
  cursor: ns-resize;
  background: #ffffff;
  user-select: none;
}

.resize-handle span {
  width: 42px;
  height: 4px;
  border-radius: 999px;
  background: #d7dce3;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.resize-handle:hover span,
.resize-handle.active span {
  background: #c9cfd8;
  transform: scaleX(1.04);
}

.composer-main {
  position: relative;
  padding: 2px 14px 0;
}

.msg-textarea {
  width: 100%;
  min-height: 138px;
  max-height: 320px;
  padding: 10px 0 0;
  border: none;
  outline: none;
  background: transparent !important;
  resize: none;
  line-height: 1.75;
  font-size: 15px;
  color: #202124;
  box-shadow: none !important;
  border-radius: 0 !important;
  overflow-y: auto;
}

.drag-mask {
  position: absolute;
  inset: 4px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border-radius: 10px;
  background: rgba(236, 240, 244, 0.9);
  backdrop-filter: blur(2px);
  z-index: 2;
  pointer-events: none;
}

.drag-mask-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 24px 16px;
  border: 1px dashed #c7d0da;
  border-radius: 12px;
  color: #5c6570;
  text-align: center;
}

.drag-mask-inner strong {
  font-size: 16px;
  font-weight: 700;
  color: #434b55;
}

.drag-mask-inner span {
  font-size: 13px;
}

.msg-textarea:disabled {
  color: #9aa0a6;
  cursor: not-allowed;
}

.msg-textarea::placeholder {
  color: transparent;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 4px 12px 10px;
}

.left-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.right-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent !important;
  color: #555e68 !important;
  font-size: 17px;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.tool-btn:hover {
  background: #f2f4f7 !important;
  color: #2f353c !important;
}

.tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.upload-state,
.char-count {
  color: #8d96a0;
  font-size: 12px;
}

.char-count.warn {
  color: #d84a4a;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 70px;
  height: 30px;
  padding: 0 12px;
  border: none !important;
  border-radius: 8px;
  background: #eceff3 !important;
  color: #8d96a0 !important;
  font-size: 12px;
  font-weight: 600;
  box-shadow: none;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.send-btn:disabled {
  background: #f3f4f6 !important;
  color: #bcc3cb !important;
  opacity: 1;
  cursor: not-allowed;
}

.send-btn:not(:disabled) {
  background: #eceff3 !important;
  color: #69727d !important;
}

.send-btn:not(:disabled):hover {
  background: #e3e8ee !important;
  color: #545d68 !important;
}

.emoji-panel {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: calc(100% + 8px);
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #dde2e8;
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.12);
  z-index: 20;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  border: none;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.03);
  cursor: pointer;
  transition: transform 0.18s ease, background-color 0.18s ease;
}

.emoji-item:hover {
  transform: translateY(-1px);
  background: rgba(0, 0, 0, 0.06);
}

.emoji-item:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.emoji-char {
  font-size: 26px;
}
</style>
