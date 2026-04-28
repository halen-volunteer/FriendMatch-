# FriendMatch 前端开发文档 — Part 1 用户认证模块

> 版本：V1.0 | 日期：2026-03-19

**对应后端文档**：`API文档_Part1_用户认证.md`  
**页面路径前缀**：`/views/auth/`  
**接口路径前缀**：`/api/auth`  
**认证要求**：本模块所有接口无需携带 Token

---

## 目录

1. [模块概述](#一模块概述)
2. [页面列表](#二页面列表)
3. [接口封装](#三接口封装)
4. [Pinia Store](#四pinia-store)
5. [页面详细设计](#五页面详细设计)
   - [5.1 登录页](#51-登录页-loginviewvue)
   - [5.2 注册页](#52-注册页-registerviewvue)
   - [5.3 忘记密码页](#53-忘记密码页-forgetpasswordviewvue)
6. [公共组件](#六公共组件)
7. [路由配置](#七路由配置)
8. [注意事项](#八注意事项)

---

## 一、模块概述

用户认证模块负责处理用户的注册、登录、密码找回等身份验证流程。该模块无需登录即可访问，登录成功后将 Token 持久化至 `localStorage`，并由全局路由守卫统一管理鉴权跳转。

**接口一览**：

| 编号 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 1.1 | GET | `/api/auth/captcha` | 获取图形验证码 |
| 1.2 | POST | `/api/auth/code` | 发送邮箱验证码 |
| 1.3 | POST | `/api/auth/register` | 用户注册 |
| 1.4 | POST | `/api/auth/login` | 用户登录 |
| 1.5 | POST | `/api/auth/me` | 获取当前用户信息 |
| 1.6 | POST | `/api/auth/forget` | 忘记密码 |

---

## 二、页面列表

| 文件 | 路由路径 | 说明 |
|------|----------|------|
| `LoginView.vue` | `/login` | 登录页 |
| `RegisterView.vue` | `/register` | 注册页 |
| `ForgetPasswordView.vue` | `/forget-password` | 忘记密码页 |

---

## 三、接口封装

文件路径：`src/api/auth.js`

```js
import request from './request'

// 1.1 获取图形验证码图片URL（供 <img :src> 直接使用）
export const getCaptchaUrl = (t = Date.now()) =>
  `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/auth/captcha?t=${t}`

// 1.1 请求验证码（用于读取响应头中的 captchaId）
export const fetchCaptchaId = () =>
  fetch(`${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/auth/captcha`)
    .then(res => ({ captchaId: res.headers.get('captchaId') }))

// 1.2 发送邮箱验证码（同一邮箱60秒内只能发送一次）
export const sendEmailCode = (email) =>
  request.post('/api/auth/code', null, { params: { email } })

// 1.3 用户注册
// params: { username, email, emailCode, password }
export const register = (params) =>
  request.post('/api/auth/register', null, { params })

// 1.4 用户登录
// data: { userAccount, userPassword, captchaId, captchaCode }
export const login = (data) => request.post('/api/auth/login', data)

// 1.5 获取当前用户信息（需要 Token）
export const getMe = () => request.post('/api/auth/me')

// 1.6 忘记密码
// params: { email, emailCode, newPassword }
export const forgetPassword = (params) =>
  request.post('/api/auth/forget', null, { params })
```

---

## 四、Pinia Store

文件路径：`src/stores/auth.js`

```js
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId)
  const userNickname = computed(() => userInfo.value?.userNickname)
  const userAvatar = computed(() => userInfo.value?.userAvatar)

  // 登录成功后调用
  function setAuth(data) {
    token.value = data.token
    userInfo.value = { userId: data.userId, userNickname: data.userNickname, userAvatar: data.userAvatar }
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  // 退出登录或 Token 过期时调用
  function clearAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  // 资料编辑后同步昵称/头像
  function updateUserInfo(patch) {
    userInfo.value = { ...userInfo.value, ...patch }
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  return { token, userInfo, isLoggedIn, userId, userNickname, userAvatar, setAuth, clearAuth, updateUserInfo }
})
```

---

## 五、页面详细设计

### 5.1 登录页（LoginView.vue）

**路由**：`/login`  
**文件**：`src/views/auth/LoginView.vue`

#### 表单字段

| 字段 | 类型 | 校验规则 |
|------|------|----------|
| userAccount | 文本输入 | 必填 |
| userPassword | 密码输入 | 必填，≥8位 |
| captchaCode | 文本输入 | 必填 |

#### 业务逻辑流程

```
页面挂载（onMounted）
    ↓
调用 fetchCaptchaId() 获取 captchaId（存入 ref）
同时设置图片 src = getCaptchaUrl()
    ↓
用户填写表单，点击「登录」
    ↓
前端校验（非空、密码长度）
    ↓
调用 POST /api/auth/login
    → 成功：authStore.setAuth(data) → 建立 WebSocket → 跳转 /chat
    → 失败（验证码错误）：刷新图形验证码，展示错误信息
    → 失败（封号）：提示「账号已被封禁」
```

#### 关键代码片段

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, getCaptchaUrl, fetchCaptchaId } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ userAccount: '', userPassword: '', captchaCode: '' })
const captchaId = ref('')
const captchaUrl = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function refreshCaptcha() {
  captchaUrl.value = getCaptchaUrl()
  const { captchaId: id } = await fetchCaptchaId()
  captchaId.value = id
}

onMounted(refreshCaptcha)

async function handleLogin() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login({ ...form.value, captchaId: captchaId.value })
    if (res.code === 200) {
      authStore.setAuth(res.data)
      router.push({ name: 'Chat' })
    } else {
      errorMsg.value = res.message
      refreshCaptcha()
    }
  } catch {
    errorMsg.value = '登录失败，请稍后重试'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <h2>登录 FriendMatch</h2>
    <input v-model="form.userAccount" placeholder="账号或邮箱" />
    <input v-model="form.userPassword" type="password" placeholder="密码" />
    <div class="captcha-row">
      <img :src="captchaUrl" alt="验证码" @click="refreshCaptcha" style="cursor:pointer" />
      <input v-model="form.captchaCode" placeholder="图形验证码" />
    </div>
    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
    <button :disabled="loading" @click="handleLogin">
      {{ loading ? '登录中...' : '登录' }}
    </button>
    <div class="links">
      <router-link to="/register">注册账号</router-link>
      <router-link to="/forget-password">忘记密码</router-link>
    </div>
  </div>
</template>
```

---

### 5.2 注册页（RegisterView.vue）

**路由**：`/register`  
**文件**：`src/views/auth/RegisterView.vue`

#### 表单字段

| 字段 | 类型 | 校验规则 |
|------|------|----------|
| username | 文本输入 | 必填，6-20位字母/数字/下划线，不能以下划线开头 |
| email | 邮箱输入 | 必填，合法邮箱格式 |
| emailCode | 文本输入 | 必填 |
| password | 密码输入 | 必填，≥8位 |
| confirmPassword | 密码输入 | 必填，与 password 一致 |

#### 业务逻辑流程

```
用户填写邮箱，点击「获取验证码」
    ↓
前端校验邮箱格式
    ↓
调用 POST /api/auth/code
    → 成功：60 秒倒计时，按钮禁用
    → 失败：展示错误信息
    ↓
用户填写完整表单，点击「注册」
    ↓
前端校验（各字段规则 + 两次密码一致）
    ↓
调用 POST /api/auth/register
    → 成功：提示「注册成功」，跳转 /login
    → 失败：展示后端返回错误信息
```

#### 关键代码片段

```vue
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendEmailCode, register } from '@/api/auth'

const router = useRouter()
const form = ref({ username: '', email: '', emailCode: '', password: '', confirmPassword: '' })
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorMsg = ref('')
let timer = null

async function handleSendCode() {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    errorMsg.value = '请输入合法邮箱地址'
    return
  }
  sending.value = true
  try {
    await sendEmailCode(form.value.email)
    countdown.value = 60
    timer = setInterval(() => { if (--countdown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || '发送失败'
  } finally {
    sending.value = false
  }
}

async function handleRegister() {
  if (form.value.password !== form.value.confirmPassword) {
    errorMsg.value = '两次密码输入不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await register({
      username: form.value.username,
      email: form.value.email,
      emailCode: form.value.emailCode,
      password: form.value.password,
    })
    if (res.code === 200) router.push({ name: 'Login' })
    else errorMsg.value = res.message
  } catch {
    errorMsg.value = '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
```

#### 页面元素清单

- 账号输入框（含格式提示：6-20位，字母/数字/下划线）
- 邮箱输入框
- 获取验证码按钮（倒计时禁用）+ 验证码输入框
- 密码输入框
- 确认密码输入框
- 注册按钮（加载状态）
- 错误提示文案
- 跳转登录链接

---

### 5.3 忘记密码页（ForgetPasswordView.vue）

**路由**：`/forget-password`  
**文件**：`src/views/auth/ForgetPasswordView.vue`

#### 表单字段

| 字段 | 类型 | 校验规则 |
|------|------|----------|
| email | 邮箱输入 | 必填，合法邮箱格式 |
| emailCode | 文本输入 | 必填 |
| newPassword | 密码输入 | 必填，≥8位 |
| confirmPassword | 密码输入 | 必填，与 newPassword 一致 |

#### 业务逻辑流程

```
用户输入注册邮箱，点击「获取验证码」
    ↓
调用 POST /api/auth/code
    → 成功：60 秒倒计时
    → 失败：提示错误
    ↓
用户填写验证码和新密码，点击「重置密码」
    ↓
前端校验（两次密码一致）
    ↓
调用 POST /api/auth/forget
    → 成功：提示「密码重置成功」，3秒后跳转 /login
    → 失败：展示错误信息
```

#### 关键代码片段

```vue
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendEmailCode, forgetPassword } from '@/api/auth'

const router = useRouter()
const form = ref({ email: '', emailCode: '', newPassword: '', confirmPassword: '' })
const loading = ref(false)
const countdown = ref(0)
const errorMsg = ref('')
const successMsg = ref('')
let timer = null

async function handleSendCode() {
  try {
    await sendEmailCode(form.value.email)
    countdown.value = 60
    timer = setInterval(() => { if (--countdown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || '发送失败'
  }
}

async function handleReset() {
  if (form.value.newPassword !== form.value.confirmPassword) {
    errorMsg.value = '两次密码不一致'
    return
  }
  loading.value = true
  try {
    const res = await forgetPassword({
      email: form.value.email,
      emailCode: form.value.emailCode,
      newPassword: form.value.newPassword,
    })
    if (res.code === 200) {
      successMsg.value = '密码重置成功，即将跳转登录...'
      setTimeout(() => router.push({ name: 'Login' }), 3000)
    } else {
      errorMsg.value = res.message
    }
  } catch {
    errorMsg.value = '重置失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
```

#### 页面元素清单

- 邮箱输入框
- 获取验证码按钮（倒计时）+ 验证码输入框
- 新密码输入框
- 确认新密码输入框
- 重置密码按钮
- 错误/成功提示文案
- 返回登录链接

---

## 六、公共组件

### CaptchaImage.vue

**路径**：`src/components/common/CaptchaImage.vue`  
**功能**：图形验证码展示，点击自动刷新，通过 `emit` 将新 `captchaId` 传给父组件。

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { getCaptchaUrl, fetchCaptchaId } from '@/api/auth'

const emit = defineEmits(['update:captchaId'])
const src = ref('')

async function refresh() {
  src.value = getCaptchaUrl()
  const { captchaId } = await fetchCaptchaId()
  emit('update:captchaId', captchaId)
}

onMounted(refresh)
</script>

<template>
  <img :src="src" alt="图形验证码" title="点击刷新" style="cursor:pointer;height:40px" @click="refresh" />
</template>
```

**使用方式**：

```vue
<CaptchaImage v-model:captchaId="captchaId" />
```

### EmailCodeButton.vue

**路径**：`src/components/common/EmailCodeButton.vue`  
**功能**：发送邮箱验证码按钮，内置 60 秒倒计时，防止重复点击。

```vue
<script setup>
import { ref } from 'vue'
import { sendEmailCode } from '@/api/auth'

const props = defineProps({ email: { type: String, required: true } })
const emit = defineEmits(['sent', 'error'])
const countdown = ref(0)
const sending = ref(false)
let timer = null

async function handleSend() {
  if (countdown.value > 0 || sending.value) return
  sending.value = true
  try {
    await sendEmailCode(props.email)
    emit('sent')
    countdown.value = 60
    timer = setInterval(() => { if (--countdown.value <= 0) clearInterval(timer) }, 1000)
  } catch (e) {
    emit('error', e?.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <button :disabled="countdown > 0 || sending" @click="handleSend">
    {{ countdown > 0 ? `${countdown}s 后重发` : sending ? '发送中...' : '获取验证码' }}
  </button>
</template>
```

**使用方式**：

```vue
<EmailCodeButton :email="form.email" @sent="onSent" @error="onError" />
```

---

## 七、路由配置

在 `src/router/index.js` 中添加以下路由（`requiresAuth: false`，无需登录）：

```js
{ path: '/login',           name: 'Login',          component: () => import('@/views/auth/LoginView.vue'),          meta: { requiresAuth: false } },
{ path: '/register',        name: 'Register',       component: () => import('@/views/auth/RegisterView.vue'),       meta: { requiresAuth: false } },
{ path: '/forget-password', name: 'ForgetPassword', component: () => import('@/views/auth/ForgetPasswordView.vue'), meta: { requiresAuth: false } },
```

**全局路由守卫**：

```js
import { useAuthStore } from '@/stores/auth'

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isLoggedIn) return { name: 'Login' }
  if (to.meta.requiresAuth === false && authStore.isLoggedIn) return { name: 'Chat' }
})
```

---

## 八、注意事项

1. **captchaId 在响应头中**：后端返回图片流，`captchaId` 在 HTTP **响应头**（非响应体），必须用原生 `fetch` 或 axios blob 模式读取 `response.headers.get('captchaId')`，不可从 `response.data` 中取。

2. **验证码冷却**：同一邮箱 60 秒内只能发送一次验证码，前端倒计时与后端限制双重保护，前端倒计时不足以完全防重，不要依赖前端状态跳过后端检查。

3. **Token 统一管理**：所有模块通过 `useAuthStore().token` 读取 Token，不直接操作 `localStorage`，确保响应式状态与持久化同步。

4. **登录态持久化**：`useAuthStore` 初始化时从 `localStorage` 恢复 Token 和 userInfo，刷新页面不会丢失登录状态。

5. **401 统一处理**：`src/api/request.js` 的响应拦截器处理 HTTP 401，自动调用 `clearAuth()` 并跳转 `/login`，各页面无需单独处理 Token 过期。

6. **账号格式**：6-20 位，只允许字母/数字/下划线，**不能以下划线开头**，推荐前端正则：`/^[a-zA-Z0-9][a-zA-Z0-9_]{5,19}$/`。

7. **密码要求**：至少 8 位，推荐正则：`/^.{8,}$/`，可根据产品需求加强复杂度校验。

---

*本文档对应后端 `API文档_Part1_用户认证.md`，如接口变更请同步更新。*
