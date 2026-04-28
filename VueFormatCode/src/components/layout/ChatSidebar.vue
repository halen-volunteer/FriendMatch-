<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { getFriendList } from '@/api/user'
import { getTeamList, createTeam } from '@/api/team'
import { getUnreadCount } from '@/api/chat'
import { buildConversationId } from '@/utils/websocket'
import { Search, Plus, ChatLineRound, UserFilled, CollectionTag } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const chatStore = useChatStore()
const keyword = ref('')
const convList = ref([])
const loading = ref(false)
const quickMenuVisible = ref(false)
const showCreateTeam = ref(false)
const createForm = ref({ teamName: '', teamIntro: '', teamTags: '', teamType: 1, joinRule: 1, joinPassword: '', maxMember: 20 })

async function load() {
  loading.value = true
  try {
    const [fRes, tRes, uRes] = await Promise.all([
      getFriendList({ page: 1, pageSize: 100 }),
      getTeamList({ page: 1, pageSize: 100 }),
      getUnreadCount(),
    ])
    const unreadConvs = uRes.code === 200 && uRes.data ? (uRes.data.conversations || uRes.data || []) : []
    const unreadMap = Object.fromEntries(unreadConvs.map(item => [item.conversationId, item]))

    const friends = (fRes.code === 200 ? fRes.data?.records || fRes.data || [] : []).map(f => {
      const targetId = f.friendId ?? f.userId
      const id = buildConversationId(authStore.userId, targetId)
      const unread = unreadMap[id] || {}
      return {
        id,
        type: 'private',
        targetId,
        name: f.friendRemark || f.userNickname || f.userAccount,
        avatar: f.userAvatar,
        lastMsg: unread.lastMsg || unread.lastMessage || '',
        time: unread.lastTime || unread.updateTime || unread.createTime || '',
        unreadCount: unread.unreadCount || chatStore.unreadMap[id] || 0,
      }
    })

    const teams = (tRes.code === 200 ? tRes.data?.records || tRes.data || [] : []).map(t => {
      const targetId = t.id ?? t.teamId
      const id = `team_${targetId}`
      const unread = unreadMap[id] || {}
      return {
        id,
        type: 'team',
        targetId,
        name: t.teamName,
        avatar: t.teamAvatar || '',
        lastMsg: unread.lastMsg || unread.lastMessage || '',
        time: unread.lastTime || unread.updateTime || unread.createTime || '',
        unreadCount: unread.unreadCount || chatStore.unreadMap[id] || 0,
      }
    })

    const merged = [...friends, ...teams].sort((a, b) => new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime())
    convList.value = merged
    chatStore.setConversationList(merged)
    merged.forEach(item => chatStore.setUnread(item.id, item.unreadCount || 0))
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  if (!keyword.value) return convList.value
  return convList.value.filter(c => c.name?.includes(keyword.value) || c.lastMsg?.includes(keyword.value))
})

function goChat(conv) {
  if (conv.type === 'private') router.push(`/chat/private/${conv.targetId}`)
  else router.push(`/chat/team/${conv.targetId}`)
}

function isActive(conv) {
  if (conv.type === 'private') return route.path === `/chat/private/${conv.targetId}`
  return route.path === `/chat/team/${conv.targetId}`
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  return isToday
    ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString([], { month: 'numeric', day: 'numeric' })
}

function openCreateTeam() {
  quickMenuVisible.value = false
  showCreateTeam.value = true
}

function openSearchUser() {
  quickMenuVisible.value = false
  router.push('/search?tab=user')
}

function openSearchTeam() {
  quickMenuVisible.value = false
  router.push('/search?tab=team')
}

async function handleCreateTeam() {
  if (!createForm.value.teamName) return
  const res = await createTeam(createForm.value).catch(() => ({ code: 400 }))
  if (res.code === 200) {
    showCreateTeam.value = false
    createForm.value = { teamName: '', teamIntro: '', teamTags: '', teamType: 1, joinRule: 1, joinPassword: '', maxMember: 20 }
  }
}

onMounted(load)
</script>

