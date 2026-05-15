import { computed, onMounted, ref } from 'vue'
import { getMyReports, getTeamReportStatus, getUserReportStatus, submitAppeal } from '@/api/report'
import { getMessageReportStatus } from '@/api/chat'
import { getReportReasonLabel, getReportTypeLabel, normalizeReportType } from '@/constants/report'
import { useToast } from '@/composables/useToast'
import { assertSuccess, getErrorMessage, pickRecords } from '@/utils/response'

function resolveMaxAppealCount(item) {
  const type = normalizeReportType(item?.reportType)
  if (
    type === 'message' &&
    Number(item?.aiCheckResult) === 1 &&
    Number(item?.adminAction) === 1
  ) {
    return 3
  }
  return 2
}

export function useReportCenterPage() {
  const activeTab = ref('reporter')
  const loading = ref(false)
  const reportStatusDialog = ref(false)
  const appealDialogVisible = ref(false)
  const appealSubmitting = ref(false)
  const currentReportStatus = ref(null)
  const appealTarget = ref(null)
  const list = ref([])
  const { toast, showToast } = useToast()

  const appealForm = ref({
    appealReason: '',
    appealEvidence: '',
  })

  const filteredList = computed(() =>
    list.value.filter((item) => (item.viewRole || 'reporter') === activeTab.value),
  )

  const isMessageAppeal = computed(
    () => normalizeReportType(appealTarget.value?.reportType) === 'message',
  )

  function canAppeal(item) {
    return Boolean(item?.canAppeal)
  }

  function getAppealQuotaText(item) {
    const used = Number(item?.appealCount || 0)
    const total = resolveMaxAppealCount(item)
    return `${used}/${total}`
  }

  function getReportReasonText(item) {
    return getReportReasonLabel(item?.reportType, item?.reportReason)
  }

  function getReportTypeText(reportType) {
    return getReportTypeLabel(reportType)
  }

  async function loadReports() {
    loading.value = true
    try {
      const response = await getMyReports({ page: 1, pageSize: 100 })
      const res = assertSuccess(response, '加载举报记录失败')
      list.value = pickRecords(res)
    } catch (error) {
      showToast(getErrorMessage(error, '加载举报记录失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function openReportStatus(item) {
    if (!item?.caseId && !item?.reportId && !item?.id) return
    const reportId = item.caseId || item.reportId || item.id
    const reportType = normalizeReportType(item.reportType)

    try {
      let response
      if (reportType === 'user') {
        response = await getUserReportStatus(reportId)
      } else if (reportType === 'team') {
        response = await getTeamReportStatus(reportId)
      } else {
        response = await getMessageReportStatus(reportId)
      }

      const res = assertSuccess(response, '加载举报详情失败')
      currentReportStatus.value = {
        ...(item || {}),
        ...(res.data || {}),
        reportId: res.data?.caseId || res.data?.reportId || reportId,
        caseId: res.data?.caseId || item.caseId || reportId,
        reportType,
      }
      reportStatusDialog.value = true
    } catch (error) {
      showToast(getErrorMessage(error, '加载举报详情失败'), 'error')
    }
  }

  function openAppeal(item) {
    if (!item || !canAppeal(item)) return
    appealTarget.value = item
    appealForm.value = {
      appealReason: '',
      appealEvidence: '',
    }
    appealDialogVisible.value = true
  }

  async function submitAppealForm() {
    const target = appealTarget.value
    if (!target) {
      showToast('未找到对应的举报记录', 'error')
      return
    }
    if (!appealForm.value.appealReason.trim()) {
      showToast('请输入申诉理由', 'warning')
      return
    }

    appealSubmitting.value = true
    try {
      const normalizedType = normalizeReportType(target.reportType)
      const relatedReportType = normalizedType === 'user' ? 1 : normalizedType === 'message' ? 2 : 3

      const response = await submitAppeal({
        relatedReportId: target.caseId || target.reportId || target.id,
        relatedReportType,
        appealReason: appealForm.value.appealReason.trim(),
        appealEvidence: isMessageAppeal.value ? '' : appealForm.value.appealEvidence.trim(),
      })
      assertSuccess(response, '提交申诉失败')
      showToast('申诉已提交，请耐心等待管理员处理', 'success')
      appealDialogVisible.value = false
      await loadReports()
    } catch (error) {
      showToast(getErrorMessage(error, '提交申诉失败'), 'error')
    } finally {
      appealSubmitting.value = false
    }
  }

  onMounted(loadReports)

  return {
    activeTab,
    appealDialogVisible,
    appealForm,
    appealSubmitting,
    appealTarget,
    canAppeal,
    currentReportStatus,
    filteredList,
    getAppealQuotaText,
    getReportReasonText,
    getReportTypeText,
    isMessageAppeal,
    loadReports,
    loading,
    openAppeal,
    openReportStatus,
    reportStatusDialog,
    submitAppealForm,
    toast,
  }
}
