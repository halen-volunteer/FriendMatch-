import { getFriendList, getFriendRequests, getUserProfile, getBlacklist } from '@/api/user'
import { getTeamList, getTeamDetail, searchTeam } from '@/api/team'
import { assertSuccess, getErrorMessage, pickRecords } from '@/utils/response'
import {
  filterJoinedTeams,
  normalizeBlacklist,
  normalizeFriend,
  normalizeRequest,
  normalizeTeam,
} from '@/composables/useFriendsHelpers'

export function useFriendsDataLoader({
  blacklists,
  currentBlacklistUser,
  currentFriend,
  currentRequest,
  currentTeam,
  friends,
  isBlacklistSection,
  isFriendsSection,
  isRequestsSection,
  isTeamsSection,
  itemId,
  joinedTeams,
  loading,
  routeToSection,
  showToast,
  squareKeyword,
  squareLoading,
  squareTeams,
  requests,
}) {
  async function loadSquareTeams() {
    squareLoading.value = true
    try {
      const response = squareKeyword.value
        ? await searchTeam({ type: 'name', keyword: squareKeyword.value, page: 1, pageSize: 20 })
        : await getTeamList({ page: 1, pageSize: 20 })
      const res = assertSuccess(response, '加载团队广场失败')
      squareTeams.value = pickRecords(res).map(normalizeTeam)
    } catch (error) {
      showToast(getErrorMessage(error, '加载团队广场失败'), 'error')
    } finally {
      squareLoading.value = false
    }
  }

  async function loadCurrentFriend() {
    const base = friends.value.find((item) => String(item.friendId) === itemId.value) || friends.value[0]
    if (!base) {
      currentFriend.value = null
      return
    }
    try {
      const res = assertSuccess(await getUserProfile(base.userId), '加载好友资料失败')
      currentFriend.value = { ...base, ...(res.data || {}) }
    } catch (error) {
      currentFriend.value = { ...base }
      showToast(getErrorMessage(error, '加载好友资料失败'), 'error')
    }
    if (String(base.friendId) !== itemId.value) {
      routeToSection('friends', base.friendId)
    }
  }

  async function loadCurrentRequest() {
    const base = requests.value.find((item) => String(item.applicantId) === itemId.value) || requests.value[0]
    if (!base) {
      currentRequest.value = null
      return
    }
    try {
      const res = assertSuccess(await getUserProfile(base.userId), '加载好友申请详情失败')
      currentRequest.value = { ...base, ...(res.data || {}) }
    } catch (error) {
      currentRequest.value = { ...base }
      showToast(getErrorMessage(error, '加载好友申请详情失败'), 'error')
    }
    if (String(base.applicantId) !== itemId.value) {
      routeToSection('requests', base.applicantId)
    }
  }

  async function loadCurrentTeam() {
    const base = joinedTeams.value.find((item) => String(item.id) === itemId.value) || joinedTeams.value[0]
    if (!base) {
      currentTeam.value = null
      return
    }
    try {
      const res = assertSuccess(await getTeamDetail(base.id), '加载团队详情失败')
      currentTeam.value = normalizeTeam({ ...base, ...(res.data || {}) })
    } catch (error) {
      currentTeam.value = normalizeTeam({ ...base })
      showToast(getErrorMessage(error, '加载团队详情失败'), 'error')
    }
    if (String(base.id) !== itemId.value) {
      routeToSection('teams', base.id)
    }
  }

  async function loadCurrentBlacklistUser() {
    const base = blacklists.value.find((item) => String(item.blackUserId) === itemId.value) || blacklists.value[0]
    if (!base) {
      currentBlacklistUser.value = null
      return
    }
    try {
      const res = assertSuccess(await getUserProfile(base.userId), '加载黑名单用户资料失败')
      currentBlacklistUser.value = { ...base, ...(res.data || {}) }
    } catch (error) {
      currentBlacklistUser.value = { ...base }
      showToast(getErrorMessage(error, '加载黑名单用户资料失败'), 'error')
    }
    if (String(base.blackUserId) !== itemId.value) {
      routeToSection('blacklist', base.blackUserId)
    }
  }

  function syncSquareSelection() {
    const base = squareTeams.value.find((item) => String(item.id) === itemId.value) || squareTeams.value[0]
    if (!base) return
    if (String(base.id) !== itemId.value) {
      routeToSection('square', base.id)
    }
  }

  async function loadCurrent() {
    currentFriend.value = null
    currentRequest.value = null
    currentBlacklistUser.value = null
    currentTeam.value = null

    if (isFriendsSection.value) {
      await loadCurrentFriend()
      return
    }

    if (isRequestsSection.value) {
      await loadCurrentRequest()
      return
    }

    if (isTeamsSection.value) {
      await loadCurrentTeam()
      return
    }

    if (isBlacklistSection.value) {
      await loadCurrentBlacklistUser()
      return
    }

    syncSquareSelection()
  }

  async function loadBase() {
    loading.value = true
    try {
      const [friendRes, requestRes, teamRes, blacklistRes] = await Promise.all([
        getFriendList({ page: 1, pageSize: 100 }),
        getFriendRequests({ page: 1, pageSize: 100 }),
        getTeamList({ page: 1, pageSize: 100 }),
        getBlacklist({ page: 1, pageSize: 100 }),
      ])

      friends.value = pickRecords(assertSuccess(friendRes, '加载好友列表失败')).map(normalizeFriend)
      requests.value = pickRecords(assertSuccess(requestRes, '加载好友申请列表失败')).map(normalizeRequest)
      joinedTeams.value = filterJoinedTeams(pickRecords(assertSuccess(teamRes, '加载已加入团队失败'))).map(normalizeTeam)
      blacklists.value = pickRecords(assertSuccess(blacklistRes, '加载黑名单失败')).map(normalizeBlacklist)

      await loadSquareTeams()
      await loadCurrent()
    } catch (error) {
      showToast(getErrorMessage(error, '加载联系人数据失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  return {
    loadBase,
    loadCurrent,
    loadSquareTeams,
  }
}
