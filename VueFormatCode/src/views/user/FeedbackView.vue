<script setup>
import { ref, onMounted } from 'vue'
import { submitFeedback as createFeedback, getMyFeedbackList as getMyFeedbacks, getFeedbackDetail } from '@/api/feedback'
import { RefreshLeft, Upload, Check, Clock, Loading } from '@element-plus/icons-vue'
const activeTab = ref('create')
const loading = ref(false)
const submitting = ref(false)

// 提交反馈表单
const feedbackForm = ref({
  feedbackType: 4,
  feedbackTitle: '',
  feedbackContent: '',
  feedbackAttachment: '',
  punishLogId: undefined
})

// 我的反馈列表
const myFeedbacks = ref([])
const detailDialog = ref(false)
const currentDetail = ref(null)

const feedbackTypes = [
  { label: '功能问题', value: 1 },
  { label: '违规举报', value: 2 },
  { label: '处罚申诉', value: 3 },
  { label: '其他建议', value: 4 }
]

function getFeedbackTypeLabel(feedbackType) {
  return feedbackTypes.find((item) => item.value === feedbackType)?.label || feedbackType
}

function getHandleStatusTagType(handleStatus) {
  if (handleStatus === 0) return 'info'
  if (handleStatus === 1) return 'warning'
  if (handleStatus === 2) return 'success'
  return 'danger'
}

function isPendingHandleStatus(handleStatus) {
  return handleStatus === 0
}

function getHandleStatusText(handleStatus) {
  if (handleStatus === 0) return '待处理'
  if (handleStatus === 1) return '处理中'
  if (handleStatus === 2) return '已解决'
  return '已驳回'
}

function formatFeedbackTime(timeText) {
  return timeText || '未知时间'
}

function getFeedbackContent(content) {
  return content || '无'
}

function getFeedbackReply(reply) {
  return reply || '暂无回复'
}

function getDetailId(detail) {
  return detail.feedbackId || detail.id
}

async function submitFeedback() {
  submitting.value = true
  try {
    const res = await createFeedback(feedbackForm.value)
    if (res.code === 200) {
      // 提交成功后刷新我的反馈列表
      loadMyFeedbacks()
      // 重置表单
      feedbackForm.value = {
        feedbackType: 4,
        feedbackTitle: '',
        feedbackContent: '',
        feedbackAttachment: '',
        punishLogId: undefined
      }
    }
  } catch (error) {
    console.error('提交反馈失败:', error)
  } finally {
    submitting.value = false
  }
}

async function loadMyFeedbacks() {
  loading.value = true
  try {
    const res = await getMyFeedbacks({ page: 1, pageSize: 50 })
    if (res.code === 200) {
      myFeedbacks.value = res.data?.records || res.data || []
    }
  } catch (error) {
    console.error('加载反馈记录失败:', error)
  } finally {
    loading.value = false
  }
}

async function openFeedbackDetail(row) {
  const feedbackId = row.feedbackId || row.id
  if (!feedbackId) return
  const res = await getFeedbackDetail(feedbackId).catch(() => ({ code: 400 }))
  if (res.code === 200) {
    currentDetail.value = res.data || row
    detailDialog.value = true
  }
}

onMounted(() => {
  loadMyFeedbacks()
})
</script>

