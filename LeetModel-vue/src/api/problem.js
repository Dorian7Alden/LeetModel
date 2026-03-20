import request from "./request";

// 查询题目列表（带分页+筛选）
export function getProblemList(params) {
  return request({
    url: "/problems",
    method: "get",
    params, // ⭐关键：GET参数必须用 params
  });
}
export function getProblemDetail(id) {
  return request({
    url: `/problems/${id}`,
    method: "get",
  });
}
