import request from './request'

export const getDeviceList = () => request.get('/api/user/devices')
export const trustDevice = (deviceId) => request.post('/api/user/device/trust', null, { params: { deviceId } })
export const removeDevice = (deviceId) => request.delete(`/api/user/device/${deviceId}`)
export const offlineDevice = (deviceId) => request.post('/api/user/device/logout', null, { params: { deviceId } })
