import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  clearSearchHistory,
  getHotKeywords,
  getSearchHistory,
  getSearchSuggest,
  searchTeam,
  searchUser,
} from '@/api/search'

function emptyListResponse() {
  return { code: 400, data: [] }
}

function normalizeTeam(item) {
  return {
    ...item,
    id: item.teamId,
    teamIntro: item.teamIntro ?? '',
    maxMember: item.maxMember ?? 0,
    teamType: item.teamType ?? 1,
    joinRule: item.joinRule ?? 1,
    memberCount: item.memberCount ?? 0,
  }
}

function normalizeUser(item) {
  return {
    ...item,
    id: item.userId,
    userId: item.userId,
    userIntro: item.userIntro ?? '',
  }
}

export function useSearchPage() {
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
  const hasKeyword = computed(() => Boolean(keyword.value.trim()))
  const hasUserResults = computed(() => userList.value.length > 0)
  const hasTeamResults = computed(() => teamList.value.length > 0)
  const shouldShowUserEmpty = computed(() => hasKeyword.value && !hasUserResults.value)
  const shouldShowTeamEmpty = computed(() => hasKeyword.value && !hasTeamResults.value)

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

  function getUserIntro(user) {
    return user.userIntro || ''
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
      getSearchHistory({ searchType: currentSearchType.value, limit: 10 }).catch(() => emptyListResponse()),
      getHotKeywords({ searchType: currentSearchType.value, limit: 10 }).catch(() => emptyListResponse()),
    ])

    historyList.value = historyRes.data || []
    hotList.value = hotRes.data || []
  }

  async function handleSearch(rawKeyword = keyword.value) {
    const kw = String(rawKeyword || '').trim()
    if (!kw) return

    keyword.value = kw
    loading.value = true

    try {
      if (activeTab.value === 'user') {
        const userResponse = await searchUser({ keyword: kw, page: 1, pageSize: 20 })
        if (userResponse.code === 200) {
          userList.value = (userResponse.data?.records || userResponse.data || []).map(normalizeUser)
        } else {
          userList.value = []
        }
        teamList.value = []
      } else {
        const teamResponse = await searchTeam({ keyword: kw, page: 1, pageSize: 20 })
        if (teamResponse.code === 200) {
          teamList.value = (teamResponse.data?.records || teamResponse.data || []).map(normalizeTeam)
        } else {
          teamList.value = []
        }
        userList.value = []
      }
    } finally {
      loading.value = false
    }
  }

  async function handleClearHistory() {
    await clearSearchHistory().catch(() => {})
    historyList.value = []
  }

  watch(
    () => route.query.tab,
    async tab => {
      activeTab.value = String(tab || 'user') === 'team' ? 'team' : 'user'
      suggestList.value = []
      await loadAssistData()
    },
    { immediate: true },
  )

  watch(keyword, async value => {
    const kw = value.trim()
    if (!kw) {
      suggestList.value = []
      return
    }

    const res = await getSearchSuggest({
      keyword: kw,
      type: currentSearchType.value,
      limit: 8,
    }).catch(() => emptyListResponse())

    if (res.code === 200) {
      suggestList.value = res.data || []
    } else {
      suggestList.value = []
    }
  })

  return {
    activeTab,
    getSuggestKey,
    getSuggestText,
    getTeamInitial,
    getTeamIntro,
    getTeamTypeText,
    getUserAccount,
    getUserInitial,
    getUserIntro,
    getUserKey,
    handleClearHistory,
    handleSearch,
    historyList,
    hotList,
    keyword,
    loading,
    shouldShowTeamEmpty,
    shouldShowUserEmpty,
    suggestList,
    teamList,
    userList,
    viewTeam,
    viewUser,
  }
}
