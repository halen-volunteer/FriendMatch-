<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getMe } from '@/api/auth'
import { updateProfile, getPrivacy, updatePrivacy } from '@/api/user'
import { presignUpload } from '@/api/oss'
import { uploadToQiniu } from '@/utils/qiniuUpload'
import TagList from '@/components/common/TagList.vue'
import { Edit, DocumentCopy } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const profile = ref({})
const privacy = ref({ viewInfo: 1, sendMsg: 1, searchByEmail: 1 })
const editForm = ref({ userNickname: '', userAvatar: '', userIntro: '', userTags: '' })
const editing = ref(false)
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarInputRef = ref(null)
const toast = ref({ msg: '', type: 'success' })
const darkMode = ref(false)
const fontSize = ref(14)

function getProfileInitial() {
  return profile.value.userNickname?.charAt(0) || '用'
}

function getUploadButtonText() {
  return uploadingAvatar.value ? '上传中...' : '更换头像'
}

function getUserIntro() {
  return profile.value.userIntro || '这个人很安静，什么都没留下。'
}

function shouldShowTagList() {
  return Boolean(profile.value.userTags) && !editing.value
}

function getProfileTags() {
  return String(profile.value.userTags || '').split(',').filter(Boolean)
}

function getSaveButtonText() {
  return saving.value ? '保存中...' : '保存'
}

function formatCreateTime(createTime) {
  return createTime ? new Date(createTime).toLocaleString('zh-CN') : '未知'
}

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

async function copyAccount() {
  try {
    await navigator.clipboard.writeText(profile.value.userAccount)
    showToast('账号已复制到剪贴板')
  } catch {
    showToast('复制失败，请手动复制', 'error')
  }
}

async function loadProfile() {
  const res = await getMe()
  if (res.code === 200) {
    profile.value = res.data
    editForm.value = { userNickname: res.data.userNickname || '', userAvatar: res.data.userAvatar || '', userIntro: res.data.userIntro || '', userTags: res.data.userTags || '' }
  }
}

async function loadPrivacy() {
  const res = await getPrivacy()
  if (res.code === 200) privacy.value = res.data
}

function chooseAvatar() { avatarInputRef.value?.click() }

async function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const presignRes = await presignUpload({ fileName: file.name, msgType: 2, fileSize: file.size })
    if (!presignRes?.data?.uploadUrl || !presignRes?.data?.uploadToken || !presignRes?.data?.key || !presignRes?.data?.fileUrl) {
      throw new Error('上传凭证不完整')
    }
    await uploadToQiniu({ file, uploadUrl: presignRes.data.uploadUrl, uploadToken: presignRes.data.uploadToken, key: presignRes.data.key })
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
      : error?.response?.message || error?.message || '头像上传失败'
    showToast(message, 'error')
  }
  finally { uploadingAvatar.value = false; event.target.value = '' }
}

async function saveProfile() {
  saving.value = true
  try {
    const res = await updateProfile(editForm.value)
    if (res.code === 200) {
      authStore.updateUserInfo({ userNickname: editForm.value.userNickname, userAvatar: editForm.value.userAvatar })
      await loadProfile()
      editing.value = false
      showToast('保存成功')
    } else showToast(res.message || '保存失败', 'error')
  } catch { showToast('保存失败', 'error') }
  finally { saving.value = false }
}

async function savePrivacy() {
  const res = await updatePrivacy(privacy.value)
  if (res.code === 200) showToast('隐私设置已保存')
  else showToast(res.message || '保存失败', 'error')
}

onMounted(() => { loadProfile(); loadPrivacy() })
</script>

