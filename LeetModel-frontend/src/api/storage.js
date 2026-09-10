import request from "./request";

/**
 * 查询存储桶对象列表与当前服务可识别的题目附件引用。
 *
 * @param {Object} params 查询参数，包含 keyword、onlyOrphans
 */
export function getAdminStorageObjects(params) {
  return request({
    url: "/admin/content/storage/objects",
    method: "get",
    params,
  });
}

/**
 * 管理员手动上传文件至存储桶。
 *
 * @param {File} file 本地文件
 * @param {string} prefix 存储前缀路径，默认为 manual
 */
export function uploadAdminStorageObject(file, prefix = "manual") {
  const formData = new FormData();
  formData.append("file", file);
  return request({
    url: `/admin/content/storage/objects?prefix=${encodeURIComponent(prefix)}`,
    method: "post",
    data: formData,
    headers: { "Content-Type": "multipart/form-data" },
  });
}

/**
 * 安全删除存储桶对象（带依赖强检查）。
 *
 * @param {string} objectKey 对象存储路径
 */
export function deleteAdminStorageObject(objectKey) {
  return request({
    url: "/admin/content/storage/objects",
    method: "delete",
    params: { objectKey },
  });
}
