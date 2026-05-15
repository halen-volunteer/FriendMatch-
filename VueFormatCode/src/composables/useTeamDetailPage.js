import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { applyTeam, auditApply, dissolveTeam, getPendingApplyList, getTeamDetail, joinByPassword, quitTeam, updateTeam } from '@/api/team'
import { presignUpload } from '@/api/oss'
import { uploadToQiniu } from '@/utils/qiniuUpload'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'
import { getJoinRuleText, getTeamTypeText } from '@/utils/teamDisplay'

function normalizeTeamDetail(detail) {
  return {
    ...detail,
    teamIntro: detail.teamIntro ?? '',
    maxMember: detail.maxMember ?? 0,
    teamType: detail.teamType ?? 1,
    joinRule: detail.joinRule ?? 1,
  }
}

function createConfirmState() {
  return {
    visible: false,
    title: '',
    message: '',
    confirmText: '确认',
    action: '',
    loading: false,
  }
}

export function useTeamDetailPage() {
  const route = useRoute()
  const router = useRouter()
  const teamId = route.params.teamId ? Number(route.params.teamId) : undefined
  const team = ref(null)
  const pendingApplies = ref([])
  const activeTab = ref('info')
  const loading = ref(true)
  const editMode = ref(false)
  const editForm = ref({})
  const myRole = ref(0)
  const confirmState = ref(createConfirmState())
  const teamAvatarInputRef = ref(null)
  const uploadingTeamAvatar = ref(false)
  const joinSubmitting = ref(false)
  const applySubmitting = ref(false)
  const showApplyDialog = ref(false)
  const showJoinPwdDialog = ref(false)
  const applyMsg = ref('')
  const joinPassword = ref('')
  const { toast, showToast } = useToast()

  const canManageApplies = computed(() => myRole.value <= 2 && myRole.value > 0)
  const uploadTeamAvatarText = computed(() => (uploadingTeamAvatar.value ? '上传中...' : '更换头像'))
  const saveDisabled = computed(() => {
    if (!editForm.value.teamName?.trim()) return true
    const nextJoinRule = Number(editForm.value.joinRule || 1)
    const originalJoinRule = Number(team.value?.joinRule || 1)
    const nextJoinPassword = String(editForm.value.joinPassword || '').trim()
    if (nextJoinRule === 3 && originalJoinRule !== 3 && !nextJoinPassword) return true
    return false
  })
  const canJoin = computed(() => myRole.value <= 0)

  function getTeamInitial(teamName) {
    return teamName?.charAt(0) || '团'
  }

  function formatCreateTime(createTime) {
    return createTime ? new Date(createTime).toLocaleString('zh-CN') : '未知'
  }

  function getApplyInitial(userNickname) {
    return userNickname?.charAt(0) || '用'
  }

  function fillEditForm(detail) {
    editForm.value = {
      teamId,
      teamName: detail.teamName || '',
      teamAvatar: detail.teamAvatar || '',
      teamIntro: detail.teamIntro ?? '',
      teamTags: detail.teamTags || '',
      teamType: detail.teamType ?? 1,
      joinRule: detail.joinRule ?? 1,
      joinPassword: '',
      maxMember: detail.maxMember ?? 200,
    }
  }

  async function load() {
    loading.value = true
    try {
      const res = assertSuccess(await getTeamDetail(teamId), '加载团队详情失败')
      const detail = normalizeTeamDetail(res.data || {})
      team.value = detail
      myRole.value = detail.myRoleType || detail.roleType || 0
      fillEditForm(detail)
    } catch (error) {
      showToast(getErrorMessage(error, '加载团队详情失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function loadApplies() {
    if (myRole.value > 2) {
      pendingApplies.value = []
      return
    }
    try {
      const res = assertSuccess(await getPendingApplyList({ teamId, page: 1, pageSize: 50 }), '加载申请列表失败')
      pendingApplies.value = res.data?.records || res.data || []
    } catch (error) {
      showToast(getErrorMessage(error, '加载申请列表失败'), 'error')
    }
  }

  function openConfirm(action, title, message, confirmText) {
    confirmState.value = {
      visible: true,
      title,
      message,
      confirmText,
      action,
      loading: false,
    }
  }

  function handleDissolve() {
    openConfirm(
      'dissolve',
      '解散团队',
      '确认解散当前团队吗？该操作不可撤销，团队成员关系和聊天入口都会失效。',
      '确认解散',
    )
  }

  function handleQuit() {
    openConfirm(
      'quit',
      '退出团队',
      '确认退出当前团队吗？退出后将无法继续接收团队消息，需要重新加入才能恢复。',
      '确认退出',
    )
  }

  function openRoleTransfer() {
    router.push({
      path: `/teams/${teamId}/members`,
      query: { action: 'transfer' },
    })
  }

  function openMemberManage() {
    router.push({
      path: `/teams/${teamId}/members`,
      query: { action: 'manage' },
    })
  }

  function openTeamOperation() {
    editMode.value = !editMode.value
  }

  function openApplyDialog() {
    applyMsg.value = ''
    showApplyDialog.value = true
  }

  function openJoinPasswordDialog() {
    joinPassword.value = ''
    showJoinPwdDialog.value = true
  }

  function closeConfirm() {
    confirmState.value = createConfirmState()
  }

  async function executeDissolve() {
    assertSuccess(await dissolveTeam(teamId), '解散团队失败')
    closeConfirm()
    router.push('/teams')
  }

  async function executeQuit() {
    assertSuccess(await quitTeam(teamId), '退出团队失败')
    closeConfirm()
    router.push('/teams')
  }

  async function submitConfirm() {
    if (confirmState.value.loading) return
    confirmState.value.loading = true
    try {
      if (confirmState.value.action === 'dissolve') {
        await executeDissolve()
        return
      }
      if (confirmState.value.action === 'quit') {
        await executeQuit()
      }
    } catch (error) {
      showToast(getErrorMessage(error, '操作失败'), 'error')
    } finally {
      confirmState.value.loading = false
    }
  }

  function triggerTeamAvatarUpload() {
    if (uploadingTeamAvatar.value) return
    teamAvatarInputRef.value?.click()
  }

  async function uploadTeamAvatar(event) {
    const file = event.target.files?.[0]
    if (!file) return
    uploadingTeamAvatar.value = true
    try {
      const presignRes = assertSuccess(
        await presignUpload({ fileName: file.name, msgType: 2, fileSize: file.size }),
        '获取上传凭证失败',
      )
      const { uploadUrl, uploadToken, key, fileUrl } = presignRes.data || {}
      if (!uploadUrl || !uploadToken || !key || !fileUrl) {
        throw new Error('上传凭证不完整')
      }
      await uploadToQiniu({ file, uploadUrl, uploadToken, key })
      editForm.value.teamAvatar = fileUrl
      showToast('团队头像上传成功')
    } catch (error) {
      showToast(getErrorMessage(error, '团队头像上传失败'), 'error')
    } finally {
      uploadingTeamAvatar.value = false
      event.target.value = ''
    }
  }

  async function submitApplyJoin() {
    if (applySubmitting.value) return
    applySubmitting.value = true
    try {
      assertSuccess(await applyTeam({ teamId, applyMsg: applyMsg.value }), '申请加入失败')
      showToast('申请已提交')
      showApplyDialog.value = false
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '申请加入失败'), 'error')
    } finally {
      applySubmitting.value = false
    }
  }

  async function submitJoinByPassword() {
    if (joinSubmitting.value) return
    joinSubmitting.value = true
    try {
      assertSuccess(await joinByPassword({ teamId, password: joinPassword.value }), '密码加入失败')
      showToast('加入成功')
      showJoinPwdDialog.value = false
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '密码加入失败'), 'error')
    } finally {
      joinSubmitting.value = false
    }
  }

  async function handleSaveEdit() {
    const originalJoinRule = Number(team.value?.joinRule || 1)
    const nextJoinRule = Number(editForm.value.joinRule || 1)
    const nextJoinPassword = String(editForm.value.joinPassword || '').trim()
    const payload = {
      teamId,
      teamName: editForm.value.teamName?.trim(),
      teamAvatar: editForm.value.teamAvatar || '',
      teamIntro: editForm.value.teamIntro || '',
      teamTags: editForm.value.teamTags || '',
      teamType: Number(editForm.value.teamType || 1),
      joinRule: nextJoinRule,
      maxMember: Number(editForm.value.maxMember || 1),
    }

    if (nextJoinRule === 3 && (nextJoinPassword || originalJoinRule !== 3)) {
      payload.joinPassword = nextJoinPassword
    }

    try {
      assertSuccess(await updateTeam(payload), '保存失败')
      showToast('团队资料保存成功')
      editMode.value = false
      await load()
    } catch (error) {
      showToast(getErrorMessage(error, '保存失败'), 'error')
    }
  }

  async function handleAudit(applyId, status) {
    try {
      assertSuccess(await auditApply({ applyId, auditStatus: status }), '审批失败')
      showToast(status === 1 ? '已通过' : '已拒绝')
      await loadApplies()
    } catch (error) {
      showToast(getErrorMessage(error, '审批失败'), 'error')
    }
  }

  function openTeamChat() {
    router.push(`/chat/team/${teamId}`)
  }

  function openTeamMembers() {
    router.push(`/teams/${teamId}/members`)
  }

  onMounted(async () => {
    await load()
    await loadApplies()
  })

  return {
    activeTab,
    canManageApplies,
    canJoin,
    closeConfirm,
    confirmState,
    editForm,
    editMode,
    formatCreateTime,
    getApplyInitial,
    getJoinRuleText,
    getTeamInitial,
    getTeamTypeText,
    handleAudit,
    handleDissolve,
    handleQuit,
    handleSaveEdit,
    load,
    loadApplies,
    loading,
    myRole,
    applyMsg,
    applySubmitting,
    joinPassword,
    joinSubmitting,
    openMemberManage,
    openApplyDialog,
    openJoinPasswordDialog,
    openRoleTransfer,
    openTeamChat,
    openTeamMembers,
    openTeamOperation,
    pendingApplies,
    saveDisabled,
    showApplyDialog,
    showJoinPwdDialog,
    submitApplyJoin,
    submitConfirm,
    submitJoinByPassword,
    team,
    teamAvatarInputRef,
    toast,
    triggerTeamAvatarUpload,
    uploadTeamAvatar,
    uploadTeamAvatarText,
    uploadingTeamAvatar,
  }
}
