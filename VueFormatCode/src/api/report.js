import request from './request'
import { reportMessage } from './chat'

export const reportUser = (data) => request.post('/api/report/user', data)
export const reportTeam = (data) => request.post('/api/report/team', data)
export { reportMessage }
export const getUserReportStatus = (reportId) => request.get(`/api/report/user/${reportId}`)
export const getTeamReportStatus = (reportId) => request.get(`/api/report/team/${reportId}`)
export const getReportStatus = (reportId, type) => request.get(`/api/report/${reportId}`, { params: { type } })
export const getMyReports = (params) => request.get('/api/report/my-list', { params })
export const getMyAppeals = (params) => request.get('/api/appeal/my', { params })
export const submitAppeal = (data) => request.post('/api/appeal/submit', data)

