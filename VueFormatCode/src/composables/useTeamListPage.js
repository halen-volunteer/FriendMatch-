import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { applyTeam, createTeam, getTeamList, joinByPassword, searchTeam } from '@/api/team'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'
import { getTeamTypeText } from '@/utils/teamDisplay'

function normalizeTeam(item) {
  return {
    ...item,
    id: item.id ?? item.teamId,
    teamIntro: item.teamIntro ?? '',
    maxMember: item.maxMember ?? 0,
  }
}

function createDefaultTeamForm() {
  return {
    teamName: '',
    teamIntro: '',
    teamTags: '',
    teamType: 1,
    joinRule: 1,
    joinPassword: '',
    maxMember: 20,
  }
}

export function useTeamListPage() {
  const router = useRouter()
  const teams = ref([])
  const keyword = ref('')
  const loading = ref(false)
  const showCreate = ref(false)
  const showApply = ref({ show: false, team: null })
  const showJoinPwd = ref({ show: false, team: null })
  const applyMsg = ref('')
  const joinPwd = ref('')
  const createSubmitting = ref(false)
  const applySubmitting = ref(false)
  const joinSubmitting = ref(false)
  const createForm = ref(createDefaultTeamForm())
  const { toast, showToast } = useToast()

  async function load() {
    loading.value = true
    try {
      const response = keyword.value
        ? await searchTeam({ type: 'name', keyword: keyword.value, page: 1, pageSize: 20 })
        : await getTeamList({ page: 1, pageSize: 20 })
      const res = assertSuccess(response, '加载团队列表失败')
      teams.value = (res.data?.records || res.data || []).map(normalizeTeam)
    } catch (error) {
      showToast(getErrorMessage(error, '加载团队列表失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function handleCreate() {
    if (!createForm.value.teamName) {
      showToast('请输入团队名称', 'error')
      return
    }
    if (createSubmitting.value) return
    createSubmitting.value = true
    try {
      assertSuccess(await createTeam(createForm.value), '创建团队失败')
      showToast('创建成功')
      showCreate.value = false
      createForm.value = createDefaultTeamForm()
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '创建团队失败'), 'error')
    } finally {
      createSubmitting.value = false
    }
  }

  async function handleApply() {
    if (applySubmitting.value) return
    const teamId = showApply.value.team?.id ?? showApply.value.team?.teamId
    applySubmitting.value = true
    try {
      assertSuccess(await applyTeam({ teamId, applyMsg: applyMsg.value }), '申请加入失败')
      showToast('申请已发送')
      showApply.value.show = false
    } catch (error) {
      showToast(getErrorMessage(error, '申请加入失败'), 'error')
    } finally {
      applySubmitting.value = false
    }
  }

  async function handleJoinPwd() {
    if (joinSubmitting.value) return
    const teamId = showJoinPwd.value.team?.id ?? showJoinPwd.value.team?.teamId
    joinSubmitting.value = true
    try {
      assertSuccess(await joinByPassword({ teamId, password: joinPwd.value }), '密码加入失败')
      showToast('加入成功')
      showJoinPwd.value.show = false
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '密码加入失败'), 'error')
    } finally {
      joinSubmitting.value = false
    }
  }

  function handleApplyDialog(team) {
    showApply.value = { show: true, team }
    applyMsg.value = ''
    applySubmitting.value = false
  }

  function handleJoinPasswordDialog(team) {
    showJoinPwd.value = { show: true, team }
    joinPwd.value = ''
    joinSubmitting.value = false
  }

  function openTeamDetail(teamId) {
    router.push(`/teams/${teamId}`)
  }

  function getTeamInitial(teamName) {
    return teamName?.charAt(0) || '团'
  }

  function getTeamIntro(teamIntro) {
    return teamIntro || '暂无简介'
  }

  onMounted(load)

  return {
    applyMsg,
    applySubmitting,
    createForm,
    createSubmitting,
    getTeamInitial,
    getTeamIntro,
    getTeamTypeText,
    handleApply,
    handleApplyDialog,
    handleCreate,
    handleJoinPasswordDialog,
    handleJoinPwd,
    joinPwd,
    joinSubmitting,
    keyword,
    load,
    loading,
    openTeamDetail,
    showApply,
    showCreate,
    showJoinPwd,
    teams,
    toast,
  }
}
