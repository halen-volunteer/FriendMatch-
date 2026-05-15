<script setup>
import { RefreshLeft } from '@element-plus/icons-vue'
import ConfirmCard from '@/components/common/ConfirmCard.vue'
import { useBlacklistPage } from '@/composables/useBlacklistPage'

const {
  closeConfirm,
  confirmState,
  formatCreateTime,
  getUserInitial,
  handleRemove,
  list,
  submitRemove,
  toast,
} = useBlacklistPage()
</script>

<template>
  <div class="page blacklist-page">
    <div class="page-header">
      <h2>黑名单管理</h2>
    </div>

    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast" @close="toast.msg = ''" />

    <el-empty v-if="!list.length" description="当前还没有拉黑的用户" />

    <el-list v-else class="blacklist-list">
      <el-list-item v-for="item in list" :key="item.blackUserId" class="blacklist-item">
        <template #prefix>
          <el-avatar :size="48" :src="item.userAvatar" class="blacklist-avatar">
            {{ getUserInitial(item.userNickname) }}
          </el-avatar>
        </template>

        <div class="blacklist-info">
          <div class="blacklist-name">{{ item.userNickname || '未知用户' }}</div>
          <div class="blacklist-account">账号：{{ item.userAccount || '未知' }}</div>
          <div class="blacklist-time">拉黑时间：{{ formatCreateTime(item.createTime) }}</div>
        </div>

        <template #suffix>
          <el-button type="primary" size="small" round @click="handleRemove(item.blackUserId)">
            <el-icon><RefreshLeft /></el-icon>
            解除拉黑
          </el-button>
        </template>
      </el-list-item>
    </el-list>

    <ConfirmCard
      v-model:visible="confirmState.visible"
      title="解除拉黑"
      message="确认将该用户从黑名单中移除吗？移除后，对方可以再次与你发起正常互动。"
      confirm-text="确认解除"
      :loading="confirmState.loading"
      danger
      @confirm="submitRemove"
      @cancel="closeConfirm"
    />
  </div>
</template>

<style scoped>
.blacklist-page {
  max-width: 760px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: #111;
  font-size: 24px;
  font-weight: 700;
}

.toast {
  margin-bottom: 20px;
}

.blacklist-list {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
}

.blacklist-item {
  padding: 18px;
  border-bottom: 1px solid #f0f0f0;
}

.blacklist-item:last-child {
  border-bottom: none;
}

.blacklist-avatar {
  border: 2px solid #f0f0f0;
}

.blacklist-info {
  flex: 1;
  margin-left: 16px;
}

.blacklist-name {
  margin-bottom: 6px;
  color: #111;
  font-size: 16px;
  font-weight: 700;
}

.blacklist-account,
.blacklist-time {
  color: #888;
  font-size: 14px;
  line-height: 1.6;
}
</style>
