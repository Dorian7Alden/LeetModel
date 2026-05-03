import axios from "axios";
import router from "@/router";
import { useUserStore } from "@/store/user";

const service = axios.create({
  baseURL: "http://localhost:8080/api/v1",
  timeout: 5000,
});

// 请求拦截
service.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.token = token;
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
      console.warn("token 已失效: " + data.msg);
      userStore.logout();
      router.push("/login");
      return Promise.reject(new Error(data.msg || "登录已过期，请重新登录"));
    }

    if (data && data.code === 40300) {
      console.warn("权限不足: " + data.msg);
      return Promise.reject(new Error(data.msg || "权限不足，无法访问该资源"));
    }

    return data;
  },

  (error) => {
    const userStore = useUserStore();

    if (error.response) {
      const status = error.response.status;

      if (status === 401) {
        console.warn("HTTP 401 token 已过期");
        userStore.logout();
        router.push("/login");
      }
    }

    return Promise.reject(error);
  },
);

export default service;
