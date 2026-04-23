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

// 上传题目
export function uploadProblem(data) {
  return request({
    url: "/problems/upload",
    method: "post",
    headers: {
      "Content-Type": "multipart/form-data"
    },
    data,
  });
}
