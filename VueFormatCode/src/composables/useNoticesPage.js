import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useNoticeStore } from '@/stores/notice'
import { getNoticeList, readNotices, deleteNotices } from '@/api/user'
import { useToast } from '@/composables/useToast'
import { assertSuccess, getErrorMessage, pickRecords } from '@/utils/response'

const NOTICE_SECTIONS = ['all', 'friend', 'team', 'system']

const typeMap = {
  1: { text: '好友申请', icon: 'UserFilled', path: '/friends', category: 'friend' },
  2: { text: '好友拒绝', icon: 'Close', path: null, category: 'friend' },
  3: { text: '入队通过', icon: 'Check', path: null, category: 'team' },
  4: { text: '入队拒绝', icon: 'Close', path: null, category: 'team' },
  5: { text: '被移出团队', icon: 'User', path: null, category: 'team' },
  6: { text: '账号异常', icon: 'WarningFilled', path: '/account-status', category: 'system' },
  7: { text: '处罚通知', icon: 'WarningFilled', path: '/reports', category: 'system' },
  8: { text: '反馈回复', icon: 'Message', path: '/feedback', category: 'system' },
  9: { text: '@提醒', icon: 'Bell', path: null, category: 'system' },
}

export function useNoticesPage() {
  const router = useRouter()
  const route = useRoute()
  const noticeStore = useNoticeStore()
  const list = ref([])
  const loading = ref(false)
  const { toast, showToast } = useToast()

  const section = computed(() => {
    const value = String(route.query.section || 'all')
    return NOTICE_SECTIONS.includes(value) ? value : 'all'
  })

  const filteredList = computed(() => {
    if (section.value === 'all') return list.value
    return list.value.filter((item) => typeMap[item.noticeType]?.category === section.value)
  })

  function formatTime(value) {
    if (!value) return ''
    return new Date(value).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  async function load() {
    loading.value = true
    try {
      const response = await getNoticeList({ page: 1, pageSize: 50 })
      const res = assertSuccess(response, '加载通知列表失败')
      list.value = pickRecords(res)
      noticeStore.clear()
    } catch (error) {
      showToast(getErrorMessage(error, '加载通知列表失败'), 'error')
    } finally {
      loading.value = false
    }
  }

  async function handleClick(item) {
    try {
      if (!item.isRead) {
        noticeStore.decrement()
        item.isRead = 1
        await readNotices([item.id])
      }
      const target = typeMap[item.noticeType]
      if (target?.path) {
        router.push(target.path)
      }
    } catch (error) {
      if (item.isRead === 1) {
        item.isRead = 0
        noticeStore.increment()
      }
      showToast(getErrorMessage(error, '处理通知失败'), 'error')
    }
  }

  async function readAll() {
    const ids = list.value.filter((item) => !item.isRead).map((item) => item.id)
    if (!ids.length) return
    try {
      await readNotices(ids)
      list.value.forEach((item) => {
        item.isRead = 1
      })
      noticeStore.clear()
      showToast('已全部标记为已读')
    } catch (error) {
      showToast(getErrorMessage(error, '批量已读失败'), 'error')
    }
  }

  async function handleDelete(id) {
    try {
      const target = list.value.find((item) => item.id === id)
      const wasUnread = target && !target.isRead
      await deleteNotices([id])
      list.value = list.value.filter((item) => item.id !== id)
      if (wasUnread) {
        noticeStore.decrement()
      }
    } catch (error) {
      showToast(getErrorMessage(error, '删除通知失败'), 'error')
    }
  }

  function handleRealtimeNotice(event) {
    const detail = event.detail || {}
    const noticeId = detail.id ?? detail.noticeId
    if (!noticeId) return

    const exists = list.value.some((item) => String(item.id) === String(noticeId))
    if (exists) return

    list.value = [
      {
        id: noticeId,
        noticeType: detail.noticeType,
        noticeContent: detail.noticeContent,
        isRead: 0,
        createTime: detail.createTime || new Date().toISOString(),
      },
      ...list.value,
    ]
  }

  watch(() => route.query.section, () => {}, { immediate: true })
  onMounted(() => {
    load()
    window.addEventListener('system-notice', handleRealtimeNotice)
  })

  onUnmounted(() => {
    window.removeEventListener('system-notice', handleRealtimeNotice)
  })

  return {
    filteredList,
    formatTime,
    handleClick,
    handleDelete,
    list,
    load,
    loading,
    readAll,
    section,
    toast,
    typeMap,
  }
}
