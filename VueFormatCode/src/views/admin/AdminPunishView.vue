<script setup>
import { computed, ref } from 'vue'
import { Loading, Warning } from '@element-plus/icons-vue'
import { cancelPunish, executePunish, getPunishLogs, getViolationCount } from '@/api/admin'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

const punishForm = ref({
  punishUserId: '',
  punishReason: '',
  msgId: '',
  aiAuditResult: '',
  operateType: 2,
})

const queryUserId = ref('')
const violationInfo = ref({
  totalViolationNum: 0,
  latestViolationTime: '',
})

const cancelDialogVisible = ref(false)
const cancelForm = ref({
  punishLogId: '',
  cancelReason: '',
})

const {
  currentPage,
  handleCurrentChange,
  handleSizeChange,
  loading,
  pageSize,
  records: punishRecords,
  total,
  load,
} = usePaginatedRequest((params) => getPunishLogs({ ...params, userId: queryUserId.value || undefined }))

const canLoadRecords = computed(() => Boolean(queryUserId.value))
const hasPunishRecords = computed(() => punishRecords.value.length > 0)

function getPunishTypeText(type) {
  if (type === 1) return '全局禁言'
  if (type === 2) return '永久封号'
  return `类型 ${type ?? '-'}`
}

function getPunishTypeTag(type) {
  if (type === 1) return 'warning'
  if (type === 2) return 'danger'
  return 'info'
}

function getPunishStatusText(row) {
  return row.isCancel === 1 ? '已撤销' : '生效中'
}

function getPunishStatusTag(row) {
  return row.isCancel === 1 ? 'info' : 'danger'
}

function formatPunishEndTime(row) {
  const fallbackText = row.punishType === 2 ? '永久生效' : '暂无时间'
  return formatDateTime(row.punishEndTime || row.endTime, fallbackText)
}

function formatPunishStartTime(row) {
  return formatDateTime(row.punishStartTime || row.startTime)
}

function formatCancelReason(cancelReason) {
  return cancelReason || '暂无撤销原因'
}

function isCancelableRow(row) {
  return row.isCancel !== 1
}

async function loadViolationInfo() {
  if (!queryUserId.value) {
    violationInfo.value = { totalViolationNum: 0, latestViolationTime: '' }
    return
  }

  const response = await getViolationCount({ userId: queryUserId.value }).catch(() => null)

  if (isSuccessResponse(response)) {
    violationInfo.value = response.data || { totalViolationNum: 0, latestViolationTime: '' }
  }
}

async function searchPunishRecords() {
  currentPage.value = 1
  await Promise.all([load(), loadViolationInfo()])
}

async function submitPunish() {
  const payload = {
    punishUserId: Number(punishForm.value.punishUserId),
    punishReason: punishForm.value.punishReason,
    msgId: punishForm.value.msgId ? String(punishForm.value.msgId).trim() : undefined,
    aiAuditResult: punishForm.value.aiAuditResult || undefined,
    operateType: punishForm.value.operateType,
  }

  const response = await executePunish(payload).catch(() => null)

  if (isSuccessResponse(response)) {
    queryUserId.value = String(punishForm.value.punishUserId)
    punishForm.value = {
      punishUserId: '',
      punishReason: '',
      msgId: '',
      aiAuditResult: '',
      operateType: 2,
    }
    await searchPunishRecords()
  }
}

function openCancelDialog(row) {
  cancelForm.value.punishLogId = row.id
  cancelForm.value.cancelReason = row.cancelReason || ''
  cancelDialogVisible.value = true
}

function closeCancelDialog() {
  cancelDialogVisible.value = false
  cancelForm.value = {
    punishLogId: '',
    cancelReason: '',
  }
}

