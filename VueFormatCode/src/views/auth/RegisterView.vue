<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { sendEmailCode, register, getMe } from '@/api/auth'
import { useEmailCodeCountdown } from '@/composables/useEmailCodeCountdown'
import { User, Message, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const form = ref({ username: '', email: '', emailCode: '', password: '', confirmPassword: '' })
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const {
  sending,
  countdown,
  getCodeButtonText,
  sendCode,
} = useEmailCodeCountdown(sendEmailCode)

function hasMessage() {
  return Boolean(errorMsg.value || successMsg.value)
}

function getRegisterButtonText() {
  return loading.value ? '注册中...' : '注册'
}

function getErrorMessage(error, fallback) {
  return error?.response?.message || error?.message || fallback
}

function isValidPassword(password) {
  return password.length >= 8 && /[A-Z]/.test(password) && /[a-z]/.test(password)
}

function goLogin() {
  router.push('/login')
}

function getRegisterToken(data) {
  if (typeof data === 'string') return data
  return data?.token || ''
}

async function enterHomeAfterRegister(data) {
  const token = getRegisterToken(data)
  if (!token) {
    successMsg.value = '注册成功，请登录后进入首页'
    setTimeout(() => router.push({ name: 'Login' }), 1500)
    return
  }

  localStorage.setItem('token', token)
  authStore.token = token
  const meRes = await getMe()
  if (meRes.code === 200) {
    authStore.setAuth({ token, ...meRes.data })
  } else {
    authStore.setAuth({ token, userId: null, userNickname: '', userAvatar: '', isAdmin: false })
  }
  router.push(authStore.isAdmin ? { name: 'AdminDashboard' } : { name: 'Chat' })
}

async function handleSendCode() {
  await sendCode(form.value.email, {
    onBefore: () => {
      errorMsg.value = ''
      successMsg.value = ''
    },
    onSuccess: (_, message) => {
      successMsg.value = message
    },
    onError: (message) => {
      errorMsg.value = message
    },
    errorMessageResolver: (error) => getErrorMessage(error, '发送失败，请稍后重试'),
  })
}

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!form.value.username || !form.value.email || !form.value.emailCode || !form.value.password || !form.value.confirmPassword) {
    errorMsg.value = '请填写完整信息'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  if (!isValidPassword(form.value.password)) {
    errorMsg.value = '密码至少 8 位，且需同时包含大写字母和小写字母'
    return
  }
  loading.value = true
  try {
    const res = await register(form.value)
    if (res.code === 200) {
      successMsg.value = '注册成功，正在进入首页...'
      await enterHomeAfterRegister(res.data)
    } else {
      errorMsg.value = res.message || '注册失败'
    }
  } catch (error) {
    errorMsg.value = getErrorMessage(error, '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="phone-shell auth-shell">
      <div class="wechat-head">
        <div class="logo">注</div>
        <h1>注册账号</h1>
        <p>进入兴趣社群空间</p>
      </div>
      <div class="wechat-form clean-form">
        <el-form :model="form" class="register-form" @submit.prevent="handleRegister">
          <el-form-item>
            <el-input v-model="form.username" placeholder="请输入昵称" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.email" placeholder="请输入邮箱" :prefix-icon="Message" />
          </el-form-item>
          <el-form-item class="code-row-item">
            <div class="code-row">
              <el-input
                v-model="form.emailCode"
                placeholder="请输入邮箱验证码"
                :prefix-icon="Message"
                class="code-input"
              />
              <el-button type="primary" :disabled="countdown > 0 || sending" @click="handleSendCode" class="code-btn">
                {{ getCodeButtonText() }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item class="password-tip-item">
            <div class="password-tip">密码至少 8 位，且需同时包含大写字母和小写字母</div>
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item v-if="hasMessage()">
            <div class="msg-wrap">
              <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon class="msg-alert" />
              <el-alert v-if="successMsg" :title="successMsg" type="success" show-icon class="msg-alert" />
            </div>
          </el-form-item>
          <el-form-item class="submit-row">
            <el-button type="primary" class="register-btn" :loading="loading" @click="handleRegister" native-type="submit">
              {{ getRegisterButtonText() }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="auth-links single-link">
        <el-button type="text" @click="goLogin">已有账号？去登录</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-form {
  width: 100%;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.code-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
}

.code-input {
  flex: 1;
}

.code-btn {
  width: 118px;
  height: 40px;
  border-radius: 12px;
  flex-shrink: 0;
}

.msg-wrap {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.password-tip-item :deep(.el-form-item__content) {
  line-height: 1.5;
}

.password-tip {
  width: 100%;
  margin-top: -8px;
  font-size: 12px;
  color: #909399;
}

.submit-row :deep(.el-form-item__content) {
  justify-content: center;
}

.register-btn {
  width: 220px;
  height: 45px;
  font-size: 16px;
  border-radius: 25px;
}
</style>
