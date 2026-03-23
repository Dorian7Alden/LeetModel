import request from "./request";

// 帖子列表查询
export function getPostList(params) {
  return request({
    url: "/posts",
    method: "get",
    params, // 注意这里是 params（GET）
  });
}
