import request from './request'

export const getMyPunishLogs = (params) => request.get('/api/punish/my-logs', { params })
