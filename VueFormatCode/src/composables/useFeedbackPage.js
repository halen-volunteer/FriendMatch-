import { onMounted, ref } from 'vue'
import { getFeedbackDetail, getMyFeedbackList as getMyFeedbacks, submitFeedback as createFeedback } from '@/api/feedback'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'

const defaultFeedbackForm = () => ({
  feedbackType: 1,
  feedbackTitle: '',
  feedbackContent: '',
  feedbackAttachment: '',
})

const feedbackTypes = [
  { label: '功能问题', value: 1 },
  { label: '违规举报', value: 2 },
  { label: '其他建议', value: 4 },
]

const feedbackTypeLabelMap = {
  1: '功能问题',
  2: '违规举报',
  3: '处罚申诉',
  4: '其他建议',
}

export function useFeedbackPage() {
  const activeTab = ref('create')
  const loading = ref(false)
  const submitting = ref(false)
  const feedbackForm = ref(defaultFeedbackForm())
  const myFeedbacks = ref([])
  const detailDialog = ref(false)
  const currentDetail = ref(null)
  const { toast, showToast } = useToast()

  function getFeedbackTypeLabel(feedbackType) {
    return feedbackTypeLabelMap[feedbackType] || feedbackType
  }

  function getHandleStatusTagType(handleStatus) {
    if (handleStatus === 0) return 'info'
    if (handleStatus === 1) return 'warning'
    if (handleStatus === 2) return 'success'
    return 'danger'
  }

  function isPendingHandleStatus(handleStatus) {
    return handleStatus === 0
  }

  function getHandleStatusText(handleStatus) {
    if (handleStatus === 0) return '待处理'
    if (handleStatus === 1) return '处理中'
    if (handleStatus === 2) return '已解决'
    return '已驳回'
  }

  function formatFeedbackTime(timeText) {
    return timeText || '未知时间'
  }

  function getFeedbackContent(content) {
    return content || '无'
  }

  function getFeedbackReply(reply) {
    return reply || '暂无回复'
  }

  function getDetailId(detail) {
    return detail.feedbackId || detail.id
  }

  function resetFeedbackForm() {
    feedbackForm.value = defaultFeedbackForm()
  }

  async function submitFeedback() {
    submitting.value = true
    try {
      assertSuccess(await createFeedback(feedbackForm.value), '提交反馈失败')
      showToast('反馈已提交')
      resetFeedbackForm()
      activeTab.value = 'my'
      await loadMyFeedbacks()
    } catch (error) {
      showToast(getErrorMessage(error, '提交反馈失败'), 'error')
    } finally {
      submitting.value = false
    }
  }

  async function loadMyFeedbacks() {
    loading.value = true
    try {
      const res = assertSuccess(await getMyFeedbacks({ page: 1, pageSize: 50 }), '加载反馈记录失败')
      myFeedbacks.value = res.data?.records || res.data || []
    } catch (error) {
      showToast(getErrorMessage(error, '加载反馈记录失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function openFeedbackDetail(row) {
    const feedbackId = row.feedbackId || row.id
    if (!feedbackId) return
    try {
      const res = assertSuccess(await getFeedbackDetail(feedbackId), '加载反馈详情失败')
      currentDetail.value = res.data || row
      detailDialog.value = true
    } catch (error) {
      showToast(getErrorMessage(error, '加载反馈详情失败'), 'error')
    }
  }

  onMounted(loadMyFeedbacks)

  return {
    activeTab,
    currentDetail,
    detailDialog,
    feedbackForm,
    feedbackTypes,
    formatFeedbackTime,
    getDetailId,
    getFeedbackContent,
    getFeedbackReply,
    getFeedbackTypeLabel,
    getHandleStatusTagType,
    getHandleStatusText,
    isPendingHandleStatus,
    loadMyFeedbacks,
    loading,
    myFeedbacks,
    openFeedbackDetail,
    submitFeedback,
    submitting,
    toast,
  }
}
