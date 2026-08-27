import request from "./request";

export function createTeam(data) {
  return request.post("/teams", data);
}

export function getMyTeams(params) {
  return request.get("/teams/mine/page", { params });
}

// 队伍分页接口要求任意一次调用都指定单个练习状态；此处合并三种状态。
export async function getAllMyTeams(params = {}) {
  const statuses = ["IN_PROGRESS", "PREPARING", "ENDED"];
  const groups = await Promise.all(
    statuses.map((status) => getMyTeams({ ...params, practiceStatus: status })),
  );
  const rows = [];
  let total = 0;
  for (const group of groups) {
    rows.push(...(group.data?.rows || []));
    total += group.data?.total || 0;
  }
  return { rows, total };
}

export function getPublicTeams(params) {
  return request.get("/teams/public", { params });
}

export function getPublicPreparingProblemIds() {
  return request.get("/teams/public/preparing-problem-ids");
}

export function getTeamDetail(teamId) {
  return request.get(`/teams/${teamId}`);
}

export function updateTeam(teamId, data) {
  return request.put(`/teams/${teamId}`, data);
}

export function publishTeamRecruitment(teamId, data) {
  return request.post(`/teams/${teamId}/recruitments`, data);
}

export function updateTeamRecruitment(teamId, recruitmentId, data) {
  return request.put(`/teams/${teamId}/recruitments/${recruitmentId}`, data);
}

export function closeTeamRecruitment(teamId, recruitmentId) {
  return request.delete(`/teams/${teamId}/recruitments/${recruitmentId}`);
}

export function dissolveTeam(teamId) {
  return request.delete(`/teams/${teamId}`);
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

export function getTeamApplications(teamId, params) {
  return request.get(`/teams/${teamId}/applications`, { params });
}

export function reviewTeamApplication(teamId, applicationId, decision) {
  return request.put(`/teams/${teamId}/applications/${applicationId}`, { decision });
}

export function startTeamPractice(teamId) {
  return request.post(`/teams/${teamId}/practice/start`);
}

export function endTeamPractice(teamId) {
  return request.post(`/teams/${teamId}/practice/end`);
}

export function updateTeamSubmissionPermission(teamId, userId, canSubmit) {
  return request.put(`/teams/${teamId}/members/${userId}/submission-permission`, { canSubmit });
}