async function submitCancelPunish() {
  const response = await cancelPunish({
    punishLogId: cancelForm.value.punishLogId,
    cancelReason: cancelForm.value.cancelReason,
  }).catch(() => null)

  if (isSuccessResponse(response)) {
    closeCancelDialog()
    await searchPunishRecords()
  }
}
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">处罚中心</h2>
        <p class="admin-page-note">处罚接口只接收用户、原因和关联消息，由后端自动按违规次数判定处罚等级。</p>
      </div>
      <el-button type="primary" :disabled="!canLoadRecords" @click="searchPunishRecords">刷新记录</el-button>
    </div>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Warning /></el-icon>
          <span>新增处罚</span>
        </div>
      </template>

      <div class="panel-banner">
        <div class="panel-banner-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div>
          <p class="panel-banner-title">后端会自动计算处罚力度</p>
          <p class="panel-banner-text">当前前端只负责提交被处罚用户、处罚原因和可选的关联消息 ID，不再手动传处罚类型和时长。</p>
        </div>
      </div>

      <el-form :model="punishForm" label-width="110px" class="panel-form">
        <div class="panel-form-grid">
          <el-form-item label="用户 ID" class="panel-col-full">
            <el-input v-model="punishForm.punishUserId" placeholder="请输入被处罚用户 ID" class="stretch-input" />
          </el-form-item>

          <el-form-item label="关联消息 ID">
            <el-input v-model="punishForm.msgId" placeholder="选填，关联违规消息" class="stretch-input" />
          </el-form-item>

          <el-form-item label="操作类型">
            <el-select v-model="punishForm.operateType" class="stretch-input">
              <el-option label="管理员手动处理" :value="2" />
              <el-option label="系统自动处理" :value="1" />
            </el-select>
          </el-form-item>

          <el-form-item label="处罚原因" class="panel-col-full">
            <el-input
              v-model="punishForm.punishReason"
              type="textarea"
              :rows="4"
              placeholder="请输入处罚原因"
              class="stretch-input"
            />
          </el-form-item>

          <el-form-item label="审核备注" class="panel-col-full">
            <el-input
              v-model="punishForm.aiAuditResult"
              type="textarea"
              :rows="3"
              placeholder="选填，用于补充 AI 或人工审核说明"
              class="stretch-input"
            />
          </el-form-item>
        </div>

        <div class="panel-form-actions">
          <el-button type="primary" class="panel-primary-button" @click="submitPunish">执行处罚</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Warning /></el-icon>
          <span>处罚记录</span>
        </div>
      </template>

      <div class="panel-form">
        <div class="panel-form-grid">
          <el-form-item label="查询用户 ID" class="panel-col-full">
            <el-input v-model="queryUserId" placeholder="请输入要查询处罚记录的用户 ID" class="stretch-input" />
          </el-form-item>
        </div>

        <div class="panel-form-actions">
          <el-button @click="loadViolationInfo">查看违规次数</el-button>
          <el-button type="primary" @click="searchPunishRecords">查询记录</el-button>
        </div>
      </div>

      <div class="punish-page__meta">
        <span>累计违规：{{ violationInfo.totalViolationNum || 0 }}</span>
        <span>最近违规：{{ formatDateTime(violationInfo.latestViolationTime, '暂无记录') }}</span>
      </div>

      <div v-if="loading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <template v-else>
        <el-empty v-if="!canLoadRecords" description="先输入用户 ID 再查询处罚记录" />
        <el-empty v-else-if="!hasPunishRecords" description="暂无处罚记录" />

        <el-table v-else :data="punishRecords" stripe class="full-width-table">
          <el-table-column prop="id" label="记录 ID" width="110" />
          <el-table-column prop="punishUserId" label="用户 ID" width="110" />
          <el-table-column label="处罚类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getPunishTypeTag(row.punishType)">
                {{ getPunishTypeText(row.punishType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="punishReason" label="处罚原因" min-width="220" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getPunishStatusTag(row)">{{ getPunishStatusText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">
              {{ formatPunishStartTime(row) }}
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="170">
            <template #default="{ row }">
              {{ formatPunishEndTime(row) }}
            </template>
          </el-table-column>
          <el-table-column prop="cancelReason" label="撤销原因" min-width="180">
            <template #default="{ row }">
              <span>{{ formatCancelReason(row.cancelReason) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="isCancelableRow(row)" type="warning" size="small" @click="openCancelDialog(row)">
                撤销
              </el-button>
              <span v-else class="admin-muted-text">已撤销</span>
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

    <el-dialog v-model="cancelDialogVisible" title="撤销处罚" width="520px" @closed="closeCancelDialog">
      <el-input
        v-model="cancelForm.cancelReason"
        type="textarea"
        :rows="4"
        placeholder="请输入撤销原因，便于后续审计"
      />

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="closeCancelDialog">取消</el-button>
          <el-button type="warning" @click="submitCancelPunish">确认撤销</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.punish-page__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-card);
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}
</style>
