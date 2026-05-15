import { onMounted } from 'vue'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { isSuccessResponse } from '@/utils/response'

export function useAdminReportPage(fetchReports, handleReport, options = {}) {
  const pagination = usePaginatedRequest(fetchReports)

  async function processReport(reportId, reportStatus) {
    const response = await handleReport({
      reportId,
      reportStatus,
      adminNote:
        reportStatus === 1
          ? options.confirmViolationNote ?? '确认违规，已按流程处理。'
          : options.confirmNoViolationNote ?? '确认未违规，已驳回该举报。',
    }).catch(() => null)

    if (isSuccessResponse(response)) {
      await pagination.load()
    }
  }

  onMounted(() => {
    pagination.load()
  })

  return {
    ...pagination,
    reports: pagination.records,
    processReport,
  }
}
