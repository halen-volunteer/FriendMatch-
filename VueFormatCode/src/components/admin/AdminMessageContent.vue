<script setup>
import { computed } from 'vue'
import { Document, Download, VideoPlay } from '@element-plus/icons-vue'

const props = defineProps({
  message: {
    type: Object,
    default: () => ({}),
  },
  emptyText: {
    type: String,
    default: '暂无内容',
  },
})

function parseContent(raw) {
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw || {}
  } catch {
    return raw
  }
}

const parsedContent = computed(() => parseContent(props.message?.msgContent))
const msgType = computed(() => Number(props.message?.msgType ?? 1))

const plainText = computed(() => {
  const rawValue = props.message?.displayText || props.message?.msgContent
  if (typeof rawValue === 'string' && rawValue.trim()) {
    return rawValue.trim()
  }
  if (msgType.value === 4 && typeof parsedContent.value === 'object') {
    return parsedContent.value?.label || parsedContent.value?.emoji || props.emptyText
  }
  return props.emptyText
})

const imageUrl = computed(() => {
  if (msgType.value !== 2) {
    return ''
  }
  return typeof parsedContent.value === 'string'
    ? parsedContent.value
    : parsedContent.value?.url || ''
})

const fileUrl = computed(() => {
  if (msgType.value !== 3) {
    return ''
  }
  return typeof parsedContent.value === 'string'
    ? parsedContent.value
    : parsedContent.value?.url || ''
})

const fileName = computed(() => {
  if (msgType.value !== 3) {
    return ''
  }
  if (typeof parsedContent.value === 'object' && parsedContent.value?.name) {
    return parsedContent.value.name
  }
  const url = fileUrl.value
  return decodeURIComponent(url.split('/').pop()?.split('?')[0] || '下载文件')
})

const fileSizeText = computed(() => {
  if (msgType.value !== 3) {
    return ''
  }
  const size = Number(parsedContent.value?.size ?? 0)
  if (!size || Number.isNaN(size)) {
    return '文件'
  }
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(size >= 10 * 1024 ? 0 : 1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(size >= 10 * 1024 * 1024 ? 0 : 1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
})

const fileExt = computed(() => {
  const name = fileName.value
  const ext = name.includes('.') ? name.split('.').pop() : ''
  return (ext || 'file').slice(0, 4).toUpperCase()
})

const lowerFileExt = computed(() => fileExt.value.toLowerCase())

const isVideoMessage = computed(() => {
  if (msgType.value !== 3) {
    return false
  }
  const mediaType = typeof parsedContent.value === 'object'
    ? String(parsedContent.value?.mediaType || parsedContent.value?.type || '').toLowerCase()
    : ''
  if (mediaType.startsWith('video')) {
    return true
  }
  return ['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv'].includes(lowerFileExt.value)
})

const fileTheme = computed(() => {
  if (['doc', 'docx'].includes(lowerFileExt.value)) return 'word'
  if (['xls', 'xlsx', 'csv'].includes(lowerFileExt.value)) return 'excel'
  if (['ppt', 'pptx'].includes(lowerFileExt.value)) return 'ppt'
  if (['pdf'].includes(lowerFileExt.value)) return 'pdf'
  if (['zip', 'rar', '7z'].includes(lowerFileExt.value)) return 'zip'
  if (['txt', 'md'].includes(lowerFileExt.value)) return 'text'
  if (['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv'].includes(lowerFileExt.value)) return 'video'
  return 'default'
})

const emojiText = computed(() => {
  if (msgType.value !== 4) {
    return ''
  }
  if (typeof parsedContent.value === 'object') {
    return parsedContent.value?.emoji || parsedContent.value?.label || ''
  }
  return typeof parsedContent.value === 'string' ? parsedContent.value : ''
})
</script>

<template>
  <div class="admin-message-content">
    <template v-if="msgType === 2 && imageUrl">
      <el-image
        :src="imageUrl"
        :preview-src-list="[imageUrl]"
        preview-teleported
        fit="cover"
        class="admin-message-content__image"
      />
    </template>

    <template v-else-if="isVideoMessage && fileUrl">
      <div class="admin-message-content__video">
        <video :src="fileUrl" controls preload="metadata" class="admin-message-content__video-player"></video>
        <a :href="fileUrl" target="_blank" rel="noreferrer" class="admin-message-content__download">
          <el-icon><VideoPlay /></el-icon>
          <span>新窗口打开视频</span>
        </a>
      </div>
    </template>

    <template v-else-if="msgType === 3 && fileUrl">
      <a
        :href="fileUrl"
        target="_blank"
        rel="noreferrer"
        :class="['admin-message-content__file', `admin-message-content__file--${fileTheme}`]"
      >
        <span class="admin-message-content__file-main">
          <span class="admin-message-content__file-name">{{ fileName }}</span>
          <span class="admin-message-content__file-meta">{{ fileSizeText }}</span>
        </span>
        <span class="admin-message-content__file-side">
          <span class="admin-message-content__file-ext">
            <el-icon><Document /></el-icon>
            <span>{{ fileExt }}</span>
          </span>
          <span class="admin-message-content__file-download">
            <el-icon><Download /></el-icon>
            <span>下载</span>
          </span>
        </span>
      </a>
    </template>

    <template v-else-if="msgType === 4">
      <div class="admin-message-content__emoji">{{ emojiText || plainText }}</div>
    </template>

    <template v-else>
      <div class="admin-message-content__text">{{ plainText }}</div>
    </template>
  </div>
</template>

<style scoped>
.admin-message-content {
  min-width: 0;
}

.admin-message-content__text,
.admin-message-content__emoji {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
}

.admin-message-content__emoji {
  font-size: 28px;
  line-height: 1.4;
}

.admin-message-content__image {
  display: block;
  width: min(320px, 100%);
  max-height: 320px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f7fa;
}

.admin-message-content__video {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(360px, 100%);
}

.admin-message-content__video-player {
  width: 100%;
  max-height: 320px;
  border-radius: 12px;
  background: #000;
}

.admin-message-content__download {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 13px;
}

.admin-message-content__file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(420px, 100%);
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: #fff;
  color: inherit;
  text-decoration: none;
  box-sizing: border-box;
}

.admin-message-content__file-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.admin-message-content__file-name {
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-word;
}

.admin-message-content__file-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.admin-message-content__file-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.admin-message-content__file-ext,
.admin-message-content__file-download {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.admin-message-content__file-ext {
  padding: 4px 10px;
  border-radius: 999px;
  background: #edf2ff;
  color: #3151b7;
}

.admin-message-content__file-download {
  color: var(--el-color-primary);
}

.admin-message-content__file--pdf .admin-message-content__file-ext {
  background: #fff1f0;
  color: #c0392b;
}

.admin-message-content__file--word .admin-message-content__file-ext {
  background: #eef4ff;
  color: #1d4ed8;
}

.admin-message-content__file--excel .admin-message-content__file-ext {
  background: #edfdf3;
  color: #0f8a4b;
}

.admin-message-content__file--ppt .admin-message-content__file-ext {
  background: #fff5eb;
  color: #d97706;
}

.admin-message-content__file--zip .admin-message-content__file-ext {
  background: #f5f3ff;
  color: #6d28d9;
}

.admin-message-content__file--video .admin-message-content__file-ext {
  background: #ecfeff;
  color: #0f766e;
}
</style>
