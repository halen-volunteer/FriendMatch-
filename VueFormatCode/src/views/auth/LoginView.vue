<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login, fetchCaptcha, getMe } from '@/api/auth'
import { User, Lock, Picture } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const form = ref({ userAccount: '', userPassword: '', checkNumber: '' })
const captchaId = ref('')
const captchaUrl = ref('')
const loading = ref(false)
const errorMsg = ref('')

function getLoginButtonText() {
  return loading.value ? '登录中...' : '登录'
}

function goRegister() {
  router.push('/register')
}

function goForgetPassword() {
  router.push('/forget-password')
}

async function refreshCaptcha() {
  try {
    const { captchaId: id, url } = await fetchCaptcha()
    captchaId.value = id
    captchaUrl.value = url
  } catch {
    errorMsg.value = '验证码加载失败，请刷新重试'
  }
}

onMounted(refreshCaptcha)

async function handleLogin() {
  if (!form.value.userAccount || !form.value.userPassword || !form.value.checkNumber) {
    errorMsg.value = '请填写完整信息'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login({
      userAccount: form.value.userAccount,
      userPassword: form.value.userPassword,
      checkNumber: form.value.checkNumber,
      captchaID: captchaId.value,
    })
    if (res.code === 200) {
      localStorage.setItem('token', res.data)
      authStore.token = res.data
      const meRes = await getMe()
      if (meRes.code === 200) authStore.setAuth({ token: res.data, ...meRes.data })
      else authStore.setAuth({ token: res.data, userId: null, userNickname: '', userAvatar: '', isAdmin: false })
      router.push(authStore.isAdmin ? { name: 'AdminDashboard' } : { name: 'Chat' })
    } else {
      errorMsg.value = res.message || '登录失败'
      refreshCaptcha()
    }
  } catch (e) {
    errorMsg.value = e?.message || '登录失败'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="phone-shell auth-shell">
      <div class="wechat-head">
        <div class="logo">微</div>
        <h1>FriendMatch</h1>
        <p>像微信一样开始连接</p>
      </div>
      <div class="wechat-form clean-form">
        <el-form :model="form" class="login-form" @submit.prevent="handleLogin">
          <el-form-item>
            <el-input v-model="form.userAccount" placeholder="请输入账号或邮箱" @keyup.enter="handleLogin" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.userPassword" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-form-item class="captcha-block">
            <el-input v-model="form.checkNumber" placeholder="请输入验证码" @keyup.enter="handleLogin" :prefix-icon="Picture" class="captcha-input" />
            <el-image :src="captchaUrl" class="captcha-img" @click="refreshCaptcha" fit="contain" />
          </el-form-item>
          <el-form-item v-if="errorMsg">
            <el-alert :title="errorMsg" type="error" show-icon class="error-msg" />
          </el-form-item>
          <el-form-item class="submit-row">
            <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin" native-type="submit">
              {{ getLoginButtonText() }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="auth-links">
        <el-button type="text" @click="goRegister">注册</el-button>
        <el-button type="text" @click="goForgetPassword">忘记密码</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-form {
  width: 100%;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.captcha-block {
  margin-bottom: 20px;
}

.captcha-block :deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.captcha-input {
  width: 100%;
}

.captcha-img {
  width: 140px;
  height: 46px;
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  display: block;
}

.submit-row :deep(.el-form-item__content) {
  justify-content: center;
}

.login-btn {
  width: 100%;
  height: 45px;
  font-size: 16px;
  border-radius: 25px;
}

.error-msg {
  width: 100%;
}
</style>
