import { ref } from 'vue'

export function useActionSubmitting(initialState = {}) {
  const actionSubmitting = ref({ ...initialState })

  async function runAction(actionKey, handler) {
    if (actionSubmitting.value[actionKey]) return
    actionSubmitting.value[actionKey] = true
    try {
      return await handler()
    } finally {
      actionSubmitting.value[actionKey] = false
    }
  }

  return {
    actionSubmitting,
    runAction,
  }
}
