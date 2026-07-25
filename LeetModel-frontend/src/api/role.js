import request from "./request";

export function getRoleList() {
  return request({ url: "/admin/roles", method: "get" });
}

export function getRoleDetail(roleId) {
  return request({ url: `/admin/roles/${roleId}`, method: "get" });
}

export function createRole(data) {
  return request({ url: "/admin/roles", method: "post", data });
}

export function updateRole(roleId, data) {
  return request({ url: `/admin/roles/${roleId}`, method: "put", data });
}

export function deleteRole(roleId) {
  return request({ url: `/admin/roles/${roleId}`, method: "delete" });
}

export function getRolePermissions(roleId) {
  return request({ url: `/admin/roles/${roleId}/permissions`, method: "get" });
}

export function assignRolePermissions(roleId, permissionIds) {
  return request({ url: `/admin/roles/${roleId}/permissions`, method: "put", data: { ids: permissionIds } });
}
