import { ref } from 'vue'
import { isSuccessResponse, pickRecords, pickTotal } from '@/utils/response'

export function usePaginatedRequest(fetcher, options = {}) {
  const loading = ref(false)
  const records = ref([])
  const currentPage = ref(options.initialPage ?? 1)
  const pageSize = ref(options.initialPageSize ?? 20)
  const total = ref(0)

  async function load(extraParams = {}) {
    loading.value = true

    try {
      const response = await fetcher({
        page: currentPage.value,
        pageSize: pageSize.value,
        ...extraParams,
      })

      if (isSuccessResponse(response)) {
        const rawRecords = pickRecords(response)
        records.value = options.transformRecords ? options.transformRecords(rawRecords) : rawRecords
        total.value = pickTotal(response)
      } else {
        records.value = []
        total.value = 0
      }

      return response
    } finally {
      loading.value = false
    }
  }

  async function handleCurrentChange(page) {
    currentPage.value = page
    return load()
  }

  async function handleSizeChange(size) {
    pageSize.value = size
    currentPage.value = 1
    return load()
  }

  return {
    loading,
    records,
    currentPage,
    pageSize,
    total,
    load,
    handleCurrentChange,
    handleSizeChange,
  }
}
