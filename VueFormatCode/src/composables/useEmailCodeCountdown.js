import { onUnmounted, ref } from 'vue'

export function useEmailCodeCountdown(sendRequest, options = {}) {
  const {
    duration = 60,
    emptyEmailMessage = '请输入邮箱',
    sendingText = '发送中',
    idleText = '获取验证码',
    fallbackError = '发送失败，请稍后重试',
  } = options

  const sending = ref(false)
  const countdown = ref(0)

  let timer = null

  function clearTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function startCountdown() {
    countdown.value = duration
    clearTimer()
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        countdown.value = 0
        clearTimer()
      }
    }, 1000)
  }

  function getCodeButtonText() {
    if (countdown.value > 0) return `${countdown.value}s`
    if (sending.value) return sendingText
    return idleText
  }

  async function sendCode(email, handlers = {}) {
    const {
      onBefore,
      onSuccess,
      onError,
      successMessageResolver = (res) => res?.message || '验证码发送成功',
      errorMessageResolver = (error) => error?.response?.message || error?.message || fallbackError,
    } = handlers

    if (!email) {
      onError?.(emptyEmailMessage)
      return false
    }

    sending.value = true
    onBefore?.()

    try {
      const res = await sendRequest(email)
      if (res.code === 200) {
        startCountdown()
        onSuccess?.(res, successMessageResolver(res))
        return true
      }
      onError?.(res.message || fallbackError)
      return false
    } catch (error) {
      onError?.(errorMessageResolver(error))
      return false
    } finally {
      sending.value = false
    }
  }

  onUnmounted(clearTimer)

  return {
    sending,
    countdown,
    getCodeButtonText,
    sendCode,
    clearTimer,
  }
}
