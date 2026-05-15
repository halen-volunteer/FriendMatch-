<script setup>
defineProps({
  request: { type: Object, default: null },
  requestSubmitting: { type: Boolean, default: false },
  getUserInitial: { type: Function, required: true },
  getUserAccount: { type: Function, required: true },
  getUserBio: { type: Function, required: true },
})

defineEmits(['agree', 'reject'])
</script>

<template>
  <div class="detail-card">
    <div class="profile-top">
      <el-avatar :size="82" :src="request?.userAvatar">
        {{ getUserInitial(request?.userNickname) }}
      </el-avatar>
      <div class="profile-main">
        <h3>{{ request?.userNickname }}</h3>
        <p>账号：{{ getUserAccount(request?.userAccount) }}</p>
      </div>
    </div>

    <div class="info-grid">
      <div class="info-row">
        <span class="label">申请理由</span>
        <span class="value">{{ request?.applyMsg || '请求添加你为好友' }}</span>
      </div>
      <div class="info-row">
        <span class="label">来源</span>
        <span class="value">{{ request?.source }}</span>
      </div>
      <div class="info-row">
        <span class="label">个性签名</span>
        <span class="value">{{ getUserBio(request?.userIntro) }}</span>
      </div>
    </div>

    <div class="action-row">
      <el-button
        type="primary"
        :loading="requestSubmitting"
        :disabled="requestSubmitting"
        @click="$emit('agree')"
      >
        通过申请
      </el-button>
      <el-button
        :loading="requestSubmitting"
        :disabled="requestSubmitting"
        @click="$emit('reject')"
      >
        拒绝
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
