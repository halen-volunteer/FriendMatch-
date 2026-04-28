import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
})

// 请求拦截器：自动附加 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截器：统一兼容后端 { success, message/errorMsg, data, total }
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.success === 'boolean') {
      const normalized = {
        code: res.success ? 200 : 400,
        message: res.message || res.errorMsg || '',
        data: res.data,
        total: res.total,
      }
      if (!res.success) {
        const error = new Error(normalized.message || '请求失败')
        error.response = normalized
        throw error
      }
      return normalized
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request
