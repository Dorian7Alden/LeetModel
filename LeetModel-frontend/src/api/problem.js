import request from "./request";

export function getProblemList(params) {
  return request({ url: "/problems", method: "get", params });
}

export function getProblemDetail(problemId) {
  return request({ url: `/problems/${problemId}`, method: "get" });
}

export function getPublicProblemList(params) {
  return request({ url: "/public/problems", method: "get", params });
}

export function getPublicProblemDetail(problemId) {
  return request({ url: `/public/problems/${problemId}`, method: "get" });
}

export function getRandomPublicProblem(params) {
  return request({ url: "/public/problems/random", method: "get", params });
}

export function getContests() {
  return request({ url: "/contests", method: "get" });
}

export function createContest(data) {
  return request({ url: "/contests", method: "post", data });
}

export function updateContest(contestId, data) {
  return request({ url: `/contests/${contestId}`, method: "put", data });
}

export function createProblem(data) {
  return request({ url: "/problems", method: "post", data });
}

export function updateProblem(problemId, data) {
  return request({ url: `/problems/${problemId}`, method: "put", data });
}

export function deleteProblem(problemId) {
  return request({ url: `/problems/${problemId}`, method: "delete" });
}
