<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getMyAppeals, submitAppeal } from '@/api/report'
import { InfoFilled, RefreshLeft, Clock, Check, Warning, Document, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const activeTab = ref('submit')
const form = ref({ relatedReportId: '', relatedReportType: 1, appealReason: '' })
const reportTypeOptions = [
  { value: 1, label: '用户举报' },
  { value: 2, label: '消息举报' },
  { value: 3, label: '团队举报' }
]
const list = ref([])
const toast = ref({ msg: '', type: 'success' })
const loading = ref(false)
const submitting = ref(false)

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

watch(() => route.query, (query) => {
  const reportId = String(query.reportId || '')
  const reportType = Number(query.reportType || 1)
  if (!reportId) return
  form.value.relatedReportId = reportId
  form.value.relatedReportType = [1, 2, 3].includes(reportType) ? reportType : 1
  activeTab.value = 'submit'
}, { immediate: true, deep: true })

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

async function loadAppeals() {
  loading.value = true
  try {
    const res = await getMyAppeals({ page: 1, pageSize: 50 }).catch(() => ({ code: 400, data: [] }))
    if (res.code === 200) list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const res = await submitAppeal({
      relatedReportId: Number(form.value.relatedReportId),
      relatedReportType: Number(form.value.relatedReportType),
      appellantType: 1,
      appealReason: form.value.appealReason,
    }).catch(() => ({ code: 400, message: '提交失败' }))

    if (res.code === 200) {
      showToast('申诉已提交')
      form.value = { relatedReportId: '', relatedReportType: 1, appealReason: '' }
      activeTab.value = 'list'
      loadAppeals()
    } else {
      showToast(res.message || '提交失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadAppeals)
</script>

<template>
  <div class="page appeal-page">
    <div class="page-header">
      <div>
        <h2>申诉中心</h2>
        <p>针对举报处理结果提交申诉，并查看当前处理进度。</p>
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
      <el-tabs v-model="activeTab" class="appeal-tabs">
        <el-tab-pane label="提交申诉" name="submit">
          <div class="tab-panel">
            <div class="intro-banner warm">
              <div class="intro-icon">
                <el-icon><InfoFilled /></el-icon>
              </div>
              <div class="intro-content">
                <h3>请先确认关联举报信息</h3>
                <p>填写对应的举报 ID、举报类型以及详细申诉原因，有助于平台更快核实处理。</p>
              </div>
            </div>

            <el-form :model="form" label-position="top" class="appeal-form">
              <div class="form-grid">
                <el-form-item label="关联举报 ID">
                  <el-input
                    v-model="form.relatedReportId"
                    placeholder="请输入举报记录 ID"
                    class="full-width"
                  />
                  <div class="form-tip">现在可以从“举报中心 → 我的举报 → 发起申诉”自动带入。</div>
                </el-form-item>

                <el-form-item label="举报类型">
                  <el-select v-model="form.relatedReportType" class="full-width">
                    <el-option
                      v-for="option in reportTypeOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item label="申诉原因" class="full-span">
                  <el-input
                    v-model="form.appealReason"
                    type="textarea"
                    :rows="6"
                    resize="none"
                    placeholder="请尽量完整描述申诉理由、事件背景及补充说明。"
                    class="full-width"
                  />
                </el-form-item>
              </div>

              <div class="form-actions">
                <el-button type="primary" :loading="submitting" class="submit-btn" @click="handleSubmit">
                  提交申诉
                </el-button>
              </div>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的申诉" name="list">
          <div class="tab-panel">
            <div class="list-toolbar">
              <div class="toolbar-title">
                <el-icon><Document /></el-icon>
                <span>申诉记录</span>
              </div>
              <el-button type="primary" plain @click="loadAppeals">
                <el-icon><RefreshLeft /></el-icon>
                <span>刷新列表</span>
              </el-button>
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
        </el-tab-pane>
      </el-tabs>
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

.appeal-form {
  padding: 6px 8px 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 20px;
}

.full-span {
  grid-column: 1 / -1;
}

.full-width {
  width: 100%;
}

.form-tip {
  font-size: 12px;
  color: #9a9a9a;
  margin-top: 6px;
  line-height: 1.6;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

.submit-btn {
  min-width: 120px;
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

:deep(.appeal-tabs .el-tabs__nav-wrap::after) {
  background-color: #efefef;
}

:deep(.appeal-tabs .el-tabs__item) {
  height: 44px;
  font-size: 15px;
  font-weight: 600;
}

:deep(.appeal-tabs .el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
}

:deep(.appeal-form .el-form-item__label) {
  padding-bottom: 8px;
  color: #4a4a4a;
  font-weight: 600;
}

@media (max-width: 768px) {
  .appeal-page {
    padding: 16px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .form-grid,
  .record-head,
  .list-toolbar {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .form-actions {
    justify-content: stretch;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
