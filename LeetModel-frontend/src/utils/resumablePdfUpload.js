import {
  completeSubmissionUpload,
  initializeSubmissionUpload,
  uploadSubmissionChunk,
} from '@/api/submission'

const MAX_RETRIES = 3
const CONCURRENCY = 2

function toHex(buffer) {
  return Array.from(new Uint8Array(buffer), value => value.toString(16).padStart(2, '0')).join('')
}

async function sha256(blob) {
  const bytes = await blob.arrayBuffer()
  return toHex(await crypto.subtle.digest('SHA-256', bytes))
}

function chunkLength(fileSize, chunkSize, totalChunks, index) {
  if (index < totalChunks - 1) return chunkSize
  return fileSize - chunkSize * (totalChunks - 1)
}

function wait(delay) {
  return new Promise(resolve => window.setTimeout(resolve, delay))
}

async function uploadWithRetry({ uploadId, index, chunk, digest, onProgress }) {
  let lastError
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt += 1) {
    try {
      await uploadSubmissionChunk(uploadId, index, chunk, digest, onProgress)
      return
    } catch (error) {
      lastError = error
      if (attempt < MAX_RETRIES) await wait(400 * (2 ** (attempt - 1)))
    }
  }
  throw lastError
}

export async function uploadPdfResumably({ teamId, file, onProgress, onStage }) {
  if (!globalThis.crypto?.subtle) throw new Error('当前浏览器不支持文件完整性校验')

  onStage?.('正在校验文件')
  const fileSha256 = await sha256(file)
  const session = (await initializeSubmissionUpload({
    teamId,
    originalFilename: file.name,
    fileSize: file.size,
    fileSha256,
  })).data

  const uploaded = new Set(session.uploadedChunks || [])
  const confirmedBytes = Array.from(uploaded).reduce(
    (total, index) => total + chunkLength(file.size, session.chunkSize, session.totalChunks, index),
    0,
  )
  let completedBytes = confirmedBytes
  const inflightBytes = new Map()
  const reportProgress = () => {
    const inflight = Array.from(inflightBytes.values()).reduce((total, value) => total + value, 0)
    onProgress?.(Math.min(99, Math.round((completedBytes + inflight) * 100 / file.size)))
  }
  reportProgress()

  const missing = Array.from({ length: session.totalChunks }, (_, index) => index)
    .filter(index => !uploaded.has(index))
  let cursor = 0
  onStage?.(missing.length ? `正在上传 ${missing.length} 个缺失分片` : '分片已完整，正在继续提交')

  async function worker() {
    while (cursor < missing.length) {
      const index = missing[cursor]
      cursor += 1
      const start = index * session.chunkSize
      const end = Math.min(file.size, start + session.chunkSize)
      const chunk = file.slice(start, end, 'application/octet-stream')
      const digest = await sha256(chunk)
      await uploadWithRetry({
        uploadId: session.uploadId,
        index,
        chunk,
        digest,
        onProgress: event => {
          if (!event.total) return
          inflightBytes.set(index, Math.min(chunk.size, event.loaded))
          reportProgress()
        },
      })
      inflightBytes.delete(index)
      completedBytes += chunk.size
      reportProgress()
    }
  }

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, missing.length) }, () => worker()))
  onStage?.('正在合并并校验 PDF')
  const submission = await completeSubmissionUpload(session.uploadId)
  onProgress?.(100)
  return submission.data
}
