<script setup>
import { computed, onMounted, ref } from 'vue'
import { Check, Close, Loading, View, Warning } from '@element-plus/icons-vue'
import { getAdminMessageReportContext, getAdminMessageReportList, handleAdminMessageReport } from '@/api/chat'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const detailVisible = ref(false)
const currentDetail = ref(null)
const remarkMap = ref({})

const {
  currentPage,
  handleCurrentChange,
  handleSizeChange,
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
  if (status === 1) return '已处理'
  if (status === 2) return '已驳回'
  return '未知状态'
}

function isPendingReport(status) {
  return status === 0
}

function getDetailValue(value, fallback = '-') {
  return value || fallback
}

function getDetailText(value) {
  return value || '暂无说明'
}

function getMessageContent(value) {
  return value || '暂无内容'
}

function getContextMessages(list) {
  return list || []
}

function hasContextMessages(list) {
  return getContextMessages(list).length > 0
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
    1: '维持原处罚决定。',
    2: '撤销处罚，当前认定不违规。',
    3: '确认违规并执行处罚。',
  }

  const response = await handleAdminMessageReport({
    reportId,
    adminDecision,
    adminNote: remarkMap.value[reportId] || noteMap[adminDecision],
  }).catch(() => null)

  if (isSuccessResponse(response)) {
    await load()
  }
}

onMounted(() => {
  load()
})
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">消息举报管理</h2>
        <p class="admin-page-note">查看消息上下文后再做处理，避免误判。</p>
      </div>
      <el-button type="primary" @click="load">刷新列表</el-button>
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
          <el-table-column prop="id" label="举报 ID" width="110" />
          <el-table-column prop="messageId" label="消息 ID" width="110" />
          <el-table-column prop="reporterId" label="举报人 ID" width="110" />
          <el-table-column prop="reportReason" label="举报原因" width="110" />
          <el-table-column prop="reportContent" label="补充说明" min-width="180" />
          <el-table-column prop="adminStatus" label="状态" width="110">
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
          <el-table-column label="处理备注" min-width="180">
            <template #default="{ row }">
              <el-input v-model="remarkMap[row.id]" placeholder="输入处理备注" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <div class="admin-inline-actions">
                <el-button type="primary" size="small" plain @click="openDetail(row.id)">
                  <el-icon><View /></el-icon>
                  <span>查看上下文</span>
                </el-button>
                <template v-if="isPendingReport(row.adminStatus)">
                  <el-button type="success" size="small" @click="handleReport(row.id, 1)">
                    <el-icon><Check /></el-icon>
                    <span>维持处罚</span>
                  </el-button>
                  <el-button type="warning" size="small" @click="handleReport(row.id, 2)">
                    <el-icon><Close /></el-icon>
                    <span>撤销处罚</span>
                  </el-button>
                  <el-button type="danger" size="small" @click="handleReport(row.id, 3)">
                    <el-icon><Warning /></el-icon>
                    <span>确认违规</span>
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
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="detailVisible" title="消息举报上下文" width="760px">
      <div v-if="currentDetail" class="stack-list">
        <el-card shadow="never">
          <div class="admin-table-text">
            <div><strong>举报 ID：</strong>{{ getDetailValue(currentDetail.report?.id) }}</div>
            <div><strong>举报原因：</strong>{{ getDetailValue(currentDetail.report?.reportReason) }}</div>
            <div><strong>补充说明：</strong>{{ getDetailText(currentDetail.report?.reportContent) }}</div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>被举报消息</template>
          <div class="admin-table-text">{{ getMessageContent(currentDetail.targetMsg?.msgContent) }}</div>
        </el-card>

        <el-card shadow="never">
          <template #header>前文消息</template>
          <el-empty v-if="!hasContextMessages(currentDetail.beforeMsgs)" description="暂无前文消息" />
          <div v-else class="stack-list">
            <article v-for="item in getContextMessages(currentDetail.beforeMsgs)" :key="item.id" class="message-report__context">
              <div class="admin-meta-text message-report__meta">{{ item.senderId }} · {{ formatDateTime(item.createTime) }}</div>
              <div class="admin-table-text">{{ getMessageContent(item.msgContent) }}</div>
            </article>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>后文消息</template>
          <el-empty v-if="!hasContextMessages(currentDetail.afterMsgs)" description="暂无后文消息" />
          <div v-else class="stack-list">
            <article v-for="item in getContextMessages(currentDetail.afterMsgs)" :key="item.id" class="message-report__context">
              <div class="admin-meta-text message-report__meta">{{ item.senderId }} · {{ formatDateTime(item.createTime) }}</div>
              <div class="admin-table-text">{{ getMessageContent(item.msgContent) }}</div>
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
