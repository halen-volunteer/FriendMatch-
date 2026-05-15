<script setup>
import { Check, CollectionTag, Loading, Message, User, Warning } from '@element-plus/icons-vue'
import { useAdminDashboard } from '@/composables/useAdminDashboard'

const { loading, quickActions, stats, loadStats } = useAdminDashboard()

function getStatusType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function getStatusText(status) {
  if (status === 0) return '待处理'
  if (status === 1) return '已确认违规'
  if (status === 2) return '已确认未违规'
  return '未知状态'
}
</script>

<template>
  <section class="admin-page">
    <div class="admin-toolbar">
      <div>
        <h2 class="admin-page-title">管理总览</h2>
        <p class="admin-page-note">快速查看当前待处理任务，并跳转到对应模块。</p>
      </div>
      <el-button type="primary" @click="loadStats">刷新数据</el-button>
    </div>

    <div v-if="loading" class="page-loading surface-card">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <template v-else>
      <div class="stats-grid">
        <el-card class="metric-card">
          <div class="metric-header">
            <el-icon><User /></el-icon>
            <span>用户总数</span>
          </div>
          <p class="metric-value">{{ stats.totalUsers }}</p>
          <span class="metric-extra">来源于用户分页接口总数。</span>
        </el-card>

        <el-card class="metric-card">
          <div class="metric-header">
            <el-icon><CollectionTag /></el-icon>
            <span>团队总数</span>
          </div>
          <p class="metric-value">{{ stats.totalTeams }}</p>
          <span class="metric-extra">来源于团队分页接口总数。</span>
        </el-card>

        <el-card class="metric-card">
          <div class="metric-header">
            <el-icon><Warning /></el-icon>
            <span>待处理举报</span>
          </div>
          <p class="metric-value">{{ stats.pendingReports }}</p>
          <span class="metric-extra">统计当前分配给你的用户、消息、团队举报。</span>
        </el-card>

        <el-card class="metric-card">
          <div class="metric-header">
            <el-icon><Check /></el-icon>
            <span>待处理申诉</span>
          </div>
          <p class="metric-value">{{ stats.pendingAppeals }}</p>
          <span class="metric-extra">帮助管理员及时处理举报申诉。</span>
        </el-card>
      </div>

      <div class="info-grid">
        <el-card>
          <template #header>
            <div class="metric-header">
              <el-icon><Message /></el-icon>
              <span>我负责的最近举报</span>
            </div>
          </template>
          <el-empty v-if="!stats.recentReports.length" description="暂无举报数据" />
          <div v-else class="stack-list">
            <article v-for="item in stats.recentReports" :key="item.id" class="activity-item">
              <div class="compact-meta">
                <strong>{{ item.type }}</strong>
                <el-tag size="small" :type="getStatusType(item.status)">
                  {{ getStatusText(item.status) }}
                </el-tag>
              </div>
              <div class="activity-item__text admin-table-text">目标：{{ item.target }}</div>
              <div class="activity-item__text admin-table-text">{{ item.content }}</div>
              <div class="activity-item__meta admin-meta-text">{{ item.createTime }}</div>
            </article>
          </div>
        </el-card>

        <el-card>
          <template #header>
            <div class="metric-header">
              <el-icon><Check /></el-icon>
              <span>我负责的最近申诉</span>
            </div>
          </template>
          <el-empty v-if="!stats.recentAppeals.length" description="暂无申诉数据" />
          <div v-else class="stack-list">
            <article v-for="item in stats.recentAppeals" :key="item.id" class="activity-item">
              <div class="compact-meta">
                <strong>{{ item.type }}</strong>
                <el-tag size="small" :type="getStatusType(item.status)">
                  {{ item.status === 0 ? '待处理' : item.status === 1 ? '已通过' : '已驳回' }}
                </el-tag>
              </div>
              <div class="activity-item__text admin-table-text">发起人：{{ item.target }}</div>
              <div class="activity-item__text admin-table-text">{{ item.content }}</div>
              <div class="activity-item__meta admin-meta-text">{{ item.createTime }}</div>
            </article>
          </div>
        </el-card>
      </div>

      <el-card>
        <template #header>
          <div class="metric-header">
            <el-icon><Warning /></el-icon>
            <span>快捷入口</span>
          </div>
        </template>
        <div class="action-row">
          <el-button v-for="item in quickActions" :key="item.label" type="primary" plain @click="item.action">
            {{ item.label }}
          </el-button>
        </div>
      </el-card>
    </template>
  </section>
</template>

<style scoped>
.activity-item {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-soft);
}

.activity-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.activity-item__text {
  line-height: 1.7;
}

.activity-item__meta {
  margin-top: 2px;
}
</style>
