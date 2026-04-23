import request from "./request";

export function getTagsByCategory(categoryId) {
  return request({
    url: `/tags/category/${categoryId}`,
    method: "get",
  });
}

export function createTag(data) {
  return request({
    url: "/tags",
    method: "post",
    data,
  });
}

export function updateTag(tagId, data) {
  return request({
    url: `/tags/${tagId}`,
    method: "put",
    data,
  });
}

export function deleteTag(tagId) {
  return request({
    url: `/tags/${tagId}`,
    method: "delete",
  });
}
