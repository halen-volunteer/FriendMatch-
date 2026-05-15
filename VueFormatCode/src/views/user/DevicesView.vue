<script setup>
import { Check, Delete, Link, Loading, Monitor, RefreshLeft, Warning } from '@element-plus/icons-vue'
import { useDevicesPage } from '@/composables/useDevicesPage'

const {
  actionLoading,
  devices,
  getDeviceName,
  getLoginTime,
  getSystemText,
  getTrustedTagType,
  getTrustedText,
  handleDeleteDevice,
  handleOfflineDevice,
  handleTrustDevice,
  loadDevices,
  loading,
  toast,
} = useDevicesPage()
</script>

<template>
  <div class="page devices-page page-container-lg">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">设备管理</h2>
      <el-button type="primary" @click="loadDevices">
        <el-icon><RefreshLeft /></el-icon>
        <span>刷新列表</span>
      </el-button>
    </div>

    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast page-toast-lg" @close="toast.msg = ''" />

    <el-card shadow="hover" class="devices-card">
      <template #header>
        <div class="card-header card-header-inline">
          <el-icon><Monitor /></el-icon>
          <span>已登录设备</span>
        </div>
      </template>

      <div v-if="loading" class="loading-container loading-center-lg">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else>
        <el-empty v-if="!devices.length" description="暂无设备" />

        <el-table v-else :data="devices" stripe class="full-width-table">
          <el-table-column prop="deviceName" label="设备名称" min-width="220">
            <template #default="{ row }">
              <div class="device-name-row">
                <span class="device-name">{{ getDeviceName(row.deviceName) }}</span>
                <el-tag v-if="row.isCurrent" size="small" type="primary" effect="dark">当前设备</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="系统 / 浏览器" min-width="280">
            <template #default="{ row }">
              <span>{{ getSystemText(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="最近登录时间" min-width="200">
            <template #default="{ row }">
              <span>{{ getLoginTime(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="isTrusted" label="信任状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getTrustedTagType(row.isTrusted)">
                <el-icon v-if="Number(row.isTrusted) === 1"><Check /></el-icon>
                <el-icon v-else><Warning /></el-icon>
                {{ getTrustedText(row.isTrusted) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="isActive" label="在线状态" width="120">
            <template #default="{ row }">
              <el-tag :type="Number(row.isActive) === 1 ? 'success' : 'info'">
                {{ Number(row.isActive) === 1 ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <div class="ops-row">
                <el-button
                  v-if="Number(row.isTrusted) !== 1"
                  type="success"
                  size="small"
                  :loading="actionLoading === `trust:${row.deviceId}`"
                  @click="handleTrustDevice(row.deviceId)"
                >
                  <el-icon><Check /></el-icon>
                  <span>信任</span>
                </el-button>

                <el-button
                  type="info"
                  size="small"
                  :disabled="row.isCurrent"
                  :loading="actionLoading === `offline:${row.deviceId}`"
                  @click="handleOfflineDevice(row.deviceId)"
                >
                  <el-icon><Link /></el-icon>
                  <span>{{ row.isCurrent ? '当前设备' : '下线' }}</span>
                </el-button>

                <el-button
                  type="danger"
                  size="small"
                  :disabled="row.isCurrent"
                  :loading="actionLoading === `delete:${row.deviceId}`"
                  @click="handleDeleteDevice(row.deviceId)"
                >
                  <el-icon><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <div class="page-footer page-footer-block">
      <el-alert title="安全提示" type="info" :closable="false" show-icon>
        <template #default>
          <div class="alert-content alert-content-text">
            <p>当前设备会自动置顶显示，并带有“当前设备”标记。</p>
            <p>为避免误操作，当前设备不允许在本页直接下线或删除。</p>
            <p>建议定期检查设备列表，及时清理不再使用的登录设备。</p>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.devices-card {
  border-radius: 16px;
  overflow: hidden;
}

.device-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-name {
  font-weight: 600;
  color: #333;
}

.ops-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
