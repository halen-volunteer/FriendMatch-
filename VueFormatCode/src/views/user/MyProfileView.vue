<script setup>
import TagList from '@/components/common/TagList.vue'
import TagInput from '@/components/common/TagInput.vue'
import { Edit, DocumentCopy } from '@element-plus/icons-vue'
import { useMyProfilePage } from '@/composables/useMyProfilePage'

const {
  avatarInputRef,
  chooseAvatar,
  copyAccount,
  editForm,
  editing,
  handleAvatarChange,
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
} = useMyProfilePage()
</script>

<template>
  <div class="page page-container-md">
    <el-alert
      v-if="toast.msg"
      :title="toast.msg"
      :type="toast.type"
      show-icon
      class="toast page-toast-lg"
      @close="toast.msg = ''"
    />

    <el-card class="profile-panel" shadow="hover">
      <div class="wx-profile-top">
        <div class="avatar-block">
          <el-avatar :size="80" :src="profile.userAvatar" class="avatar">
            {{ profileInitial }}
          </el-avatar>
          <el-button
            v-if="editing"
            type="primary"
            size="small"
            :loading="uploadingAvatar"
            class="avatar-btn"
            @click="chooseAvatar"
          >
            {{ uploadButtonText }}
          </el-button>
          <input ref="avatarInputRef" type="file" hidden accept="image/*" @change="handleAvatarChange" />
        </div>

        <div class="user-meta">
          <div class="user-header">
            <h2>{{ profile.userNickname }}</h2>
            <el-button v-if="!editing" type="primary" class="edit-btn" @click="editing = true">
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

          <p class="user-intro">{{ userIntro }}</p>
        </div>
      </div>

      <TagList v-if="shouldShowTagList" :tags="profileTags" />

      <el-form v-if="editing" :model="editForm" class="form-card">
        <el-form-item label="昵称" required>
          <el-input v-model="editForm.userNickname" placeholder="请输入昵称" maxlength="16" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="editForm.userIntro"
            type="textarea"
            rows="3"
            maxlength="512"
            placeholder="请输入简介"
          />
        </el-form-item>
        <el-form-item label="标签">
          <TagInput
            v-model="editForm.userTags"
            placeholder="输入标签后回车，也可以用逗号一次输入多个标签"
          />
        </el-form-item>
        <el-form-item>
          <div class="row-actions">
            <el-button type="primary" :loading="saving || uploadingAvatar" @click="saveProfile">
              {{ saveButtonText }}
            </el-button>
            <el-button @click="editing = false">取消</el-button>
          </div>
        </el-form-item>
      </el-form>

      <div v-if="!editing" class="profile-details">
        <el-divider content-position="left">资料详情</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="邮箱">{{ profile.userEmail || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.userPhone || '未设置' }}</el-descriptions-item>
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
  </div>
</template>

<style scoped>
.profile-panel,
.privacy-panel {
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
