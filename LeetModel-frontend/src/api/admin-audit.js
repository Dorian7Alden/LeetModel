import request from "./request";

export function searchAdminAudit(params = {}) {
  return request({ url: "/admin/audit/events", method: "get", params });
}

export function getAdminAuditRetentionPolicy() {
  return request({ url: "/admin/audit/retention-policy", method: "get" });
}
