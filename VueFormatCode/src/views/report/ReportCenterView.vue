<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { reportUser, reportTeam, reportMessage, getMyReports, getUserReportStatus, getTeamReportStatus, getReportStatus } from '@/api/report'
import { getMessageReportStatus, appealMessageReport } from '@/api/chat'
import { InfoFilled, User, Message, Phone, RefreshLeft, Document, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const activeTab = ref('submit')
const reportType = ref('user')
const reportForm = ref({ reportedUserId: '', reportedTeamId: '', messageId: '', reportReason: 1, reportContent: '' })
const reportReasonOptions = [
  { value: 1, label: '垃圾信息' },
  { value: 2, label: '辱骂骚扰' },
  { value: 3, label: '违规内容' },
  { value: 4, label: '诈骗风险' },
  { value: 5, label: '其他' }
]
const reportTypeLabelMap = { user: '用户举报', team: '团队举报', message: '消息举报', 1: '用户举报', 2: '消息举报', 3: '团队举报' }
const list = ref([])
const reportStatusDialog = ref(false)
const currentReportStatus = ref(null)
const toast = ref({ msg: '', type: 'success' })
const loading = ref(false)
const submitting = ref(false)

function showToast(msg, type = 'success') { toast.value = { msg, type }; setTimeout(() => (toast.value.msg = ''), 3000) }
function getReportTypeLabel(type) { return reportTypeLabelMap[type] || String(type || '未知类型') }
function getReportReasonLabel(reason) { return reportReasonOptions.find(item => item.value === Number(reason))?.label || reason || '未知原因' }
function canAppeal(item) { return Boolean(item.reportId || item.id) }
function openAppeal(item) {
  const reportId = item.reportId || item.id
  if (!reportId) return
  const rawType = item.relatedReportType || item.reportType
  const reportTypeValue = rawType === 'user' ? 1 : rawType === 'message' ? 2 : rawType === 'team' ? 3 : Number(rawType) || 1
  router.push({ path: '/appeals', query: { reportId: String(reportId), reportType: String(reportTypeValue) } })
}

async function loadReports() {
  loading.value = true
  try {
    const res = await getMyReports({ page: 1, pageSize: 50 }).catch(() => ({ code: 400, data: [] }))
    if (res.code === 200) list.value = res.data?.records || res.data || []
  } finally { loading.value = false }
}

async function handleSubmit() {
  submitting.value = true
  try {
    let res
    if (reportType.value === 'user') res = await reportUser({ reportedUserId: Number(reportForm.value.reportedUserId), reportReason: reportForm.value.reportReason, reportContent: reportForm.value.reportContent })
    else if (reportType.value === 'team') res = await reportTeam({ teamId: Number(reportForm.value.reportedTeamId), reportReason: reportForm.value.reportReason, reportContent: reportForm.value.reportContent })
    else res = await reportMessage({ messageId: Number(reportForm.value.messageId), reportReason: reportForm.value.reportReason, reportContent: reportForm.value.reportContent })
    if (res.code === 200) {
      showToast('举报已提交')
      reportForm.value = { reportedUserId: '', reportedTeamId: '', messageId: '', reportReason: 1, reportContent: '' }
      activeTab.value = 'list'
      loadReports()
    } else showToast(res.message || '提交失败', 'error')
  } finally { submitting.value = false }
}

async function openReportStatus(item) {
  const reportId = item.reportId || item.id
  if (!reportId) return
  let res
  if (item.reportType === 'user') res = await getUserReportStatus(reportId).catch(() => null)
  else if (item.reportType === 'team') res = await getTeamReportStatus(reportId).catch(() => null)
  else if (item.reportType === 'message') res = await getMessageReportStatus(reportId).catch(() => null)
  else res = await getReportStatus(reportId, item.reportType).catch(() => null)
  if (res?.code === 200) { currentReportStatus.value = res.data || item; reportStatusDialog.value = true }
}

async function handleMessageAppeal(item) {
  const reportId = item.reportId || item.id
  if (!reportId) return
  const res = await appealMessageReport(reportId).catch(() => ({ code: 400, message: '申诉失败' }))
  if (res.code === 200) showToast('消息举报申诉已提交')
  else showToast(res.message || '申诉失败', 'error')
}

onMounted(loadReports)
</script>

<template>
  <div class="page report-page">
    <div class="page-header"><div><h2>举报中心</h2><p>发起举报、查看处理状态，也可以从我的举报中直接发起申诉。</p></div></div>
    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast" @close="toast.msg = ''" />
    <el-card shadow="hover" class="report-card">
      <el-tabs v-model="activeTab" class="report-tabs">
        <el-tab-pane label="发起举报" name="submit">
          <div class="tab-panel">
            <div class="intro-banner warm"><div class="intro-icon"><el-icon><InfoFilled /></el-icon></div><div class="intro-content"><h3>提交后可在“我的举报”查看举报 ID</h3><p>生成举报记录后，你可以直接从记录里查看状态或发起申诉，无需手动查找。</p></div></div>
            <el-form label-position="top" class="report-form">
              <div class="form-grid">
                <el-form-item label="举报类型" class="full-span"><el-radio-group v-model="reportType"><el-radio-button label="user"><el-icon><User /></el-icon><span>用户</span></el-radio-button><el-radio-button label="team"><el-icon><Phone /></el-icon><span>团队</span></el-radio-button><el-radio-button label="message"><el-icon><Message /></el-icon><span>消息</span></el-radio-button></el-radio-group></el-form-item>
                <el-form-item v-if="reportType === 'user'" label="用户 ID"><el-input v-model="reportForm.reportedUserId" placeholder="请输入用户 ID" class="full-width" /></el-form-item>
                <el-form-item v-if="reportType === 'team'" label="团队 ID"><el-input v-model="reportForm.reportedTeamId" placeholder="请输入团队 ID" class="full-width" /></el-form-item>
                <el-form-item v-if="reportType === 'message'" label="消息 ID"><el-input v-model="reportForm.messageId" placeholder="请输入消息 ID" class="full-width" /></el-form-item>
                <el-form-item label="举报原因"><el-select v-model="reportForm.reportReason" class="full-width"><el-option v-for="option in reportReasonOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
                <el-form-item label="补充说明" class="full-span"><el-input v-model="reportForm.reportContent" type="textarea" :rows="5" resize="none" placeholder="请补充举报原因与详细情况。" class="full-width" /></el-form-item>
              </div>
              <div class="form-actions"><el-button type="primary" :loading="submitting" class="submit-btn" @click="handleSubmit">提交举报</el-button></div>
            </el-form>
          </div>
        </el-tab-pane>
        <el-tab-pane label="我的举报" name="list">
          <div class="tab-panel">
            <div class="list-toolbar"><div class="toolbar-title"><el-icon><Document /></el-icon><span>举报记录</span></div><el-button type="primary" plain @click="loadReports"><el-icon><RefreshLeft /></el-icon><span>刷新列表</span></el-button></div>
            <div v-if="loading" class="loading-container"><el-icon class="is-loading"><RefreshLeft /></el-icon></div>
            <div v-else>
              <el-empty v-if="!list.length" description="暂无举报记录" class="report-empty" />
              <div v-else class="report-list">
                <el-card v-for="item in list" :key="item.id || item.reportId" shadow="never" class="report-record-card">
                  <div class="record-head"><div class="record-meta"><span class="record-id">举报 ID：{{ item.reportId || item.id || '-' }}</span><span class="record-type">{{ getReportTypeLabel(item.reportType) }}</span><el-tag type="warning" effect="light" round>{{ getReportReasonLabel(item.reportReason) }}</el-tag></div><span class="record-time">{{ item.createTime || '未知时间' }}</span></div>
                  <div class="record-section"><div class="record-label">补充说明</div><div class="record-text">{{ item.reportContent || '无' }}</div></div>
                  <div class="record-section reply"><div class="record-label">处理状态</div><div class="record-text">{{ item.handleStatusText || item.handleStatus || item.reportStatus || '待处理' }}</div></div>
                  <div class="record-actions"><el-button type="primary" plain @click="openReportStatus(item)"><span>查看状态</span><el-icon><ArrowRight /></el-icon></el-button><el-button v-if="canAppeal(item)" @click="openAppeal(item)">发起申诉</el-button><el-button v-if="item.reportType === 'message'" type="warning" plain @click="handleMessageAppeal(item)">消息申诉</el-button></div>
                </el-card>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <el-dialog v-model="reportStatusDialog" title="举报状态" width="520px"><div v-if="currentReportStatus" class="status-detail"><p><strong>举报 ID：</strong>{{ currentReportStatus.reportId || currentReportStatus.id }}</p><p><strong>状态：</strong>{{ currentReportStatus.reportStatus || currentReportStatus.handleStatus || currentReportStatus.adminStatus || '处理中' }}</p><p><strong>处理结果：</strong>{{ currentReportStatus.handleResult || currentReportStatus.adminRemark || currentReportStatus.remark || '暂无' }}</p><p><strong>创建时间：</strong>{{ currentReportStatus.createTime || '未知时间' }}</p></div></el-dialog>
  </div>
</template>

<style scoped>
.report-page{max-width:1000px;margin:0 auto;padding:20px}.page-header{margin-bottom:20px}.page-header h2{font-size:28px;font-weight:700;color:#1f1f1f;margin:0 0 8px}.page-header p{margin:0;color:#8a8a8a;font-size:14px}.toast{margin-bottom:16px}.report-card{border-radius:16px;overflow:hidden;border:1px solid #ececec}.tab-panel{padding:8px 4px 4px}.intro-banner{display:flex;align-items:flex-start;gap:14px;padding:16px 18px;border-radius:14px;margin-bottom:20px}.intro-banner.warm{background:linear-gradient(135deg,rgba(255,250,217,.95),rgba(244,248,210,.92));border:1px solid rgba(224,213,128,.45)}.intro-icon{width:36px;height:36px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,.75);color:#8c6b00;flex-shrink:0}.intro-content h3{margin:0 0 6px;font-size:15px;font-weight:600;color:#3a3200}.intro-content p{margin:0;font-size:13px;line-height:1.7;color:#6f6632}.report-form{padding:6px 8px 10px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 20px}.full-span{grid-column:1 / -1}.full-width{width:100%}.form-actions{display:flex;justify-content:flex-end;padding-top:8px}.submit-btn{min-width:120px}.list-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}.toolbar-title{display:inline-flex;align-items:center;gap:8px;font-size:15px;color:#333;font-weight:600}.loading-container{min-height:320px;display:flex;align-items:center;justify-content:center}.report-empty{--el-empty-padding:40px 0}.report-list{display:flex;flex-direction:column;gap:14px}.report-record-card{border-radius:14px;border:1px solid #ececec;background:#fcfcfc}.record-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}.record-meta{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.record-id{font-size:14px;font-weight:600;color:#2f2f2f}.record-type{font-size:12px;color:#7d7d7d;background:#f4f4f4;padding:4px 10px;border-radius:999px}.record-time{color:#9a9a9a;font-size:12px}.record-section{padding:14px 16px;border-radius:12px;background:#fff;border:1px solid #efefef}.record-section+.record-section{margin-top:10px}.record-section.reply{background:#fafafa}.record-label{font-size:13px;font-weight:600;color:#666;margin-bottom:8px}.record-text{font-size:14px;line-height:1.75;color:#333;word-break:break-word}.record-actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:14px}.status-detail{display:grid;gap:10px;color:#4a4a4a}:deep(.report-tabs .el-tabs__nav-wrap::after){background-color:#efefef}:deep(.report-tabs .el-tabs__item){height:44px;font-size:15px;font-weight:600}:deep(.report-tabs .el-tabs__active-bar){height:3px;border-radius:999px}:deep(.report-form .el-form-item__label){padding-bottom:8px;color:#4a4a4a;font-weight:600}@media (max-width:768px){.report-page{padding:16px}.page-header h2{font-size:24px}.form-grid,.record-head,.list-toolbar{grid-template-columns:1fr;flex-direction:column;align-items:flex-start}.form-actions{justify-content:stretch}.submit-btn{width:100%}}
</style>
