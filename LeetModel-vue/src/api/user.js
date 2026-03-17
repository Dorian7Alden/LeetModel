import request from "./request";

// 登录接口
export function login(data) {
  return request({
    url: "/auth/login",
    method: "post",
    data,
  });
}
// 注册接口
// 发送验证码
export function sendCode(email) {
  return request.post("/auth/send-code", {
    email: email,
  });
}
// 注册
export function register(data) {
  return request.post("/auth/register", data);
}
