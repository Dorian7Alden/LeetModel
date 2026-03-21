import request from "./request";

// 登录接口
export function login(data) {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
}
// 发送验证码
export function sendCode(email) {
  return request.post("/auth/verification-codes", {
    target: email,
  });
}
// 注册
export function register(data) {
  return request.post("/auth/register", data);
}
// 更新用户信息（按ID）
export function updateUser(userId, data) {
  return request.put(`/users/${userId}`, data);
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
  const token = localStorage.getItem("token");

  return request.post("/auth/logout", {
    token: token,
  });
}
// // ✅ 退出登录
// export function logout(data) {
//   return request({
//     url: "/auth/logout",
//     method: "post",
//     data, // ✅ 必须有
//   });
// }
