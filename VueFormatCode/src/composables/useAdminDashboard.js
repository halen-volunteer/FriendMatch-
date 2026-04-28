import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminTeamReports, getAdminUserReports, getPendingAppeals } from '@/api/admin'
import { getTeamList } from '@/api/team'
import { getUserList } from '@/api/user'
import { clampText, formatDateTime } from '@/utils/format'
import { isSuccessResponse, pickRecords, pickTotal } from '@/utils/response'

function normalizeReportActivity(item, type) {
  return {
    id: `${type}-${item.id}`,
    type: type === 'user' ? '用户举报' : '队伍举报',
    target: item.reportedUserId ?? item.reportedTeamId ?? '-',
    content: clampText(item.reportContent, 48),
    status: item.reportStatus ?? 0,
    createTime: formatDateTime(item.createTime),
  }
}

function normalizeAppealActivity(item) {
  return {
    id: item.id,
    type: '处罚申诉',
    target: item.appellantId ?? item.userId ?? '-',
    content: clampText(item.appealReason, 48),
    status: item.appealStatus ?? 0,
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
    { label: '队伍举报', action: () => router.push('/admin/team-reports') },
    { label: '处罚中心', action: () => router.push('/admin/punish') },
    { label: '反馈管理', action: () => router.push('/admin/feedback') },
  ])

  async function loadStats() {
    loading.value = true

    try {
      const [userListRes, teamListRes, userReportsRes, teamReportsRes, appealsRes] = await Promise.all([
        getUserList({ page: 1, pageSize: 1 }).catch(() => null),
        getTeamList({ page: 1, pageSize: 1 }).catch(() => null),
        getAdminUserReports({ page: 1, pageSize: 5 }).catch(() => null),
        getAdminTeamReports({ page: 1, pageSize: 5 }).catch(() => null),
        getPendingAppeals({ page: 1, pageSize: 5 }).catch(() => null),
      ])

      const userReports = isSuccessResponse(userReportsRes) ? pickRecords(userReportsRes) : []
      const teamReports = isSuccessResponse(teamReportsRes) ? pickRecords(teamReportsRes) : []
      const appeals = isSuccessResponse(appealsRes) ? pickRecords(appealsRes) : []

      stats.value = {
        totalUsers: isSuccessResponse(userListRes) ? pickTotal(userListRes) : 0,
        totalTeams: isSuccessResponse(teamListRes) ? pickTotal(teamListRes) : 0,
        pendingReports: [...userReports, ...teamReports].filter((item) => item.reportStatus === 0).length,
        pendingAppeals: appeals.filter((item) => item.appealStatus === 0).length,
        recentReports: [
          ...userReports.map((item) => normalizeReportActivity(item, 'user')),
          ...teamReports.map((item) => normalizeReportActivity(item, 'team')),
        ].slice(0, 5),
        recentAppeals: appeals.map(normalizeAppealActivity).slice(0, 5),
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
