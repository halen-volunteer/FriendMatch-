<script setup>
defineProps({
  user: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  getUserInitial: { type: Function, required: true },
  getUserAccount: { type: Function, required: true },
  getUserBio: { type: Function, required: true },
})

defineEmits(['remove-blacklist'])

function formatTime(value) {
  if (!value) return '未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未知' : date.toLocaleString('zh-CN')
}
</script>

<template>
  <div class="detail-card">
    <div class="profile-top">
      <el-avatar :size="82" :src="user?.userAvatar">
        {{ getUserInitial(user?.userNickname, '黑') }}
      </el-avatar>
      <div class="profile-main">
        <h3>{{ user?.userNickname }}</h3>
        <p>账号：{{ getUserAccount(user?.userAccount) }}</p>
      </div>
    </div>

    <div class="info-grid">
      <div class="info-row">
        <span class="label">状态</span>
        <span class="value">已加入黑名单</span>
      </div>
      <div class="info-row">
        <span class="label">拉黑时间</span>
        <span class="value">{{ formatTime(user?.createTime) }}</span>
      </div>
      <div class="info-row">
        <span class="label">个性签名</span>
        <span class="value">{{ getUserBio(user?.userIntro) }}</span>
      </div>
    </div>

    <div class="action-row">
      <el-button
        type="primary"
        :loading="loading"
        :disabled="loading"
        @click="$emit('remove-blacklist')"
      >
        解除拉黑
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-card {
  max-width: 760px;
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f0f0f0;
}

.profile-main h3 {
  margin: 0 0 6px;
  color: #202020;
  font-size: 28px;
}

.profile-main p {
  margin: 0;
  color: #8d8d8d;
  font-size: 16px;
}

.info-grid {
  margin-top: 12px;
}

.info-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 18px;
  padding: 18px 0;
  border-bottom: 1px solid #f3f3f3;
}

.label {
  color: #7d7d7d;
  font-size: 16px;
}

.value {
  color: #202020;
  font-size: 18px;
  line-height: 1.6;
}

.action-row {
  display: flex;
  gap: 12px;
  padding-top: 26px;
}
</style>
