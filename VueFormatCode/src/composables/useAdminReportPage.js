import { onMounted } from 'vue'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { isSuccessResponse } from '@/utils/response'

const DEFAULT_NOTES = {
  approved: '举报属实，已按规范处理。',
  rejected: '举报信息不足，当前不予通过。',
}

export function useAdminReportPage(fetchReports, handleReport, options = {}) {
  const pagination = usePaginatedRequest(fetchReports)

  async function processReport(reportId, approved) {
    const response = await handleReport({
      reportId,
      reportStatus: approved ? 1 : 2,
      adminNote: approved ? options.approvedNote ?? DEFAULT_NOTES.approved : options.rejectedNote ?? DEFAULT_NOTES.rejected,
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
