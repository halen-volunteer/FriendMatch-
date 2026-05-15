<script setup>
import { computed, onMounted, ref } from 'vue'
import { Check, Close, Loading, View, Warning } from '@element-plus/icons-vue'
import AdminMessageContent from '@/components/admin/AdminMessageContent.vue'
import { getAdminMessageReportContext, getAdminMessageReportList, handleAdminMessageReport } from '@/api/chat'
import { getReportReasonLabel } from '@/constants/report'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const ALL_ADMIN_STATUS = 'all'

const detailVisible = ref(false)
const currentDetail = ref(null)
const remarkMap = ref({})
const selectedAdminStatus = ref(0)

const adminStatusOptions = [
  { label: '待处理', value: 0 },
  { label: '已确认违规', value: 1 },
  { label: '已确认未违规', value: 2 },
  { label: '全部', value: ALL_ADMIN_STATUS },
]

const {
  currentPage,
  loading,
  pageSize,
  records: reports,
  total,
  load,
} = usePaginatedRequest(getAdminMessageReportList)

const hasReports = computed(() => reports.value.length > 0)

function getAdminStatusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function getAdminStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '已确认违规'
  if (status === 2) return '已确认未违规'
  return '未知状态'
}

function isPendingReport(status) {
  return status === 0
}

function getDetailValue(value, fallback = '-') {
  return value === null || value === undefined || value === '' ? fallback : value
}

function getDetailText(value) {
  return value || '暂无说明'
}

function getContextMessages(list) {
  return Array.isArray(list) ? list : []
}

function hasContextMessages(list) {
  return getContextMessages(list).length > 0
}

function getCaseId(row) {
  return row?.caseId || row?.reportId || row?.id
}

function getReasonText(row) {
  return getReportReasonLabel('message', row?.reportReason)
}

function getSenderText(item) {
  return item?.senderNickname || item?.senderId || '未知用户'
}

function buildListQueryParams() {
  if (selectedAdminStatus.value === ALL_ADMIN_STATUS) {
    return {}
  }

  return { adminStatus: selectedAdminStatus.value }
}

async function loadReports() {
  return load(buildListQueryParams())
}

async function handleStatusChange() {
  currentPage.value = 1
  await loadReports()
}

async function handlePageChange(page) {
  currentPage.value = page
  await loadReports()
}

async function handlePageSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  await loadReports()
}

async function openDetail(reportId) {
  const response = await getAdminMessageReportContext(reportId).catch(() => null)
  if (isSuccessResponse(response)) {
    currentDetail.value = response.data || null
    detailVisible.value = true
  }
}

async function handleReport(reportId, adminDecision) {
  const noteMap = {
    1: '确认违规，已按流程执行处罚。',
    2: '确认未违规，已驳回该举报。',
  }

  const response = await handleAdminMessageReport({
    reportId,
    adminDecision,
    adminNote: remarkMap.value[reportId] || noteMap[adminDecision],
  }).catch(() => null)

  if (isSuccessResponse(response)) {
    await loadReports()
  }
}

onMounted(() => {
  loadReports()
})
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">消息举报管理</h2>
        <p class="admin-page-note">默认仅展示待处理举报；如需查看 AI 自动结案或人工已处理记录，可切换状态筛选。</p>
      </div>
      <div class="admin-inline-actions">
        <el-select
          v-model="selectedAdminStatus"
          placeholder="筛选状态"
          style="width: 180px"
          @change="handleStatusChange"
        >
          <el-option
            v-for="item in adminStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="loadReports">刷新列表</el-button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Warning /></el-icon>
          <span>消息举报列表</span>
        </div>
      </template>

      <div v-if="loading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <template v-else>
        <el-empty v-if="!hasReports" description="暂无消息举报" />

        <el-table v-else :data="reports" stripe class="full-width-table">
          <el-table-column prop="caseId" label="举报主单 ID" width="120" />
          <el-table-column prop="messageId" label="消息 ID" width="110" />
          <el-table-column prop="reporterId" label="举报人 ID" width="110" />
          <el-table-column label="举报原因" width="120">
            <template #default="{ row }">
              <span>{{ getReasonText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reportContent" label="补充说明" min-width="180" />
          <el-table-column prop="adminStatus" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getAdminStatusTagType(row.adminStatus)">
                {{ getAdminStatusText(row.adminStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="举报时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="处理备注" min-width="220">
            <template #default="{ row }">
              <el-input
                v-if="isPendingReport(row.adminStatus)"
                v-model="remarkMap[getCaseId(row)]"
                placeholder="输入处理备注"
              />
              <span v-else class="admin-muted-text">
                {{ row.adminNote || '已处理，无需再次填写' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <div class="admin-inline-actions">
                <el-button type="primary" size="small" plain @click="openDetail(getCaseId(row))">
                  <el-icon><View /></el-icon>
                  <span>查看上下文</span>
                </el-button>
                <template v-if="isPendingReport(row.adminStatus)">
                  <el-button type="success" size="small" @click="handleReport(getCaseId(row), 1)">
                    <el-icon><Check /></el-icon>
                    <span>确认违规</span>
                  </el-button>
                  <el-button type="danger" size="small" @click="handleReport(getCaseId(row), 2)">
                    <el-icon><Close /></el-icon>
                    <span>确认未违规</span>
                  </el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-pagination">
          <el-pagination
            :current-page="currentPage"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="detailVisible" title="消息举报上下文" width="760px">
      <div v-if="currentDetail" class="stack-list">
        <el-card shadow="never">
          <div class="admin-table-text">
            <div><strong>举报主单 ID：</strong>{{ getDetailValue(currentDetail.report?.caseId || currentDetail.report?.reportId || currentDetail.report?.id) }}</div>
            <div><strong>举报原因：</strong>{{ getReasonText(currentDetail.report) }}</div>
            <div><strong>补充说明：</strong>{{ getDetailText(currentDetail.report?.reportContent) }}</div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>被举报消息</template>
          <AdminMessageContent :message="currentDetail.targetMsg" />
        </el-card>

        <el-card shadow="never">
          <template #header>前文消息</template>
          <el-empty v-if="!hasContextMessages(currentDetail.beforeMsgs)" description="暂无前文消息" />
          <div v-else class="stack-list">
            <article v-for="item in getContextMessages(currentDetail.beforeMsgs)" :key="item.id" class="message-report__context">
              <div class="admin-meta-text message-report__meta">{{ getSenderText(item) }} · {{ formatDateTime(item.createTime) }}</div>
              <AdminMessageContent :message="item" />
            </article>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>后文消息</template>
          <el-empty v-if="!hasContextMessages(currentDetail.afterMsgs)" description="暂无后文消息" />
          <div v-else class="stack-list">
            <article v-for="item in getContextMessages(currentDetail.afterMsgs)" :key="item.id" class="message-report__context">
              <div class="admin-meta-text message-report__meta">{{ getSenderText(item) }} · {{ formatDateTime(item.createTime) }}</div>
              <AdminMessageContent :message="item" />
            </article>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
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
