import request from "./request";

/**
 * 查询文件资产分页、分组与容量统计。
 *
 * @param {Object} params 查询参数，包含 keyword、onlyOrphans
 */
export function getAdminStorageObjects(params) {
  return request({
    url: "/admin/content/files",
    method: "get",
    params,
  });
}

/**
 * 管理员手动上传文件至存储桶。
 *
 * @param {File} file 本地文件
 * @param {string} groupPath manual 命名空间下的逻辑分组
 */
export function uploadAdminStorageObject(file, groupPath = "", onUploadProgress) {
  const formData = new FormData();
  formData.append("file", file);
  return request({
    url: "/admin/content/files",
    method: "post",
    data: formData,
    params: { groupPath },
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress,
  });
}

/**
 * 为文件资产生成新的临时访问链接。
 *
 * @param {string} id 文件资产 ID
 */
export function createAdminStorageAccessUrl(id) {
  return request({
    url: `/admin/content/files/${id}/access-url`,
    method: "post",
  });
}

/** 为文件资产生成预览用的短时访问链接。 */
export function createAdminStoragePreviewUrl(id) {
  return request({
    url: `/admin/content/files/${id}/preview-url`,
    method: "post",
  });
}

/** 对手动上传资产发起逻辑删除。 */
export function deleteAdminStorageObject(id) {
  return request({
    url: `/admin/content/files/${id}`,
    method: "delete",
  });
}

/** 扫描存储桶并登记尚未纳管的历史对象。 */
export function reconcileAdminStorageObjects() {
  return request({
    url: "/admin/content/files/reconcile",
    method: "post",
  });
}
