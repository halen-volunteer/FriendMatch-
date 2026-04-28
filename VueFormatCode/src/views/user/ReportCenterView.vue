<script setup>
import { ref, onMounted } from 'vue'
import { reportUser, reportTeam, reportMessage, getMyReports } from '@/api/report'
import { Warning, Check, User, Phone, Message, Upload, RefreshLeft, Clock, Loading } from '@element-plus/icons-vue'
const activeTab = ref('create')
const loading = ref(false)
const submitting = ref(false)

// 发起举报表单
const reportForm = ref({
  targetType: 'user',
  targetId: '',
  reportReason: 1,
  reportContent: '',
  reportEvidence: ''
})

// 我的举报列表
const myReports = ref([])

const reportTypes = [
  { label: '垃圾信息', value: 1 },
  { label: '欺诈行为', value: 2 },
  { label: '色情内容', value: 3 },
  { label: '暴力内容', value: 4 },
  { label: '侵犯隐私', value: 5 },
  { label: '其他', value: 6 }
]

function getTargetTagType(targetType) {
  if (targetType === 'user') return 'info'
  if (targetType === 'team') return 'success'
  return 'warning'
}

function getTargetTypeText(targetType) {
  if (targetType === 'user') return '用户'
  if (targetType === 'team') return '团队'
  return '消息'
}

function getTargetName(targetName) {
  return targetName || '未知'
}

function getReportTypeLabel(reportReason) {
  return reportTypes.find((item) => item.value === reportReason)?.label || reportReason
}

function getStatusTagType(status) {
  if (status === 'pending') return 'info'
  if (status === 'processed') return 'success'
  return 'warning'
}

function getStatusText(status) {
  if (status === 'pending') return '待处理'
  if (status === 'processed') return '已处理'
  return '已驳回'
}

function isPendingStatus(status) {
  return status === 'pending'
}

function isProcessedStatus(status) {
  return status === 'processed'
}

function formatCreateTime(createTime) {
  return createTime || '未知时间'
}

function getReportContent(reportContent) {
  return reportContent || '无'
}

async function submitReport() {
  submitting.value = true
  try {
    const payloadBase = {
      reportReason: Number(reportForm.value.reportReason),
      reportContent: reportForm.value.reportContent
    }
    let res
    if (reportForm.value.targetType === 'user') {
      res = await reportUser({
        reportedUserId: Number(reportForm.value.targetId),
        ...payloadBase
      })
    } else if (reportForm.value.targetType === 'team') {
      res = await reportTeam({
        teamId: Number(reportForm.value.targetId),
        reportReason: Number(reportForm.value.reportReason),
        reportContent: reportForm.value.reportContent
      })
    } else {
      res = await reportMessage({
        messageId: Number(reportForm.value.targetId),
        reportReason: Number(reportForm.value.reportReason),
        reportContent: reportForm.value.reportContent
      })
    }
    if (res.code === 200) {
      loadMyReports()
      reportForm.value = {
        targetType: 'user',
        targetId: '',
        reportReason: 1,
        reportContent: '',
        reportEvidence: ''
      }
    }
  } catch (error) {
    console.error('提交举报失败:', error)
  } finally {
    submitting.value = false
  }
}

