import { formatConversationPreview } from '@/utils/chatPreview'

export function escapeHtml(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export function renderHighlightedText(text, keyword = '') {
  const safe = escapeHtml(text || '')
  const kw = keyword.trim()
  if (!kw) return safe
  const escapedKw = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safe.replace(new RegExp(`(${escapedKw})`, 'gi'), '<mark>$1</mark>')
}

export function renderConversationPreviewHighlight(item, keyword = '') {
  const preview = formatConversationPreview(item?.msgType, item?.msgContent)
  return item?.msgType === 1 || item?.msgType === 5
    ? renderHighlightedText(preview, keyword)
    : escapeHtml(preview)
}
