const DB_NAME = 'friendmatch_chat'
const STORE = 'messages'
const EXPIRE_MS = 7 * 24 * 60 * 60 * 1000
let db = null

function normalizeLocalMessageId(value) {
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'bigint') return value.toString()
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || !Number.isInteger(value) || !Number.isSafeInteger(value)) {
      return ''
    }
    return String(value)
  }
  return ''
}

function normalizeLocalMessage(msg) {
  if (!msg) return null
  const normalizedId = normalizeLocalMessageId(msg.msgId ?? msg.id)
  if (!normalizedId) {
    return null
  }
  return {
    ...msg,
    msgId: normalizedId,
  }
}

export function openDb() {
  return new Promise((resolve, reject) => {
    if (db) return resolve(db)
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = (e) => {
      const s = e.target.result.createObjectStore(STORE, { keyPath: 'msgId' })
      s.createIndex('by_conv', 'conversationId')
    }
    req.onsuccess = (e) => { db = e.target.result; resolve(db) }
    req.onerror = reject
  })
}

export async function clearConversationMessages(conversationId) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.index('by_conv').getAll(IDBKeyRange.only(conversationId))
    req.onsuccess = (e) => {
      e.target.result.forEach(m => store.delete(m.msgId))
      tx.oncomplete = res
    }
    req.onerror = rej
  })
}

export async function deleteLocalMessage(msgId) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const normalizedId = normalizeLocalMessageId(msgId)
    if (msgId !== undefined && msgId !== null && msgId !== '') {
      store.delete(msgId)
    }
    if (normalizedId && normalizedId !== msgId) {
      store.delete(normalizedId)
    }
    tx.oncomplete = res
    tx.onerror = rej
  })
}

export async function saveMessageToLocal(msg) {
  const normalized = normalizeLocalMessage(msg)
  if (!normalized) return
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).put(normalized)
    tx.oncomplete = res
    tx.onerror = rej
  })
}

export async function getLocalMessages(conversationId, limit = 30) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.index('by_conv').getAll(IDBKeyRange.only(conversationId))
    req.onsuccess = (e) => {
      const normalizedList = []
      e.target.result.forEach((message) => {
        const normalized = normalizeLocalMessage(message)
        if (!normalized) {
          store.delete(message.msgId)
          return
        }
        if (message.msgId !== normalized.msgId) {
          store.delete(message.msgId)
          store.put(normalized)
        }
        normalizedList.push(normalized)
      })
      const list = normalizedList.sort((a, b) => {
        if (a.createTime < b.createTime) return -1
        if (a.createTime > b.createTime) return 1
        return 0
      })
      // 取最新的 limit 条，保持时间升序
      res(list.slice(-limit))
    }
    req.onerror = rej
  })
}

export async function cleanupExpiredMessages(maxAgeMs = EXPIRE_MS) {
  const d = await openDb()
  const expireBefore = Date.now() - maxAgeMs
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.getAll()
    req.onsuccess = (e) => {
      e.target.result.forEach((m) => {
        const normalized = normalizeLocalMessage(m)
        if (!normalized) {
          store.delete(m.msgId)
          return
        }
        if (m.msgId !== normalized.msgId) {
          store.delete(m.msgId)
          store.put(normalized)
        }
        const time = new Date(normalized.createTime || 0).getTime()
        if (!time || time < expireBefore) store.delete(normalized.msgId)
      })
      tx.oncomplete = res
    }
    req.onerror = rej
  })
}

export async function clearFailedMessages(conversationId) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    const req = store.index('by_conv').getAll(IDBKeyRange.only(conversationId))
    req.onsuccess = (e) => {
      e.target.result.forEach((m) => {
        const normalized = normalizeLocalMessage(m)
        if (!normalized) {
          store.delete(m.msgId)
          return
        }
        if (m.msgId !== normalized.msgId) {
          store.delete(m.msgId)
          store.put(normalized)
        }
        if (normalized.localStatus === 2) store.delete(normalized.msgId)
      })
      tx.oncomplete = res
    }
    req.onerror = rej
  })
}

export async function markRevokedLocal(msgId) {
  const d = await openDb()
  const tx = d.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  const normalizedId = normalizeLocalMessageId(msgId)
  const req = store.get(normalizedId || msgId)
  req.onsuccess = (e) => {
    const m = e.target.result
    const normalized = normalizeLocalMessage(m)
    if (normalized) {
      normalized.isRevoke = true
      store.put(normalized)
    }
  }
}

export async function updateLocalStatus(msgId, status) {
  const d = await openDb()
  const tx = d.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  const normalizedId = normalizeLocalMessageId(msgId)
  const req = store.get(normalizedId || msgId)
  req.onsuccess = (e) => {
    const m = e.target.result
    const normalized = normalizeLocalMessage(m)
    if (normalized) {
      normalized.localStatus = status
      store.put(normalized)
    }
  }
}
