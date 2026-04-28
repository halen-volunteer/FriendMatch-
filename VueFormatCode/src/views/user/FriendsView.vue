<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFriendList, getFriendRequests, agreeFriend, rejectFriend, getUserProfile } from '@/api/user'
import { getTeamList, getTeamDetail, searchTeam, applyTeam, joinByPassword } from '@/api/team'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { buildConversationId } from '@/utils/websocket'
import { Search, UserFilled, Lock, Message, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const authStore = useAuthStore()
const friends = ref([])
const requests = ref([])
const joinedTeams = ref([])
const squareTeams = ref([])
const currentFriend = ref(null)
const currentRequest = ref(null)
const currentTeam = ref(null)
const loading = ref(false)
const squareLoading = ref(false)
const toast = ref({ msg: '', type: 'success' })
const squareKeyword = ref('')
const showApply = ref({ show: false, team: null })
const showJoinPwd = ref({ show: false, team: null })
const applyMsg = ref('')
const joinPwd = ref('')

const section = computed(() => {
  const value = String(route.query.section || 'friends')
  return ['friends', 'requests', 'teams', 'square'].includes(value) ? value : 'friends'
})
const itemId = computed(() => String(route.query.itemId || ''))

function showToast(msg, type = 'success') { toast.value = { msg, type }; setTimeout(() => (toast.value.msg = ''), 3000) }
function normalizeFriend(item) {
  return {
    ...item,
    friendId: item.friendId ?? item.userId,
    userId: item.userId ?? item.friendId,
    userNickname: item.userNickname ?? item.friendRemark ?? item.nickname ?? '好友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? '',
    userIntro: item.userIntro ?? item.userBio ?? '',
  }
}

function normalizeRequest(item) {
  const target = item.target ?? item.userAccount ?? item.userEmail ?? ''
  const source = item.sourceText || item.source || (String(target).includes('@') ? '通过邮箱添加' : '通过账号添加')
  return {
    ...item,
    applicantId: item.applicantId ?? item.userId ?? item.friendId,
    userId: item.userId ?? item.applicantId ?? item.friendId,
    userNickname: item.userNickname ?? item.nickname ?? '新的朋友',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    userAccount: item.userAccount ?? item.account ?? target,
    applyMsg: item.applyMsg ?? item.message ?? '请求添加你为好友',
    source,
  }
}
function normalizeTeam(item) {
  return { ...item, id: item.id ?? item.teamId, teamIntro: item.teamIntro ?? item.teamDesc ?? '', maxMember: item.maxMember ?? item.maxMemberNum ?? 0, memberCount: item.memberCount ?? item.currentMemberCount ?? 0 }
}
async function loadSquareTeams() {
  squareLoading.value = true
  try {
    const res = squareKeyword.value
      ? await searchTeam({ type: 'name', keyword: squareKeyword.value, page: 1, pageSize: 20 })
      : await getTeamList({ page: 1, pageSize: 20 })
    if (res.code === 200) squareTeams.value = (res.data?.records || res.data || []).map(normalizeTeam)
  } finally {
    squareLoading.value = false
  }
}

async function loadBase() {
  loading.value = true
  try {
    const [friendRes, rRes, teamRes] = await Promise.all([
      getFriendList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
      getFriendRequests({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
      getTeamList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
    ])
    friends.value = (friendRes.data?.records || friendRes.data || []).map(normalizeFriend)
    requests.value = (rRes.data?.records || rRes.data || []).map(normalizeRequest)
    joinedTeams.value = (teamRes.data?.records || teamRes.data || []).map(normalizeTeam)
    await loadSquareTeams()
    await loadCurrent()
  } finally { loading.value = false }
}
async function loadCurrent() {
  currentFriend.value = null
  currentRequest.value = null
  currentTeam.value = null

  if (section.value === 'friends') {
    const base = friends.value.find(item => String(item.friendId) === itemId.value) || friends.value[0]
    if (!base) return
    const profileRes = await getUserProfile(base.userId).catch(() => ({ code: 400, data: {} }))
    currentFriend.value = { ...base, ...(profileRes.code === 200 ? profileRes.data || {} : {}) }
    if (String(base.friendId) !== itemId.value) router.replace({ path: '/friends', query: { section: 'friends', itemId: String(base.friendId) } })
    return
  }

  if (section.value === 'requests') {
    const base = requests.value.find(item => String(item.applicantId) === itemId.value) || requests.value[0]
    if (!base) return
    const profileRes = await getUserProfile(base.userId).catch(() => ({ code: 400, data: {} }))
    currentRequest.value = { ...base, ...(profileRes.code === 200 ? profileRes.data || {} : {}) }
    if (String(base.applicantId) !== itemId.value) router.replace({ path: '/friends', query: { section: 'requests', itemId: String(base.applicantId) } })
    return
  }

  if (section.value === 'teams') {
    const base = joinedTeams.value.find(item => String(item.id) === itemId.value) || joinedTeams.value[0]
    if (!base) return
    const detailRes = await getTeamDetail(base.id).catch(() => ({ code: 400, data: {} }))
    currentTeam.value = normalizeTeam({ ...base, ...(detailRes.code === 200 ? detailRes.data || {} : {}) })
    if (String(base.id) !== itemId.value) router.replace({ path: '/friends', query: { section: 'teams', itemId: String(base.id) } })
    return
  }

  const base = squareTeams.value.find(item => String(item.id) === itemId.value) || squareTeams.value[0]
  if (!base) return
  if (String(base.id) !== itemId.value) router.replace({ path: '/friends', query: { section: 'square', itemId: String(base.id) } })
}
async function handleAgree() {
  if (!currentRequest.value) return
  const res = await agreeFriend(currentRequest.value.applicantId).catch(() => ({ code: 400, message: '操作失败' }))
  if (res.code === 200) { showToast('已同意'); await loadBase() } else showToast(res.message || '操作失败', 'error')
}
async function handleReject() {
  if (!currentRequest.value) return
  const res = await rejectFriend(currentRequest.value.applicantId).catch(() => ({ code: 400, message: '操作失败' }))
  if (res.code === 200) { showToast('已拒绝'); await loadBase() } else showToast(res.message || '操作失败', 'error')
}
function upsertConversation(conversation) {
  const list = [...(chatStore.conversationList || [])]
  const index = list.findIndex(item => item.id === conversation.id)
  if (index >= 0) list.splice(index, 1)
  chatStore.setConversationList([conversation, ...list])
}

function sendMessageToFriend() {
  if (!currentFriend.value?.friendId) return
  const id = buildConversationId(authStore.userId, currentFriend.value.friendId)
  upsertConversation({
    id,
    type: 'private',
    targetId: currentFriend.value.friendId,
    name: currentFriend.value.userNickname || currentFriend.value.userAccount || '好友',
    avatar: currentFriend.value.userAvatar || '',
    lastMsg: '',
    time: new Date().toISOString(),
    unreadCount: 0,
  })
  router.push(`/chat/private/${currentFriend.value.friendId}`)
}

function enterTeam(team = currentTeam.value) {
  if (!team?.id) return
  const convId = `team_${team.id}`
  upsertConversation({ id: convId, type: 'team', targetId: team.id, name: team.teamName, avatar: team.teamAvatar || '', lastMsg: '', time: new Date().toISOString(), unreadCount: 0 })
  router.push(`/chat/team/${team.id}`)
}

async function handleApply() {
  const teamId = showApply.value.team?.id ?? showApply.value.team?.teamId
  const res = await applyTeam({ teamId, applyMsg: applyMsg.value }).catch(() => ({ code: 400, message: '申请失败' }))
  if (res.code === 200) {
    showToast('申请已发送')
    showApply.value.show = false
  } else showToast(res.message || '申请失败', 'error')
}

async function handleJoinPwd() {
  const teamId = showJoinPwd.value.team?.id ?? showJoinPwd.value.team?.teamId
  const res = await joinByPassword({ teamId, teamPassword: joinPwd.value }).catch(() => ({ code: 400, message: '加入失败' }))
  if (res.code === 200) {
    showToast('加入成功')
    showJoinPwd.value.show = false
    await Promise.all([loadSquareTeams(), loadBase()])
  } else showToast(res.message || '密码错误', 'error')
}

function handleApplyDialog(team) {
  showApply.value.team = team
  showApply.value.show = true
  applyMsg.value = ''
}

function handleJoinPasswordDialog(team) {
  showJoinPwd.value.team = team
  showJoinPwd.value.show = true
  joinPwd.value = ''
}

function getUserInitial(name, fallback = '友') {
  return name?.charAt(0) || fallback
}

function getUserAccount(account) {
  return account || '未知'
}

function getUserBio(primaryBio, fallbackBio) {
  return primaryBio || fallbackBio || '这个人很安静，什么都没留下。'
}

function getTeamInitial(name) {
  return name?.charAt(0) || '团'
}

function getTeamMemberText(memberCount, maxMember) {
  return `${memberCount || 0}/${maxMember || 0} 人`
}

function getTeamIntro(teamIntro) {
  return teamIntro || '暂无简介'
}

function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

function getJoinRuleText(joinRule) {
  if (joinRule === 1) return '申请审批'
  if (joinRule === 2) return '仅邀请'
  return '密码加入'
}

function openTeamDetail(teamId) {
  router.push(`/teams/${teamId}`)
}
watch(() => route.fullPath, loadCurrent)
onMounted(loadBase)
</script>

<template>
  <div class="friends-view-root">
  <div class="page friends-page contact-manage-page">
    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast" @close="toast.msg = ''" />
    <div v-if="loading" class="detail-empty">加载中...</div>
    <template v-else-if="section === 'friends'">
      <div class="detail-head"><h2>好友</h2></div>
      <div v-if="!currentFriend" class="detail-empty">请选择一个好友</div>
      <div v-else class="detail-card">
        <div class="profile-top">
          <el-avatar :size="82" :src="currentFriend.userAvatar">{{ getUserInitial(currentFriend.userNickname) }}</el-avatar>
          <div class="profile-main">
            <h3>{{ currentFriend.userNickname }}</h3>
            <p>账号：{{ getUserAccount(currentFriend.userAccount) }}</p>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-row"><span class="label">备注</span><span class="value">{{ currentFriend.friendRemark || currentFriend.userNickname || '未设置' }}</span></div>
          <div class="info-row"><span class="label">标签</span><span class="value">{{ currentFriend.userTags || '暂无标签' }}</span></div>
          <div class="info-row"><span class="label">来源</span><span class="value">好友列表</span></div>
          <div class="info-row"><span class="label">个性签名</span><span class="value">{{ getUserBio(currentFriend.userBio, currentFriend.userIntro) }}</span></div>
        </div>
        <div class="action-row"><el-button type="primary" @click="sendMessageToFriend">发消息</el-button></div>
      </div>
    </template>

    <template v-else-if="section === 'requests'">
      <div class="detail-head"><h2>新朋友</h2></div>
      <div v-if="!currentRequest" class="detail-empty">请选择一个好友申请</div>
      <div v-else class="detail-card">
        <div class="profile-top">
          <el-avatar :size="82" :src="currentRequest.userAvatar">{{ getUserInitial(currentRequest.userNickname) }}</el-avatar>
          <div class="profile-main">
            <h3>{{ currentRequest.userNickname }}</h3>
            <p>账号：{{ getUserAccount(currentRequest.userAccount) }}</p>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-row"><span class="label">申请理由</span><span class="value">{{ currentRequest.applyMsg || '请求添加你为好友' }}</span></div>
          <div class="info-row"><span class="label">来源</span><span class="value">{{ currentRequest.source }}</span></div>
          <div class="info-row"><span class="label">个性签名</span><span class="value">{{ getUserBio(currentRequest.userBio, currentRequest.userIntro) }}</span></div>
        </div>
        <div class="action-row">
          <el-button type="primary" @click="handleAgree">通过申请</el-button>
          <el-button @click="handleReject">拒绝</el-button>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'teams'">
      <div class="detail-head"><h2>团队管理</h2></div>
      <div v-if="!currentTeam" class="detail-empty">请选择一个团队</div>
      <div v-else class="detail-card team-card-detail">
        <div class="team-cover">
          <el-avatar :size="88" :src="currentTeam.teamAvatar">{{ getTeamInitial(currentTeam.teamName) }}</el-avatar>
          <div class="team-main"><h3>{{ currentTeam.teamName }}</h3><p>{{ getTeamMemberText(currentTeam.memberCount, currentTeam.maxMember) }}</p></div>
        </div>
        <div v-if="currentTeam.teamTags" class="tag-row"><el-tag v-for="tag in String(currentTeam.teamTags).split(',').filter(Boolean)" :key="tag" size="small">{{ tag }}</el-tag></div>
        <div class="info-grid">
          <div class="info-row"><span class="label">团队简介</span><span class="value">{{ getTeamIntro(currentTeam.teamIntro) }}</span></div>
          <div class="info-row"><span class="label">团队类型</span><span class="value">{{ getTeamTypeText(currentTeam.teamType) }}</span></div>
          <div class="info-row"><span class="label">加入方式</span><span class="value">{{ getJoinRuleText(currentTeam.joinRule) }}</span></div>
        </div>
        <div class="action-row"><el-button type="success" @click="enterTeam(currentTeam)">进入团队</el-button></div>
      </div>
    </template>

    <template v-else>
      <div class="square-shell">
        <div class="page-header square-header">
          <h2>团队广场</h2>
        </div>

        <div class="search-bar">
          <el-input v-model="squareKeyword" placeholder="搜索团队名称或标签..." @keyup.enter="loadSquareTeams" class="search-input">
            <template #append>
              <el-button type="primary" @click="loadSquareTeams">
                <el-icon><Search /></el-icon>
                <span>搜索</span>
              </el-button>
            </template>
          </el-input>
        </div>

        <div v-if="squareLoading" class="square-loading-container">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <el-empty v-else-if="!squareTeams.length" description="暂无团队" />
        <div v-else class="team-grid">
          <el-card v-for="t in squareTeams" :key="t.id" shadow="hover" class="team-card" @click="openTeamDetail(t.id)">
            <template #header>
              <div class="team-header">
                <el-avatar :size="48" :src="t.teamAvatar" class="team-avatar">{{ getTeamInitial(t.teamName) }}</el-avatar>
                <div class="team-info">
                  <h3 class="team-name">{{ t.teamName }}</h3>
                  <div class="team-meta">
                    <span class="team-members">{{ t.memberCount || 0 }}人</span>
                    <span class="team-type">{{ getTeamTypeText(t.teamType) }}</span>
                  </div>
                </div>
              </div>
            </template>
            <div class="team-intro">{{ getTeamIntro(t.teamIntro) }}</div>
            <div v-if="t.teamTags" class="team-tags">
              <el-tag v-for="tag in String(t.teamTags).split(',').filter(Boolean)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
            <div class="team-actions" @click.stop>
              <el-button v-if="t.joinRule === 1" type="primary" size="small" @click="handleApplyDialog(t)">
                <el-icon><UserFilled /></el-icon>
                申请加入
              </el-button>
              <el-button v-else-if="t.joinRule === 3" type="success" size="small" @click="handleJoinPasswordDialog(t)">
                <el-icon><Lock /></el-icon>
                密码加入
              </el-button>
              <el-button v-else type="info" size="small" disabled>
                <el-icon><Message /></el-icon>
                仅邀请
              </el-button>
            </div>
          </el-card>
        </div>
      </div>
    </template>
  </div>

    <el-dialog v-model="showApply.show" :title="`申请加入「${showApply.team?.teamName}」`" width="400px">
      <el-form :model="{ applyMsg }">
        <el-form-item label="申请留言（选填）">
          <el-input v-model="applyMsg" type="textarea" rows="3" placeholder="请输入申请留言" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="showApply.show = false">取消</el-button>
          <el-button type="primary" @click="handleApply">发送申请</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="showJoinPwd.show" :title="`加入「${showJoinPwd.team?.teamName}」`" width="400px">
      <el-form :model="{ joinPwd }">
        <el-form-item label="入团密码" required>
          <el-input v-model="joinPwd" type="password" placeholder="请输入入团密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-row">
          <el-button @click="showJoinPwd.show = false">取消</el-button>
          <el-button type="primary" @click="handleJoinPwd">加入</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.friends-view-root {
  height: 100%;
}

.contact-manage-page {
  max-width: none;
  height: 100%;
  padding: 28px 32px;
  background: #fff;
}

.toast {
  margin-bottom: 16px;
}

.detail-head h2,
.square-header h2 {
  margin: 0 0 18px;
  color: #1f1f1f;
  font-size: 28px;
}

.detail-card {
  max-width: 760px;
}

.profile-top,
.team-cover {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f0f0f0;
}

.profile-main h3,
.team-main h3 {
  margin: 0 0 6px;
  color: #202020;
  font-size: 28px;
}

.profile-main p,
.team-main p {
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

.tag-row,
.team-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.action-row {
  display: flex;
  gap: 12px;
  padding-top: 26px;
}

.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  font-size: 16px;
}

.loading-container,
.square-loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  color: #999;
  font-size: 16px;
}

.team-card-detail {
  padding-top: 18px;
}

.square-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.square-header,
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-input {
  width: 100%;
}

.team-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  overflow: auto;
  padding-bottom: 8px;
}

.team-card {
  cursor: pointer;
  transition: all 0.3s ease;
}

.team-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.team-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.team-avatar {
  border: 3px solid #f0f0f0;
}

.team-info {
  flex: 1;
}

.team-name {
  margin: 0 0 4px 0;
  color: #111;
  font-size: 18px;
  font-weight: 700;
}

.team-meta {
  display: flex;
  gap: 12px;
  color: #888;
  font-size: 14px;
}

.team-intro {
  display: -webkit-box;
  margin: 12px 0;
  overflow: hidden;
  color: #555;
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

.team-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.create-form {
  margin-top: 20px;
}
</style>