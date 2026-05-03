import request from "./request";

export function getSubmissionList(params) {
  return request({ url: "/admin/submissions", method: "get", params });
}

export function getSubmissionDetail(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}`, method: "get" });
}

export function createSubmission(data) {
  return request({ url: "/admin/submissions", method: "post", data });
}

export function reEvaluateSubmission(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}/re-evaluate`, method: "post" });
}

export function deleteSubmission(submissionId) {
  return request({ url: `/admin/submissions/${submissionId}`, method: "delete" });
}
