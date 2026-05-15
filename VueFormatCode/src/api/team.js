import request from './request'

// 团队 CRUD
export const createTeam = (data) => request.post('/api/team/create', data)
export const getTeamList = (params) => request.get('/api/team/list', { params })
export const searchTeam = (params) => request.get('/api/team/search', { params })
export const getTeamDetail = (teamId) => request.get(`/api/team/${teamId}`)
export const updateTeam = (data) => request.post('/api/team/update', data)
export const dissolveTeam = (teamId) => request.post('/api/team/dissolve', null, { params: { teamId } })

// 加入团队
export const applyTeam = (data) => request.post('/api/team/apply', data)
export const joinByPassword = (data) => request.post('/api/team/join-by-password', data)
export const inviteUser = (data) => request.post('/api/team/invite', data)
export const quitTeam = (teamId) => request.post('/api/team/quit', null, { params: { teamId } })

// 审批
export const getPendingApplyList = (params) => request.get('/api/team/apply/pending', { params })
export const auditApply = (data) => request.post('/api/team/apply/audit', data)

// 成员管理
export const getTeamMembers = (teamId, params) => request.get(`/api/team/${teamId}/members`, { params })
export const getTeamMembersByRole = (params) => request.get('/api/team/members', { params })
export const removeMember = (data) => request.post('/api/team/member/remove', data)
export const updateMemberRole = (data) => request.post('/api/team/member/role/update', data)
export const transferLeader = (data) => request.post('/api/team/transfer-leader', data)

// 禁言
export const muteAll = (data) => request.post('/api/team/mute-all', data)
export const muteMember = (data) => request.post('/api/team/member/mute', data)
export const unmuteMember = (data) => request.post('/api/team/member/unmute', data)
