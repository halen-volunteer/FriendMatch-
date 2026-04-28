import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useNoticeStore = defineStore('notice', () => {
  const unreadCount = ref(0)

  function setUnreadCount(n) { unreadCount.value = n }
  function increment() { unreadCount.value++ }
  function decrement(n = 1) { unreadCount.value = Math.max(0, unreadCount.value - n) }
  function clear() { unreadCount.value = 0 }

  return { unreadCount, setUnreadCount, increment, decrement, clear }
})
