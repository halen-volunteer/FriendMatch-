<script setup>
import { ref, onMounted } from 'vue'
import { getBlacklist, removeBlacklist } from '@/api/user'
import { RefreshLeft } from '@element-plus/icons-vue'

const list = ref([])
const toast = ref({ msg: '', type: 'success' })
function showToast(msg, type = 'success') { toast.value = { msg, type }; setTimeout(() => (toast.value.msg = ''), 3000) }
async function load() { const res = await getBlacklist({ page: 1, pageSize: 100 }); if (res.code === 200) list.value = (res.data?.records || res.data || []).map((item) => ({ ...item, blackUserId: item.blackUserId ?? item.userId })) }
async function handleRemove(blackUserId) { if (!confirm('确定解除拉黑？')) return; const res = await removeBlacklist(blackUserId); if (res.code === 200) { showToast('已解除拉黑'); await load() } else showToast(res.message || '操作失败', 'error') }
function getUserInitial(userNickname) { return userNickname?.charAt(0) || '用' }
function formatCreateTime(createTime) { return createTime ? new Date(createTime).toLocaleString('zh-CN') : '未知' }
onMounted(load)
</script>

<template>
  <div class="page blacklist-page">
    <div class="page-header">
      <h2>黑名单</h2>
    </div>
    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast" @close="toast.msg = ''" />
    <el-empty v-if="!list.length" description="黑名单为空" />
    <el-list v-else class="blacklist-list">
      <el-list-item v-for="item in list" :key="item.blackUserId" class="blacklist-item">
        <template #prefix>
          <el-avatar :size="48" :src="item.userAvatar" class="blacklist-avatar">
            {{ getUserInitial(item.userNickname) }}
          </el-avatar>
        </template>
        <div class="blacklist-info">
          <div class="blacklist-name">{{ item.userNickname }}</div>
          <div class="blacklist-time">
            拉黑时间：{{ formatCreateTime(item.createTime) }}
          </div>
        </div>
        <template #suffix>
          <el-button type="primary" size="small" @click="handleRemove(item.blackUserId)">
            <el-icon>
              <RefreshLeft />
            </el-icon>
            解除拉黑
          </el-button>
        </template>
      </el-list-item>
    </el-list>
  </div>
</template>

<style scoped>
.blacklist-page {
  max-width: 720px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: bold;
  color: #111;
  margin: 0;
}

.toast {
  margin-bottom: 20px;
}

.blacklist-list {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.blacklist-item {
  padding: 16px;
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
  font-size: 16px;
  font-weight: bold;
  color: #111;
  margin-bottom: 4px;
}

.blacklist-time {
  font-size: 14px;
  color: #888;
}
</style>
