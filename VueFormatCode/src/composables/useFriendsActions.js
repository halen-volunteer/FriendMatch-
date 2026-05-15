import { agreeFriend, rejectFriend, removeBlacklist } from '@/api/user'
import { applyTeam, joinByPassword } from '@/api/team'
import { assertSuccess, getErrorMessage } from '@/utils/response'
import { createConfirmState } from '@/composables/useFriendsHelpers'

export function useFriendsActions({
  applyMsg,
  confirmState,
  currentBlacklistUser,
  currentFriend,
  currentRequest,
  currentTeam,
  joinPwd,
  joinPwdSubmitting,
  loadBase,
  loadSquareTeams,
  requestSubmitting,
  router,
  showApply,
  showJoinPwd,
  showToast,
  teamApplySubmitting,
}) {
  async function handleAgree() {
    if (!currentRequest.value || requestSubmitting.value) return
    requestSubmitting.value = true
    try {
      assertSuccess(await agreeFriend(currentRequest.value.applicantId), '同意好友申请失败')
      showToast('已同意')
      await loadBase()
    } catch (error) {
      showToast(getErrorMessage(error, '同意好友申请失败'), 'error')
    } finally {
      requestSubmitting.value = false
    }
  }

  async function handleReject() {
    if (!currentRequest.value || requestSubmitting.value) return
    requestSubmitting.value = true
    try {
      assertSuccess(await rejectFriend(currentRequest.value.applicantId), '拒绝好友申请失败')
      showToast('已拒绝')
      await loadBase()
    } catch (error) {
      showToast(getErrorMessage(error, '拒绝好友申请失败'), 'error')
    } finally {
      requestSubmitting.value = false
    }
  }

  function handleRemoveBlacklist(blackUserId = currentBlacklistUser.value?.blackUserId) {
    if (!blackUserId) return
    confirmState.value = {
      visible: true,
      title: '解除拉黑',
      message: '确认将该用户从黑名单中移除吗？移除后对方可以再次与你建立正常社交关系。',
      confirmText: '确认解除',
      blackUserId,
      loading: false,
    }
  }

  function closeConfirm() {
    confirmState.value = createConfirmState()
  }

  async function submitRemoveBlacklist() {
    const blackUserId = confirmState.value.blackUserId
    if (!blackUserId || confirmState.value.loading) return
    confirmState.value.loading = true
    try {
      assertSuccess(await removeBlacklist(blackUserId), '解除拉黑失败')
      showToast('已解除拉黑')
      closeConfirm()
      await loadBase()
    } catch (error) {
      showToast(getErrorMessage(error, '解除拉黑失败'), 'error')
    } finally {
      confirmState.value.loading = false
    }
  }

  function sendMessageToFriend() {
    if (!currentFriend.value?.friendId) return
    router.push(`/chat/private/${currentFriend.value.friendId}`)
  }

  function enterTeam(team = currentTeam.value) {
    if (!team?.id) return
    router.push(`/chat/team/${team.id}`)
  }

  async function handleApply() {
    if (teamApplySubmitting.value) return
    const teamId = showApply.value.team?.id ?? showApply.value.team?.teamId
    teamApplySubmitting.value = true
    try {
      assertSuccess(await applyTeam({ teamId, applyMsg: applyMsg.value }), '申请加入团队失败')
      showToast('申请已发送')
      showApply.value = { ...showApply.value, show: false }
    } catch (error) {
      showToast(getErrorMessage(error, '申请加入团队失败'), 'error')
    } finally {
      teamApplySubmitting.value = false
    }
  }

  async function handleJoinPwd() {
    if (joinPwdSubmitting.value) return
    const teamId = showJoinPwd.value.team?.id ?? showJoinPwd.value.team?.teamId
    joinPwdSubmitting.value = true
    try {
      assertSuccess(await joinByPassword({ teamId, password: joinPwd.value }), '密码加入团队失败')
      showToast('加入成功')
      showJoinPwd.value = { ...showJoinPwd.value, show: false }
      await Promise.all([loadSquareTeams(), loadBase()])
    } catch (error) {
      showToast(getErrorMessage(error, '密码加入团队失败'), 'error')
    } finally {
      joinPwdSubmitting.value = false
    }
  }

  function handleApplyDialog(team) {
    showApply.value = { show: true, team }
    applyMsg.value = ''
    teamApplySubmitting.value = false
  }

  function handleJoinPasswordDialog(team) {
    showJoinPwd.value = { show: true, team }
    joinPwd.value = ''
    joinPwdSubmitting.value = false
  }

  function openTeamDetail(teamId) {
    router.push(`/teams/${teamId}`)
  }

  return {
    closeConfirm,
    enterTeam,
    handleAgree,
    handleApply,
    handleApplyDialog,
    handleJoinPasswordDialog,
    handleJoinPwd,
    handleReject,
    handleRemoveBlacklist,
    openTeamDetail,
    sendMessageToFriend,
    submitRemoveBlacklist,
  }
}
