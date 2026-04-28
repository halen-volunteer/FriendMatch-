import request from './request'

export const getMyPunishLogs = (params) => request.get('/api/punish/my-logs', { params })
export const submitFeedback = (data) => request.post('/api/feedback/submit', data)
export const getMyFeedbackList = (params) => request.get('/api/feedback/my-list', { params })
export const getFeedbackDetail = (feedbackId) => request.get('/api/feedback/detail', { params: { feedbackId } })