<template>
  <div class="page page-container-md">
    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast page-toast-lg" @close="toast.msg = ''" />
    <el-card class="profile-panel" shadow="hover">
      <div class="wx-profile-top">
        <div class="avatar-block">
          <el-avatar :size="80" :src="profile.userAvatar" class="avatar">
            {{ getProfileInitial() }}
          </el-avatar>
          <el-button v-if="editing" type="primary" size="small" :loading="uploadingAvatar" @click="chooseAvatar"
            class="avatar-btn">
            {{ getUploadButtonText() }}
          </el-button>
          <input ref="avatarInputRef" type="file" hidden accept="image/*" @change="handleAvatarChange" />
        </div>
        <div class="user-meta">
          <div class="user-header">
            <h2>{{ profile.userNickname }}</h2>
            <el-button v-if="!editing" type="primary" @click="editing = true" class="edit-btn">
              <el-icon>
                <Edit />
              </el-icon>
              <span>编辑资料</span>
            </el-button>
          </div>
          <div class="user-info">
            <span class="user-account">@{{ profile.userAccount }}</span>
            <el-button type="text" size="small" @click="copyAccount">
              <el-icon>
                <DocumentCopy />
              </el-icon>
              复制账号
            </el-button>
          </div>
          <p class="user-intro">{{ getUserIntro() }}</p>
        </div>
      </div>
      <TagList v-if="shouldShowTagList()" :tags="getProfileTags()" />
      <el-form v-if="editing" :model="editForm" class="form-card">
        <el-form-item label="昵称" required>
          <el-input v-model="editForm.userNickname" placeholder="请输入昵称" maxlength="16" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.userIntro" type="textarea" rows="3" maxlength="512" placeholder="请输入简介" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.userTags" placeholder="请输入标签，逗号分隔" />
        </el-form-item>
        <el-form-item>
          <div class="row-actions">
            <el-button type="primary" :loading="saving || uploadingAvatar" @click="saveProfile">
              {{ getSaveButtonText() }}
            </el-button>
            <el-button @click="editing = false">取消</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div class="profile-details" v-if="!editing">
        <el-divider content-position="left">资料详情</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="邮箱">{{ profile.userEmail || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.userPhone || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ formatCreateTime(profile.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="账号ID">{{ profile.userId || '未知' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <el-card class="privacy-panel section-gap-top" shadow="hover">
      <template #header>
        <div class="card-header card-header-inline">
          <span class="card-header-title">隐私设置</span>
        </div>
      </template>
      <el-form :model="privacy">
        <el-form-item label="资料可见性">
          <el-select v-model="privacy.viewInfo" class="w-full">
            <el-option :value="1" label="所有人" />
            <el-option :value="2" label="仅团队成员" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息接收">
          <el-select v-model="privacy.sendMsg" class="w-full">
            <el-option :value="1" label="所有人" />
            <el-option :value="2" label="仅团队成员" />
            <el-option :value="3" label="需要验证" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱搜索">
          <el-select v-model="privacy.searchByEmail" class="w-full">
            <el-option :value="1" label="允许" />
            <el-option :value="0" label="不允许" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="savePrivacy">保存隐私设置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="settings-panel section-gap-top" shadow="hover">
      <template #header>
        <div class="card-header card-header-inline">
          <span class="card-header-title">通用设置</span>
        </div>
      </template>
      <el-form>
        <el-form-item label="暗黑模式">
          <el-switch v-model="darkMode" />
        </el-form-item>
        <el-form-item label="字体大小">
          <el-slider v-model="fontSize" :min="12" :max="18" :step="1" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>

.profile-panel,
.privacy-panel,
.settings-panel {
  margin-bottom: 20px;
}

.wx-profile-top {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar {
  border: 3px solid #f0f0f0;
}

.avatar-btn {
  margin-top: 8px;
}

.user-meta {
  flex: 1;
}

.user-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.user-header h2 {
  font-size: 24px;
  font-weight: bold;
  color: #111;
  margin: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.user-account {
  color: #888;
  font-size: 14px;
}

.user-intro {
  color: #555;
  line-height: 1.7;
  margin: 0;
}

.form-card {
  margin-top: 20px;
}

.row-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.profile-details {
  margin-top: 30px;
}

</style>
