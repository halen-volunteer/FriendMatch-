<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyAppeals } from '@/api/report'
import { ChatDotRound, Check, Clock, InfoFilled, RefreshLeft, Warning } from '@element-plus/icons-vue'
import { useToast } from '@/composables/useToast'
import { assertSuccess, getErrorMessage, pickRecords } from '@/utils/response'

const router = useRouter()
const list = ref([])
const { toast, showToast } = useToast()
const loading = ref(false)

const reportTypeOptions = [
  { value: 1, label: '用户举报' },
  { value: 2, label: '消息举报' },
  { value: 3, label: '团队举报' },
]

function getAppealStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '已通过'
  if (status === 2) return '已驳回'
  return '未知状态'
}

function getAppealStatusTagType(status) {
  if (status === 0) return 'info'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

function goReportCenter() {
  router.push('/reports')
}

async function loadAppeals() {
  loading.value = true
  try {
    const response = await getMyAppeals({ page: 1, pageSize: 100 })
    const res = assertSuccess(response, '加载申诉记录失败')
    list.value = pickRecords(res)
  } catch (error) {
    showToast(getErrorMessage(error, '加载申诉记录失败'), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadAppeals)
</script>

<template>
  <div class="page appeal-page">
    <div class="page-header">
      <div>
        <h2>申诉记录</h2>
        <p>申诉提交入口已经收口到举报中心的具体举报记录中，这里只保留申诉处理进度查询。</p>
      </div>
    </div>

    <el-alert
      v-if="toast.msg"
      :title="toast.msg"
      :type="toast.type"
      show-icon
      class="toast"
      @close="toast.msg = ''"
    />

    <el-card shadow="hover" class="appeal-card">
      <div class="tab-panel">
        <div class="intro-banner warm">
          <div class="intro-icon">
            <el-icon><InfoFilled /></el-icon>
          </div>
          <div class="intro-content">
            <h3>如何发起新的申诉</h3>
            <p>请先进入举报中心，在具体举报记录上点击“发起申诉”，系统会自动带上对应的举报上下文。</p>
          </div>
        </div>

        <div class="list-toolbar">
          <div class="toolbar-title">
            <span>我的申诉</span>
          </div>
          <div class="toolbar-actions">
            <el-button @click="goReportCenter">前往举报中心</el-button>
            <el-button type="primary" plain @click="loadAppeals">
              <el-icon><RefreshLeft /></el-icon>
              <span>刷新列表</span>
            </el-button>
          </div>
        </div>

        <div v-if="loading" class="loading-container">
          <el-icon class="is-loading"><RefreshLeft /></el-icon>
        </div>

        <div v-else>
          <el-empty v-if="!list.length" description="暂无申诉记录" class="appeal-empty" />
          <div v-else class="appeal-list">
            <el-card
              v-for="item in list"
              :key="item.id || `${item.relatedReportId}-${item.createTime}`"
              shadow="never"
              class="appeal-record-card"
            >
              <div class="record-head">
                <div class="record-meta">
                  <span class="record-id">举报 ID：{{ item.relatedReportId || '-' }}</span>
                  <span class="record-type">{{ reportTypeOptions.find(option => option.value === item.relatedReportType)?.label || '未知类型' }}</span>
                  <el-tag :type="getAppealStatusTagType(item.appealStatus)" effect="light" round>
                    <el-icon v-if="item.appealStatus === 0"><Clock /></el-icon>
                    <el-icon v-else-if="item.appealStatus === 1"><Check /></el-icon>
                    <el-icon v-else><Warning /></el-icon>
                    {{ getAppealStatusText(item.appealStatus) }}
                  </el-tag>
                </div>
                <span class="record-time">{{ item.createTime || '未知时间' }}</span>
              </div>

              <div class="record-section">
                <div class="record-label">申诉原因</div>
                <div class="record-text">{{ item.appealReason || '无' }}</div>
              </div>

              <div class="record-section">
                <div class="record-label">申诉轮次</div>
                <div class="record-text">第 {{ item.appealRound || 1 }} 次</div>
              </div>

              <div class="record-section reply">
                <div class="record-label with-icon">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>处理结果</span>
                </div>
                <div class="record-text">{{ item.adminReply || '待处理' }}</div>
              </div>
            </el-card>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.appeal-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #1f1f1f;
  margin: 0 0 8px;
}

.page-header p {
  margin: 0;
  color: #8a8a8a;
  font-size: 14px;
}

.toast {
  margin-bottom: 16px;
}

.appeal-card {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #ececec;
}

.tab-panel {
  padding: 8px 4px 4px;
}

.intro-banner {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 14px;
  margin-bottom: 20px;
}

.intro-banner.warm {
  background: linear-gradient(135deg, rgba(255, 250, 217, 0.95), rgba(244, 248, 210, 0.92));
  border: 1px solid rgba(224, 213, 128, 0.45);
}

.intro-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.75);
  color: #8c6b00;
  flex-shrink: 0;
}

.intro-content h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #3a3200;
}

.intro-content p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #6f6632;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.toolbar-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #333;
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.loading-container {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.appeal-empty {
  --el-empty-padding: 40px 0;
}

.appeal-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.appeal-record-card {
  border-radius: 14px;
  border: 1px solid #ececec;
  background: #fcfcfc;
}

.record-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.record-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.record-id {
  font-size: 14px;
  font-weight: 600;
  color: #2f2f2f;
}

.record-type {
  font-size: 12px;
  color: #7d7d7d;
  background: #f4f4f4;
  padding: 4px 10px;
  border-radius: 999px;
}

.record-time {
  color: #9a9a9a;
  font-size: 12px;
}

.record-section {
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #efefef;
}

.record-section + .record-section {
  margin-top: 10px;
}

.record-section.reply {
  background: #fafafa;
}

.record-label {
  font-size: 13px;
  font-weight: 600;
  color: #666;
  margin-bottom: 8px;
}

.record-label.with-icon {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.record-text {
  font-size: 14px;
  line-height: 1.75;
  color: #333;
  word-break: break-word;
}

@media (max-width: 768px) {
  .appeal-page {
    padding: 16px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .record-head,
  .list-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
