import request from "./request";

export function getAdminAiCalls(params) {
  return request({ url: "/admin/ai/calls", method: "get", params });
}

export function getAdminAiCallPage(params) {
  return request({ url: "/admin/ai/calls/page", method: "get", params });
}

export function getAdminAiCallStats(params) {
  return request({ url: "/admin/ai/calls/stats", method: "get", params });
}

export function getAdminAiModelStats(params) {
  return request({ url: "/admin/ai/calls/model-stats", method: "get", params });
}

export function getAdminAiCallFilterOptions() {
  return request({ url: "/admin/ai/calls/filter-options", method: "get" });
}

export function getAdminProviderModels(provider = "NEW_API") {
  return request({ url: `/admin/ai/models/${provider}`, method: "get" });
}

export function getAdminAiQueue(params) {
  return request({ url: "/admin/ai/queue", method: "get", params });
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

export function estimateEvaluation(data) {
  return request({ url: "/admin/ai/evaluations/estimates", method: "post", data });
}

export function getEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}`, method: "get" });
}

export function retryEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}/retry`, method: "post" });
}

export function pauseEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}/pause`, method: "post" });
}

export function resumeEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}/resume`, method: "post" });
}

export function cancelEvaluationTask(taskId) {
  return request({ url: `/admin/ai/evaluations/tasks/${taskId}/cancel`, method: "post" });
}

export function listEvaluationWeightSchemes(params = {}) {
  return request({ url: "/admin/ai/evaluations/weight-schemes", method: "get", params });
}

export function recalculateEvaluationScore(taskId, weightSchemeId) {
  return request({
    url: `/admin/ai/evaluations/tasks/${taskId}/score-results/recalculate`,
    method: "post",
    data: { weightSchemeId },
  });
}

export function compareEvaluation(datasetId, repeatCount) {
  return request({
    url: "/admin/ai/evaluations/comparisons",
    method: "get",
    params: { datasetId, repeatCount },
  });
}

export function listAssistantProductionWorkflows() {
  return request({ url: "/admin/ai/assistant/production/workflows", method: "get" });
}

export function getAssistantProductionCurrent() {
  return request({ url: "/admin/ai/assistant/production/current", method: "get" });
}

export function listAssistantProductionConfigs(limit = 20) {
  return request({
    url: "/admin/ai/assistant/production/configs",
    method: "get",
    params: { limit },
  });
}

export function listAssistantProductionAudits(limit = 20) {
  return request({
    url: "/admin/ai/assistant/production/audits",
    method: "get",
    params: { limit },
  });
}

export function previewAssistantProductionChange(data) {
  return request({
    url: "/admin/ai/assistant/production/changes/preview",
    method: "post",
    data,
  });
}

export function applyAssistantProductionChange(changeRequestId) {
  return request({
    url: "/admin/ai/assistant/production/changes/apply",
    method: "post",
    data: { changeRequestId },
  });
}
