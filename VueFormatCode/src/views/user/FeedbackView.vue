<script setup>
import { RefreshLeft, Upload, Check, Clock, Loading } from '@element-plus/icons-vue'
import { useFeedbackPage } from '@/composables/useFeedbackPage'

const {
  activeTab,
  currentDetail,
  detailDialog,
  feedbackForm,
  feedbackTypes,
  formatFeedbackTime,
  getDetailId,
  getFeedbackContent,
  getFeedbackReply,
  getFeedbackTypeLabel,
  getHandleStatusTagType,
  getHandleStatusText,
  isPendingHandleStatus,
  loadMyFeedbacks,
  loading,
  myFeedbacks,
  openFeedbackDetail,
  submitFeedback,
  submitting,
  toast,
} = useFeedbackPage()
</script>

<template>
  <div class="page feedback-page page-container-lg">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">反馈中心</h2>
    </div>

    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast page-toast-lg" @close="toast.msg = ''" />

    <el-card shadow="hover" class="feedback-card">
      <el-tabs v-model="activeTab" class="feedback-tabs">
        <el-tab-pane label="提交反馈" name="create">
          <el-alert
            title="处罚申诉入口已调整"
            type="info"
            :closable="false"
            show-icon
            class="feedback-entry-alert"
          >
            <template #default>
              处罚相关申诉请前往“举报中心”中的具体举报记录发起；当前反馈表单仅用于功能问题、违规举报建议和其他建议。
            </template>
          </el-alert>

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

            <el-form-item label="联系说明">
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
                :loading="submitting"
                class="submit-btn"
                @click="submitFeedback"
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
                  <template #default="{ row }">
                    <el-tag type="info">
                      {{ getFeedbackTypeLabel(row.feedbackType) }}
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="handleStatus" label="处理状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getHandleStatusTagType(row.handleStatus)">
                      <el-icon v-if="isPendingHandleStatus(row.handleStatus)"><Clock /></el-icon>
                      <el-icon v-else><Check /></el-icon>
                      {{ getHandleStatusText(row.handleStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>

                <el-table-column prop="createTime" label="提交时间" width="180">
                  <template #default="{ row }">
                    <span>{{ formatFeedbackTime(row.createTime) }}</span>
                  </template>
                </el-table-column>

                <el-table-column prop="feedbackContent" label="反馈内容" min-width="200">
                  <template #default="{ row }">
                    <div class="feedback-content">
                      {{ getFeedbackContent(row.feedbackContent) }}
                    </div>
                  </template>
                </el-table-column>

                <el-table-column prop="handleContent" label="回复内容" min-width="200">
                  <template #default="{ row }">
                    <div class="feedback-reply">
                      {{ getFeedbackReply(row.handleContent) }}
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button type="primary" link @click="openFeedbackDetail(row)">查看详情</el-button>
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
            <p>你的反馈对我们改进产品非常重要。</p>
            <p>提交反馈后，我们会尽快处理并回复。</p>
            <p>感谢你对产品的支持与建议。</p>
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

.feedback-entry-alert {
  margin-bottom: 18px;
}

.type-select,
.content-input,
.contact-input {
  width: 400px;
}

.form-tip {
  margin-top: 4px;
  color: #999;
  font-size: 12px;
}

.upload-demo {
  margin-top: 10px;
}

.upload-tip {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
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
  color: #666;
  font-size: 14px;
  line-height: 1.4;
}

.feedback-detail {
  display: grid;
  gap: 10px;
}
</style>
