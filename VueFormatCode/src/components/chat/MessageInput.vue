<script setup>
import { ref, computed } from 'vue'
import twemoji from 'twemoji'
import { presignUpload } from '@/api/oss'
import { uploadToQiniu, isLargeUpload } from '@/utils/qiniuUpload'

const props = defineProps({ disabled: { type: Boolean, default: false }, placeholder: { type: String, default: '输入消息，Enter 发送，Shift+Enter 换行' } })
const emit = defineEmits(['send'])
const text = ref('')
const uploading = ref(false)
const uploadText = ref('')
const emojiPanelVisible = ref(false)
const canSendTextMessage = computed(() => !props.disabled && !uploading.value && !!text.value.trim() && text.value.length <= 2000)
const sendButtonText = computed(() => (uploading.value ? '上传中...' : '发送'))
const inputPlaceholder = computed(() => {
  if (props.disabled) return '你已被禁言'
  if (uploading.value) return uploadText.value || '文件上传中...'
  return props.placeholder
})

const emojiList = [
  { value: '😀', label: '笑脸' },
  { value: '😄', label: '大笑' },
  { value: '😂', label: '笑哭' },
  { value: '🥹', label: '感动' },
  { value: '😍', label: '喜欢' },
  { value: '😎', label: '酷' },
  { value: '🤔', label: '思考' },
  { value: '😭', label: '大哭' },
  { value: '😡', label: '生气' },
  { value: '👍', label: '点赞' },
  { value: '🙏', label: '感谢' },
  { value: '🎉', label: '庆祝' },
  { value: '🔥', label: '火' },
  { value: '❤️', label: '爱心' },
  { value: '🐶', label: '小狗' },
  { value: '🌙', label: '月亮' },
]

function handleSend() {
  if (!text.value.trim() || props.disabled) return
  emit('send', { msgType: 1, msgContent: text.value.trim() })
  text.value = ''
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

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file || props.disabled) return
  uploading.value = true
  uploadText.value = '准备上传...'
  try {
    const isImage = file.type.startsWith('image/')
    const msgType = isImage ? 2 : 3
    const presignRes = await presignUpload({ fileName: file.name, msgType, fileSize: file.size })
    if (!presignRes?.data?.uploadUrl || !presignRes?.data?.uploadToken || !presignRes?.data?.key || !presignRes?.data?.fileUrl) {
      throw new Error('上传凭证不完整')
    }
    uploadText.value = isLargeUpload(file.size) ? '分片上传中 0%' : '上传中 0%'
    await uploadToQiniu({ file, uploadUrl: presignRes.data.uploadUrl, uploadToken: presignRes.data.uploadToken, key: presignRes.data.key, onProgress: (progress) => { uploadText.value = `${isLargeUpload(file.size) ? '分片上传中' : '上传中'} ${progress}%` } })
    emit('send', { msgType, msgContent: JSON.stringify({ url: presignRes.data.fileUrl, name: file.name, size: file.size }), fileUrl: presignRes.data.fileUrl, fileName: file.name, fileSize: file.size })
    uploadText.value = ''
  } catch (error) {
    uploadText.value = error?.response?.message || error?.message || '上传失败'
    setTimeout(() => (uploadText.value = ''), 3000)
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}
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
    <textarea v-model="text" :disabled="disabled || uploading" :placeholder="inputPlaceholder" class="msg-textarea" rows="3" @keydown="handleKeydown" />
    <div class="input-actions">
      <div class="left-actions">
        <button type="button" class="emoji-btn" :disabled="disabled || uploading" @click="emojiPanelVisible = !emojiPanelVisible">表情</button>
        <label class="upload-btn" :class="{ disabled: disabled || uploading }">图片/文件<input type="file" :disabled="disabled || uploading" hidden @change="handleFileChange" /></label>
        <span class="char-count" :class="{ warn: text.length > 1800 }">{{ uploading ? uploadText : `${text.length}/2000` }}</span>
      </div>
      <button class="send-btn" :disabled="!canSendTextMessage" @click="handleSend">{{ sendButtonText }}</button>
    </div>
  </div>
</template>

<style scoped>
.msg-input-bar {
  position: relative;
  padding: 14px 16px;
  border-top: 1px solid var(--wx-line);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
}

.msg-textarea {
  width: 100%;
  resize: none;
  line-height: 1.5;
}

.msg-textarea:disabled {
  color: var(--wx-muted);
  cursor: not-allowed;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
}

.left-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.emoji-btn,
.upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 86px;
  height: var(--chat-action-btn-height);
  padding: 0 var(--chat-action-btn-padding-x);
  border: 1px solid var(--chat-action-btn-border);
  border-radius: var(--chat-action-btn-radius);
  background: var(--chat-action-btn-bg);
  color: var(--chat-action-btn-text);
  font-size: var(--chat-action-btn-font);
  font-weight: 600;
}

.emoji-btn:hover,
.upload-btn:hover {
  background: var(--chat-action-btn-hover-bg);
}

.emoji-btn:disabled,
.upload-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.char-count {
  color: var(--wx-muted);
  font-size: 12px;
}

.char-count.warn {
  color: var(--wx-danger);
}

.send-btn {
  padding: 9px 22px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.emoji-panel {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: calc(100% + 10px);
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  background: rgba(255,255,255,.96);
  border: 1px solid var(--wx-line);
  border-radius: 18px;
  box-shadow: 0 10px 32px rgba(0, 0, 0, .12);
  backdrop-filter: blur(12px);
  z-index: 20;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1;
  border: none;
  border-radius: 14px;
  background: rgba(0,0,0,.03);
  cursor: pointer;
  transition: transform .18s ease, background-color .18s ease;
}
.emoji-item:hover { transform: translateY(-1px) scale(1.04); background: rgba(149,236,105,.24); }
.emoji-item:disabled { cursor: not-allowed; opacity: .5; }
.emoji-char { font-size: 28px; line-height: 1; }
</style>
