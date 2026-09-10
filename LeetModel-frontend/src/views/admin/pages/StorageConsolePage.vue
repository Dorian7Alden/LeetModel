<template>
  <el-card shadow="never" class="storage-console-card">
    <div class="toolbar">
      <div>
        <h2 class="panel-title">MinIO 存储桶管理</h2>
        <p class="panel-subtitle">按对象前缀分组查看文件，支持手动上传、下载、复制临时链接与谨慎删除。</p>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="loading" @click="loadData">刷新对账</el-button>
        <el-button type="primary" @click="openUploadDialog">
          <el-icon><Upload /></el-icon> 手动上传文件
        </el-button>
      </div>
    </div>

    <div class="storage-stat-grid">
      <div class="stat-box">
        <div class="stat-num">{{ totalObjects }}</div>
        <div class="stat-label">存储对象总数</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">{{ formatFileSize(totalBytes) }}</div>
        <div class="stat-label">占用物理容量</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">{{ storageGroupCount }}</div>
        <div class="stat-label">一级前缀分组</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">{{ identifiedReferenceCount }}</div>
        <div class="stat-label">已识别题目附件</div>
      </div>
    </div>

    <div class="storage-workspace">
      <aside class="storage-group-panel">
        <div class="group-panel-heading">
          <span>对象分组</span>
          <small>按一级前缀聚合</small>
        </div>
        <button
          v-for="group in storageGroups"
          :key="group.key"
          type="button"
          class="storage-group-item"
          :class="{ active: activeGroup === group.key }"
          @click="activeGroup = group.key"
        >
          <span class="group-name"><el-icon><Folder /></el-icon>{{ group.label }}</span>
          <span class="group-meta">{{ group.count }} 个 · {{ formatFileSize(group.size) }}</span>
        </button>
      </aside>

      <section class="storage-object-panel">
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件路径、名称或关联题目..."
            clearable
            prefix-icon="Search"
          />
          <span class="result-count">当前显示 {{ visibleObjects.length }} 个对象</span>
        </div>

        <div class="reference-boundary-tip">
          “未识别引用”只表示没有匹配到题目附件记录，不代表头像、论文等其他服务一定没有使用。
        </div>

        <el-table :data="visibleObjects" v-loading="loading" stripe style="width: 100%" class="storage-table">
          <el-table-column label="分组" width="110">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ getGroupLabel(getObjectGroup(row.objectKey)) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="文件路径 (Object Key)" min-width="280">
            <template #default="{ row }">
              <div class="object-key-cell">
                <el-icon class="object-icon"><Document /></el-icon>
                <span class="object-key-text" :title="row.objectKey">{{ row.objectKey }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="修改时间" width="160">
            <template #default="{ row }">{{ formatTime(row.lastModified) }}</template>
          </el-table-column>
          <el-table-column label="已识别引用" min-width="210">
            <template #default="{ row }">
              <div v-if="row.refCount > 0" class="ref-status in-use">
                <el-tag type="success" size="small" effect="plain">题目附件</el-tag>
                <span class="ref-problem" :title="row.refProblemTitle">{{ row.refProblemTitle || '#' + row.refProblemId }}</span>
              </div>
              <div v-else class="ref-status untracked">
                <el-tag type="info" size="small" effect="plain">未识别引用</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <a v-if="row.downloadUrl" :href="row.downloadUrl" target="_blank" rel="noopener noreferrer" class="table-action-btn">下载</a>
              <el-button v-if="row.downloadUrl" link size="small" @click="copyObjectLink(row.downloadUrl)">复制临时链接</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无匹配的存储对象" /></template>
        </el-table>
      </section>
    </div>

    <!-- 手动上传文件对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="手动上传文件至存储桶" width="520px" append-to-body destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="存储前缀">
          <el-select v-model="uploadPrefix" filterable allow-create default-first-option style="width: 100%" placeholder="选择或输入前缀">
            <el-option v-for="group in uploadGroupOptions" :key="group.key" :label="group.label" :value="group.key" />
          </el-select>
          <span class="form-tip">MinIO 分组是对象 Key 的前缀，可输入如 manual/images；仅支持字母、数字、/、_ 和 -</span>
        </el-form-item>
        <el-form-item label="选择文件">
          <input type="file" ref="fileRef" accept=".txt,.md,.csv,.pdf,.doc,.docx,.xlsx,.jpg,.jpeg,.png,.gif,.webp,.zip,.rar,.7z,.tar,.gz,.tgz,.bz2,.xz" @change="onManualFileChange" />
          <div v-if="selectedFile" class="file-chosen-tip">
            已选: <strong>{{ selectedFile.name }}</strong> ({{ formatFileSize(selectedFile.size) }})
          </div>
        </el-form-item>
      </el-form>
      <div class="upload-link-tip">上传后生成的是有时效的 MinIO 预签名链接，可随时在列表中重新复制。</div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="submitManualUpload">
          确认上传
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { Document, Folder, Upload } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAdminStorageObjects,
  uploadAdminStorageObject,
  deleteAdminStorageObject,
} from "@/api/storage";

