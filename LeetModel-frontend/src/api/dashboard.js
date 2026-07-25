import request from "./request";

export function getDashboard() {
  return request({ url: "/admin/dashboard", method: "get" });
}
