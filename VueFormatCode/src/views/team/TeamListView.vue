<script setup>
import { ref, onMounted } from 'vue'
import { getTeamList, searchTeam, createTeam, applyTeam, joinByPassword } from '@/api/team'
import { Plus, Search, UserFilled, Lock, Message, Loading } from '@element-plus/icons-vue'

const teams = ref([])
const keyword = ref('')
const loading = ref(false)
const showCreate = ref(false)
const showApply = ref({ show: false, team: null })
const showJoinPwd = ref({ show: false, team: null })
const applyMsg = ref('')
const joinPwd = ref('')
const toast = ref({ msg: '', type: 'success' })
const createForm = ref({ teamName: '', teamIntro: '', teamTags: '', teamType: 1, joinRule: 1, joinPassword: '', maxMember: 20 })
function showToast(msg, type = 'success') { toast.value = { msg, type }; setTimeout(() => (toast.value.msg = ''), 3000) }
async function load() { loading.value = true; try { const res = keyword.value ? await searchTeam({ type: 'name', keyword: keyword.value, page: 1, pageSize: 20 }) : await getTeamList({ page: 1, pageSize: 20 }); if (res.code === 200) { const rawList = res.data?.records || res.data || []; teams.value = rawList.map((item) => ({ ...item, id: item.id ?? item.teamId, teamIntro: item.teamIntro ?? item.teamDesc ?? '', maxMember: item.maxMember ?? item.maxMemberNum ?? 0 })) } } finally { loading.value = false } }
async function handleCreate() { if (!createForm.value.teamName) { showToast('请输入团队名称', 'error'); return } const res = await createTeam(createForm.value); if (res.code === 200) { showToast('创建成功'); showCreate.value = false; load() } else showToast(res.message || '创建失败', 'error') }
async function handleApply() { const teamId = showApply.value.team?.id ?? showApply.value.team?.teamId; const res = await applyTeam({ teamId, applyMsg: applyMsg.value }); if (res.code === 200) { showToast('申请已发送'); showApply.value.show = false } else showToast(res.message || '申请失败', 'error') }
async function handleJoinPwd() {
  const teamId = showJoinPwd.value.team?.id ?? showJoinPwd.value.team?.teamId;
  const res = await joinByPassword({ teamId, teamPassword: joinPwd.value });
  if (res.code === 200) {
    showToast('加入成功');
    showJoinPwd.value.show = false;
    load();
  } else {
    showToast(res.message || '密码错误', 'error');
  }
}

function handleApplyDialog(team) {
  showApply.value.team = team;
  showApply.value.show = true;
  applyMsg.value = '';
}

function handleJoinPasswordDialog(team) {
  showJoinPwd.value.team = team;
  showJoinPwd.value.show = true;
  joinPwd.value = '';
}

function openTeamDetail(teamId) {
  router.push(`/teams/${teamId}`)
}

function getTeamInitial(teamName) {
  return teamName?.charAt(0) || '团'
}

function getTeamTypeText(teamType) {
  return teamType === 1 ? '公开' : '私有'
}

function getTeamIntro(teamIntro) {
  return teamIntro || '暂无简介'
}
onMounted(load)
</script>

<template>
  <div class="page team-list-page page-container-xl">
    <div class="page-header page-head-row">
      <h2 class="page-head-title">团队广场</h2>
      <el-button type="primary" @click="showCreate = true">
        <el-icon>
          <Plus />
        </el-icon>
        <span>创建团队</span>
      </el-button>
    </div>
    <el-alert v-if="toast.msg" :title="toast.msg" :type="toast.type" show-icon class="toast page-toast-lg" @close="toast.msg = ''" />
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索团队名称或标签..." @keyup.enter="load" class="search-input">
        <template #append>
          <el-button type="primary" @click="load">
            <el-icon>
              <Search />
            </el-icon>
            <span>搜索</span>
          </el-button>
        </template>
      </el-input>
    </div>
    <div v-if="loading" class="loading-container loading-center-lg">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <el-empty v-else-if="!teams.length" description="暂无团队" />
    <div v-else-if="teams.length" class="team-grid">
      <el-card v-for="t in teams" :key="t.id" shadow="hover" class="team-card" @click="openTeamDetail(t.id)">
        <template #header>
          <div class="team-header">
            <el-avatar :size="48" :src="t.teamAvatar" class="team-avatar">
              {{ getTeamInitial(t.teamName) }}
            </el-avatar>
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
          <el-tag v-for="tag in t.teamTags.split(',').filter(Boolean)" :key="tag" size="small" effect="plain">
            {{ tag }}
          </el-tag>
        </div>
        <div class="team-actions" @click.stop>
          <el-button v-if="t.joinRule === 1" type="primary" size="small" @click="handleApplyDialog(t)">
            <el-icon>
              <UserFilled />
            </el-icon>
            申请加入
          </el-button>
          <el-button v-else-if="t.joinRule === 3" type="success" size="small" @click="handleJoinPasswordDialog(t)">
            <el-icon>
              <Lock />
            </el-icon>
            密码加入
          </el-button>
          <el-button v-else type="info" size="small" disabled>
            <el-icon>
              <Message />
            </el-icon>
            仅邀请
          </el-button>
        </div>
      </el-card>
    </div>
    <el-dialog v-model="showCreate" title="创建团队" width="500px">
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
        <div class="dialog-footer dialog-footer-row">
          <el-button @click="showCreate = false">取消</el-button>
          <el-button type="primary" @click="handleCreate">创建</el-button>
        </div>
      </template>
    </el-dialog>
    <el-dialog v-model="showApply.show" :title="`申请加入「${showApply.team?.teamName}」`" width="400px">
      <el-form :model="{ applyMsg }">
        <el-form-item label="申请留言（选填）">
          <el-input v-model="applyMsg" type="textarea" rows="3" placeholder="请输入申请留言" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer dialog-footer-row">
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
        <div class="dialog-footer dialog-footer-row">
          <el-button @click="showJoinPwd.show = false">取消</el-button>
          <el-button type="primary" @click="handleJoinPwd">加入</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
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
  gap: 16px;
  align-items: center;
}

.team-avatar {
  border: 3px solid #f0f0f0;
}

.team-info {
  flex: 1;
}

.team-name {
  font-size: 18px;
  font-weight: bold;
  color: #111;
  margin: 0 0 4px 0;
}

.team-meta {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #888;
}

.team-intro {
  margin: 12px 0;
  font-size: 14px;
  color: #555;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.team-tags {
  margin: 12px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.team-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.create-form {
  margin-top: 20px;
}

</style>