const objects = ref([]);
const loading = ref(false);
const searchKeyword = ref("");
const activeGroup = ref("all");

const uploadDialogVisible = ref(false);
const uploadPrefix = ref("manual");
const selectedFile = ref(null);
const uploading = ref(false);
const fileRef = ref(null);

const totalObjects = computed(() => objects.value.length);
const totalBytes = computed(() => objects.value.reduce((acc, cur) => acc + (cur.fileSize || 0), 0));
const identifiedReferenceCount = computed(() => objects.value.filter(object => object.refCount > 0).length);

const groupLabels = {
  avatars: "用户头像",
  manual: "手动上传",
  problems: "题目资源",
  "submission-uploads": "论文上传分片",
  submissions: "论文文件",
  root: "根目录",
};

const getObjectGroup = (objectKey) => {
  if (!objectKey || !objectKey.includes("/")) return "root";
  return objectKey.slice(0, objectKey.indexOf("/")) || "root";
};

const getGroupLabel = (groupKey) => groupLabels[groupKey] || groupKey;

const storageGroups = computed(() => {
  const groups = new Map();
  objects.value.forEach((object) => {
    const key = getObjectGroup(object.objectKey);
    const current = groups.get(key) || { key, label: getGroupLabel(key), count: 0, size: 0 };
    current.count += 1;
    current.size += object.fileSize || 0;
    groups.set(key, current);
  });

  const children = Array.from(groups.values()).sort((first, second) => first.label.localeCompare(second.label, "zh-CN"));
  return [
    { key: "all", label: "全部对象", count: totalObjects.value, size: totalBytes.value },
    ...children,
  ];
});

const storageGroupCount = computed(() => Math.max(storageGroups.value.length - 1, 0));
const uploadGroupOptions = computed(() => {
  const availableGroups = storageGroups.value.filter(group => !["all", "root"].includes(group.key));
  if (availableGroups.some(group => group.key === "manual")) return availableGroups;
  return [{ key: "manual", label: "手动上传" }, ...availableGroups];
});
const visibleObjects = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  return objects.value.filter((object) => {
    const matchesGroup = activeGroup.value === "all" || getObjectGroup(object.objectKey) === activeGroup.value;
    if (!matchesGroup) return false;
    if (!keyword) return true;

    const searchableText = [object.objectKey, object.fileName, object.refProblemTitle]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();
    return searchableText.includes(keyword);
  });
});

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
}

function formatTime(val) {
  if (!val) return "—";
  return String(val).replace("T", " ").slice(0, 16);
}

async function loadData() {
  loading.value = true;
  try {
    const res = await getAdminStorageObjects({});
    objects.value = res.data || [];
  } catch (error) {
    ElMessage.error(error.message || "获取存储对象清单失败");
  } finally {
    loading.value = false;
  }
}

function openUploadDialog() {
  selectedFile.value = null;
  uploadPrefix.value = ["all", "root"].includes(activeGroup.value) ? "manual" : activeGroup.value;
  uploadDialogVisible.value = true;
}

function onManualFileChange(e) {
  const files = e.target.files;
  if (files && files[0]) {
    selectedFile.value = files[0];
  }
}

async function submitManualUpload() {
  if (!selectedFile.value) return;
  uploading.value = true;
  try {
    const targetPrefix = uploadPrefix.value.trim() || "manual";
    await uploadAdminStorageObject(selectedFile.value, targetPrefix);
    ElMessage.success("文件已成功上传至存储桶");
    uploadDialogVisible.value = false;
    await loadData();
    activeGroup.value = getObjectGroup(`${targetPrefix}/placeholder`);
  } catch (error) {
    ElMessage.error(error.message || "上传失败");
  } finally {
    uploading.value = false;
  }
}

