function parseMessageContent(raw) {
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw || {}
  } catch {
    return raw
  }
}

function getFileName(parsed, fallback = '') {
  if (typeof parsed === 'object' && parsed?.name) return parsed.name
  if (typeof parsed === 'string') return fallback || parsed
  return fallback
}

function getFileExt(name = '') {
  if (!name.includes('.')) return ''
  return name.split('.').pop().toLowerCase()
}

function isVideoPayload(parsed, raw) {
  const mediaType = typeof parsed === 'object'
    ? String(parsed?.mediaType || parsed?.type || '').toLowerCase()
    : ''
  if (mediaType.startsWith('video')) return true
  const name = getFileName(parsed, typeof raw === 'string' ? raw : '')
  return ['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv'].includes(getFileExt(name))
}

export function formatConversationPreview(msgType, msgContent) {
  if (msgType === 1 || msgType === 5) {
    return typeof msgContent === 'string' ? msgContent : ''
  }

  if (msgType === 2) {
    return '[图片]'
  }

  if (msgType === 4) {
    return '[表情包]'
  }

  if (msgType === 3) {
    const parsed = parseMessageContent(msgContent)
    if (isVideoPayload(parsed, msgContent)) {
      return '[视频]'
    }

    const fileName = getFileName(parsed, '文件')
    return `[文件]${fileName || '文件'}`
  }

  return typeof msgContent === 'string' ? msgContent : ''
}
