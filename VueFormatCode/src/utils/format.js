const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(value, fallback = '暂无时间') {
  if (!value) {
    return fallback
  }

  const parsed = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return String(value)
  }

  return dateTimeFormatter.format(parsed)
}

export function clampText(value, maxLength = 80, fallback = '暂无内容') {
  const text = String(value ?? '').trim()
  if (!text) {
    return fallback
  }

  if (text.length <= maxLength) {
    return text
  }

  return `${text.slice(0, maxLength)}...`
}
