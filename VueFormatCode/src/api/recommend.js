import request from './request'

export const getRecommendUsers = (params) => request.get('/api/recommend/users', { params })
export const getRecommendTeams = (params) => request.get('/api/recommend/teams', { params })
export const clickRecommendUser = (userId) => request.post('/api/recommend/click', { recommend_id: userId, recommend_type: 1 })
export const clickRecommendTeam = (teamId) => request.post('/api/recommend/click', { recommend_id: teamId, recommend_type: 2 })
