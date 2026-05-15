export function useChatMessageList(messages, currentUserId) {
  const TEMP_REPLACE_WINDOW = 60 * 1000

  function normalizeTimeString(value) {
    const text = String(value || '').trim()
    if (!text) return ''

    const withDateSeparator = text.includes(' ') && !text.includes('T')
      ? text.replace(' ', 'T')
      : text

    return withDateSeparator.replace(
      /\.(\d{3})\d+/,
      '.$1',
    )
  }

  function toTimeValue(value) {
    if (!value) return 0
    if (value instanceof Date) return value.getTime()
    if (typeof value === 'number') return Number.isFinite(value) ? value : 0

    const normalized = normalizeTimeString(value)
    if (!normalized) return 0

    const parsed = Date.parse(normalized)
    if (!Number.isNaN(parsed)) return parsed

    const match = normalized.match(
      /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,3}))?$/,
    )
    if (!match) return 0

    const [, year, month, day, hour, minute, second = '0', millisecond = '0'] = match
    return new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hour),
      Number(minute),
      Number(second),
      Number(millisecond.padEnd(3, '0')),
    ).getTime()
  }

  function compareMessageId(a, b) {
    const aText = String(a ?? '').trim()
    const bText = String(b ?? '').trim()
    const integerPattern = /^-?\d+$/

    if (integerPattern.test(aText) && integerPattern.test(bText)) {
      try {
        const aBigInt = BigInt(aText)
        const bBigInt = BigInt(bText)
        if (aBigInt === bBigInt) return 0
        return aBigInt > bBigInt ? 1 : -1
      } catch {
        if (aText.length !== bText.length) return aText.length - bText.length
      }
    }

    return aText.localeCompare(bText)
  }

  function normalizeMessages(rawList) {
    return (rawList || [])
      .map((item) => ({
        ...item,
        msgId: item.msgId ?? item.id,
        msgContent: item.msgContent ?? item.content,
        senderId: item.senderId ?? item.sender_id,
        isSelf: item.isSelf ?? ((item.senderId ?? item.sender_id) === currentUserId),
      }))
      .slice()
      .sort((a, b) => {
        const timeDiff = toTimeValue(a.createTime) - toTimeValue(b.createTime)
        if (timeDiff !== 0) return timeDiff
        return compareMessageId(a.msgId, b.msgId)
      })
  }

  function isTempMessageId(msgId) {
    return String(msgId || '').startsWith('temp_')
  }

  function isPendingLocalMessage(item) {
    const localStatus = Number(item?.localStatus ?? 1)
    return isTempMessageId(item?.msgId) || localStatus === 0 || localStatus === 2
  }

  function isRealMessage(item) {
    return !!item && !isTempMessageId(item?.msgId)
  }

  function normalizeComparableValue(value) {
    return String(value ?? '').trim()
  }

  function getComparablePayload(item) {
    const msgType = Number(item?.msgType ?? 0)
    if (msgType === 4) {
      return normalizeComparableValue(item?.emojiId || item?.msgContent)
    }
    if (msgType === 2 || msgType === 3) {
      return normalizeComparableValue(item?.fileUrl || item?.msgContent || item?.fileName)
    }
    return normalizeComparableValue(item?.msgContent)
  }

  function isSameMessageFingerprint(a, b) {
    if (!a || !b) return false
    return Number(a?.senderId ?? 0) === Number(b?.senderId ?? 0)
      && Number(a?.msgType ?? 0) === Number(b?.msgType ?? 0)
      && getComparablePayload(a) === getComparablePayload(b)
  }

  function isWithinTempReplaceWindow(a, b) {
    return Math.abs(toTimeValue(a?.createTime) - toTimeValue(b?.createTime)) <= TEMP_REPLACE_WINDOW
  }

  function shouldReplaceTempMessage(localItem, realItem) {
    if (!isPendingLocalMessage(localItem) || !isRealMessage(realItem)) return false
    if (localItem?.isRevoke || realItem?.isRevoke) return false
    return isSameMessageFingerprint(localItem, realItem) && isWithinTempReplaceWindow(localItem, realItem)
  }

  function findMatchingMessageIndex(target, list = messages.value) {
    if (!isRealMessage(target)) return -1
    return list.findIndex((item) => shouldReplaceTempMessage(item, target))
  }

  function mergeMessages(serverList = [], localList = [], options = {}) {
    const {
      keepOnlyUnsyncedLocal = false,
    } = options
    const map = new Map()
    normalizeMessages(serverList).forEach(item => map.set(String(item.msgId), item))
    normalizeMessages(localList).forEach(item => {
      const key = String(item.msgId)
      const localStatus = Number(item?.localStatus ?? 1)
      const isUnsyncedLocal = isTempMessageId(item?.msgId) || localStatus === 0 || localStatus === 2

      if (keepOnlyUnsyncedLocal && !map.has(key) && !isUnsyncedLocal) {
        return
      }

      const matchedEntry = Array.from(map.entries()).find(([, serverItem]) => shouldReplaceTempMessage(item, serverItem))
      if (matchedEntry) {
        const [serverKey, serverItem] = matchedEntry
        map.set(serverKey, { ...item, ...serverItem, localStatus: serverItem?.localStatus ?? 1 })
        return
      }

      if (!map.has(key)) {
        map.set(key, item)
      } else {
        map.set(key, { ...item, ...map.get(key) })
      }
    })
    return normalizeMessages(Array.from(map.values()))
  }

  function upsertMessage(message) {
    const normalized = normalizeMessages([message])[0]
    if (!normalized) return null
    const index = messages.value.findIndex(item => String(item.msgId) === String(normalized.msgId))
    if (index >= 0) {
      messages.value[index] = { ...messages.value[index], ...normalized }
    } else {
      const matchingIndex = findMatchingMessageIndex(normalized)
      if (matchingIndex >= 0) {
        messages.value[matchingIndex] = { ...messages.value[matchingIndex], ...normalized, localStatus: normalized?.localStatus ?? 1 }
      } else {
        messages.value = normalizeMessages([...messages.value, normalized])
      }
    }
    return normalized
  }

  function markMessageRevoked(msgId) {
    const index = messages.value.findIndex(item => String(item.msgId) === String(msgId))
    if (index < 0) return false
    messages.value[index] = { ...messages.value[index], isRevoke: true }
    return true
  }

  function shouldShowTime(index) {
    if (index === 0) return true
    const current = messages.value[index]
    const previous = messages.value[index - 1]
    if (!current?.createTime || !previous?.createTime) return false
    return toTimeValue(current.createTime) - toTimeValue(previous.createTime) > 10 * 60 * 1000
  }

  function formatMessageDivider(time) {
    const timeValue = toTimeValue(time)
    if (!timeValue) return ''
    return new Date(timeValue).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  return {
    formatMessageDivider,
    markMessageRevoked,
    mergeMessages,
    normalizeMessages,
    findMatchingMessageIndex,
    shouldShowTime,
    upsertMessage,
  }
}
