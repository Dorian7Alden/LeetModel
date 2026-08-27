import axios from "axios";
import router from "@/router";
import { useUserStore } from "@/store/user";

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 30000,
  transformResponse: [
    (data) => {
      // 数据库主键使用 Long，超出 JS 安全整数范围时转成字符串，
      // 避免精度丢失导致路由跳转 / 接口拼接拿到错误 ID。
      if (typeof data !== "string" || !data) return data;
      try {
        return JSON.parse(data, (key, value) => {
          if (
            typeof value === "number" &&
            Number.isInteger(value) &&
            (value > Number.MAX_SAFE_INTEGER || value < Number.MIN_SAFE_INTEGER)
          ) {
            return String(value);
          }
          return value;
        });
      } catch {
        return data;
      }
    },
  ],
});

function parseErrorBody(body) {
  if (!body || typeof body !== "object") return { code: 0, message: "请求失败" };
  return {
    code: body.code,
    message: body.message || body.msg || "请求失败",
  };
}

function toLogin() {
  const userStore = useUserStore();
  userStore.clearSession();
  if (router.currentRoute.value.path !== "/login") {
    router.push("/login");
  }
}

// 请求拦截
service.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.satoken = token;
  }
  return config;
});

// 响应拦截
service.interceptors.response.use(
  (response) => {
    const body = response.data;

    // 业务成功（2xxxx）
    if (body && typeof body.code === "number" && body.code >= 20000 && body.code < 30000) {
      return body;
    }

    // 兜底处理携带业务错误码的情况（部分路径仍可能以 HTTP 200 返回错误体）。
    const { code, message } = parseErrorBody(body);
    if (code === 40101 || code === 40103) {
      toLogin();
      return Promise.reject(new Error(message || "请先登录"));
    }
    const error = new Error(message || "请求失败");
    error.code = code;
    error.data = body;
    return Promise.reject(error);
  },

  (error) => {
    const status = error.response?.status;
    const body = error.response?.data;
    const { code, message } = parseErrorBody(body);

    if (status === 401 || code === 40101 || code === 40103) {
      toLogin();
    } else if (status === 403 || code === 40104) {
      const err = new Error(message || "没有权限执行该操作");
      err.code = code || 40104;
      return Promise.reject(err);
    }

    const messageText = message || (status ? `请求失败（${status}）` : error.message || "网络异常");
    const err = new Error(messageText);
    err.code = code || status || 0;
    err.data = body;
    return Promise.reject(err);
  },
);

export default service;
