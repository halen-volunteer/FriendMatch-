<script setup>
import { computed, ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import AdminMessageContent from '@/components/admin/AdminMessageContent.vue'
import AdminReportTable from '@/components/admin/AdminReportTable.vue'
import { useAdminReportPage } from '@/composables/useAdminReportPage'
import { getAdminTeamReportContext, getAdminTeamReports, handleAdminTeamReport } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)

const {
  currentPage,
  loading,
  pageSize,
  processReport,
  reports,
  total,
  load,
} = useAdminReportPage(getAdminTeamReports, handleAdminTeamReport, {
  confirmViolationNote: '团队举报成立，已按流程执行团队处罚。',
  confirmNoViolationNote: '团队举报不成立，已驳回该举报。',
})

const chatContext = computed(() => currentDetail.value?.chatContext || {})
const contextMessages = computed(() => chatContext.value.messages || [])

function getSafeValue(value, fallback = '-') {
  return value === null || value === undefined || value === '' ? fallback : value
}

function getTextValue(value, fallback = '暂无说明') {
  return value?.trim?.() ? value.trim() : fallback
}

async function openDetail(reportId) {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  const response = await getAdminTeamReportContext(reportId).catch(() => null)
  if (isSuccessResponse(response)) {
    currentDetail.value = response.data || null
  }
  detailLoading.value = false
}

function handleDetailClosed() {
  currentDetail.value = null
  detailLoading.value = false
}
</script>

<template>
  <div class="admin-report-page">
    <AdminReportTable
      page-title="团队举报管理"
      page-note="统一处理针对团队资料、公告和成员行为的举报。"
      card-title="团队举报列表"
      empty-text="暂无团队举报"
      reported-id-label="被举报团队 ID"
      reported-id-prop="reportedTeamId"
      :loading="loading"
      :reports="reports"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      @refresh="load"
      @process="processReport"
      @view-detail="openDetail"
      @update:current-page="currentPage = $event"
      @update:page-size="pageSize = $event"
    />

    <el-dialog
      v-model="detailVisible"
      title="团队举报详情"
      width="760px"
      destroy-on-close
      @closed="handleDetailClosed"
    >
      <div v-if="detailLoading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else-if="currentDetail" class="stack-list">
        <el-card shadow="never">
          <div class="detail-grid">
            <div><strong>举报 ID：</strong>{{ getSafeValue(currentDetail.report?.id) }}</div>
            <div><strong>举报人 ID：</strong>{{ getSafeValue(currentDetail.report?.reporterId) }}</div>
            <div><strong>被举报团队 ID：</strong>{{ getSafeValue(currentDetail.report?.reportedTeamId) }}</div>
            <div><strong>举报原因：</strong>{{ getSafeValue(currentDetail.report?.reportReason) }}</div>
            <div><strong>举报时间：</strong>{{ formatDateTime(currentDetail.report?.createTime) }}</div>
            <div><strong>当前状态：</strong>{{ getSafeValue(currentDetail.report?.reportStatus) }}</div>
          </div>
          <div class="detail-block">
            <strong>举报说明：</strong>
            <div class="admin-table-text">{{ getTextValue(currentDetail.report?.reportContent) }}</div>
          </div>
          <div class="detail-block">
            <strong>举报证据：</strong>
            <div class="admin-table-text">{{ getTextValue(currentDetail.report?.reportEvidence) }}</div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>被举报团队资料</template>
          <div class="detail-grid">
            <div><strong>团队 ID：</strong>{{ getSafeValue(currentDetail.reportedTeam?.id) }}</div>
            <div><strong>团队名称：</strong>{{ getSafeValue(currentDetail.reportedTeam?.teamName) }}</div>
            <div><strong>创建人 ID：</strong>{{ getSafeValue(currentDetail.reportedTeam?.creatorId) }}</div>
            <div><strong>全员禁言：</strong>{{ getSafeValue(currentDetail.reportedTeam?.teamAllMute, 0) }}</div>
          </div>
          <div class="detail-block">
            <strong>团队简介：</strong>
            <div class="admin-table-text">{{ getTextValue(currentDetail.reportedTeam?.teamIntro) }}</div>
          </div>
          <div class="detail-block">
            <strong>团队标签：</strong>
            <div class="admin-table-text">{{ getTextValue(currentDetail.reportedTeam?.teamTags) }}</div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>
            举报前 {{ chatContext.windowMinutes || 5 }} 分钟群聊上下文
            <span v-if="chatContext.truncated" class="detail-tip">仅展示前 {{ contextMessages.length }} 条，完整命中 {{ chatContext.totalCount }} 条</span>
          </template>
          <el-empty v-if="!contextMessages.length" description="该时间窗口内暂无群聊记录" />
          <div v-else class="stack-list">
            <article v-for="item in contextMessages" :key="item.id" class="message-report__context">
              <div class="admin-meta-text message-report__meta">
                {{ item.senderId }} · {{ formatDateTime(item.createTime) }}
              </div>
              <AdminMessageContent :message="item" empty-text="暂无可展示内容" />
            </article>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
}

.detail-block {
  margin-top: 16px;
}

.detail-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.message-report__context {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-soft);
}

.message-report__context:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.message-report__meta {
  margin-bottom: 4px;
}
</style>
