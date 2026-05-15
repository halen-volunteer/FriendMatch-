<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Loading, Star, View } from '@element-plus/icons-vue'
import AdminMessageContent from '@/components/admin/AdminMessageContent.vue'
import { getPendingAppeals, handleAppeal } from '@/api/admin'
import { getAdminMessageReportContext } from '@/api/chat'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)

const {
  currentPage,
  handleCurrentChange,
  handleSizeChange,
  loading,
  pageSize,
  records: appeals,
  total,
  load,
} = usePaginatedRequest(getPendingAppeals)

const hasAppeals = computed(() => appeals.value.length > 0)
const currentAppeal = computed(() => currentDetail.value?.appeal || null)
const currentMessageContext = computed(() => currentDetail.value?.messageContext || null)

function getAppealStatusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function getAppealStatusText(status, appellantType) {
  if (status === 0) return '待处理'
  if (status === 1) {
    return appellantType === 1 ? '已改判违规' : '已撤销处罚'
  }
  if (status === 2) {
    return appellantType === 1 ? '维持未违规' : '维持原处罚'
  }
  return '未知状态'
}

function getReportTypeText(type) {
  if (type === 1) return '用户举报'
  if (type === 2) return '消息举报'
  if (type === 3) return '团队举报'
  return '未知类型'
}

function getAppellantTypeText(type, reportType) {
  if (type === 1) return '举报人'
  if (reportType === 2 && type === 2) return '被举报消息发送者'
  if (reportType === 3 && type === 2) return '被举报团队管理侧'
  if (type === 2) return '被举报方'
  return '未知身份'
}

function getApproveButtonText(row) {
  return row.appellantType === 1 ? '改判违规' : '撤销处罚'
}

function getRejectButtonText(row) {
  return row.appellantType === 1 ? '维持未违规' : '维持处罚'
}

function getApproveReply(row) {
  return row.appellantType === 1
    ? '复核后改判为违规，已按流程执行处罚。'
    : '复核后决定撤销原处罚结果。'
}

function getRejectReply(row) {
  return row.appellantType === 1
    ? '复核后维持未违规结论。'
    : '复核后维持原处罚结果。'
}

function getSafeValue(value, fallback = '-') {
  return value === null || value === undefined || value === '' ? fallback : value
}

function getTextValue(value, fallback = '暂无说明') {
  return value?.trim?.() ? value.trim() : fallback
}

function getContextMessages(list) {
  return Array.isArray(list) ? list : []
}

function hasContextMessages(list) {
  return getContextMessages(list).length > 0
}

function getSenderText(item) {
  return item?.senderNickname || item?.senderId || '未知用户'
}

function isPendingAppeal(status) {
  return status === 0
}

async function processAppeal(row, approve) {
  const response = await handleAppeal({
    appealId: row.id,
    appealStatus: approve ? 1 : 2,
    adminReply: approve ? getApproveReply(row) : getRejectReply(row),
  }).catch(() => null)

  if (isSuccessResponse(response)) {
    ElMessage.success(approve ? `${getApproveButtonText(row)}成功` : `${getRejectButtonText(row)}成功`)
    await load()
    return
  }

  ElMessage.error(response?.message || '处理申诉失败')
}

async function openAppealDetail(row) {
  detailVisible.value = true
  detailLoading.value = true

  const detail = {
    appeal: row,
    messageContext: null,
  }

  if (row.relatedReportType === 2 && row.relatedReportId) {
    const response = await getAdminMessageReportContext(row.relatedReportId).catch(() => null)
    if (isSuccessResponse(response)) {
      detail.messageContext = response.data || null
    }
  }

  currentDetail.value = detail
  detailLoading.value = false
}

function handleDetailClosed() {
  currentDetail.value = null
  detailLoading.value = false
}

