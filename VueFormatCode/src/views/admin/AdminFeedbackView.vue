<script setup>
import { computed, onMounted, ref } from 'vue'
import { Loading, Message } from '@element-plus/icons-vue'
import { getFeedbacks, replyFeedback } from '@/api/admin'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const dialogVisible = ref(false)
const activeFeedback = ref(null)
const handleForm = ref({
  handleContent: '',
})

const {
  currentPage,
  handleCurrentChange,
  handleSizeChange,
  loading,
  pageSize,
  records: feedbacks,
  total,
  load,
} = usePaginatedRequest(getFeedbacks)

const hasFeedbacks = computed(() => feedbacks.value.length > 0)

function getFeedbackTypeLabel(type) {
  const typeMap = {
    1: '功能问题',
    2: '违规举报',
    3: '处罚申诉',
    4: '其他建议',
  }

  return typeMap[type] || `类型 ${type ?? '-'}`
}

function getHandleStatusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'primary'
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  return 'info'
}

function getHandleStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '处理中'
  if (status === 2) return '已解决'
  if (status === 3) return '已驳回'
  return '未知状态'
}

function formatFeedbackTitle(feedbackTitle) {
  return feedbackTitle || '未填写标题'
}

function formatFeedbackText(feedbackText, fallbackText) {
  return feedbackText || fallbackText
}

function openDialog(row) {
  activeFeedback.value = row
  handleForm.value.handleContent = row.handleContent || ''
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  activeFeedback.value = null
  handleForm.value.handleContent = ''
}

async function submitHandle(handleStatus) {
  if (!activeFeedback.value) {
    return
  }

  const response = await replyFeedback({
    feedbackId: activeFeedback.value.id,
    handleStatus,
    handleContent: handleForm.value.handleContent,
  }).catch(() => null)

  if (isSuccessResponse(response)) {
    closeDialog()
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
        <h2 class="admin-page-title">反馈管理</h2>
        <p class="admin-page-note">集中处理用户反馈、申诉和其他后台问题。</p>
      </div>
      <el-button type="primary" @click="load">刷新列表</el-button>
    </div>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Message /></el-icon>
          <span>反馈列表</span>
        </div>
      </template>

      <div v-if="loading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <template v-else>
        <el-empty v-if="!hasFeedbacks" description="暂无反馈" />

        <el-table v-else :data="feedbacks" stripe class="full-width-table">
          <el-table-column prop="id" label="反馈 ID" width="110" />
          <el-table-column prop="userId" label="用户 ID" width="110" />
          <el-table-column label="反馈类型" width="120">
            <template #default="{ row }">
              <el-tag type="info">{{ getFeedbackTypeLabel(row.feedbackType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="feedbackTitle" label="标题" min-width="180">
            <template #default="{ row }">
              <span>{{ formatFeedbackTitle(row.feedbackTitle) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="feedbackContent" label="反馈内容" min-width="240">
            <template #default="{ row }">
              <div class="admin-table-text">{{ formatFeedbackText(row.feedbackContent, '暂无内容') }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="handleStatus" label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getHandleStatusTagType(row.handleStatus)">
                {{ getHandleStatusText(row.handleStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="handleContent" label="处理回复" min-width="220">
            <template #default="{ row }">
              <div class="admin-table-text">{{ formatFeedbackText(row.handleContent, '暂无处理回复') }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="openDialog(row)">
                处理
              </el-button>
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

    <el-dialog v-model="dialogVisible" title="处理反馈" width="560px" @closed="closeDialog">
      <div class="stack-list">
        <div v-if="activeFeedback" class="feedback-page__summary">
          <div><strong>反馈 ID：</strong>{{ activeFeedback.id }}</div>
          <div><strong>用户 ID：</strong>{{ activeFeedback.userId }}</div>
          <div><strong>反馈类型：</strong>{{ getFeedbackTypeLabel(activeFeedback.feedbackType) }}</div>
        </div>

        <el-input
          v-model="handleForm.handleContent"
          type="textarea"
          :rows="5"
          placeholder="请输入处理回复内容"
        />
      </div>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="closeDialog">取消</el-button>
          <el-button @click="submitHandle(1)">标记处理中</el-button>
          <el-button type="success" @click="submitHandle(2)">标记已解决</el-button>
          <el-button type="danger" @click="submitHandle(3)">驳回反馈</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.feedback-page__summary {
  display: grid;
  gap: var(--space-xs);
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}
</style>
