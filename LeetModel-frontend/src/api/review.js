import request from './request'

export function getTeamReviews(teamId) {
  return request.get(`/reviews/teams/${teamId}`)
}

export function getReviewTask(taskId) {
  return request.get(`/reviews/${taskId}`)
}

export function retryReviewTask(taskId) {
  return request.post(`/reviews/${taskId}/retry`)
}
