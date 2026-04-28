import request from './request'

export const presignUpload = (data) => request.post('/api/oss/presign', data)
