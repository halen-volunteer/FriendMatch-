import request from './request'

export const searchUser = (params) => request.get('/api/search/users', { params })
export const searchTeam = (params) => request.get('/api/search/teams', { params })
export const getSearchHistory = (params) => request.get('/api/search/history', { params })
export const clearSearchHistory = () => request.delete('/api/search/history')
export const getHotKeywords = (params) => request.get('/api/search/hot-keywords', { params })
export const getSearchSuggest = (params) => request.get('/api/search/suggest', { params })

