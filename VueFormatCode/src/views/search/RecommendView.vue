<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getRecommendUsers, getRecommendTeams, clickRecommendUser, clickRecommendTeam } from '@/api/recommend'
import { Search, User, UserFilled, ArrowRight, Star } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const users = ref([])
const teams = ref([])
const loading = ref(false)
const section = computed(() => {
  const value = String(route.query.section || 'users')
  return ['users', 'teams'].includes(value) ? value : 'users'
})
const hasUsers = computed(() => users.value.length > 0)
const hasTeams = computed(() => teams.value.length > 0)

function getUserKey(user) {
  return user.userId || user.id
}

function getUserInitial(user) {
  return user.userNickname?.charAt(0) || '用'
}

function getUserIntro(user) {
  return user.userIntro || '这个用户还没有填写个人简介'
}

function getUserTagList(user) {
  return String(user.userTags || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 4)
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

async function load() {
  loading.value = true
  try {
    const [uRes, tRes] = await Promise.all([
      getRecommendUsers({ limit: 10 }).catch(() => ({ code: 400, data: [] })),
      getRecommendTeams({ limit: 10 }).catch(() => ({ code: 400, data: [] })),
    ])
    if (uRes.code === 200) {
      users.value = uRes.data?.records || uRes.data || []
    }
    if (tRes.code === 200) {
      teams.value = (tRes.data?.records || tRes.data || []).map((item) => ({
        ...item,
        id: item.teamId,
        teamIntro: item.teamIntro ?? '',
        maxMember: item.maxMember ?? item.memberCount ?? 0,
      }))
    }
  } finally {
    loading.value = false
  }
}

function handleViewUser(user) {
  clickRecommendUser(user.recommendId || user.userId || user.id).catch(() => { })
  router.push(`/profile/${user.userId || user.id}`)
}

function handleViewTeam(team) {
  clickRecommendTeam(team.recommendId || team.teamId || team.id).catch(() => { })
  router.push(`/teams/${team.id}`)
}

onMounted(load)
</script>

<template>
  <div class="page recommend-page">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">推荐发现</h2>
    </div>

    <div v-if="loading" class="loading-container loading-center-lg">
      <el-icon class="is-loading">
        <Search />
      </el-icon>
      <span>加载中...</span>
    </div>

    <div v-else>
      <div v-if="section === 'users'" class="section">
        <div class="section-header">
          <el-icon>
            <User />
          </el-icon>
          <h3>推荐用户</h3>
        </div>
        <el-empty v-if="!hasUsers" description="暂无推荐用户" class="empty-section" />
        <div v-else class="user-list">
          <div v-for="u in users" :key="getUserKey(u)" class="user-item">
            <div class="user-item-content">
              <el-avatar :size="48" :src="u.userAvatar" class="user-avatar">
                {{ getUserInitial(u) }}
              </el-avatar>
              <div class="user-info">
                <div class="user-name">{{ u.userNickname }}</div>
                <div class="user-bio">{{ getUserIntro(u) }}</div>
                <div v-if="getUserTagList(u).length" class="user-tags">
                  <span v-for="tag in getUserTagList(u)" :key="tag" class="user-tag">{{ tag }}</span>
                </div>
                <div class="user-extra">
                  <span v-if="u.mutualFriends" class="user-metric">
                    <el-icon>
                      <UserFilled />
                    </el-icon>
                    {{ u.mutualFriends }} 位共同好友
                  </span>
                  <span v-if="u.mutualTeams" class="user-metric">
                    <el-icon>
                      <Star />
                    </el-icon>
                    {{ u.mutualTeams }} 个共同团队
                  </span>
                  <span v-if="u.recommendReason" class="user-reason">{{ u.recommendReason }}</span>
                </div>
              </div>
              <el-button type="primary" size="small" @click="handleViewUser(u)">
                <span>查看</span>
                <el-icon>
                  <ArrowRight />
                </el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="section === 'teams'" class="section">
        <div class="section-header">
          <el-icon>
            <UserFilled />
          </el-icon>
          <h3>推荐团队</h3>
        </div>
        <el-empty v-if="!hasTeams" description="暂无推荐团队" class="empty-section" />
        <el-row v-else :gutter="20" class="team-grid">
          <el-col v-for="t in teams" :key="t.id" :span="8">
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
              <el-button type="primary" size="small" class="view-btn" @click="handleViewTeam(t)">
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
  </div>
</template>

<style scoped>
.recommend-page {
  max-width: 1000px;
}

.loading-container {
  min-height: 500px;
}

.section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.section-header h3 {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin: 0;
}

.empty-section {
  --el-empty-padding: 40px 0;
}

.user-list {
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
  margin-bottom: 6px;
}

.user-bio {
  font-size: 14px;
  color: #666;
  line-height: 1.4;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.user-extra {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 12px;
  color: #888;
}

.user-metric {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.user-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f3f5f7;
  color: #5b6470;
  font-size: 12px;
  line-height: 1;
}

.user-reason {
  color: #667085;
}

.team-grid {
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
