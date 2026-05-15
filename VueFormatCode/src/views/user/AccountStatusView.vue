<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyPunishLogs as getPunishRecords } from '@/api/punish'
import { Calendar, Check, Clock, InfoFilled, Loading, Warning } from '@element-plus/icons-vue'

const router = useRouter()
const punishRecords = ref([])
const loading = ref(false)

function getPunishReason(reason) {
  return reason || '未知原因'
}

function isActiveStatus(status) {
  return status === 'active'
}

function getStatusTagType(status) {
  return isActiveStatus(status) ? 'danger' : 'success'
}

function getStatusText(status) {
  return isActiveStatus(status) ? '生效中' : '已结束'
}

function getTimeText(time) {
  return time || '未知时间'
}

function goAppealPage() {
  router.push('/reports')
}

async function loadPunishRecords() {
  loading.value = true
  try {
    const res = await getPunishRecords()
    if (res.code === 200) {
      punishRecords.value = res.data?.records || res.data || []
    }
  } catch (error) {
    console.error('加载处罚记录失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(loadPunishRecords)
</script>

<template>
  <div class="page account-status-page page-container-lg">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">账号状态</h2>
    </div>
    <el-card shadow="hover" class="status-card card-shell">
      <template #header>
        <div class="card-header card-header-inline">
          <el-icon><InfoFilled /></el-icon>
          <span>处罚记录</span>
        </div>
      </template>
      <div v-if="loading" class="loading-container loading-center-lg">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <div v-else>
        <el-empty v-if="!punishRecords.length" description="暂无处罚记录" />
        <el-table v-else :data="punishRecords" stripe class="full-width-table">
          <el-table-column prop="punishReason" label="处罚原因" min-width="200">
            <template #default="scope">
              <div class="punish-reason">
                {{ getPunishReason(scope.row.punishReason) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="120">
            <template #default="scope">
              <el-tag :type="getStatusTagType(scope.row.status)">
                <el-icon v-if="isActiveStatus(scope.row.status)"><Warning /></el-icon>
                <el-icon v-else><Check /></el-icon>
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="生效时间" width="180">
            <template #default="scope">
              <div class="time-info">
                <el-icon class="time-icon"><Calendar /></el-icon>
                <span>{{ getTimeText(scope.row.startTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="endTime" label="结束时间" width="180">
            <template #default="scope">
              <div class="time-info">
                <el-icon class="time-icon"><Clock /></el-icon>
                <span>{{ getTimeText(scope.row.endTime) }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
    <div class="page-footer page-footer-block">
      <el-alert
        title="账号状态说明"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content alert-content-text">
            <p>1. 此处显示您账号的所有处罚记录。</p>
            <p>2. 生效中的处罚会影响您的账号功能。</p>
            <p>3. 如有异议，请先进入举报中心，在对应举报记录中发起申诉。</p>
            <el-button type="primary" size="small" class="inline-top-gap-sm" @click="goAppealPage">
              <span>前往举报中心</span>
            </el-button>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.account-status-page {
}

.punish-reason {
  line-height: 1.4;
  color: #333;
}

.time-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #666;
  font-size: 14px;
}

.time-icon {
  font-size: 14px;
}
</style>