async function loadMyReports() {
  loading.value = true
  try {
    const res = await getMyReports({ page: 1, pageSize: 50 })
    if (res.code === 200) {
      myReports.value = res.data?.records || res.data || []
    }
  } catch (error) {
    console.error('加载举报记录失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMyReports()
})
</script>

<template>
  <div class="page report-center-page page-container-lg">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">举报中心</h2>
    </div>
    <el-card shadow="hover" class="report-card">
      <el-tabs v-model="activeTab" class="report-tabs">
        <el-tab-pane label="发起举报" name="create">
          <el-form :model="reportForm" label-width="120px" class="report-form">
            <el-form-item label="举报对象类型">
              <el-radio-group v-model="reportForm.targetType">
                <el-radio-button label="user">
                  <el-icon><User /></el-icon>
                  <span>用户</span>
                </el-radio-button>
                <el-radio-button label="team">
                  <el-icon><Phone /></el-icon>
                  <span>团队</span>
                </el-radio-button>
                <el-radio-button label="message">
                  <el-icon><Message /></el-icon>
                  <span>消息</span>
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="举报对象ID">
              <el-input
                v-model="reportForm.targetId"
                placeholder="请输入举报对象的ID"
                class="id-input"
              />
            </el-form-item>
            <el-form-item label="举报类型">
              <el-select
                v-model="reportForm.reportReason"
                placeholder="请选择举报类型"
                class="type-select"
              >
                <el-option
                  v-for="type in reportTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="详细描述">
              <el-input
                v-model="reportForm.reportContent"
                type="textarea"
                :rows="4"
                placeholder="请详细描述举报原因"
                class="description-input"
              />
            </el-form-item>
            <el-form-item label="证据上传">
              <el-upload
                class="upload-demo"
                action=""
                :auto-upload="false"
                :on-change="() => {}"
                :on-remove="() => {}"
                multiple
              >
                <el-button type="primary">
                  <el-icon><Upload /></el-icon>
                  <span>上传证据</span>
                </el-button>
                <template #tip>
                  <div class="upload-tip">
                    支持上传图片、视频等证据文件
                  </div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="submitReport"
                :loading="submitting"
                class="submit-btn"
              >
                <span>提交举报</span>
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="我的举报" name="my">
          <div class="my-reports">
            <div class="reports-header">
              <el-button type="primary" @click="loadMyReports">
                <el-icon><RefreshLeft /></el-icon>
                <span>刷新列表</span>
              </el-button>
            </div>
            <div v-if="loading" class="loading-container loading-center-lg">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
            <div v-else>
              <el-empty v-if="!myReports.length" description="暂无举报记录" />
              <el-table v-else :data="myReports" stripe class="full-width-table">
                <el-table-column prop="targetName" label="举报对象" width="180">
                  <template #default="scope">
                    <div class="target-info">
                      <el-tag :type="getTargetTagType(scope.row.targetType)">
                        {{ getTargetTypeText(scope.row.targetType) }}
                      </el-tag>
                      <span class="target-name">{{ getTargetName(scope.row.targetName) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="reportReason" label="举报类型" width="120">
                  <template #default="scope">
                    <el-tag type="warning">
                      {{ getReportTypeLabel(scope.row.reportReason) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="处理状态" width="120">
                  <template #default="scope">
                    <el-tag :type="getStatusTagType(scope.row.status)">
                      <el-icon v-if="isPendingStatus(scope.row.status)"><Clock /></el-icon>
                      <el-icon v-else-if="isProcessedStatus(scope.row.status)"><Check /></el-icon>
                      <el-icon v-else><Warning /></el-icon>
                      {{ getStatusText(scope.row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="举报时间" width="180">
                  <template #default="scope">
                    <span>{{ formatCreateTime(scope.row.createTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="reportContent" label="举报原因" min-width="200">
                  <template #default="scope">
                    <div class="report-reason">
                      {{ getReportContent(scope.row.reportContent) }}
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <div class="page-footer page-footer-block">
      <el-alert
        title="举报说明"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content alert-content-text">
            <p>• 请确保举报内容真实有效，不得恶意举报</p>
            <p>• 提交举报后，平台会尽快处理</p>
            <p>• 恶意举报可能会导致账号受到处罚</p>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>

.report-card {
  border-radius: 12px;
  overflow: hidden;
}

.report-tabs {
  min-height: 400px;
}

.report-form {
  padding: 20px 0;
}

.id-input,
.type-select,
.description-input {
  width: 400px;
}

.upload-demo {
  margin-top: 10px;
}

.upload-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.submit-btn {
  margin-top: 10px;
}

.my-reports {
  padding: 20px 0;
}

.reports-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.target-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.target-name {
  font-size: 14px;
  color: #333;
  margin-top: 4px;
}

.report-reason {
  line-height: 1.4;
  color: #666;
  font-size: 14px;
}

</style>