<template>
  <aside class="session-list">
    <div class="session-top">
      <div class="session-search">
        <el-icon class="search-icon">
          <Search />
        </el-icon>
        <input v-model="keyword" type="text" placeholder="搜索会话" />
      </div>
      <div class="quick-wrap">
        <button class="create-btn" title="新建" @click="quickMenuVisible = !quickMenuVisible">
          <el-icon>
            <Plus />
          </el-icon>
        </button>
        <div v-if="quickMenuVisible" class="quick-menu">
          <button class="quick-item active" @click="openCreateTeam">创建团队</button>
          <button class="quick-item" @click="openSearchUser">
            <el-icon><UserFilled /></el-icon>
            <span>搜索用户</span>
          </button>
          <button class="quick-item" @click="openSearchTeam">
            <el-icon><CollectionTag /></el-icon>
            <span>搜索团队</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="session-empty">加载中...</div>

    <div v-else-if="!filtered.length" class="session-empty">
      <el-icon class="empty-icon">
        <ChatLineRound />
      </el-icon>
      <span>暂无会话</span>
    </div>

    <div v-else class="session-list-container">
      <div
        v-for="item in filtered"
        :key="item.id"
        class="session-item"
        :class="{ 'current-session': isActive(item) }"
        @click="goChat(item)"
      >
        <div class="cover">
          <el-badge :value="chatStore.unreadMap[item.id] || item.unreadCount || 0" :hidden="!(chatStore.unreadMap[item.id] || item.unreadCount || 0)">
            <el-avatar :src="item.avatar" :size="40">
              {{ item.name?.charAt(0) || '?' }}
            </el-avatar>
          </el-badge>
        </div>

        <div class="session-info">
          <div class="session-name">
            <span class="name">{{ item.name }}</span>
            <span class="update-at">{{ formatTime(item.time) }}</span>
          </div>
          <div class="last-chat">{{ item.lastMsg || '暂无消息' }}</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showCreateTeam" title="创建团队" width="500px">
      <el-form :model="createForm" class="create-form">
        <el-form-item label="团队名称" required>
          <el-input v-model="createForm.teamName" maxlength="64" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.teamIntro" type="textarea" rows="3" maxlength="512" placeholder="请输入团队简介" />
        </el-form-item>
        <el-form-item label="标签（逗号分隔）">
          <el-input v-model="createForm.teamTags" placeholder="请输入团队标签，逗号分隔" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.teamType" class="w-full">
            <el-option :value="1" label="公开" />
            <el-option :value="2" label="私有" />
          </el-select>
        </el-form-item>
        <el-form-item label="加入方式">
          <el-select v-model="createForm.joinRule" class="w-full">
            <el-option :value="1" label="申请审批" />
            <el-option :value="2" label="仅邀请" />
            <el-option :value="3" label="密码加入" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.joinRule === 3" label="入团密码">
          <el-input v-model="createForm.joinPassword" placeholder="请设置入团密码" />
        </el-form-item>
        <el-form-item label="最大人数">
          <el-input-number v-model="createForm.maxMember" :min="1" :max="1000" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="showCreateTeam = false">取消</el-button>
          <el-button type="primary" @click="handleCreateTeam">创建</el-button>
        </div>
      </template>
    </el-dialog>
  </aside>
</template>

<style scoped>
.session-list { width: 100%; height: 100%; background: #ffffff; text-align: left; display: flex; flex-direction: column; }
.session-top { display: flex; align-items: center; gap: 10px; height: 56px; padding: 10px 12px; border-bottom: 1px solid #e9e9e9; background: #ffffff; }
.session-search { flex: 1; display: flex; align-items: center; gap: 6px; padding: 7px 10px; border: 1px solid #dddddd; border-radius: 8px; background: #fafafa; }
.session-search input { width: 100%; border: none; outline: none; background: transparent; font-size: 12px; color: #555; }
.search-icon { color: #999; font-size: 14px; }
.quick-wrap { position: relative; }
.create-btn { width: 34px; height: 34px; border: none; border-radius: 10px; background: #edf3ff; color: var(--brand-primary); cursor: pointer; display: flex; align-items: center; justify-content: center; }
.create-btn:hover { background: #dcecff; }
.quick-menu { position: absolute; top: 44px; right: 0; z-index: 50; width: var(--quick-menu-width); padding: var(--quick-menu-padding); background: rgba(255,255,255,.98); border: 1px solid rgba(0,0,0,.06); border-radius: var(--quick-menu-radius); box-shadow: 0 12px 28px rgba(0,0,0,.12); }
.quick-item { width: 100%; display: flex; align-items: center; gap: var(--quick-menu-item-gap); border: none; background: transparent; border-radius: 10px; padding: var(--quick-menu-item-padding); font-size: var(--quick-menu-item-font); color: #1f1f1f; text-align: left; line-height: 1.25; font-weight: 500; }
.quick-item.active, .quick-item:hover { background: #f3f3f3; }
.session-list-container { flex: 1; overflow-y: auto; background: #fff; }
.session-item { display: flex; align-items: center; padding: 12px; cursor: pointer; border-bottom: 1px solid #f2f2f2; }
.session-item:hover { background: #f6f6f6; }
.current-session { background: #ededed; }
.current-session:hover { background: #ededed; }
.cover { width: 40px; min-width: 40px; height: 40px; }
.session-info { flex: 1; min-width: 0; margin-left: 10px; display: flex; flex-direction: column; gap: 4px; }
.session-name { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; color: #222; }
.update-at { flex-shrink: 0; font-size: 11px; color: #9b9b9b; }
.last-chat { font-size: 12px; color: #9a9a9a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #9b9b9b; font-size: 13px; }
.empty-icon { font-size: 22px; }
.create-form { margin-top: 12px; }
</style>
