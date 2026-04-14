import request from "./request";

// 管理端文件上传
export function uploadFile(file) {
  const formData = new FormData();
  formData.append("file", file);

  return request({
    url: "/admin/upload",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}
