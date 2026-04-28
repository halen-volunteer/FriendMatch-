<script setup>
import { ref, onMounted } from 'vue'
import { getDeviceList as getDevices, offlineDevice as unbindDevice, removeDevice as deleteDevice, trustDevice } from '@/api/device'
import { bindDevice } from '@/api/user'
import { Monitor, RefreshLeft, Warning, Check, Link, Delete, Loading } from '@element-plus/icons-vue'

const devices = ref([])
const loading = ref(false)

function getDeviceId(row) {
  return row.id || row.deviceId
}

function getDeviceName(name) {
  return name || '未知设备'
}

function getDeviceModel(model) {
  return model || '未知型号'
}

function getLoginTime(loginTime) {
  return loginTime || '未知时间'
}

function getTrustedTagType(isTrusted) {
  return isTrusted ? 'success' : 'warning'
}

function getTrustedText(isTrusted) {
  return isTrusted ? '已信任' : '未信任'
}

function buildCurrentDevicePayload() {
  return {
    deviceId: `${navigator.platform || 'web'}-${navigator.userAgent || 'browser'}`.slice(0, 120),
    deviceName: navigator.platform || 'Web Device',
    deviceType: 1,
    deviceOs: navigator.platform || 'unknown',
    deviceBrowser: navigator.userAgent.slice(0, 120),
    deviceIp: '',
    deviceLocation: '',
  }
}

async function loadDevices() {
  loading.value = true
  try {
    await bindDevice(buildCurrentDevicePayload()).catch(() => {})
    const res = await getDevices()
    if (res.code === 200) {
      devices.value = res.data || []
    }
  } catch (error) {
    console.error('加载设备列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function handleTrustDevice(deviceId) {
  try {
    const res = await trustDevice(deviceId)
    if (res.code === 200) {
      loadDevices()
    }
  } catch (error) {
    console.error('信任设备失败:', error)
  }
}

async function handleUnbindDevice(deviceId) {
  try {
    const res = await unbindDevice(deviceId)
    if (res.code === 200) {
      loadDevices()
    }
  } catch (error) {
    console.error('设备下线失败:', error)
  }
}

async function handleDeleteDevice(deviceId) {
  try {
    const res = await deleteDevice(deviceId)
    if (res.code === 200) {
      loadDevices()
    }
  } catch (error) {
    console.error('删除设备失败:', error)
  }
}

onMounted(loadDevices)
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
          <el-table-column prop="deviceName" label="设备名称" width="180">
            <template #default="scope">
              <div class="device-name">
                {{ getDeviceName(scope.row.deviceName) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="deviceModel" label="设备型号" width="180">
            <template #default="scope">
              <span>{{ getDeviceModel(scope.row.deviceModel) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="loginTime" label="登录时间" width="220">
            <template #default="scope">
              <span>{{ getLoginTime(scope.row.loginTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="isTrusted" label="信任状态" width="120">
            <template #default="scope">
              <el-tag :type="getTrustedTagType(scope.row.isTrusted)">
                <el-icon v-if="scope.row.isTrusted"><Check /></el-icon>
                <el-icon v-else><Warning /></el-icon>
                {{ getTrustedText(scope.row.isTrusted) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button
                v-if="!scope.row.isTrusted"
                type="success"
                size="small"
                @click="handleTrustDevice(getDeviceId(scope.row))"
              >
                <el-icon><Check /></el-icon>
                <span>信任</span>
              </el-button>
              <el-button
                type="info"
                size="small"
                @click="handleUnbindDevice(getDeviceId(scope.row))"
              >
                <el-icon><Link /></el-icon>
                <span>下线</span>
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="handleDeleteDevice(getDeviceId(scope.row))"
              >
                <el-icon><Delete /></el-icon>
                <span>删除</span>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
    <div class="page-footer page-footer-block">
      <el-alert
        title="安全提示"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content alert-content-text">
            <p>• 请定期检查您的登录设备，确保没有未授权的设备登录您的账号</p>
            <p>• 对于不再使用的设备，请及时下线并删除</p>
            <p>• 建议只在信任的设备上登录您的账号</p>
          </div>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<style scoped>
.devices-card {
  border-radius: 12px;
  overflow: hidden;
}

.device-name {
  font-weight: 500;
  color: #333;
}
</style>
