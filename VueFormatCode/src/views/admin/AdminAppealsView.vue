<script setup>
import { computed, onMounted } from 'vue'
import { Check, Close, Loading, Star } from '@element-plus/icons-vue'
import { getPendingAppeals, handleAppeal } from '@/api/admin'
import { usePaginatedRequest } from '@/composables/usePaginatedRequest'
import { formatDateTime } from '@/utils/format'
import { isSuccessResponse } from '@/utils/response'

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

function getAppealStatusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function getAppealStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '已通过'
  if (status === 2) return '已驳回'
  return '未知状态'
}

function formatRelatedPunishId(relatedPunishId) {
  return relatedPunishId || '-'
}

function formatAppealReason(appealReason) {
  return appealReason || '暂无说明'
}

function isPendingAppeal(status) {
  return status === 0
}

async function processAppeal(appealId, approved) {
  const response = await handleAppeal({
    appealId,
    appealStatus: approved ? 1 : 2,
    adminReply: approved ? '申诉通过，已同步撤销处罚。' : '申诉驳回，维持原处罚决定。',
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
        <h2 class="admin-page-title">申诉处理</h2>
        <p class="admin-page-note">处理用户针对处罚结果发起的申诉申请。</p>
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
          <el-table-column prop="relatedPunishId" label="关联处罚 ID" width="140">
            <template #default="{ row }">
              <span>{{ formatRelatedPunishId(row.relatedPunishId) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="relatedReportId" label="关联举报 ID" width="140" />
          <el-table-column prop="appealRound" label="申诉轮次" width="100" />
          <el-table-column prop="appealReason" label="申诉原因" min-width="240">
            <template #default="{ row }">
              <div class="admin-table-text">{{ formatAppealReason(row.appealReason) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="appealStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getAppealStatusTagType(row.appealStatus)">
                {{ getAppealStatusText(row.appealStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div v-if="isPendingAppeal(row.appealStatus)" class="admin-inline-actions">
                <el-button type="success" size="small" @click="processAppeal(row.id, true)">
                  <el-icon><Check /></el-icon>
                  <span>通过</span>
                </el-button>
                <el-button type="danger" size="small" @click="processAppeal(row.id, false)">
                  <el-icon><Close /></el-icon>
                  <span>驳回</span>
                </el-button>
              </div>
              <span v-else class="admin-muted-text">已处理</span>
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
  </section>
</template>