<template>
  <div class="page feedback-page page-container-lg">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">反馈中心</h2>
    </div>
    <el-card shadow="hover" class="feedback-card">
      <el-tabs v-model="activeTab" class="feedback-tabs">
        <el-tab-pane label="提交反馈" name="create">
          <el-form :model="feedbackForm" label-width="120px" class="feedback-form">
            <el-form-item label="反馈类型">
              <el-select
                v-model="feedbackForm.feedbackType"
                placeholder="请选择反馈类型"
                class="type-select"
              >
                <el-option
                  v-for="type in feedbackTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="反馈内容">
              <el-input
                v-model="feedbackForm.feedbackContent"
                type="textarea"
                :rows="4"
                placeholder="请详细描述您的反馈"
                class="content-input"
              />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input
                v-model="feedbackForm.feedbackTitle"
                placeholder="请输入反馈标题（可选）"
                class="contact-input"
              />
              <div class="form-tip">
                留空也可以，建议概括问题主题
              </div>
            </el-form-item>
            <el-form-item label="附件上传">
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
                  <span>上传附件</span>
                </el-button>
                <template #tip>
                  <div class="upload-tip">
                    支持上传图片、视频等附件文件
                  </div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="submitFeedback"
                :loading="submitting"
                class="submit-btn"
              >
                <span>提交反馈</span>
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="我的反馈" name="my">
          <div class="my-feedbacks">
            <div class="feedbacks-header">
              <el-button type="primary" @click="loadMyFeedbacks">
                <el-icon><RefreshLeft /></el-icon>
                <span>刷新列表</span>
              </el-button>
            </div>
            <div v-if="loading" class="loading-container loading-center-lg">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
            <div v-else>
              <el-empty v-if="!myFeedbacks.length" description="暂无反馈记录" />
              <el-table v-else :data="myFeedbacks" stripe class="full-width-table">
                <el-table-column prop="feedbackType" label="反馈类型" width="120">
                  <template #default="scope">
                    <el-tag type="info">
                      {{ getFeedbackTypeLabel(scope.row.feedbackType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="handleStatus" label="处理状态" width="120">
                  <template #default="scope">
                    <el-tag :type="getHandleStatusTagType(scope.row.handleStatus)">
                      <el-icon v-if="isPendingHandleStatus(scope.row.handleStatus)"><Clock /></el-icon>
                      <el-icon v-else><Check /></el-icon>
                      {{ getHandleStatusText(scope.row.handleStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="提交时间" width="180">
                  <template #default="scope">
                    <span>{{ formatFeedbackTime(scope.row.createTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="feedbackContent" label="反馈内容" min-width="200">
                  <template #default="scope">
                    <div class="feedback-content">
                      {{ getFeedbackContent(scope.row.feedbackContent) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="handleContent" label="回复内容" min-width="200">
                  <template #default="scope">
                    <div class="feedback-reply">
                      {{ getFeedbackReply(scope.row.handleContent) }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120">
                  <template #default="scope">
                    <el-button type="primary" link @click="openFeedbackDetail(scope.row)">查看详情</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <el-dialog v-model="detailDialog" title="反馈详情" width="620px">
      <div v-if="currentDetail" class="feedback-detail">
        <p><strong>ID：</strong>{{ getDetailId(currentDetail) }}</p>
        <p><strong>类型：</strong>{{ getFeedbackTypeLabel(currentDetail.feedbackType) }}</p>
        <p><strong>提交时间：</strong>{{ formatFeedbackTime(currentDetail.createTime) }}</p>
        <p><strong>处理时间：</strong>{{ currentDetail.handleTime || '暂无' }}</p>
        <p><strong>内容：</strong>{{ getFeedbackContent(currentDetail.feedbackContent) }}</p>
        <p><strong>回复：</strong>{{ getFeedbackReply(currentDetail.handleContent) }}</p>
      </div>
    </el-dialog>
    <div class="page-footer page-footer-block">
      <el-alert
        title="反馈说明"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content alert-content-text">
            <p>• 您的反馈对我们改进产品非常重要</p>
            <p>• 提交反馈后，我们会尽快处理并回复</p>
            <p>• 感谢您对产品的支持与建议</p>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.feedback-card {
  border-radius: 12px;
  overflow: hidden;
}

.feedback-tabs {
  min-height: 400px;
}

.feedback-form {
  padding: 20px 0;
}

.type-select,
.content-input,
.contact-input {
  width: 400px;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
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

.my-feedbacks {
  padding: 20px 0;
}

.feedbacks-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 20px;
}

.feedback-content,
.feedback-reply,
.feedback-detail {
  line-height: 1.4;
  color: #666;
  font-size: 14px;
}

.feedback-detail {
  display: grid;
  gap: 10px;
}
</style>