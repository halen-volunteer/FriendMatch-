<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useNoticeStore } from '@/stores/notice'
import { getNoticeList, readNotices, deleteNotices } from '@/api/user'
import { Bell, Check, Close, UserFilled, User, WarningFilled, Message, Delete, ArrowRight, Loading } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const noticeStore = useNoticeStore()
const list = ref([])
const loading = ref(false)
const toast = ref({ msg: '', type: 'success' })
const section = computed(() => {
  const value = String(route.query.section || 'all')
  return ['all', 'friend', 'team', 'system'].includes(value) ? value : 'all'
})

function showToast(msg, type = 'success') {
  toast.value = { msg, type }
  setTimeout(() => (toast.value.msg = ''), 3000)
}

const typeMap = {
  1: { text: '好友申请', icon: 'UserFilled', path: '/friends', category: 'friend' },
  2: { text: '好友拒绝', icon: 'Close', path: null, category: 'friend' },
  3: { text: '入群通过', icon: 'Check', path: null, category: 'team' },
  4: { text: '入群拒绝', icon: 'Close', path: null, category: 'team' },
  5: { text: '被移出团队', icon: 'User', path: null, category: 'team' },
  6: { text: '账号异常', icon: 'WarningFilled', path: '/feedback', category: 'system' },
  7: { text: '处罚通知', icon: 'WarningFilled', path: '/feedback', category: 'system' },
  8: { text: '反馈回复', icon: 'Message', path: '/feedback', category: 'system' }
}

const filteredList = computed(() => {
  if (section.value === 'all') return list.value
  return list.value.filter(item => typeMap[item.noticeType]?.category === section.value)
})

async function load() {
  loading.value = true
  try {
    const res = await getNoticeList({ page: 1, pageSize: 50 })
    if (res.code === 200) {
      list.value = res.data?.records || res.data || []
    }
    noticeStore.clear()
  } finally {
    loading.value = false
  }
}

async function handleClick(item) {
  if (!item.isRead) {
    await readNotices([item.id])
    item.isRead = 1
  }
  const t = typeMap[item.noticeType]
  if (t?.path) {
    router.push(t.path)
  }
}

async function readAll() {
  const ids = list.value.filter(i => !i.isRead).map(i => i.id)
  if (!ids.length) return
  await readNotices(ids)
  list.value.forEach(i => (i.isRead = 1))
  noticeStore.clear()
  showToast('已全部标为已读')
}

async function handleDelete(id) {
  await deleteNotices([id])
  list.value = list.value.filter(i => i.id !== id)
}

function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
}

watch(() => route.query.section, () => {}, { immediate: true })
onMounted(load)
</script>

<template>
  <div class="page notices-page">
    <div class="page-header">
      <h2>通知中心</h2>
      <el-button
        type="info"
        @click="readAll"
      >
        <el-icon><Check /></el-icon>
        <span>全部已读</span>
      </el-button>
    </div>
    <el-alert
      v-if="toast.msg"
      :title="toast.msg"
      :type="toast.type"
      show-icon
      class="toast"
      @close="toast.msg = ''"
    />
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <el-empty v-else-if="!filteredList.length" description="暂无通知" />
    <div v-else class="notices-list">
      <el-card
        v-for="item in filteredList"
        :key="item.id"
        :class="['notice-item', { 'unread': !item.isRead }]"
        shadow="hover"
        @click="handleClick(item)"
      >
        <div class="notice-content">
          <div class="notice-left">
            <el-avatar :size="48" class="notice-icon" :class="`notice-icon-${item.noticeType}`">
              <el-icon v-if="typeMap[item.noticeType]?.icon === 'UserFilled'">
                <UserFilled />
              </el-icon>
              <el-icon v-else-if="typeMap[item.noticeType]?.icon === 'Close'">
                <Close />
              </el-icon>
              <el-icon v-else-if="typeMap[item.noticeType]?.icon === 'Check'">
                <Check />
              </el-icon>
              <el-icon v-else-if="typeMap[item.noticeType]?.icon === 'User'">
                <User />
              </el-icon>
              <el-icon v-else-if="typeMap[item.noticeType]?.icon === 'WarningFilled'">
                <WarningFilled />
              </el-icon>
              <el-icon v-else-if="typeMap[item.noticeType]?.icon === 'Message'">
                <Message />
              </el-icon>
              <el-icon v-else>
                <Bell />
              </el-icon>
            </el-avatar>
          </div>
          <div class="notice-body">
            <div class="notice-header">
              <span class="notice-type">{{ typeMap[item.noticeType]?.text }}</span>
              <span v-if="!item.isRead" class="unread-badge">未读</span>
            </div>
            <p class="notice-text">{{ item.noticeContent }}</p>
            <div class="notice-footer">
              <span class="notice-time">{{ formatTime(item.createTime) }}</span>
              <div v-if="typeMap[item.noticeType]?.path" class="notice-action">
                <el-button
                  type="text"
                  size="small"
                  @click.stop="handleClick(item)"
                >
                  <span>查看详情</span>
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
          <div class="notice-right">
            <el-button
              type="danger"
              size="small"
              @click.stop="handleDelete(item.id)"
              class="delete-btn"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.notices-page { max-width: 760px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 20px;
  font-weight: bold;
  color: #111;
  margin: 0;
}
.toast { margin-bottom: 20px; }
.loading-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.notices-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notice-item {
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}
.notice-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
}
.notice-item.unread {
  border-left: 4px solid #67c23a;
}
.notice-content {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.notice-left {
  flex-shrink: 0;
}
.notice-icon {
  border: 2px solid #f0f0f0;
  background: #f9f9f9;
}
.notice-icon-1 { background: rgba(103, 194, 58, 0.1); }
.notice-icon-2 { background: rgba(233, 30, 99, 0.1); }
.notice-icon-3 { background: rgba(103, 194, 58, 0.1); }
.notice-icon-4 { background: rgba(233, 30, 99, 0.1); }
.notice-icon-5 { background: rgba(255, 159, 64, 0.1); }
.notice-icon-6 { background: rgba(255, 193, 7, 0.1); }
.notice-icon-7 { background: rgba(255, 193, 7, 0.1); }
.notice-icon-8 { background: rgba(33, 150, 243, 0.1); }
.notice-body {
  flex: 1;
  min-width: 0;
}
.notice-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.notice-type {
  font-size: 14px;
  font-weight: bold;
  color: #333;
}
.unread-badge {
  font-size: 12px;
  padding: 2px 8px;
  background: #67c23a;
  color: white;
  border-radius: 999px;
}
.notice-text {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin: 0 0 12px 0;
  word-break: break-word;
}
.notice-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.notice-time {
  font-size: 12px;
  color: #999;
}
.notice-action {
  display: flex;
  align-items: center;
}
.notice-right {
  flex-shrink: 0;
}
.delete-btn {
  --el-button-size: 32px;
  padding: 0;
  width: 32px;
  height: 32px;
}
</style>