import request from './request'

// 用户资料
export const updateProfile = (data) => request.post('/api/user/profile/update', data)
export const getPrivacy = () => request.get('/api/user/privacy')
export const updatePrivacy = (data) => request.post('/api/user/privacy/update', data)
export const getUserProfile = (userId) => request.get(`/api/user/${userId}/profile`)
export const getUserList = (params) => request.get('/api/user/list', { params })
export const searchUser = (params) => request.get('/api/user/search', { params })

// 好友管理
export const addFriend = (data) => request.post('/api/friend/add', data)
export const getFriendList = (params) => request.get('/api/friend/list', { params })
export const getFriendRequests = (params) => request.get('/api/friend/requests', { params })
export const agreeFriend = (friendId) => request.post('/api/friend/agree', { friendId })
export const rejectFriend = (friendId) => request.post('/api/friend/reject', { friendId })
export const deleteFriend = (friendId) => request.post('/api/friend/delete', { friendId })

// 黑名单
export const addBlacklist = (blackUserId) => request.post('/api/blacklist/add', { blackUserId })
export const removeBlacklist = (blackUserId) => request.post('/api/blacklist/remove', { blackUserId })
export const getBlacklist = (params) => request.get('/api/blacklist', { params })

// 通知
export const getUnreadNoticeCount = () => request.get('/api/notice/unread-count')
export const getNoticeList = (params) => request.get('/api/notice/list', { params })
export const readNotices = (noticeIds) => request.post('/api/notice/read', noticeIds)
export const deleteNotices = (noticeIds) => request.post('/api/notice/delete', noticeIds)

// 设备
export const bindDevice = (data) => request.post('/api/user/device/bind', data)

