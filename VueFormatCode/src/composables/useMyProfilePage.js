import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getMe } from '@/api/auth'
import { updateProfile, getPrivacy, updatePrivacy } from '@/api/user'
import { presignUpload } from '@/api/oss'
import { uploadToQiniu } from '@/utils/qiniuUpload'
import { useToast } from '@/composables/useToast'
import { getErrorMessage } from '@/utils/response'

export function useMyProfilePage() {
  const authStore = useAuthStore()
  const profile = ref({})
  const privacy = ref({ viewInfo: 1, sendMsg: 1, searchByEmail: 1 })
  const editForm = ref({ userNickname: '', userAvatar: '', userIntro: '', userTags: '' })
  const editing = ref(false)
  const saving = ref(false)
  const uploadingAvatar = ref(false)
  const avatarInputRef = ref(null)
  const { toast, showToast } = useToast()

  const profileInitial = computed(() => profile.value.userNickname?.charAt(0) || '用')
  const uploadButtonText = computed(() => uploadingAvatar.value ? '上传中...' : '更换头像')
  const userIntro = computed(() => profile.value.userIntro || '这个人很安静，什么都没有留下。')
  const shouldShowTagList = computed(() => Boolean(profile.value.userTags) && !editing.value)
  const profileTags = computed(() => String(profile.value.userTags || '').split(',').filter(Boolean))
  const saveButtonText = computed(() => saving.value ? '保存中...' : '保存')

  async function copyAccount() {
    try {
      await navigator.clipboard.writeText(profile.value.userAccount)
      showToast('账号已复制到剪贴板')
    } catch {
      showToast('复制失败，请手动复制', 'error')
    }
  }

  async function loadProfile() {
    try {
      const res = await getMe()
      if (res.code === 200) {
        profile.value = res.data
        editForm.value = {
          userNickname: res.data.userNickname || '',
          userAvatar: res.data.userAvatar || '',
          userIntro: res.data.userIntro || '',
          userTags: res.data.userTags || '',
        }
      }
    } catch (error) {
      showToast(getErrorMessage(error, '加载个人资料失败'), 'error')
    }
  }

  async function loadPrivacy() {
    try {
      const res = await getPrivacy()
      if (res.code === 200) {
        privacy.value = res.data
      }
    } catch (error) {
      showToast(getErrorMessage(error, '加载隐私设置失败'), 'error')
    }
  }

  function chooseAvatar() {
    avatarInputRef.value?.click()
  }

  async function handleAvatarChange(event) {
    const file = event.target.files?.[0]
    if (!file) return
    uploadingAvatar.value = true
    try {
      const presignRes = await presignUpload({ fileName: file.name, msgType: 2, fileSize: file.size })
      if (!presignRes?.data?.uploadUrl || !presignRes?.data?.uploadToken || !presignRes?.data?.key || !presignRes?.data?.fileUrl) {
        throw new Error('上传凭证不完整')
      }
      await uploadToQiniu({
        file,
        uploadUrl: presignRes.data.uploadUrl,
        uploadToken: presignRes.data.uploadToken,
        key: presignRes.data.key,
      })
      const avatarUrl = presignRes.data.fileUrl
      const saveRes = await updateProfile({ userAvatar: avatarUrl })
      if (saveRes.code !== 200) {
        throw new Error(saveRes.message || '头像保存失败')
      }
      editForm.value.userAvatar = avatarUrl
      profile.value = { ...profile.value, userAvatar: avatarUrl }
      authStore.updateUserInfo({ userAvatar: avatarUrl })
      await loadProfile()
      showToast('头像上传成功')
    } catch (error) {
      const message = error?.response?.status === 401
        ? '登录已失效，请重新登录后再上传头像'
        : getErrorMessage(error, '头像上传失败')
      showToast(message, 'error')
    } finally {
      uploadingAvatar.value = false
      event.target.value = ''
    }
  }

  async function saveProfile() {
    saving.value = true
    try {
      const res = await updateProfile(editForm.value)
      if (res.code === 200) {
        authStore.updateUserInfo({
          userNickname: editForm.value.userNickname,
          userAvatar: editForm.value.userAvatar,
        })
        await loadProfile()
        editing.value = false
        showToast('保存成功')
      } else {
        showToast(res.message || '保存失败', 'error')
      }
    } catch (error) {
      showToast(getErrorMessage(error, '保存失败'), 'error')
    } finally {
      saving.value = false
    }
  }

  async function savePrivacy() {
    try {
      const res = await updatePrivacy(privacy.value)
      if (res.code === 200) {
        showToast('隐私设置已保存')
      } else {
        showToast(res.message || '保存失败', 'error')
      }
    } catch (error) {
      showToast(getErrorMessage(error, '保存失败'), 'error')
    }
  }

  onMounted(() => {
    loadProfile()
    loadPrivacy()
  })

  return {
    avatarInputRef,
    chooseAvatar,
    copyAccount,
    editForm,
    editing,
    handleAvatarChange,
    loadPrivacy,
    loadProfile,
    privacy,
    profile,
    profileInitial,
    profileTags,
    saveButtonText,
    savePrivacy,
    saveProfile,
    saving,
    shouldShowTagList,
    toast,
    uploadButtonText,
    uploadingAvatar,
    userIntro,
  }
}
