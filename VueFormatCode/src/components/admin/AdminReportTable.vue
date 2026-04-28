<script setup>
import { computed } from 'vue'
import { Check, Close, Loading, RefreshLeft, Warning } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  pageTitle: { type: String, required: true },
  pageNote: { type: String, default: '' },
  cardTitle: { type: String, required: true },
  emptyText: { type: String, required: true },
  reportedIdLabel: { type: String, required: true },
  reportedIdProp: { type: String, required: true },
  loading: { type: Boolean, default: false },
  reports: { type: Array, default: () => [] },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
})

const emit = defineEmits(['refresh', 'process', 'update:currentPage', 'update:pageSize'])

const normalizedReports = computed(() =>
  props.reports.map((item) => ({
    ...item,
    displayCreateTime: formatDateTime(item.createTime),
  })),
)

function getReportStatusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function getReportStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '已通过'
  if (status === 2) return '已驳回'
  if (status === 3) return '已忽略'
  return '未知状态'
}

function handleSizeChange(size) {
  emit('update:pageSize', size)
  emit('refresh')
}

function handleCurrentChange(page) {
  emit('update:currentPage', page)
  emit('refresh')
}
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">{{ pageTitle }}</h2>
        <p v-if="pageNote" class="admin-page-note">{{ pageNote }}</p>
      </div>
      <el-button type="primary" @click="emit('refresh')">
        <el-icon><RefreshLeft /></el-icon>
        <span>刷新列表</span>
      </el-button>
    </div>

    <el-card>
      <template #header>
        <div class="panel-toolbar-title">
          <el-icon><Warning /></el-icon>
          <span>{{ cardTitle }}</span>
        </div>
      </template>

      <div v-if="loading" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <template v-else>
        <el-empty v-if="!normalizedReports.length" :description="emptyText" />

        <el-table v-else :data="normalizedReports" stripe class="full-width-table">
          <el-table-column prop="id" label="举报 ID" width="110" />
          <el-table-column prop="reporterId" label="举报人 ID" width="120" />
          <el-table-column :prop="reportedIdProp" :label="reportedIdLabel" width="140" />
          <el-table-column prop="reportReason" label="举报原因" width="120" />
          <el-table-column prop="reportContent" label="举报内容" min-width="240">
            <template #default="{ row }">
              <div class="report-table__text">{{ row.reportContent || '暂无补充说明' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="reportStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getReportStatusTagType(row.reportStatus)">
                {{ getReportStatusText(row.reportStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="displayCreateTime" label="举报时间" width="170" />
          <el-table-column prop="adminNote" label="处理备注" min-width="200">
            <template #default="{ row }">
              <div class="report-table__text">{{ row.adminNote || '暂无处理备注' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div v-if="row.reportStatus === 0" class="report-table__actions">
                <el-button type="success" size="small" @click="emit('process', row.id, true)">
                  <el-icon><Check /></el-icon>
                  <span>通过</span>
                </el-button>
                <el-button type="danger" size="small" @click="emit('process', row.id, false)">
                  <el-icon><Close /></el-icon>
                  <span>驳回</span>
                </el-button>
              </div>
              <span v-else class="report-table__done">已处理</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="report-table__pagination">
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

<style scoped>
.report-table__text {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.report-table__actions {
  display: flex;
  gap: 8px;
}

.report-table__done {
  color: var(--text-muted);
  font-size: 13px;
}

.report-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
