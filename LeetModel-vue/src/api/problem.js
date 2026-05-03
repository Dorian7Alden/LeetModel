import request from "./request";

export function getProblemList(params) {
  return request({ url: "/admin/problems", method: "get", params });
}

export function getProblemDetail(problemId) {
  return request({ url: `/admin/problems/${problemId}`, method: "get" });
}

export function createProblem(data) {
  return request({ url: "/admin/problems", method: "post", data });
}

export function updateProblem(problemId, data) {
  return request({ url: `/admin/problems/${problemId}`, method: "put", data });
}

export function deleteProblem(problemId) {
  return request({ url: `/admin/problems/${problemId}`, method: "delete" });
}
