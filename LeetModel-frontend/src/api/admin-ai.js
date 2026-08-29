import request from "./request";

export function getAdminAiCalls(params) {
  return request({ url: "/admin/ai/calls", method: "get", params });
}

export function getAdminAiCallStats() {
  return request({ url: "/admin/ai/calls/stats", method: "get" });
}

export function listEvaluationDatasets() {
  return request({ url: "/admin/ai/evaluations/datasets", method: "get" });
}

export function listEvaluationFeatures() {
  return request({ url: "/admin/ai/evaluations/features", method: "get" });
}

export function createEvaluationDataset(data) {
  return request({ url: "/admin/ai/evaluations/datasets", method: "post", data });
}

export function listEvaluationTasks(limit = 20) {
  return request({ url: "/admin/ai/evaluations/tasks", method: "get", params: { limit } });
}

export function createEvaluationTask(data) {
  return request({ url: "/admin/ai/evaluations/tasks", method: "post", data });
}

export function getEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}`, method: "get" });
}

export function retryEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}/retry`, method: "post" });
}

export function compareEvaluation(datasetId, repeatCount) {
  return request({
    url: "/admin/ai/evaluations/comparisons",
    method: "get",
    params: { datasetId, repeatCount },
  });
}
