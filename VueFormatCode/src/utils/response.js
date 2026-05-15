export function isSuccessResponse(response) {
  return response?.code === 200
}

export function getErrorMessage(error, fallback = '请求失败') {
  return error?.response?.message || error?.message || fallback
}

export function assertSuccess(response, fallback = '请求失败') {
  if (isSuccessResponse(response)) return response
  const error = new Error(response?.message || fallback)
  error.response = response
  throw error
}

export function pickRecords(response) {
  const data = response?.data

  if (Array.isArray(data?.records)) {
    return data.records
  }

  if (Array.isArray(data?.list)) {
    return data.list
  }

  if (Array.isArray(data)) {
    return data
  }

  return []
}

export function pickTotal(response) {
  if (typeof response?.total === 'number') {
    return response.total
  }

  if (typeof response?.data?.total === 'number') {
    return response.data.total
  }

  const records = pickRecords(response)
  return records.length
}
