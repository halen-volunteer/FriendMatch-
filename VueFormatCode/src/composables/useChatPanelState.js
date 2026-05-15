import { ref } from 'vue'

export function useChatPanelState(loaders = {}) {
  const activePanel = ref('')
  const loadingPanel = ref(false)

  async function openPanel(panel) {
    activePanel.value = activePanel.value === panel ? '' : panel
    if (!activePanel.value) return

    loadingPanel.value = true
    try {
      const loader = loaders[panel]
      if (typeof loader === 'function') {
        await loader()
      }
    } finally {
      loadingPanel.value = false
    }
  }

  function closePanel() {
    activePanel.value = ''
  }

  return {
    activePanel,
    closePanel,
    loadingPanel,
    openPanel,
  }
}
