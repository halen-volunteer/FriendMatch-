import { useAuthStore } from '@/stores/auth'

const WS_BASE = import.meta.env.VITE_WS_BASE_URL || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`
let ws = null
let heartbeatTimer = null
let reconnectTimer = null
let reconnectCount = 0
let manualClose = false
let _onMessage = null

export function connectWebSocket(onMessage) {
  _onMessage = onMessage
  manualClose = false
  const authStore = useAuthStore()
  if (!authStore.token) return

  ws = new WebSocket(`${WS_BASE}/ws?token=${authStore.token}`)

  ws.onopen = () => {
    reconnectCount = 0
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
    if (!manualClose && reconnectCount < 5) {
      reconnectTimer = setTimeout(() => {
        reconnectCount++
        connectWebSocket(_onMessage)
      }, 3000 * (reconnectCount + 1))
    }
  }

  ws.onerror = () => ws.close()
}

export function disconnectWebSocket() {
  manualClose = true
  clearInterval(heartbeatTimer)
  clearTimeout(reconnectTimer)
  ws?.close()
  ws = null
}

export function buildConversationId(uid1, uid2) {
  return [Number(uid1), Number(uid2)].sort((a, b) => a - b).join('_')
}
