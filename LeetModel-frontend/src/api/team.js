import request from "./request";

export function createTeam(data) {
  return request.post("/teams", data);
}

export function getMyTeams() {
  return request.get("/teams/mine");
}

export function getPublicTeams(params) {
  return request.get("/teams/public", { params });
}

export function getTeamDetail(teamId) {
  return request.get(`/teams/${teamId}`);
}

export function updateTeam(teamId, data) {
  return request.put(`/teams/${teamId}`, data);
}

export function updateTeamRecruitment(teamId, data) {
  return request.put(`/teams/${teamId}/recruitment`, data);
}

export function dissolveTeam(teamId) {
  return request.delete(`/teams/${teamId}`);
}

export function addTeamMember(teamId, userId) {
  return request.post(`/teams/${teamId}/members`, { userId });
}

export function removeTeamMember(teamId, userId) {
  return request.delete(`/teams/${teamId}/members/${userId}`);
}

export function updateTeamMemberRoles(teamId, userId, data) {
  return request.put(`/teams/${teamId}/members/${userId}/roles`, data);
}

export function leaveTeam(teamId) {
  return request.delete(`/teams/${teamId}/leave`);
}

export function submitTeamApplication(teamId, data) {
  return request.post(`/teams/${teamId}/applications`, data);
}

export function cancelTeamApplication(teamId) {
  return request.delete(`/teams/${teamId}/applications/mine`);
}

export function getTeamApplications(teamId) {
  return request.get(`/teams/${teamId}/applications`);
}

export function reviewTeamApplication(teamId, applicationId, decision) {
  return request.put(`/teams/${teamId}/applications/${applicationId}`, { decision });
}
