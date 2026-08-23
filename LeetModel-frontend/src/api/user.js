import request from "./request";

// 登录接口
export function login(data) {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
}
// 注册
export function register(data) {
  return request.post("/auth/register", data);
}

// 获取当前登录用户
export function getCurrentUser() {
  return request.get("/users/me");
}

// 获取当前用户角色和权限
export function getCurrentAuthorization() {
  return request.get("/users/me/authorization");
}

// 更新当前登录用户
export function updateCurrentUser(data) {
  return request.put("/users/me", data);
}

// 修改当前登录用户密码
export function changePassword(data) {
  return request.put("/users/me/password", data);
}

// 上传当前登录用户头像
export function uploadCurrentAvatar(file) {
  const formData = new FormData();
  formData.append("file", file);
  return request({
    url: "/users/me/avatar",
    method: "post",
    data: formData,
  });
}

// 更新用户信息（按ID）
export function updateUser(userId, data) {
  return request.put(`/users/${userId}`, data);
}
// 上传用户头像
export function uploadAvatar(userId, file) {
  const formData = new FormData();
  formData.append("file", file);
  return request({
    url: `/users/${userId}/avatar`,
    method: "post",
    data: formData,
    headers: { "Content-Type": "multipart/form-data" },
  });
}
// 删除用户
export function deleteUser(userId) {
  return request.delete(`/users/${userId}`);
}

// ✅ 重置密码
export function resetPassword(data) {
  return request({
    url: "/auth/reset-password",
    method: "post",
    data,
  });
}

/**
 * 退出登录
 */
export function logout() {
  return request.post("/auth/logout");
}
export function getAllUsers() {
  return request({ url: "/admin/users", method: "get" });
}

export function getUserRoles(userId) {
  return request({ url: `/admin/users/${userId}/roles`, method: "get" });
}

export function assignUserRoles(userId, roleIds) {
  return request({ url: `/admin/users/${userId}/roles`, method: "put", data: { ids: roleIds } });
}
