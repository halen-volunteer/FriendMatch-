<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { searchUser, searchTeam, getSearchHistory, clearSearchHistory, getHotKeywords, getSearchSuggest } from '@/api/search'
import { Search, User, UserFilled, Delete, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const keyword = ref('')
const activeTab = ref(String(route.query.tab || 'user') === 'team' ? 'team' : 'user')
const userList = ref([])
const teamList = ref([])
const historyList = ref([])
const hotList = ref([])
const suggestList = ref([])
const loading = ref(false)
const currentSearchType = computed(() => (activeTab.value === 'user' ? 1 : 2))
const hasKeyword = computed(() => Boolean(keyword.value))
const hasUserResults = computed(() => userList.value.length > 0)
const hasTeamResults = computed(() => teamList.value.length > 0)
const shouldShowUserEmpty = computed(() => hasKeyword.value && !hasUserResults.value)
const shouldShowTeamEmpty = computed(() => hasKeyword.value && !hasTeamResults.value)

function normalizeTeam(item) {
  return {
    ...item,
    id: item.id ?? item.teamId,
    teamIntro: item.teamIntro ?? item.teamDesc ?? '',
    maxMember: item.maxMember ?? item.maxMemberNum ?? 0,
  }
}

function getSuggestText(item) {
  if (typeof item === 'string') return item
  return item?.keyword || item?.name || ''
}

function getSuggestKey(item) {
  return typeof item === 'string' ? item : (item?.keyword || item?.name || item?.id || '')
}

function getUserKey(user) {
  return user.userId || user.id
}

function getUserInitial(user) {
  return user.userNickname?.charAt(0) || '用'
}

function getUserAccount(user) {
  return user.userAccount || '未知'
}

function getTeamInitial(team) {
  return team.teamName?.charAt(0) || '团'
}

function getTeamIntro(team) {
  return team.teamIntro || '暂无简介'
}

function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

function viewUser(user) {
  router.push(`/profile/${getUserKey(user)}`)
}

function viewTeam(team) {
  router.push(`/teams/${team.id}`)
}

async function loadAssistData() {
  const [historyRes, hotRes] = await Promise.all([
    getSearchHistory({ searchType: currentSearchType.value, limit: 10 }).catch(() => ({ code: 400, data: [] })),
    getHotKeywords({ searchType: currentSearchType.value, limit: 10 }).catch(() => ({ code: 400, data: [] }))
  ])
  historyList.value = historyRes.data || []
  hotList.value = hotRes.data || []
}

async function handleSearch(kw = keyword.value) {
  if (!kw) return
  keyword.value = kw
  loading.value = true
  try {
    const [uRes, tRes] = await Promise.all([
      searchUser({ keyword: kw, page: 1, pageSize: 20 }),
      searchTeam({ type: 'name', keyword: kw, page: 1, pageSize: 20 })
    ])
    if (uRes.code === 200) {
      userList.value = uRes.data?.records || uRes.data || []
    }
    if (tRes.code === 200) {
      teamList.value = (tRes.data?.records || tRes.data || []).map(normalizeTeam)
    }
  } finally {
    loading.value = false
  }
}

async function handleClearHistory() {
  await clearSearchHistory().catch(() => { })
  historyList.value = []
}

watch(() => route.query.tab, async (tab) => {
  activeTab.value = String(tab || 'user') === 'team' ? 'team' : 'user'
  suggestList.value = []
  await loadAssistData()
}, { immediate: true })

watch(keyword, async (val) => {
  const kw = val.trim()
  if (!kw) {
    suggestList.value = []
    return
  }
  const res = await getSearchSuggest({ keyword: kw, type: currentSearchType.value, limit: 8 }).catch(() => ({ code: 400, data: [] }))
  if (res.code === 200) suggestList.value = res.data || []
})

onMounted(() => {
  loadAssistData()
})
</script>

<template>
  <div class="page search-page">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">搜索中心</h2>
    </div>
    <div class="search-box">
      <el-input v-model="keyword" placeholder="搜索用户或团队..." prefix-icon="Search" @keyup.enter="handleSearch()"
        class="search-input" />
      <el-button type="primary" @click="handleSearch()" class="search-btn">
        <el-icon>
          <Search />
        </el-icon>
        <span>搜索</span>
      </el-button>
    </div>
    <div v-if="suggestList.length" class="suggest-box">
      <el-tag v-for="item in suggestList" :key="getSuggestKey(item)"
        class="suggest-tag" effect="plain" @click="handleSearch(getSuggestText(item))">
        {{ getSuggestText(item) || '联想词' }}
      </el-tag>
    </div>
    <div v-if="activeTab === 'user'" class="search-mode-hint">
      <el-icon><User /></el-icon>
      <span>当前为用户搜索</span>
    </div>
    <div v-else class="search-mode-hint">
      <el-icon><UserFilled /></el-icon>
      <span>当前为团队搜索</span>
    </div>

    <div class="assist-grid">
      <el-card shadow="hover" class="assist-card">
        <div class="card-header">
          <h3>搜索历史</h3>
          <el-button type="text" @click="handleClearHistory" class="clear-btn">
            <el-icon>
              <Delete />
            </el-icon>
            <span>清空</span>
          </el-button>
        </div>
        <el-empty v-if="!historyList.length" description="暂无历史" class="empty-small" />
        <div v-else class="tag-wrap">
          <el-tag v-for="item in historyList" :key="item.id || item.searchKeyword" type="info" effect="plain"
            @click="handleSearch(item.searchKeyword)" class="history-tag">
            {{ item.searchKeyword }}
          </el-tag>
        </div>
      </el-card>
      <el-card shadow="hover" class="assist-card">
        <h3 class="card-title">热门搜索</h3>
        <el-empty v-if="!hotList.length" description="暂无数据" class="empty-small" />
        <div v-else class="tag-wrap">
          <el-tag v-for="item in hotList" :key="item.id || item.keyword" type="warning" effect="plain"
            @click="handleSearch(item.keyword)" class="hot-tag">
            {{ item.keyword }}
          </el-tag>
        </div>
      </el-card>
    </div>

    <div v-if="loading" class="loading-container loading-center-lg">
      <el-icon class="is-loading">
        <Search />
      </el-icon>
      <span>搜索中...</span>
    </div>

    <div v-else-if="activeTab === 'user'" class="user-list">
      <el-empty v-if="shouldShowUserEmpty" description="未找到相关用户" />
      <div v-else class="user-result-list">
        <div v-for="u in userList" :key="getUserKey(u)" class="user-item">
          <div class="user-item-content">
            <el-avatar :size="48" :src="u.userAvatar" class="user-avatar">
              {{ getUserInitial(u) }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ u.userNickname }}</div>
              <div class="user-account">账号：{{ getUserAccount(u) }}</div>
              <div v-if="u.userBio" class="user-bio">{{ u.userBio }}</div>
            </div>
            <el-button type="primary" size="small" @click="viewUser(u)">
              <span>查看</span>
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="team-list">
      <el-empty v-if="shouldShowTeamEmpty" description="未找到相关团队" />
      <el-row v-else :gutter="20" class="team-result-grid">
        <el-col v-for="t in teamList" :key="t.id" :span="8">
          <el-card shadow="hover" class="team-card">
            <template #header>
              <div class="team-card-header">
                <el-avatar :size="40" class="team-avatar">
                  {{ getTeamInitial(t) }}
                </el-avatar>
                <h4 class="team-name">{{ t.teamName }}</h4>
              </div>
            </template>
            <div class="team-info">
              <p class="team-intro">{{ getTeamIntro(t) }}</p>
              <div class="team-meta">
                <span class="member-count">{{ t.memberCount }}/{{ t.maxMember }} 人</span>
                <span class="team-type">{{ getTeamTypeText(t.teamType) }}</span>
              </div>
            </div>
            <el-button type="primary" size="small" class="view-btn" @click="viewTeam(t)">
              <span>查看详情</span>
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  max-width: 1000px;
}

