import { onUnmounted, ref } from 'vue'

export function useToast(duration = 3000) {
  const toast = ref({ msg: '', type: 'success' })
  let timer = null

  function clearToast() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    toast.value.msg = ''
  }

  function showToast(msg, type = 'success') {
    if (timer) {
      clearTimeout(timer)
    }
    toast.value = { msg, type }
    timer = setTimeout(() => {
      toast.value.msg = ''
      timer = null
    }, duration)
  }

  onUnmounted(() => {
    if (timer) {
      clearTimeout(timer)
    }
  })

  return {
    toast,
    showToast,
    clearToast,
  }
}
