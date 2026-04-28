<script setup>
import { useRouter } from 'vue-router'
import { Lock, Bell, Warning } from '@element-plus/icons-vue'

const router = useRouter()

function goDevices() {
  router.push('/devices')
}

function goAccountStatus() {
  router.push('/account-status')
}

function goFeedback() {
  router.push('/feedback')
}

function goReports() {
  router.push('/reports')
}

function goAppeals() {
  router.push('/appeals')
}
</script>

<template>
  <div class="page settings-page">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">其它中心</h2>
    </div>

    <el-card shadow="hover" class="settings-card card-shell">
      <div class="settings-content">
        <el-form label-width="160px" class="settings-form">
          <el-divider content-position="left">其它入口</el-divider>
          <el-form-item label="设备与安全">
            <div class="security-actions">
              <el-button type="primary" @click="goDevices">
                <el-icon><Lock /></el-icon>
                <span>设备管理</span>
              </el-button>
              <el-button @click="goAccountStatus">
                <span>账号状态</span>
              </el-button>
              <el-button @click="goFeedback">
                <el-icon><Bell /></el-icon>
                <span>意见反馈</span>
              </el-button>
              <el-button @click="goReports">
                <el-icon><Warning /></el-icon>
                <span>举报中心</span>
              </el-button>
              <el-button @click="goAppeals">
                <span>申诉中心</span>
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.settings-content {
  padding: 20px 0;
}

.settings-form {
  max-width: 760px;
}

.security-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
