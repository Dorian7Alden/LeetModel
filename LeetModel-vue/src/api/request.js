import axios from "axios";

const service = axios.create({
  baseURL: "http://localhost:8080/api/v1",
  timeout: 5000,
});

service.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.token = token; // ⭐ 改这里
  }

  return config;
});

service.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error),
);

export default service;
