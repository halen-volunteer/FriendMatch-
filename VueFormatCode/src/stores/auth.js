import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const userId = computed(() => userInfo.value?.userId)
  const userNickname = computed(() => userInfo.value?.userNickname)
  const userAvatar = computed(() => userInfo.value?.userAvatar)
  const adminName = computed(() => userInfo.value?.adminName || '')
  const isAdmin = computed(() => !!userInfo.value?.isAdmin)

  function setAuth(data) {
    token.value = data.token
    userInfo.value = {
      userId: data.userId || data.id,
      userNickname: data.userNickname || '',
      userAvatar: data.userAvatar || '',
      userAccount: data.userAccount || '',
      isAdmin: !!data.isAdmin,
      adminId: data.adminId || null,
      adminName: data.adminName || '',
    }
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function clearAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  function updateUserInfo(patch) {
    userInfo.value = { ...userInfo.value, ...patch }
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  return { token, userInfo, isLoggedIn, userId, userNickname, userAvatar, adminName, isAdmin, setAuth, clearAuth, updateUserInfo }
})
