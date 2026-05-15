import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminTeamReports, getAdminUserReports, getPendingAppeals } from '@/api/admin'
import { getAdminMessageReportList } from '@/api/chat'
import { getTeamList } from '@/api/team'
import { getUserList } from '@/api/user'
import { clampText, formatDateTime } from '@/utils/format'
import { isSuccessResponse, pickRecords, pickTotal } from '@/utils/response'

function normalizeReportActivity(item, type) {
  return {
    id: `${type}-${item.id}`,
    type: type === 'user' ? '用户举报' : type === 'message' ? '消息举报' : '团队举报',
    target: item.reportedUserId ?? item.reportedTeamId ?? item.messageId ?? '-',
    content: clampText(item.reportContent, 48),
    status: item.reportStatus ?? item.adminStatus ?? 0,
    sortTime: item.createTime || '',
    createTime: formatDateTime(item.createTime),
  }
}

function normalizeAppealActivity(item) {
  return {
    id: item.id,
    type: '举报申诉',
    target: item.appellantId ?? item.userId ?? '-',
    content: clampText(item.appealReason, 48),
    status: item.appealStatus ?? 0,
    sortTime: item.createTime || '',
    createTime: formatDateTime(item.createTime),
  }
}

export function useAdminDashboard() {
  const router = useRouter()
  const loading = ref(false)
  const stats = ref({
    totalUsers: 0,
    totalTeams: 0,
    pendingReports: 0,
    pendingAppeals: 0,
    recentReports: [],
    recentAppeals: [],
  })

  const quickActions = computed(() => [
    { label: '用户举报', action: () => router.push('/admin/reports') },
    { label: '消息举报', action: () => router.push('/admin/message-reports') },
    { label: '团队举报', action: () => router.push('/admin/team-reports') },
    { label: '处罚中心', action: () => router.push('/admin/punish') },
    { label: '反馈管理', action: () => router.push('/admin/feedback') },
  ])

  async function loadStats() {
    loading.value = true

    try {
      const [
        userListRes,
        teamListRes,
        pendingUserReportsRes,
        pendingTeamReportsRes,
        pendingMessageReportsRes,
        appealsRes,
        recentUserReportsRes,
        recentTeamReportsRes,
        recentMessageReportsRes,
      ] = await Promise.all([
        getUserList({ page: 1, pageSize: 1 }).catch(() => null),
        getTeamList({ page: 1, pageSize: 1 }).catch(() => null),
        getAdminUserReports({ reportStatus: 0, page: 1, pageSize: 1 }).catch(() => null),
        getAdminTeamReports({ reportStatus: 0, page: 1, pageSize: 1 }).catch(() => null),
        getAdminMessageReportList({ adminStatus: 0, page: 1, pageSize: 1 }).catch(() => null),
        getPendingAppeals({ page: 1, pageSize: 1 }).catch(() => null),
        getAdminUserReports({ page: 1, pageSize: 3 }).catch(() => null),
        getAdminTeamReports({ page: 1, pageSize: 3 }).catch(() => null),
        getAdminMessageReportList({ page: 1, pageSize: 3 }).catch(() => null),
      ])

      const userReports = isSuccessResponse(recentUserReportsRes) ? pickRecords(recentUserReportsRes) : []
      const teamReports = isSuccessResponse(recentTeamReportsRes) ? pickRecords(recentTeamReportsRes) : []
      const messageReports = isSuccessResponse(recentMessageReportsRes) ? pickRecords(recentMessageReportsRes) : []
      const appeals = isSuccessResponse(appealsRes) ? pickRecords(appealsRes) : []

      const recentReports = [
        ...userReports.map((item) => normalizeReportActivity(item, 'user')),
        ...messageReports.map((item) => normalizeReportActivity(item, 'message')),
        ...teamReports.map((item) => normalizeReportActivity(item, 'team')),
      ]
        .sort((a, b) => new Date(b.sortTime).getTime() - new Date(a.sortTime).getTime())
        .slice(0, 5)
        .map(({ sortTime, ...item }) => item)

      const recentAppeals = appeals
        .map(normalizeAppealActivity)
        .sort((a, b) => new Date(b.sortTime).getTime() - new Date(a.sortTime).getTime())
        .slice(0, 5)
        .map(({ sortTime, ...item }) => item)

      stats.value = {
        totalUsers: isSuccessResponse(userListRes) ? pickTotal(userListRes) : 0,
        totalTeams: isSuccessResponse(teamListRes) ? pickTotal(teamListRes) : 0,
        pendingReports:
          (isSuccessResponse(pendingUserReportsRes) ? pickTotal(pendingUserReportsRes) : 0)
          + (isSuccessResponse(pendingTeamReportsRes) ? pickTotal(pendingTeamReportsRes) : 0)
          + (isSuccessResponse(pendingMessageReportsRes) ? pickTotal(pendingMessageReportsRes) : 0),
        pendingAppeals: isSuccessResponse(appealsRes) ? pickTotal(appealsRes) : 0,
        recentReports,
        recentAppeals,
      }
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    loadStats()
  })

  return {
    loading,
    quickActions,
    stats,
    loadStats,
  }
}
