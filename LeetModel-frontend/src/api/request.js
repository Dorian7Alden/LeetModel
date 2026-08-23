import axios from "axios";
import router from "@/router";
import { useUserStore } from "@/store/user";

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
  timeout: 5000,
});

// 请求拦截
service.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.satoken = token;
  }

  return config;
});

// ⭐ 响应拦截
service.interceptors.response.use(
  (response) => {
    const data = response.data;
    const userStore = useUserStore();

    // 后端始终返回 HTTP 200，通过 body.code 标识错误
    if (data && (data.code === 40100 || data.code === 40101)) {
      console.warn("token 已失效: " + data.message);
      userStore.clearSession();
      router.push("/login");
      return Promise.reject(new Error(data.message || "登录已过期，请重新登录"));
    }

    if (data && data.code === 40300) {
      console.warn("权限不足: " + data.message);
      return Promise.reject(new Error(data.message || "权限不足，无法访问该资源"));
    }

    if (data && (data.code < 20000 || data.code >= 30000)) {
      const error = new Error(data.message || "请求失败");
      error.code = data.code;
      error.data = data;
      return Promise.reject(error);
    }

    return data;
  },

  (error) => {
    const userStore = useUserStore();

    if (error.response) {
      const status = error.response.status;

      if (status === 401) {
        console.warn("HTTP 401 token 已过期");
        userStore.clearSession();
        router.push("/login");
      }
    }

    return Promise.reject(error);
  },
);

export default service;
