import request from "./request";

export function getAdminTeams(limit = 20) {
  return request({ url: "/admin/teams", method: "get", params: { limit } });
}

export function getAdminSubmissions(limit = 20) {
  return request({ url: "/admin/submissions", method: "get", params: { limit } });
}

export function getAdminSubmissionPreview(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}/preview`, method: "get" });
}

export function getAdminReviews(limit = 20) {
  return request({ url: "/admin/reviews", method: "get", params: { limit } });
}

export function getAdminSuggestions(limit = 20) {
  return request({ url: "/admin/suggestions", method: "get", params: { limit } });
}

export function getAdminConversations(limit = 20) {
  return request({ url: "/admin/assistant/conversations", method: "get", params: { limit } });
}

export function getAdminRanking(problemId, keyword) {
  return request({
    url: `/admin/rankings/problems/${problemId}`,
    method: "get",
    params: keyword ? { keyword } : {},
  });
}

export function rebuildAdminRanking(problemId) {
  return request({ url: `/admin/rankings/problems/${problemId}/rebuild`, method: "post" });
}
