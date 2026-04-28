<script setup>
import AdminReportTable from '@/components/admin/AdminReportTable.vue'
import { useAdminReportPage } from '@/composables/useAdminReportPage'
import { getAdminTeamReports, handleAdminTeamReport } from '@/api/admin'

const {
  currentPage,
  loading,
  pageSize,
  processReport,
  reports,
  total,
  load,
} = useAdminReportPage(getAdminTeamReports, handleAdminTeamReport, {
  approvedNote: '队伍举报核验通过，已记录处理。',
  rejectedNote: '队伍举报证据不足，暂不通过。',
})
</script>

<template>
  <AdminReportTable
    page-title="队伍举报管理"
    page-note="统一处理针对队伍资料、公告和成员行为的举报。"
    card-title="队伍举报列表"
    empty-text="暂无队伍举报"
    reported-id-label="被举报队伍 ID"
    reported-id-prop="reportedTeamId"
    :loading="loading"
    :reports="reports"
    :current-page="currentPage"
    :page-size="pageSize"
    :total="total"
    @refresh="load"
    @process="processReport"
    @update:current-page="currentPage = $event"
    @update:page-size="pageSize = $event"
  />
</template>
