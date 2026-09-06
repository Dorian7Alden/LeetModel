import request from "./request";

/**
 * 查询知识库大纲目录与多维标签树
 */
export function getKnowledgeTree() {
  return request({
    url: "/api/admin/knowledge/tree",
    method: "get",
  });
}

/**
 * 打包导出知识库自包含 ZIP
 */
export function exportKnowledgeZip() {
  return request({
    url: "/api/admin/knowledge/export",
    method: "get",
    responseType: "blob",
  });
}

/**
 * 上传 ZIP 自包含知识包并无损解析导入
 */
export function importKnowledgeZip(formData) {
  return request({
    url: "/api/admin/knowledge/import",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

/**
 * 查询当前物理索引状态
 */
export function getKnowledgeIndexStatus() {
  return request({
    url: "/api/admin/knowledge/index/status",
    method: "get",
  });
}

/**
 * 手动触发物理索引全量蓝绿重建
 */
export function rebuildKnowledgeIndex() {
  return request({
    url: "/api/admin/knowledge/index/rebuild",
    method: "post",
  });
}