async function copyObjectLink(downloadUrl) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(downloadUrl);
    } else {
      const temporaryInput = document.createElement("textarea");
      temporaryInput.value = downloadUrl;
      temporaryInput.style.position = "fixed";
      temporaryInput.style.opacity = "0";
      document.body.appendChild(temporaryInput);
      temporaryInput.select();
      const copied = document.execCommand("copy");
      document.body.removeChild(temporaryInput);
      if (!copied) throw new Error("copy failed");
    }
    ElMessage.success("临时下载链接已复制");
  } catch {
    ElMessage.warning("浏览器未允许复制，请通过下载入口手动复制链接");
  }
}

async function handleDelete(row) {
  // 1. 若文件被题目引用：直接阻断并弹出告警
  if (row.refCount > 0) {
    const targetTitle = row.refProblemTitle || `#${row.refProblemId}`;
    ElMessageBox.alert(
      `该文件当前正被题目【${targetTitle}】作为附件引用，禁止直接删除！如确需清理，请先前往对应题目解绑附件。`,
      "防误删保护提醒",
      {
        confirmButtonText: "我知道了",
        type: "warning",
      }
    );
    return;
  }

  // 2. 未识别题目附件引用时，提示跨服务引用边界并由管理员二次确认
  try {
    await ElMessageBox.confirm(
      `确定要物理删除「${row.objectKey}」吗？当前只能确认它未被题目附件引用，无法排除头像、论文等其他服务仍在使用；删除后不可恢复。`,
      "存储对象删除确认",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "danger",
      }
    );
    await deleteAdminStorageObject(row.objectKey);
    ElMessage.success("存储对象已物理删除");
    await loadData();
  } catch (err) {
    if (err !== "cancel") {
      ElMessage.error(err.message || "删除失败");
    }
  }
}

onMounted(loadData);
</script>

<style scoped>
.storage-console-card {
  border: none;
  box-shadow: none;
}
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.panel-title { margin: 0; font-size: 18px; }
.panel-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }

/* 统计卡片网格 */
.storage-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-box {
  padding: 12px 16px;
  background: #fafafa;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  text-align: center;
}
.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
}
.stat-label {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 4px;
}

.storage-workspace {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.storage-group-panel {
  padding: 10px;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  background: #fafafa;
}
.group-panel-heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 6px 10px;
  color: var(--lm-text-primary);
  font-size: 13px;
  font-weight: 600;
}
.group-panel-heading small {
  color: var(--lm-text-muted);
  font-size: 11px;
  font-weight: 400;
}
.storage-group-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  gap: 3px;
  padding: 9px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--lm-text-secondary);
  cursor: pointer;
  text-align: left;
}
.storage-group-item:hover {
  background: #f1f5f9;
}
.storage-group-item.active {
  background: color-mix(in srgb, var(--lm-primary) 10%, white);
  color: var(--lm-primary);
}
.group-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  word-break: break-all;
}
.group-meta {
  padding-left: 20px;
  color: var(--lm-text-muted);
  font-size: 10.5px;
}
.storage-object-panel {
  min-width: 0;
}

/* 过滤栏 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
}
.filter-bar .el-input {
  max-width: 420px;
}
.result-count {
  flex-shrink: 0;
  color: var(--lm-text-muted);
  font-size: 11px;
}
.reference-boundary-tip {
  margin-bottom: 12px;
  padding: 9px 11px;
  border: 1px solid #fde68a;
  border-radius: 6px;
  background: #fffbeb;
  color: #92400e;
  font-size: 11.5px;
  line-height: 1.6;
}

/* 表格细节 */
.object-key-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.object-icon {
  color: var(--lm-primary);
  flex-shrink: 0;
}
.object-key-text {
  font-family: var(--lm-code-font-family);
  font-size: 11.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ref-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
}
.ref-problem {
  color: var(--lm-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.table-action-btn {
  font-size: 12px;
  color: var(--lm-primary);
  text-decoration: none;
  margin-right: 12px;
}
.table-action-btn:hover {
  text-decoration: underline;
}
.form-tip {
  display: block;
  width: 100%;
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 4px;
  line-height: 1.5;
}
.file-chosen-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}
.upload-link-tip {
  padding: 9px 11px;
  border-radius: 6px;
  background: #f8fafc;
  color: var(--lm-text-muted);
  font-size: 11.5px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .storage-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .storage-workspace {
    grid-template-columns: 1fr;
  }
  .storage-group-panel {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
  }
  .group-panel-heading {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .toolbar,
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }
  .toolbar-actions {
    flex-wrap: wrap;
  }
  .filter-bar .el-input {
    max-width: none;
  }
}
</style>
