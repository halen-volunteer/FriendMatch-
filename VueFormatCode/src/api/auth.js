import request from './request'

const BASE = import.meta.env.VITE_API_BASE_URL || ''

// 1.1 获取图形验证码（图片URL + captchaId）
// 后端通过响应头 captchaId 传递 Redis key
export const fetchCaptcha = async () => {
  const res = await fetch(`${BASE}/api/auth/captcha?t=${Date.now()}`, {
    credentials: 'include',
  })
  const captchaId = res.headers.get('captchaId') || res.headers.get('captchaid')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  return { captchaId, url }
}

// 1.2 发送邮箱验证码
export const sendEmailCode = (email) =>
  request.post('/api/auth/code', null, { params: { email } })

// 1.3 用户注册
export const register = (data) =>
  request.post('/api/auth/register', null, {
    params: {
      username: data.username,
      email: data.email,
      emailCode: data.emailCode,
      password: data.password,
    },
  })

// 1.4 用户登录
export const login = (data) => request.post('/api/auth/login', data)

// 1.5 获取当前用户信息
export const getMe = () => request.post('/api/auth/me')

// 1.6 获取 WebSocket 握手票据
export const getWebSocketTicket = () => request.post('/api/auth/ws-ticket')

// 1.7 忘记密码
export const forgetPassword = (data) =>
  request.post('/api/auth/forget', null, {
    params: {
      email: data.email,
      emailCode: data.emailCode,
      newPassword: data.newPassword,
    },
  })
