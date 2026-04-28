<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { sendEmailCode, register } from '@/api/auth'
import { User, Message, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({ username: '', email: '', emailCode: '', password: '', confirmPassword: '' })
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const errorMsg = ref('')
const successMsg = ref('')
let timer = null

function hasMessage() {
  return Boolean(errorMsg.value || successMsg.value)
}

function getCodeButtonText() {
  if (countdown.value > 0) return `${countdown.value}s`
  if (sending.value) return '发送中'
  return '获取验证码'
}

function getRegisterButtonText() {
  return loading.value ? '注册中...' : '注册'
}

function goLogin() {
  router.push('/login')
}

async function handleSendCode() {
  if (!form.value.email) {
    errorMsg.value = '请输入邮箱'
    return
  }
  sending.value = true
  errorMsg.value = ''
  try {
    const res = await sendEmailCode(form.value.email)
    if (res.code === 200) {
      countdown.value = 60
      clearInterval(timer)
      timer = setInterval(() => {
        if (--countdown.value <= 0) clearInterval(timer)
      }, 1000)
    } else errorMsg.value = res.message || '发送失败'
  } catch {
    errorMsg.value = '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!form.value.username || !form.value.email || !form.value.emailCode || !form.value.password) {
    errorMsg.value = '请填写完整信息'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMsg.value = '两次密码不一致'
    return
  }
  loading.value = true
  try {
    const res = await register(form.value)
    if (res.code === 200) {
      successMsg.value = '注册成功，正在跳转登录...'
      setTimeout(() => router.push({ name: 'Login' }), 1500)
    } else errorMsg.value = res.message || '注册失败'
  } catch {
    errorMsg.value = '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="auth-page">
    <div class="phone-shell auth-shell">
      <div class="wechat-head">
        <div class="logo">注</div>
        <h1>注册账号</h1>
        <p>进入 FriendMatch 的微信式空间</p>
      </div>
      <div class="wechat-form clean-form">
        <el-form :model="form" class="register-form" @submit.prevent="handleRegister">
          <el-form-item>
            <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.email" placeholder="请输入邮箱" :prefix-icon="Message" />
          </el-form-item>
          <el-form-item class="code-row-item">
            <div class="code-row">
              <el-input v-model="form.emailCode" placeholder="请输入邮箱验证码" :prefix-icon="Message" class="code-input" />
              <el-button type="primary" :disabled="countdown > 0 || sending" @click="handleSendCode" class="code-btn">
                {{ getCodeButtonText() }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" :prefix-icon="Lock" show-password />
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
