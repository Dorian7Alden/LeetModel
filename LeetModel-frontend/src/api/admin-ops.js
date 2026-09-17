import request from "./request";

export function getAdminTeams(limit = 20) {
  return request({ url: "/admin/teams", method: "get", params: { limit } });
}

export function getAdminTeamPage(params) {
  return request({ url: "/admin/teams/page", method: "get", params });
}

export function getAdminTeamStats() {
  return request({ url: "/admin/teams/stats", method: "get" });
}

export function getAdminTeamDetail(id) {
  return request({ url: `/admin/teams/${id}/detail`, method: "get" });
}

export function createAdminTeam(data) {
  return request({ url: "/admin/teams", method: "post", data });
}

export function updateAdminTeam(id, data) {
  return request({ url: `/admin/teams/${id}`, method: "put", data });
}

export function updateAdminTeamPracticeStatus(id, data) {
  return request({ url: `/admin/teams/${id}/practice-status`, method: "put", data });
}

export function dissolveAdminTeam(id, reason) {
  return request({ url: `/admin/teams/${id}`, method: "delete", params: reason ? { reason } : {} });
}

export function getAdminSubmissionPage(params) {
  return request({ url: "/admin/submissions/page", method: "get", params });
}

export function getAdminSubmissionStats() {
  return request({ url: "/admin/submissions/stats", method: "get" });
}

export function getAdminSubmissionDetail(id) {
  return request({ url: `/admin/submissions/${id}/detail`, method: "get" });
}

export function setAdminFinalSubmission(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}/set-final`, method: "put" });
}

export function invalidateAdminSubmission(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}`, method: "delete" });
}

export function redispatchAdminReview(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}/re-dispatch`, method: "post" });
}

export function getAdminTeamReferences(teamIds) {
  return request({
    url: "/admin/teams/references",
    method: "get",
    params: { teamIds: teamIds.join(",") },
  });
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

export function getAdminGlobalRankingStats() {
  return request({ url: "/admin/rankings/global-stats", method: "get" });
}

export function rebuildAdminRanking(problemId) {
  return request({ url: `/admin/rankings/problems/${problemId}/rebuild`, method: "post" });
}

export function getAdminRankingPage(problemId, params) {
  return request({ url: `/admin/rankings/problems/${problemId}/page`, method: "get", params });
}

export function getAdminProblemRankingStats(problemId) {
  return request({ url: `/admin/rankings/problems/${problemId}/stats`, method: "get" });
}

export function disqualifyAdminRankingEntry(id) {
  return request({ url: `/admin/rankings/entries/${id}`, method: "delete" });
}

export function overrideAdminRankingScore(id, data) {
  return request({ url: `/admin/rankings/entries/${id}/score-override`, method: "put", data });
}

export function rebuildAllAdminRankings() {
  return request({ url: "/admin/rankings/rebuild-all", method: "post" });
}
