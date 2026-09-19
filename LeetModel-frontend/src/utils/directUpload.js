import {
  abortUploadSession,
  completeUploadSession,
  createUploadSession,
  getUploadPartUrl,
  getUploadSession,
} from '../api/fileTransfer'

/** 超过该大小的文件走预签名分片直传，避免经过业务服务中转。 */
export const DIRECT_UPLOAD_THRESHOLD_BYTES = 5 * 1024 * 1024

const RESUME_STORAGE_KEY = 'leetmodel:direct-upload:v1'

export function shouldUseDirectUpload(file) {
  return Boolean(file) && file.size > DIRECT_UPLOAD_THRESHOLD_BYTES
}

/**
 * 以预签名分片直传方式上传文件，支持中断后续传。
 *
 * @param file 浏览器文件对象
 * @param options.purpose file-service 业务用途编码
 * @param options.groupPath 逻辑分组路径
 * @param options.onProgress 进度回调，参数为已上传字节与总字节
 * @returns file-service 返回的文件资产摘要
 */
export async function uploadFileDirect(file, options = {}) {
  const { purpose, groupPath, onProgress } = options
  const resumeKey = signature(file)
  const session = await requireSession(file, resumeKey, purpose, groupPath)
  const partSize = Number(session.partSize)
  const partCount = Number(session.partCount)
  const uploaded = new Set(session.uploaded || [])

  if (uploaded.size === 0) {
    const remote = await getUploadSession(session.sessionId)
    if (remote.code === 20000 && remote.data) {
      for (const partNumber of remote.data.uploadedPartNumbers || []) uploaded.add(partNumber)
    }
  }

  let uploadedBytes = Math.min(uploaded.size * partSize, file.size)
  onProgress?.(uploadedBytes, file.size)

  for (let partNumber = 1; partNumber <= partCount; partNumber++) {
    if (uploaded.has(partNumber)) continue
    const urlResponse = await getUploadPartUrl(session.sessionId, partNumber)
    if (urlResponse.code !== 20000 || !urlResponse.data?.uploadUrl) {
      throw new Error(urlResponse.message || '获取分片上传地址失败')
    }
    const start = (partNumber - 1) * partSize
    const blob = file.slice(start, Math.min(start + partSize, file.size))
    await putPart(urlResponse.data.uploadUrl, blob, (loaded) => {
      onProgress?.(Math.min(uploadedBytes + loaded, file.size), file.size)
    })
    uploaded.add(partNumber)
    uploadedBytes = Math.min(uploaded.size * partSize, file.size)
    persistSession(resumeKey, { ...session, uploaded: [...uploaded] })
  }

  const completed = await completeUploadSession(session.sessionId)
  if (completed.code !== 20000 || !completed.data?.fileId) {
    throw new Error(completed.message || '合并上传文件失败')
  }
  clearSession(resumeKey)
  return completed.data
}

export async function cancelFileDirectUpload(file) {
  const resumeKey = signature(file)
  const session = readSessions()[resumeKey]
  if (!session?.sessionId) return
  try {
    await abortUploadSession(session.sessionId)
  } finally {
    clearSession(resumeKey)
  }
}

async function requireSession(file, resumeKey, purpose, groupPath) {
  const stored = readSessions()[resumeKey]
  if (stored?.sessionId) return stored

  const response = await createUploadSession({
    purpose,
    groupPath,
    originalName: file.name,
    contentType: file.type || 'application/octet-stream',
    fileSize: file.size,
  })
  if (response.code !== 20000 || !response.data?.sessionId) {
    throw new Error(response.message || '创建上传会话失败')
  }
  const session = {
    sessionId: response.data.sessionId,
    partSize: response.data.partSize,
    partCount: response.data.partCount,
    uploaded: [],
  }
  persistSession(resumeKey, session)
  return session
}

function putPart(url, blob, onProgressTick) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url, true)
    xhr.upload.onprogress = (event) => onProgressTick?.(event.loaded)
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`分片上传失败，状态码 ${xhr.status}`))
    }
    xhr.onerror = () => reject(new Error('分片上传网络中断，可重新选择同一文件继续上传'))
    xhr.send(blob)
  })
}

function signature(file) {
  return `${file.name}::${file.size}::${file.lastModified}`
}

function readSessions() {
  try {
    return JSON.parse(localStorage.getItem(RESUME_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

function persistSession(key, session) {
  const sessions = readSessions()
  sessions[key] = session
  localStorage.setItem(RESUME_STORAGE_KEY, JSON.stringify(sessions))
}

function clearSession(key) {
  const sessions = readSessions()
  delete sessions[key]
  localStorage.setItem(RESUME_STORAGE_KEY, JSON.stringify(sessions))
}
