import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { useFriendsActions } from '@/composables/useFriendsActions'
import { useFriendsDataLoader } from '@/composables/useFriendsDataLoader'
import {
  createConfirmState,
  getJoinRuleText,
  getTeamInitial,
  getTeamIntro,
  getTeamMemberText,
  getTeamTypeText,
  getUserAccount,
  getUserBio,
  getUserInitial,
} from '@/composables/useFriendsHelpers'
import { useFriendsQueryState } from '@/composables/useFriendsQueryState'

export function useFriendsPage() {
  const route = useRoute()
  const router = useRouter()
  const friends = ref([])
  const requests = ref([])
  const blacklists = ref([])
  const joinedTeams = ref([])
  const squareTeams = ref([])
  const currentFriend = ref(null)
  const currentRequest = ref(null)
  const currentBlacklistUser = ref(null)
  const currentTeam = ref(null)
  const loading = ref(false)
  const squareLoading = ref(false)
  const confirmState = ref(createConfirmState())
  const squareKeyword = ref('')
  const showApply = ref({ show: false, team: null })
  const showJoinPwd = ref({ show: false, team: null })
  const applyMsg = ref('')
  const joinPwd = ref('')
  const requestSubmitting = ref(false)
  const teamApplySubmitting = ref(false)
  const joinPwdSubmitting = ref(false)
  const { toast, showToast } = useToast()

  const queryState = useFriendsQueryState(route, router)

  const dataLoader = useFriendsDataLoader({
    blacklists,
    currentBlacklistUser,
    currentFriend,
    currentRequest,
    currentTeam,
    friends,
    isBlacklistSection: queryState.isBlacklistSection,
    isFriendsSection: queryState.isFriendsSection,
    isRequestsSection: queryState.isRequestsSection,
    isTeamsSection: queryState.isTeamsSection,
    itemId: queryState.itemId,
    joinedTeams,
    loading,
    requests,
    routeToSection: queryState.routeToSection,
    showToast,
    squareKeyword,
    squareLoading,
    squareTeams,
  })

  const actions = useFriendsActions({
    applyMsg,
    confirmState,
    currentBlacklistUser,
    currentFriend,
    currentRequest,
    currentTeam,
    joinPwd,
    joinPwdSubmitting,
    loadBase: dataLoader.loadBase,
    loadSquareTeams: dataLoader.loadSquareTeams,
    requestSubmitting,
    router,
    showApply,
    showJoinPwd,
    showToast,
    teamApplySubmitting,
  })

  watch(() => route.fullPath, dataLoader.loadCurrent)
  onMounted(dataLoader.loadBase)

  return {
    applyMsg,
    closeConfirm: actions.closeConfirm,
    confirmState,
    currentBlacklistUser,
    currentFriend,
    currentRequest,
    currentTeam,
    enterTeam: actions.enterTeam,
    friends,
    getJoinRuleText,
    getTeamInitial,
    getTeamIntro,
    getTeamMemberText,
    getTeamTypeText,
    getUserAccount,
    getUserBio,
    getUserInitial,
    handleAgree: actions.handleAgree,
    handleApply: actions.handleApply,
    handleApplyDialog: actions.handleApplyDialog,
    handleJoinPasswordDialog: actions.handleJoinPasswordDialog,
    handleJoinPwd: actions.handleJoinPwd,
    handleReject: actions.handleReject,
    handleRemoveBlacklist: actions.handleRemoveBlacklist,
    isBlacklistSection: queryState.isBlacklistSection,
    isFriendsSection: queryState.isFriendsSection,
    isRequestsSection: queryState.isRequestsSection,
    isSquareSection: queryState.isSquareSection,
    isTeamsSection: queryState.isTeamsSection,
    joinPwd,
    joinedTeams,
    joinPwdSubmitting,
    loading,
    openTeamDetail: actions.openTeamDetail,
    requestSubmitting,
    requests,
    section: queryState.section,
    sendMessageToFriend: actions.sendMessageToFriend,
    showApply,
    showJoinPwd,
    squareKeyword,
    squareLoading,
    loadSquareTeams: dataLoader.loadSquareTeams,
    squareTeams,
    submitRemoveBlacklist: actions.submitRemoveBlacklist,
    teamApplySubmitting,
    toast,
  }
}
