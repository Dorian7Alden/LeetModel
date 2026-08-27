import request from "./request";

export function createSuggestion(submissionId) {
  return request({ url: "/suggestions", method: "post", data: { submissionId } });
}

export function getSuggestion(taskId) {
  return request({ url: `/suggestions/${taskId}`, method: "get" });
}

export function getSuggestionBySubmission(submissionId) {
  return request({ url: `/suggestions/submissions/${submissionId}`, method: "get" });
}

export function listTeamSuggestions(teamId) {
  return request({ url: `/suggestions/teams/${teamId}`, method: "get" });
}

export function retrySuggestion(taskId) {
  return request({ url: `/suggestions/${taskId}/retry`, method: "post" });
}
