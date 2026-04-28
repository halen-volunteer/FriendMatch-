import request from './request'

export const getAdminUserReports = (params) => request.get('/api/report/admin/user/list', { params })
export const handleAdminUserReport = (data) => request.post('/api/report/admin/user/handle', data)
export const getAdminTeamReports = (params) => request.get('/api/report/admin/team/list', { params })
export const handleAdminTeamReport = (data) => request.post('/api/report/admin/team/handle', data)
export const getPendingAppeals = (params) => request.get('/api/appeal/pending', { params })
export const handleAppeal = (data) => request.post('/api/appeal/handle', data)
export const getFeedbacks = (params) => request.get('/api/feedback/admin/list', { params })
export const replyFeedback = (data) => request.post('/api/feedback/handle', data)
export const executePunish = (data) => request.post('/api/punish/execute', data)
export const cancelPunish = (data) => request.post('/api/punish/cancel', data)
export const getPunishLogs = (params) => request.get('/api/punish/logs', { params })
export const getViolationCount = (params) => request.get('/api/punish/violation-count', { params })
