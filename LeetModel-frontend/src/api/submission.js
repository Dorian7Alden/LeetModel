import request from './request'

export function initializeSubmissionUpload(data) {
  return request.post('/submissions/uploads', data)
}

export function getSubmissionUpload(uploadId) {
  return request.get(`/submissions/uploads/${uploadId}`)
}

export function uploadSubmissionChunk(uploadId, chunkIndex, file, sha256, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `/submissions/uploads/${uploadId}/chunks/${chunkIndex}`,
    method: 'put',
    params: { sha256 },
    data,
    timeout: 60000,
    onUploadProgress,
  })
}

export function completeSubmissionUpload(uploadId) {
  return request.post(`/submissions/uploads/${uploadId}/complete`)
}

export function cancelSubmissionUpload(uploadId) {
  return request.delete(`/submissions/uploads/${uploadId}`)
}

export function getTeamSubmissionHistory(teamId) {
  return request.get(`/submissions/teams/${teamId}`)
}

export function finalizeTeamSubmission(teamId) {
  return request.post(`/submissions/teams/${teamId}/finalize`)
}
