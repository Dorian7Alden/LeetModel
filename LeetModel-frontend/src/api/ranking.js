import request from "./request";

export function getRanking(problemId, keyword) {
  return request({
    url: `/rankings/problems/${problemId}`,
    method: "get",
    params: keyword ? { keyword } : {},
  });
}

export function locateTeamRanking(problemId, teamId, radius = 2) {
  return request({
    url: `/rankings/problems/${problemId}/teams/${teamId}`,
    method: "get",
    params: { radius },
  });
}

export function getProblemScoreDistribution(problemId) {
  return request({
    url: `/rankings/public/problems/${problemId}/score-distribution`,
    method: "get",
  });
}
