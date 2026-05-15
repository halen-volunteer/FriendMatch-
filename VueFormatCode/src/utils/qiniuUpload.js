const LARGE_FILE_THRESHOLD = 20 * 1024 * 1024
const CHUNK_SIZE = 4 * 1024 * 1024
const MAX_RETRY = 3
const RESUME_PREFIX = 'qiniu_resume_'

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function toBase64Url(value) {
  const utf8 = encodeURIComponent(value).replace(/%([0-9A-F]{2})/g, (_, hex) =>
    String.fromCharCode(parseInt(hex, 16)),
  )

  return btoa(utf8).replace(/\+/g, '-').replace(/\//g, '_')
}

function buildResumeKey({ key, file }) {
  return `${RESUME_PREFIX}${key}_${file.size}_${file.lastModified}`
}

function readResumeState(resumeKey) {
  try {
    return JSON.parse(localStorage.getItem(resumeKey) || 'null')
  } catch {
    return null
  }
}

function writeResumeState(resumeKey, state) {
  localStorage.setItem(resumeKey, JSON.stringify({ ...state, updatedAt: Date.now() }))
}

function clearResumeState(resumeKey) {
  localStorage.removeItem(resumeKey)
}

function parseUploadError(responseText) {
  try {
    const parsed = JSON.parse(responseText || '{}')
    return parsed.error || parsed.err || responseText || ''
  } catch {
    return responseText || ''
  }
}

async function uploadDirect({ file, uploadUrl, uploadToken, key, onProgress }) {
  await new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', uploadUrl)

    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable || !onProgress) {
        return
      }

      onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)))
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.(100)
        resolve()
        return
      }

      const detail = parseUploadError(xhr.responseText)
      reject(new Error(`上传失败: ${xhr.status}${detail ? ` - ${detail}` : ''}`))
    }

    xhr.onerror = () => reject(new Error('上传失败'))

    const formData = new FormData()
    formData.append('token', uploadToken)
    formData.append('key', key)
    formData.append('file', file)
    xhr.send(formData)
  })
}

async function uploadChunk({ chunk, chunkSize, uploadUrl, uploadToken, onRetry }) {
  let lastError = null

  for (let attempt = 0; attempt < MAX_RETRY; attempt += 1) {
    try {
      const response = await fetch(`${uploadUrl}/mkblk/${chunkSize}`, {
        method: 'POST',
        headers: {
          Authorization: `UpToken ${uploadToken}`,
          'Content-Type': 'application/octet-stream',
        },
        body: chunk,
      })

      if (!response.ok) {
        throw new Error(`分片上传失败: ${response.status}`)
      }

      return await response.json()
    } catch (error) {
      lastError = error
      onRetry?.(attempt + 1)

      if (attempt < MAX_RETRY - 1) {
        await sleep(500 * (attempt + 1))
      }
    }
  }

  throw lastError || new Error('分片上传失败')
}

async function uploadChunked({ file, uploadUrl, uploadToken, key, onProgress }) {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  const resumeKey = buildResumeKey({ key, file })
  const savedState = readResumeState(resumeKey)
  const contexts = Array.isArray(savedState?.contexts) ? savedState.contexts : []

  for (let index = 0; index < totalChunks; index += 1) {
    if (contexts[index]) {
      onProgress?.(Math.min(99, Math.round(((index + 1) / totalChunks) * 100)))
      continue
    }

    const start = index * CHUNK_SIZE
    const end = Math.min(file.size, start + CHUNK_SIZE)
    const chunk = file.slice(start, end)
    const result = await uploadChunk({
      chunk,
      chunkSize: end - start,
      uploadUrl,
      uploadToken,
    })

    contexts[index] = result.ctx
    writeResumeState(resumeKey, {
      fileName: file.name,
      size: file.size,
      key,
      contexts,
    })
    onProgress?.(Math.min(99, Math.round(((index + 1) / totalChunks) * 100)))
  }

  const response = await fetch(
    `${uploadUrl}/mkfile/${file.size}/key/${toBase64Url(key)}/fname/${toBase64Url(file.name)}`,
    {
      method: 'POST',
      headers: {
        Authorization: `UpToken ${uploadToken}`,
        'Content-Type': 'text/plain',
      },
      body: contexts.join(','),
    },
  )

  if (!response.ok) {
    throw new Error(`合并分片失败: ${response.status}`)
  }

  clearResumeState(resumeKey)
  onProgress?.(100)
}

export async function uploadToQiniu({ file, uploadUrl, uploadToken, key, onProgress }) {
  if (file.size >= LARGE_FILE_THRESHOLD) {
    await uploadChunked({ file, uploadUrl, uploadToken, key, onProgress })
    return { mode: 'chunked' }
  }

  await uploadDirect({ file, uploadUrl, uploadToken, key, onProgress })
  return { mode: 'direct' }
}

export function isLargeUpload(fileSize) {
  return fileSize >= LARGE_FILE_THRESHOLD
}