.search-box {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  --el-input-height: 40px;
}

.search-btn {
  --el-button-height: 40px;
}

.search-mode-hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 18px;
  color: #333;
  font-size: 15px;
}

.suggest-box {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -8px 0 18px;
}

.suggest-tag {
  cursor: pointer;
}

.assist-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.assist-card {
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header h3,
.card-title {
  font-size: 14px;
  font-weight: bold;
  color: #333;
  margin: 0;
}

.clear-btn {
  font-size: 12px;
  color: #999;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-tag {
  font-size: 12px;
  padding: 6px 12px;
  cursor: pointer;
}

.hot-tag {
  font-size: 12px;
  padding: 6px 12px;
  cursor: pointer;
}

.empty-small {
  --el-empty-padding: 16px 0;
}

.loading-container {
  min-height: 400px;
}

.user-list {
  margin-top: 20px;
}

.user-result-list {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

.user-item {
  border-bottom: 1px solid #f0f0f0;
}

.user-item:last-child {
  border-bottom: none;
}

.user-item-content {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  border: 2px solid #f0f0f0;
}

.user-info {
  flex: 1;
  margin-left: 16px;
}

.user-name {
  font-size: 16px;
  font-weight: bold;
  color: #111;
  margin-bottom: 4px;
}

.user-account {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
}

.user-bio {
  font-size: 14px;
  color: #666;
  line-height: 1.4;
}

.team-list {
  margin-top: 20px;
}

.team-result-grid {
  margin-top: 0;
}

.team-card {
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.team-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.team-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.team-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 16px;
  font-weight: bold;
}

.team-name {
  font-size: 16px;
  font-weight: bold;
  color: #111;
  margin: 0;
}

.team-info {
  margin-bottom: 16px;
}

.team-intro {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.team-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #888;
}

.view-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
