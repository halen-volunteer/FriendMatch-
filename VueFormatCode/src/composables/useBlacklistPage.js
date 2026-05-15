import { onMounted, ref } from 'vue'
import { getBlacklist, removeBlacklist } from '@/api/user'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'

function normalizeBlacklistItem(item) {
  return {
    ...item,
    blackUserId: item.blackUserId ?? item.userId,
  }
}

function createDefaultConfirmState() {
  return {
    visible: false,
    blackUserId: null,
    loading: false,
  }
}

export function useBlacklistPage() {
  const list = ref([])
  const confirmState = ref(createDefaultConfirmState())
  const { toast, showToast } = useToast()

  async function load() {
    try {
      const res = assertSuccess(await getBlacklist({ page: 1, pageSize: 100 }), '加载黑名单失败')
      list.value = (res.data?.records || res.data || []).map(normalizeBlacklistItem)
    } catch (error) {
      showToast(getErrorMessage(error, '加载黑名单失败'), 'error')
    }
  }

  function handleRemove(blackUserId) {
    if (!blackUserId) return
    confirmState.value = {
      visible: true,
      blackUserId,
      loading: false,
    }
  }

  function closeConfirm() {
    confirmState.value = createDefaultConfirmState()
  }

  async function submitRemove() {
    const blackUserId = confirmState.value.blackUserId
    if (!blackUserId || confirmState.value.loading) return
    confirmState.value.loading = true
    try {
      assertSuccess(await removeBlacklist(blackUserId), '解除拉黑失败')
      showToast('已解除拉黑')
      closeConfirm()
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '解除拉黑失败'), 'error')
    } finally {
      confirmState.value.loading = false
    }
  }

  function getUserInitial(userNickname) {
    return userNickname?.charAt(0) || '用'
  }

  function formatCreateTime(createTime) {
    return createTime ? new Date(createTime).toLocaleString('zh-CN') : '未知'
  }

  onMounted(load)

  return {
    closeConfirm,
    confirmState,
    formatCreateTime,
    getUserInitial,
    handleRemove,
    list,
    load,
    submitRemove,
    toast,
  }
}
