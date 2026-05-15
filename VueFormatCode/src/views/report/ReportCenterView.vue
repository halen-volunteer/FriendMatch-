<script setup>
import { ArrowRight, Document, InfoFilled, RefreshLeft } from '@element-plus/icons-vue'
import { useReportCenterPage } from '@/composables/useReportCenterPage'

const {
  activeTab,
  appealDialogVisible,
  appealForm,
  appealSubmitting,
  appealTarget,
  canAppeal,
  currentReportStatus,
  filteredList,
  getAppealQuotaText,
  getReportReasonText,
  getReportTypeText,
  isMessageAppeal,
  loadReports,
  loading,
  openAppeal,
  openReportStatus,
  reportStatusDialog,
  submitAppealForm,
  toast,
} = useReportCenterPage()
</script>

<template>
  <div class="page report-page">
    <div class="page-header">
      <div>
        <h2>举报中心</h2>
        <p>这里用于查看与你相关的举报记录、处理状态和申诉进度。新的举报入口已经收口到聊天场景内。</p>
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

    <el-card shadow="hover" class="report-card">
      <div class="tab-panel">
        <div class="intro-banner warm">
          <div class="intro-icon">
            <el-icon><InfoFilled /></el-icon>
          </div>
          <div class="intro-content">
            <h3>举报入口已移动到聊天场景</h3>
            <p>消息举报请在消息气泡处发起，举报用户请在单聊页面右上角发起，举报团队请在群聊页面右上角发起。</p>
          </div>
        </div>

        <div class="list-toolbar">
          <div class="toolbar-title">
            <el-icon><Document /></el-icon>
            <span>举报记录</span>
          </div>
          <el-button type="primary" plain :loading="loading" @click="loadReports">
            <el-icon><RefreshLeft /></el-icon>
            <span>刷新列表</span>
          </el-button>
        </div>

        <el-tabs v-model="activeTab" class="report-tabs">
          <el-tab-pane label="我发起的举报" name="reporter" />
          <el-tab-pane label="我作为被举报方" name="reported" />
        </el-tabs>

        <div v-if="loading" class="loading-container">
          <el-icon class="is-loading"><RefreshLeft /></el-icon>
        </div>

        <div v-else>
          <el-empty v-if="!filteredList.length" description="当前分类下暂无举报记录" class="report-empty" />

          <div v-else class="report-list">
            <el-card
              v-for="item in filteredList"
              :key="`${item.reportType}-${item.id || item.reportId}`"
              shadow="never"
              class="report-record-card"
            >
              <div class="record-head">
                <div class="record-meta">
                  <span class="record-id">举报 ID：{{ item.reportId || item.id || '-' }}</span>
                  <span class="record-type">{{ getReportTypeText(item.reportType) }}</span>
                  <el-tag type="warning" effect="light" round>
                    {{ getReportReasonText(item) }}
                  </el-tag>
                  <el-tag effect="plain" round>
                    {{ item.viewRoleText || '举报记录' }}
                  </el-tag>
                </div>
                <span class="record-time">{{ item.createTime || '未知时间' }}</span>
              </div>

              <div class="record-section">
                <div class="record-label">补充说明</div>
                <div class="record-text">{{ item.reportContent || '无' }}</div>
              </div>

              <div class="record-section reply">
                <div class="record-label">处理状态</div>
                <div class="record-text">
                  {{ item.handleStatusText || item.handleStatus || item.reportStatus || '待处理' }}
                </div>
              </div>

              <div class="record-actions">
                <el-button type="primary" plain @click="openReportStatus(item)">
                  <span>查看状态</span>
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
                <el-button v-if="canAppeal(item)" @click="openAppeal(item)">发起申诉</el-button>
              </div>
            </el-card>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="reportStatusDialog" title="举报状态" width="560px">
      <div v-if="currentReportStatus" class="status-detail">
        <p><strong>举报 ID：</strong>{{ currentReportStatus.reportId || currentReportStatus.id }}</p>
        <p><strong>举报类型：</strong>{{ getReportTypeText(currentReportStatus.reportType) }}</p>
        <p><strong>当前身份：</strong>{{ currentReportStatus.viewRoleText || '举报记录' }}</p>
        <p>
          <strong>状态：</strong>
          {{
            currentReportStatus.handleStatusText
              || currentReportStatus.reportStatus
              || currentReportStatus.handleStatus
              || currentReportStatus.adminStatus
              || '处理中'
          }}
        </p>
        <p>
          <strong>处理备注：</strong>
          {{
            currentReportStatus.adminNote
              || currentReportStatus.handleResult
              || currentReportStatus.adminRemark
              || currentReportStatus.remark
              || '暂无'
          }}
        </p>
        <p><strong>申诉次数：</strong>{{ getAppealQuotaText(currentReportStatus) }}</p>
        <p>
          <strong>申诉进度：</strong>
          {{ currentReportStatus.hasPendingAppeal ? '当前存在待处理申诉' : '当前无待处理申诉' }}
        </p>
        <p><strong>创建时间：</strong>{{ currentReportStatus.createTime || currentReportStatus.reportTime || '未知时间' }}</p>
      </div>
    </el-dialog>

    <el-dialog v-model="appealDialogVisible" title="发起申诉" width="560px">
      <div class="appeal-dialog-content">
        <p class="appeal-dialog-tip">
          当前将对举报记录
          <strong>#{{ appealTarget?.reportId || appealTarget?.id || '-' }}</strong>
          发起申诉。
        </p>
        <p v-if="isMessageAppeal" class="appeal-dialog-tip subtle">
          管理员会结合被举报消息及其上下 50 条聊天记录进行复核，无需额外补充证据。
        </p>

        <el-form label-position="top">
          <el-form-item label="申诉原因">
            <el-input
              v-model="appealForm.appealReason"
              type="textarea"
              :rows="5"
              resize="none"
              placeholder="请尽量清楚说明你认为举报处理结果存在问题的原因。"
            />
          </el-form-item>
          <el-form-item v-if="!isMessageAppeal" label="补充证据">
            <el-input
              v-model="appealForm.appealEvidence"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="可选，填写截图链接或补充说明。"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="appealSubmitting" @click="appealDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="appealSubmitting" @click="submitAppealForm">提交申诉</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.report-page {
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

.report-card {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #ececec;
}

.tab-panel {
  padding: 16px 12px 12px;
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
  margin-bottom: 8px;
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

.report-empty {
  --el-empty-padding: 40px 0;
}

.report-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-record-card {
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

.record-text {
  font-size: 14px;
  line-height: 1.75;
  color: #333;
  word-break: break-word;
}

.record-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.status-detail {
  display: grid;
  gap: 10px;
  color: #4a4a4a;
}

.appeal-dialog-content {
  display: grid;
  gap: 8px;
}

.appeal-dialog-tip {
  margin: 0 0 4px;
  color: #666;
  font-size: 14px;
}

.appeal-dialog-tip.subtle {
  color: #8a8a8a;
  font-size: 13px;
}

:deep(.report-tabs .el-tabs__nav-wrap::after) {
  background-color: #efefef;
}

:deep(.report-tabs .el-tabs__item) {
  height: 44px;
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .report-page {
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
