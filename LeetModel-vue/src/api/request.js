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

// ⭐ 响应拦截（核心修复点）
service.interceptors.response.use(
  (response) => response.data,

  (error) => {
    const userStore = useUserStore();

    if (error.response) {
      const status = error.response.status;

      // ⭐ token 失效
      if (status === 401) {
        console.warn("token 已过期");

        // 清空登录状态
        userStore.logout();

        // 跳转登录页
        router.push("/login");

        alert("登录已过期，请重新登录");
      }
    }

    return Promise.reject(error);
  },
);

export default service;
