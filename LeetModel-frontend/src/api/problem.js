import request from "./request";

export function getProblemList(params) {
  return request({ url: "/problems", method: "get", params });
}

export function getProblemDetail(problemId) {
  return request({ url: `/problems/${problemId}`, method: "get" });
}

export function getPublicProblemList(params) {
  return request({
    url: "/public/problems",
    method: "get",
    params,
    paramsSerializer: { indexes: null },
  });
}

export function getPublicProblemFilterOptions() {
  return request({ url: "/public/problems/filter-options", method: "get" });
}

export function getPublicProblemDetail(problemId) {
  return request({ url: `/public/problems/${problemId}`, method: "get" });
}

export function getRandomPublicProblem(params) {
  return request({
    url: "/public/problems/random",
    method: "get",
    params,
    paramsSerializer: { indexes: null },
  });
}

export function getContests() {
  return request({ url: "/contests", method: "get" });
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

// ==================== 管理端题目、标签、赛事与附件 ====================

export function getAdminContentProblems(params) {
  return request({ url: "/admin/content/problems", method: "get", params });
}

export function getAdminContentProblem(problemId) {
  return request({ url: `/admin/content/problems/${problemId}`, method: "get" });
}

export function createAdminContentProblem(data) {
  return request({ url: "/admin/content/problems", method: "post", data });
}

export function updateAdminContentProblem(problemId, data) {
  return request({ url: `/admin/content/problems/${problemId}`, method: "put", data });
}

export function deleteAdminContentProblem(problemId) {
  return request({ url: `/admin/content/problems/${problemId}`, method: "delete" });
}

export function getAdminContentTags() {
  return request({ url: "/admin/content/tags", method: "get" });
}

export function createAdminContentTag(data) {
  return request({ url: "/admin/content/tags", method: "post", data });
}

export function updateAdminContentTag(tagId, data) {
  return request({ url: `/admin/content/tags/${tagId}`, method: "put", data });
}

export function deleteAdminContentTag(tagId) {
  return request({ url: `/admin/content/tags/${tagId}`, method: "delete" });
}

export function getAdminContentContests() {
  return request({ url: "/admin/content/contests", method: "get" });
}

export function uploadAdminAttachment(problemId, file, options = {}) {
  const formData = new FormData();
  formData.append("file", file);
  if (options.description) formData.append("description", options.description);
  if (options.sortOrder !== undefined) formData.append("sortOrder", options.sortOrder);
  return request({
    url: `/admin/content/problems/${problemId}/attachments`,
    method: "post",
    data: formData,
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export function deleteAdminAttachment(problemId, attachmentId) {
  return request({
    url: `/admin/content/problems/${problemId}/attachments/${attachmentId}`,
    method: "delete",
  });
}
