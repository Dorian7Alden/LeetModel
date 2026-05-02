import request from "./request";

export function getPermissionList() {
  return request({ url: "/admin/permissions", method: "get" });
}

export function createPermission(data) {
  return request({ url: "/admin/permissions", method: "post", data });
}

export function updatePermission(permissionId, data) {
  return request({ url: `/admin/permissions/${permissionId}`, method: "put", data });
}

export function deletePermission(permissionId) {
  return request({ url: `/admin/permissions/${permissionId}`, method: "delete" });
}
