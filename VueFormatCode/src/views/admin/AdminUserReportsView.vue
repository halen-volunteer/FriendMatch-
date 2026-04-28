<script setup>
import AdminReportTable from '@/components/admin/AdminReportTable.vue'
import { useAdminReportPage } from '@/composables/useAdminReportPage'
import { getAdminUserReports, handleAdminUserReport } from '@/api/admin'

const {
  currentPage,
  loading,
  pageSize,
  processReport,
  reports,
  total,
  load,
} = useAdminReportPage(getAdminUserReports, handleAdminUserReport, {
  approvedNote: '用户举报核验通过，已记录处理。',
  rejectedNote: '用户举报证据不足，暂不通过。',
})
</script>

<template>
  <AdminReportTable
    page-title="用户举报管理"
    page-note="统一处理针对用户资料、行为和账号状态的举报。"
    card-title="用户举报列表"
    empty-text="暂无用户举报"
    reported-id-label="被举报用户 ID"
    reported-id-prop="reportedUserId"
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
