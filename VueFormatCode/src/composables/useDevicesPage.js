import { computed, ref } from 'vue'
import { getDeviceList as getDevices, offlineDevice, removeDevice, trustDevice } from '@/api/device'
import { bindDevice } from '@/api/user'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'

function buildRawDeviceFingerprint() {
  return [
    navigator.platform || 'web',
    navigator.userAgent || 'browser',
    navigator.language || 'zh-CN',
    window.screen?.width || 0,
    window.screen?.height || 0,
  ].join('|')
}

function hashDeviceFingerprint(input) {
  let hash = 0
  for (let index = 0; index < input.length; index += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(index)
    hash |= 0
  }
  return `web-${Math.abs(hash).toString(16)}`
}

function buildCurrentDevicePayload() {
  const rawFingerprint = buildRawDeviceFingerprint()
  return {
    deviceId: hashDeviceFingerprint(rawFingerprint).slice(0, 64),
    deviceName: navigator.platform || 'Web Device',
    deviceType: 1,
    deviceOs: (navigator.platform || 'unknown').slice(0, 64),
    deviceBrowser: (navigator.userAgent || 'browser').slice(0, 64),
    deviceIp: '',
    deviceLocation: '',
  }
}

function normalizeDevice(row, currentDeviceId) {
  const deviceId = row.deviceId || row.id || ''
  return {
    ...row,
    deviceId,
    isCurrent: deviceId === currentDeviceId,
  }
}

export function useDevicesPage() {
  const devices = ref([])
  const loading = ref(false)
  const actionLoading = ref('')
  const { toast, showToast } = useToast()

  const currentDeviceId = computed(() => buildCurrentDevicePayload().deviceId)

  function getDeviceName(name) {
    return name || '未知设备'
  }

  function getSystemText(row) {
    const parts = [row.deviceOs, row.deviceBrowser].filter(Boolean)
    return parts.length ? parts.join(' / ') : '未知系统'
  }

  function getLoginTime(row) {
    const value = row.lastLoginTime || row.loginTime || row.updateTime || row.createTime
    if (!value) return '未知时间'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN')
  }

  function getTrustedTagType(isTrusted) {
    return Number(isTrusted) === 1 ? 'success' : 'warning'
  }

  function getTrustedText(isTrusted) {
    return Number(isTrusted) === 1 ? '已信任' : '未信任'
  }

  async function loadDevices() {
    loading.value = true
    try {
      await bindDevice(buildCurrentDevicePayload()).catch(() => {})
      const res = assertSuccess(await getDevices(), '加载设备列表失败')
      const list = (res.data || []).map((row) => normalizeDevice(row, currentDeviceId.value))
      devices.value = list.sort((a, b) => {
        if (a.isCurrent && !b.isCurrent) return -1
        if (!a.isCurrent && b.isCurrent) return 1
        return 0
      })
    } catch (error) {
      showToast(getErrorMessage(error, '加载设备列表失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function runDeviceAction(action, deviceId, requestTask, successMessage) {
    actionLoading.value = `${action}:${deviceId}`
    try {
      assertSuccess(await requestTask(), '操作失败')
      showToast(successMessage)
      await loadDevices()
    } catch (error) {
      showToast(getErrorMessage(error, '操作失败'), 'error')
    } finally {
      actionLoading.value = ''
    }
  }

  function handleTrustDevice(deviceId) {
    return runDeviceAction('trust', deviceId, () => trustDevice(deviceId), '设备已设为信任')
  }

  function handleOfflineDevice(deviceId) {
    if (deviceId === currentDeviceId.value) return Promise.resolve()
    return runDeviceAction('offline', deviceId, () => offlineDevice(deviceId), '设备已下线')
  }

  function handleDeleteDevice(deviceId) {
    if (deviceId === currentDeviceId.value) return Promise.resolve()
    return runDeviceAction('delete', deviceId, () => removeDevice(deviceId), '设备已删除')
  }

  return {
    actionLoading,
    currentDeviceId,
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
  }
}
