import request from './request'

export const setOnlineStatus = (status) =>
  request.post('/api/online/status', null, { params: { status } })
export const heartbeat = () => request.post('/api/online/heartbeat')
export const getUserOnlineStatus = (userId) =>
  request.get('/api/online/status', { params: { userId } })
export const goOffline = () => request.post('/api/online/offline')
