import request from './request'

export function submitTeamPdf(teamId, file) {
  const data = new FormData()
  data.append('teamId', teamId)
  data.append('file', file)
  return request({ url: '/submissions', method: 'post', data, timeout: 30000 })
}

export function getTeamSubmissionHistory(teamId) {
  return request.get(`/submissions/teams/${teamId}`)
}

export function finalizeTeamSubmission(teamId) {
  return request.post(`/submissions/teams/${teamId}/finalize`)
}
