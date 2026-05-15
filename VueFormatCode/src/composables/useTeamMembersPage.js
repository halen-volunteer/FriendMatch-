import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  getTeamDetail,
  getTeamMembers,
  getTeamMembersByRole,
  muteAll,
  muteMember,
  removeMember,
  transferLeader,
  unmuteMember,
  updateMemberRole,
} from '@/api/team'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { useToast } from '@/composables/useToast'

function normalizeMember(item) {
  return {
    ...item,
    userId: item.userId ?? item.id,
    userNickname: item.userNickname ?? item.nickname ?? '',
    userAvatar: item.userAvatar ?? item.avatar ?? '',
    roleType: item.roleType ?? item.teamRoleType,
    isMuted: item.isMuted ?? item.teamMuteType === 1,
  }
}

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function createConfirmState() {
  return {
    visible: false,
    title: '',
    message: '',
    confirmText: '确认',
    action: '',
    userId: null,
    loading: false,
  }
}

export function useTeamMembersPage() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const teamId = route.params.teamId ? Number(route.params.teamId) : undefined
  const pageAction = computed(() => String(route.query.action || 'manage'))
  const members = ref([])
  const myRole = ref(0)
  const isMuteAll = ref(false)
  const roleFilter = ref(0)
  const loading = ref(true)
  const rowSubmittingKey = ref('')
  const muteAllSubmitting = ref(false)
  const confirmState = ref(createConfirmState())
  const muteDialog = ref({ visible: false, userId: null, userNickname: '', duration: 60 })
  const contextMenu = ref({ visible: false, x: 0, y: 0, member: null })
  const { toast, showToast } = useToast()

  const muteDurationOptions = [
    { label: '10分钟', value: 10 },
    { label: '30分钟', value: 30 },
    { label: '1小时', value: 60 },
    { label: '3小时', value: 180 },
    { label: '12小时', value: 720 },
    { label: '24小时', value: 1440 },
  ]

  const canManageMuteAll = computed(() => myRole.value <= 2 && myRole.value > 0)
  const currentUserId = computed(() => Number(authStore.userId || 0))
  const pageHint = computed(() => {
    if (pageAction.value === 'transfer') {
      return '当前为队长权限移交入口，请在成员列表中选择目标成员后点击“转让队长”完成操作。'
    }
    return '当前页面可统一处理成员权限、禁言、移出团队等操作。'
  })

  function canOperate(targetRole) {
    if (myRole.value === 1) return toNumber(targetRole) !== 1
    if (myRole.value === 2) return toNumber(targetRole) >= 3
    return false
  }

  function getOperateBlockReasonSafe(member) {
    if (!member) return '当前用户不存在'
    const targetUserId = toNumber(member.userId ?? member.id)
    const targetRole = toNumber(member.roleType ?? member.teamRoleType)
    if (targetUserId && currentUserId.value && targetUserId === currentUserId.value) {
      return '不能操作自己'
    }
    if (targetRole === 1) {
      return '无法操作当前用户'
    }
    if (myRole.value === 1) {
      return ''
    }
    if (myRole.value === 2) {
      return targetRole >= 3 ? '' : '没有权限'
    }
    return '没有权限'
  }

  function buildRowKey(action, userId) {
    return `${action}:${userId}`
  }

  function isRowSubmitting(action, userId) {
    return rowSubmittingKey.value === buildRowKey(action, userId)
  }

  async function runRowAction(action, userId, task) {
    const key = buildRowKey(action, userId)
    if (rowSubmittingKey.value) return
    rowSubmittingKey.value = key
    try {
      await task()
    } finally {
      rowSubmittingKey.value = ''
    }
  }

  async function load() {
    loading.value = true
    try {
      const memberPromise = roleFilter.value
        ? getTeamMembersByRole({ teamId, roleType: roleFilter.value, page: 1, pageSize: 100 })
        : getTeamMembers(teamId, { page: 1, pageSize: 100 })
      const [memberRes, detailRes] = await Promise.all([
        memberPromise,
        getTeamDetail(teamId),
      ])
      const normalizedMemberRes = assertSuccess(memberRes, '加载成员列表失败')
      const normalizedDetailRes = assertSuccess(detailRes, '加载团队信息失败')
      members.value = (normalizedMemberRes.data?.records || normalizedMemberRes.data || []).map(normalizeMember)
      const detail = normalizedDetailRes.data || {}
      myRole.value = detail.myRoleType || detail.currentUserRole || detail.roleType || 0
      isMuteAll.value = Number(detail.teamAllMute ?? detail.muteAll ?? 0) === 1
    } catch (error) {
      showToast(getErrorMessage(error, '加载成员列表失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  function closeContextMenu() {
    contextMenu.value.visible = false
  }

  function openContextMenu(event, member) {
    event.preventDefault()
    const blockReason = getOperateBlockReasonSafe(member)
    if (blockReason) {
      showToast(blockReason, 'error')
      closeContextMenu()
      return
    }
    event.stopPropagation()
    contextMenu.value = {
      visible: true,
      x: event.clientX,
      y: event.clientY,
      member,
    }
  }

  function openMemberProfile(member) {
    const targetUserId = toNumber(member?.userId ?? member?.id)
    if (!targetUserId) return
    if (targetUserId === currentUserId.value) {
      router.push('/profile')
      return
    }
    router.push(`/profile/${targetUserId}`)
  }

  function canTransfer(member) {
    return myRole.value === 1 && !getOperateBlockReasonSafe(member)
  }

  function canPromoteToAdmin(member) {
    return myRole.value === 1 && !getOperateBlockReasonSafe(member) && Number(member?.roleType) === 3
  }

  function canDemoteToMember(member) {
    return myRole.value === 1 && !getOperateBlockReasonSafe(member) && Number(member?.roleType) === 2
  }

  function handleRemove(userId) {
    confirmState.value = {
      visible: true,
      title: '移出团队',
      message: '确认将该成员移出团队吗？移出后他将无法继续参与当前团队聊天与协作。',
      confirmText: '确认移出',
      action: 'remove',
      userId,
      loading: false,
    }
  }

  async function executeRemove(userId) {
    await runRowAction('remove', userId, async () => {
      assertSuccess(await removeMember({ teamId, userId }), '移出成员失败')
      showToast('成员已移出团队')
      closeConfirm()
      await load()
    })
  }

  async function handleMute(userId, muteDuration = 60) {
    await runRowAction('mute', userId, async () => {
      const res = await muteMember({ teamId, userId, muteDuration })
      assertSuccess(res, '禁言失败')
      showToast(`已禁言 ${muteDuration} 分钟`)
      await load()
    })
  }

  async function handleUnmute(userId) {
    await runRowAction('unmute', userId, async () => {
      const res = await unmuteMember({ teamId, userId })
      assertSuccess(res, '解除禁言失败')
      showToast('已解除禁言')
      await load()
    })
  }

  async function handleRoleChange(userId, roleType) {
    await runRowAction(`role-${roleType}`, userId, async () => {
      const res = await updateMemberRole({ teamId, userId, roleType })
      assertSuccess(res, '更新角色失败')
      showToast('成员角色已更新')
      await load()
    })
  }

  function handleTransfer(userId) {
    confirmState.value = {
      visible: true,
      title: '转让队长',
      message: '确认将队长权限转让给该成员吗？转让后你的权限会发生变化，请谨慎操作。',
      confirmText: '确认转让',
      action: 'transfer',
      userId,
      loading: false,
    }
  }

  async function executeTransfer(userId) {
    await runRowAction('transfer', userId, async () => {
      const res = await transferLeader({ teamId, userId })
      assertSuccess(res, '转让队长失败')
      showToast('队长权限已转让')
      closeConfirm()
      await load()
    })
  }

  function openMuteDialog(member) {
    closeContextMenu()
    if (!member || member.isMuted) return
    muteDialog.value = {
      visible: true,
      userId: member.userId,
      userNickname: member.userNickname || '',
      duration: 60,
    }
  }

  async function submitMuteDialog() {
    const { userId, duration } = muteDialog.value
    if (!userId) return
    await handleMute(userId, duration)
    muteDialog.value.visible = false
  }

  async function handleContextMute(member) {
    if (!member) return
    if (member.isMuted) {
      closeContextMenu()
      await handleUnmute(member.userId)
      return
    }
    openMuteDialog(member)
  }

  function handleContextRemove(member) {
    if (!member) return
    const blockReason = getOperateBlockReasonSafe(member)
    if (blockReason) {
      showToast(blockReason, 'error')
      return
    }
    closeContextMenu()
    handleRemove(member.userId)
  }

  async function handleContextRoleChange(member, roleType) {
    if (!member) return
    const blockReason = getOperateBlockReasonSafe(member)
    if (blockReason) {
      showToast(blockReason, 'error')
      return
    }
    closeContextMenu()
    await handleRoleChange(member.userId, roleType)
  }

  function handleContextTransfer(member) {
    if (!member) return
    const blockReason = getOperateBlockReasonSafe(member)
    if (blockReason) {
      showToast(blockReason, 'error')
      return
    }
    closeContextMenu()
    handleTransfer(member.userId)
  }

  function closeConfirm() {
    confirmState.value = createConfirmState()
  }

  async function submitConfirm() {
    const { action, userId } = confirmState.value
    if (!action || !userId || confirmState.value.loading) return
    confirmState.value.loading = true
    try {
      if (action === 'remove') {
        await executeRemove(userId)
        return
      }
      if (action === 'transfer') {
        await executeTransfer(userId)
      }
    } catch (error) {
      showToast(getErrorMessage(error, '操作失败'), 'error')
    } finally {
      confirmState.value.loading = false
    }
  }

  async function handleMuteAll() {
    if (muteAllSubmitting.value) return
    const next = !isMuteAll.value
    muteAllSubmitting.value = true
    try {
      const res = await muteAll({ teamId, isMute: next })
      assertSuccess(res, '更新全员禁言失败')
      isMuteAll.value = next
      showToast(next ? '已开启全员禁言' : '已关闭全员禁言')
    } catch (error) {
      showToast(getErrorMessage(error, '更新全员禁言失败'), 'error')
    } finally {
      muteAllSubmitting.value = false
    }
  }

  function getMemberInitial(name) {
    return name?.charAt(0) || '用'
  }

  function formatJoinTime(joinTime) {
    return joinTime ? new Date(joinTime).toLocaleString('zh-CN') : ''
  }

  onMounted(load)
  onMounted(() => {
    window.addEventListener('click', closeContextMenu)
  })

  onUnmounted(() => {
    window.removeEventListener('click', closeContextMenu)
  })

  return {
    canManageMuteAll,
    canOperate,
    closeConfirm,
    closeContextMenu,
    confirmState,
    contextMenu,
    formatJoinTime,
    getMemberInitial,
    handleContextMute,
    handleContextRemove,
    handleContextRoleChange,
    handleContextTransfer,
    handleMute,
    handleMuteAll,
    handleRemove,
    handleRoleChange,
    handleTransfer,
    handleUnmute,
    isMuteAll,
    isRowSubmitting,
    load,
    loading,
    members,
    muteDialog,
    muteDurationOptions,
    muteAllSubmitting,
    openMemberProfile,
    myRole,
    canDemoteToMember,
    openContextMenu,
    openMuteDialog,
    pageAction,
    pageHint,
    canPromoteToAdmin,
    canTransfer,
    roleFilter,
    rowSubmittingKey,
    submitConfirm,
    submitMuteDialog,
    toast,
  }
}
