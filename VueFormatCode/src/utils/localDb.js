const DB_NAME = 'friendmatch_chat'
const STORE = 'messages'
const EXPIRE_MS = 7 * 24 * 60 * 60 * 1000
let db = null

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
    tx.objectStore(STORE).delete(msgId)
    tx.oncomplete = res
    tx.onerror = rej
  })
}

export async function saveMessageToLocal(msg) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const tx = d.transaction(STORE, 'readwrite')
    tx.objectStore(STORE).put(msg)
    tx.oncomplete = res
    tx.onerror = rej
  })
}

export async function getLocalMessages(conversationId, limit = 30) {
  const d = await openDb()
  return new Promise((res, rej) => {
    const req = d.transaction(STORE, 'readonly')
      .objectStore(STORE).index('by_conv').getAll(IDBKeyRange.only(conversationId))
    req.onsuccess = (e) => {
      const list = e.target.result.sort((a, b) => {
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
        const time = new Date(m.createTime || 0).getTime()
        if (!time || time < expireBefore) store.delete(m.msgId)
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
        if (m.localStatus === 2) store.delete(m.msgId)
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
  const req = store.get(Number(msgId))
  req.onsuccess = (e) => {
    const m = e.target.result
    if (m) { m.isRevoke = true; store.put(m) }
  }
}

export async function updateLocalStatus(msgId, status) {
  const d = await openDb()
  const tx = d.transaction(STORE, 'readwrite')
  const store = tx.objectStore(STORE)
  const req = store.get(Number(msgId))
  req.onsuccess = (e) => {
    const m = e.target.result
    if (m) { m.localStatus = status; store.put(m) }
  }
}
