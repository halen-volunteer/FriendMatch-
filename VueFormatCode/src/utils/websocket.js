import { useAuthStore } from '@/stores/auth'
import { getWebSocketTicket } from '@/api/auth'

const WS_BASE = import.meta.env.VITE_WS_BASE_URL || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`
const CONNECT_JITTER_MAX_MS = 800
let ws = null
let heartbeatTimer = null
let reconnectTimer = null
let reconnectCount = 0
let manualClose = false
let _onMessage = null

export async function connectWebSocket(onMessage) {
  _onMessage = onMessage
  manualClose = false
  const authStore = useAuthStore()
  if (!authStore.token) return

  // 1. 首次建连和重连前都做一个很轻的随机抖动，避免大量客户端在同一毫秒同时握手。
  await sleep(Math.floor(Math.random() * CONNECT_JITTER_MAX_MS))

  let ticket = ''
  try {
    const res = await getWebSocketTicket()
    ticket = res.data?.ticket || ''
  } catch {
    scheduleReconnect()
    return
  }
  if (!ticket) {
    scheduleReconnect()
    return
  }

  ws = new WebSocket(`${WS_BASE}/ws?ticket=${encodeURIComponent(ticket)}`)

  ws.onopen = () => {
    reconnectCount = 0
    clearInterval(heartbeatTimer)
    heartbeatTimer = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN)
        ws.send(JSON.stringify({ type: 'heartbeat' }))
    }, 30000)
  }

  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      _onMessage && _onMessage(msg)
    } catch { /* ignore */ }
  }

  ws.onclose = () => {
    clearInterval(heartbeatTimer)
    scheduleReconnect()
  }

  ws.onerror = () => ws.close()
}

function scheduleReconnect() {
  clearTimeout(reconnectTimer)
  if (!manualClose && reconnectCount < 5) {
    reconnectTimer = setTimeout(() => {
      reconnectCount++
      connectWebSocket(_onMessage)
    }, 3000 * (reconnectCount + 1))
  }
}

export function disconnectWebSocket() {
  manualClose = true
  clearInterval(heartbeatTimer)
  clearTimeout(reconnectTimer)
  ws?.close()
  ws = null
}

function sleep(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

export function buildConversationId(uid1, uid2) {
  return [Number(uid1), Number(uid2)].sort((a, b) => a - b).join('_')
}
