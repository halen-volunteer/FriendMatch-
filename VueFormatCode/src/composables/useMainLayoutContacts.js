import { computed, ref } from 'vue'
import { getFriendRequests, getFriendList, getBlacklist } from '@/api/user'
import { getTeamList } from '@/api/team'
import { clampText } from '@/utils/format'

function normalizeFriends(list = []) {
  return list.map((item) => ({
    ...item,
    friendId: item.friendId,
    userNickname: item.userNickname ?? item.friendRemark ?? '好友',
    userAvatar: item.userAvatar ?? '',
    userIntro: item.userIntro ?? '',
  }))
}

function normalizeRequests(list = []) {
  return list.map((item) => ({
    ...item,
    applicantId: item.applicantId,
    userNickname: item.userNickname ?? '新的朋友',
    userAvatar: item.userAvatar ?? '',
    userIntro: item.userIntro ?? '',
    userTags: item.userTags ?? '',
    applyMsg: item.applyMsg ?? '',
  }))
}

function normalizeTeams(list = []) {
  return list.map((item) => ({
    ...item,
    id: item.id,
    teamAvatar: item.teamAvatar ?? '',
    teamIntro: item.teamIntro ?? '',
    maxMember: item.maxMember ?? 0,
    memberCount: item.memberCount ?? 0,
  }))
}

function filterJoinedTeams(list = []) {
  return list.filter((item) => Number(item?.isMember) === 1)
}

export function useMainLayoutContacts({
  route,
  router,
  isFriendsRoute,
  contactSection,
  selectedItemId,
}) {
  const contactRequests = ref([])
  const contactFriends = ref([])
  const joinedTeams = ref([])
  const squareTeams = ref([])
  const blacklists = ref([])
  const contactLoading = ref(false)

  const contactSectionMap = computed(() => ({
    friends: {
      key: 'friends',
      label: '好友',
      emptyText: '暂无好友',
      items: contactFriends.value.map((item) => ({
        id: item.friendId,
        avatar: item.userAvatar,
        title: item.userNickname,
        description: clampText(item.userIntro || item.userAccount || '点击查看资料', 22),
        fallback: '友',
      })),
    },
    requests: {
      key: 'requests',
      label: '新的朋友',
      emptyText: '暂无好友申请',
      items: contactRequests.value.map((item) => ({
        id: item.applicantId,
        avatar: item.userAvatar,
        title: item.userNickname,
        description: clampText(item.applyMsg || '新的好友申请', 22),
        fallback: '新',
      })),
    },
    teams: {
      key: 'teams',
      label: '队伍管理',
      emptyText: '暂无已加入队伍',
      items: joinedTeams.value.map((item) => ({
        id: item.id,
        avatar: item.teamAvatar,
        title: item.teamName,
        description: `${item.memberCount || 0} 人`,
        fallback: '队',
      })),
    },
    square: {
      key: 'square',
      label: '队伍广场',
      emptyText: '请从右侧查看广场内容',
      items: squareTeams.value.map((item) => ({
        id: item.id,
        avatar: item.teamAvatar,
        title: item.teamName,
        description: clampText(item.teamIntro || '公开招募中', 22),
        fallback: '广',
      })),
    },
    blacklist: {
      key: 'blacklist',
      label: '黑名单管理',
      emptyText: '暂无拉黑用户',
      items: blacklists.value.map((item) => ({
        id: item.blackUserId,
        avatar: item.userAvatar ?? '',
        title: item.userNickname ?? '黑名单用户',
        description: clampText(item.blackReason || '已加入黑名单', 22),
        fallback: '黑',
      })),
    },
  }))

  const contactSections = computed(() => Object.values(contactSectionMap.value))

  function ensureContactQuery() {
    if (!isFriendsRoute.value) {
      return
    }

    const list = contactSectionMap.value[contactSection.value]?.items || []
    if (!list.length) {
      return
    }

    const exists = list.some((item) => String(item.id) === selectedItemId.value)
    if (!selectedItemId.value || !exists) {
      router.replace({
        path: '/friends',
        query: {
          section: contactSection.value,
          itemId: String(list[0].id),
        },
      })
    }
  }

  async function loadContactManageData() {
    if (!isFriendsRoute.value) {
      return
    }

    contactLoading.value = true

    try {
      const [friendRes, requestRes, teamRes, blacklistRes] = await Promise.all([
        getFriendList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
        getFriendRequests({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
        getTeamList({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
        getBlacklist({ page: 1, pageSize: 100 }).catch(() => ({ code: 400, data: [] })),
      ])

      const teamSource = teamRes.data?.records || teamRes.data || []
      contactFriends.value = normalizeFriends(friendRes.data?.records || friendRes.data || [])
      contactRequests.value = normalizeRequests(requestRes.data?.records || requestRes.data || [])
      joinedTeams.value = normalizeTeams(filterJoinedTeams(teamSource))
      squareTeams.value = normalizeTeams(teamSource)
      blacklists.value = (blacklistRes.data?.records || blacklistRes.data || []).map((item) => ({
        ...item,
        blackUserId: item.blackUserId,
        userAvatar: item.userAvatar ?? '',
        userNickname: item.userNickname ?? '黑名单用户',
        blackReason: item.blackReason ?? '',
      }))
      ensureContactQuery()
    } finally {
      contactLoading.value = false
    }
  }

  function openContactSection(section) {
    const list = contactSectionMap.value[section]?.items || []
    const firstId = list[0] ? String(list[0].id) : undefined
    router.replace({
      path: '/friends',
      query: {
        section,
        ...(firstId ? { itemId: firstId } : {}),
      },
    })
  }

  function selectContactItem(section, itemId) {
    router.replace({
      path: '/friends',
      query: { section, itemId: String(itemId) },
    })
  }

  function isContactItemActive(section, itemId) {
    return contactSection.value === section && selectedItemId.value === String(itemId)
  }

  return {
    blacklists,
    contactFriends,
    contactLoading,
    contactSectionMap,
    contactSections,
    isContactItemActive,
    joinedTeams,
    loadContactManageData,
    openContactSection,
    selectContactItem,
    squareTeams,
  }
}