onMounted(() => {
  load()
})
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">申诉处理</h2>
        <p class="admin-page-note">根据申诉发起方的身份，分别执行改判违规、维持未违规、撤销处罚或维持处罚。</p>
      </div>
      <el-button type="primary" @click="load">刷新列表</el-button>
    </div>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Star /></el-icon>
          <span>待处理申诉列表</span>
        </div>
      </template>

      <div v-if="loading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <template v-else>
        <el-empty v-if="!hasAppeals" description="暂无待处理申诉" />

        <el-table v-else :data="appeals" stripe class="full-width-table">
          <el-table-column prop="id" label="申诉 ID" width="110" />
          <el-table-column prop="appellantId" label="申诉人 ID" width="120" />
          <el-table-column label="举报类型" width="120">
            <template #default="{ row }">
              <span>{{ getReportTypeText(row.relatedReportType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="申诉身份" width="160">
            <template #default="{ row }">
              <span>{{ getAppellantTypeText(row.appellantType, row.relatedReportType) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="relatedReportId" label="关联举报 ID" width="130" />
          <el-table-column label="关联处罚 ID" width="130">
            <template #default="{ row }">
              <span>{{ getSafeValue(row.relatedPunishId) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="appealRound" label="申诉轮次" width="100" />
          <el-table-column prop="appealReason" label="申诉原因" min-width="240">
            <template #default="{ row }">
              <div class="admin-table-text">{{ getTextValue(row.appealReason) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="appealStatus" label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getAppealStatusTagType(row.appealStatus)">
                {{ getAppealStatusText(row.appealStatus, row.appellantType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <div class="admin-inline-actions">
                <el-button type="primary" plain size="small" @click="openAppealDetail(row)">
                  <el-icon><View /></el-icon>
                  <span>查看详情</span>
                </el-button>
                <template v-if="isPendingAppeal(row.appealStatus)">
                  <el-button type="success" size="small" @click="processAppeal(row, true)">
                    <el-icon><Check /></el-icon>
                    <span>{{ getApproveButtonText(row) }}</span>
                  </el-button>
                  <el-button type="warning" size="small" @click="processAppeal(row, false)">
                    <el-icon><Close /></el-icon>
                    <span>{{ getRejectButtonText(row) }}</span>
                  </el-button>
                </template>
                <span v-else class="admin-muted-text">已处理</span>
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
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="detailVisible" title="申诉详情" width="760px" @closed="handleDetailClosed">
      <div v-if="detailLoading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else-if="currentAppeal" class="stack-list">
        <el-card shadow="never">
          <div class="detail-grid">
            <div><strong>申诉 ID：</strong>{{ getSafeValue(currentAppeal.id) }}</div>
            <div><strong>申诉轮次：</strong>{{ getSafeValue(currentAppeal.appealRound) }}</div>
            <div><strong>举报类型：</strong>{{ getReportTypeText(currentAppeal.relatedReportType) }}</div>
            <div><strong>申诉身份：</strong>{{ getAppellantTypeText(currentAppeal.appellantType, currentAppeal.relatedReportType) }}</div>
            <div><strong>关联举报 ID：</strong>{{ getSafeValue(currentAppeal.relatedReportId) }}</div>
            <div><strong>关联处罚 ID：</strong>{{ getSafeValue(currentAppeal.relatedPunishId) }}</div>
          </div>
          <div class="detail-block">
            <strong>申诉原因：</strong>
            <div class="admin-table-text">{{ getTextValue(currentAppeal.appealReason) }}</div>
          </div>
          <div class="detail-block">
            <strong>申诉证据：</strong>
            <div class="admin-table-text">{{ getTextValue(currentAppeal.appealEvidence) }}</div>
          </div>
        </el-card>

        <el-card v-if="currentMessageContext" shadow="never">
          <template #header>关联消息举报上下文</template>
          <div class="stack-list">
            <div class="admin-table-text admin-message-block">
              <strong>被举报消息：</strong>
              <AdminMessageContent :message="currentMessageContext.targetMsg" />
            </div>
            <div>
              <strong>前文消息</strong>
              <el-empty v-if="!hasContextMessages(currentMessageContext.beforeMsgs)" description="暂无前文消息" />
              <div v-else class="stack-list">
                <article v-for="item in getContextMessages(currentMessageContext.beforeMsgs)" :key="item.id" class="message-report__context">
                  <div class="admin-meta-text message-report__meta">{{ getSenderText(item) }} · {{ formatDateTime(item.createTime) }}</div>
                  <AdminMessageContent :message="item" />
                </article>
              </div>
            </div>
            <div>
              <strong>后文消息</strong>
              <el-empty v-if="!hasContextMessages(currentMessageContext.afterMsgs)" description="暂无后文消息" />
              <div v-else class="stack-list">
                <article v-for="item in getContextMessages(currentMessageContext.afterMsgs)" :key="item.id" class="message-report__context">
                  <div class="admin-meta-text message-report__meta">{{ getSenderText(item) }} · {{ formatDateTime(item.createTime) }}</div>
                  <AdminMessageContent :message="item" />
                </article>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </section>
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

.admin-message-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
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
