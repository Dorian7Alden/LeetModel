import request from './request'

export function createUploadSession(data) {
  return request.post('/admin/content/files/uploads', data)
}

export function getUploadSession(sessionId) {
  return request.get(`/admin/content/files/uploads/${sessionId}`)
}

export function getUploadPartUrl(sessionId, partNumber) {
  return request.post(`/admin/content/files/uploads/${sessionId}/parts/${partNumber}/url`)
}

export function completeUploadSession(sessionId) {
  return request.post(`/admin/content/files/uploads/${sessionId}/complete`)
}

export function abortUploadSession(sessionId) {
  return request.delete(`/admin/content/files/uploads/${sessionId}`)
}
