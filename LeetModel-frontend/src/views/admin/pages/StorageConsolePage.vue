<template>
  <el-card shadow="never" class="storage-console-card">
    <div class="toolbar">
      <div>
        <span class="eyebrow">FILE ASSET WORKSPACE</span>
        <h2 class="panel-title">文件资产</h2>
        <p class="panel-subtitle">文件名用于识别，文件 ID 用于引用，MinIO 对象路径仅在技术详情中展示。</p>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="reconciling" @click="reconcile"><el-icon><RefreshRight /></el-icon>盘点历史</el-button>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
        <el-button type="primary" @click="openUploadDialog"><el-icon><Upload /></el-icon>上传文件</el-button>
      </div>
    </div>

    <div class="storage-stat-grid">
      <div class="stat-box"><span class="stat-label">纳管文件</span><strong>{{ summary.totalFiles }}</strong></div>
      <div class="stat-box"><span class="stat-label">登记容量</span><strong>{{ formatFileSize(summary.totalBytes) }}</strong></div>
      <div class="stat-box"><span class="stat-label">手动上传</span><strong>{{ summary.manualFiles }}</strong></div>
      <div class="stat-box"><span class="stat-label">历史盘点（只读）</span><strong>{{ summary.discoveredFiles }}</strong></div>
    </div>

    <div class="storage-workspace">
      <aside class="storage-group-panel">
        <div class="group-panel-heading">
          <span>分组</span>
          <small>按逻辑分组浏览，不暴露物理路径</small>
        </div>
        <button
          v-for="group in groups"
          :key="group.filterKey"
          type="button"
          class="storage-group-item"
          :class="{ active: activeGroup === group.filterKey }"
          @click="selectGroup(group.filterKey)"
        >
          <span class="group-name"><el-icon><Folder /></el-icon><span>{{ groupLabel(group.groupPath) }}</span></span>
          <span class="group-meta">{{ group.fileCount }} 个 · {{ formatFileSize(group.totalBytes) }}</span>
        </button>
      </aside>

      <section class="storage-object-panel">
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名、文件 ID 或扩展名"
            clearable
            prefix-icon="Search"
            @keyup.enter="search"
            @clear="search"
          />
          <el-button @click="search">搜索</el-button>
        </div>

        <div class="reference-boundary-tip">
          <el-icon><WarningFilled /></el-icon>
          <span>历史盘点文件为只读；手动上传文件删除后会先进入宽限期，后台再执行物理清理。</span>
        </div>

        <el-table :data="assets" v-loading="loading" stripe style="width: 100%" class="storage-table">
          <el-table-column label="文件" min-width="280">
            <template #default="{ row }">
              <div class="file-cell">
                <div class="file-type-icon" :class="previewTypeClass(row)">
                  <el-icon><component :is="fileIcon(row)" /></el-icon>
                </div>
                <div class="file-main">
                  <button type="button" class="file-name-button" :title="row.originalName" @click="openPreview(row)">
                    {{ row.originalName || "未命名文件" }}
                  </button>
                  <span class="file-meta">{{ fileTypeLabel(row) }} · {{ formatFileSize(row.fileSize) }}</span>
                  <span class="file-id">文件 ID：{{ row.id }} <button type="button" class="inline-copy" @click.stop="copyFileId(row)">复制</button></span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="分组" min-width="130">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ groupLabel(row.groupPath) }}</el-tag></template>
          </el-table-column>

          <el-table-column label="来源 / 状态" min-width="170">
            <template #default="{ row }">
              <div class="state-cell">
                <el-tag :type="row.sourceType === 'MANUAL' ? 'primary' : 'info'" size="small">{{ sourceLabel(row.sourceType) }}</el-tag>
                <span :class="statusClass(row)">{{ statusLabel(row.lifecycleStatus) }}</span>
              </div>
              <small v-if="row.lifecycleStatus === 'PENDING_DELETE' && row.cleanupAfter">预计 {{ formatTime(row.cleanupAfter) }} 清理</small>
              <small v-else-if="row.lifecycleStatus === 'DELETE_FAILED'">清理失败，将自动重试</small>
              <small v-else-if="row.sourceType !== 'MANUAL'">历史对象不可直接删除</small>
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="155">
            <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
          </el-table-column>

          <el-table-column label="操作" width="270" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="openPreview(row)"><el-icon><View /></el-icon>预览</el-button>
              <el-button link size="small" @click="downloadFile(row)"><el-icon><Download /></el-icon>下载</el-button>
              <el-button link size="small" @click="copyFileId(row)"><el-icon><CopyDocument /></el-icon>复制 ID</el-button>
              <el-dropdown @command="command => handleCommand(command, row)">
                <el-button link size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy-link">复制临时链接</el-dropdown-item>
                    <el-dropdown-item command="details">技术详情</el-dropdown-item>
                    <el-dropdown-item command="delete" divided :disabled="!row.deletable">申请删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>

          <template #empty><el-empty description="暂无匹配的文件资产" /></template>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadData"
            @size-change="changePageSize"
          />
        </div>
      </section>
    </div>

    <el-drawer v-model="previewVisible" :title="previewAsset?.originalName || '文件预览'" size="min(680px, 88vw)" destroy-on-close>
      <template v-if="previewAsset">
        <div class="preview-header">
          <div class="preview-file-icon" :class="previewTypeClass(previewAsset)">
            <el-icon><component :is="fileIcon(previewAsset)" /></el-icon>
          </div>
          <div>
            <strong>{{ previewAsset.originalName }}</strong>
            <span>{{ fileTypeLabel(previewAsset) }} · {{ formatFileSize(previewAsset.fileSize) }} · 文件 ID：{{ previewAsset.id }}</span>
          </div>
        </div>
        <div class="preview-actions">
          <el-button size="small" @click="copyFileId(previewAsset)">复制文件 ID</el-button>
          <el-button size="small" @click="copyLink(previewAsset)">复制临时链接</el-button>
          <el-button size="small" @click="downloadFile(previewAsset)">下载文件</el-button>
        </div>
        <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" show-icon />
        <div v-else-if="previewLoading" class="preview-placeholder">正在生成预览…</div>
        <div v-else-if="previewType(previewAsset) === 'IMAGE'" class="image-preview">
          <img :src="previewUrl" :alt="previewAsset.originalName" />
        </div>
        <iframe v-else-if="previewType(previewAsset) === 'PDF'" class="pdf-preview" :src="previewUrl" :title="previewAsset.originalName"></iframe>
        <div v-else-if="previewType(previewAsset) === 'TEXT'" class="text-preview"><pre>{{ previewText }}</pre></div>
        <div v-else-if="previewType(previewAsset) === 'MARKDOWN'" class="markdown-preview">
          <div class="preview-mode-switch">
            <el-radio-group v-model="previewMode" size="small">
              <el-radio-button label="rendered">渲染预览</el-radio-button>
              <el-radio-button label="source">原文</el-radio-button>
            </el-radio-group>
          </div>
          <article v-if="previewMode === 'rendered'" class="markdown-body" v-html="previewHtml"></article>
          <pre v-else>{{ previewText }}</pre>
        </div>
        <div v-else class="unsupported-preview">
          <el-icon :size="34"><Document /></el-icon>
          <strong>{{ previewType(previewAsset) === 'ARCHIVE' ? '暂不支持在线查看压缩包内容' : '此文件暂不支持在线预览' }}</strong>
          <span>{{ fileTypeLabel(previewAsset) }} · {{ formatFileSize(previewAsset.fileSize) }}</span>
          <el-button type="primary" plain @click="downloadFile(previewAsset)">下载文件</el-button>
        </div>

        <el-collapse class="technical-details">
          <el-collapse-item title="查看技术详情" name="technical">
            <dl>
              <div><dt>文件 ID</dt><dd>{{ previewAsset.id }}</dd></div>
              <div><dt>文件名</dt><dd>{{ previewAsset.originalName }}</dd></div>
              <div><dt>来源</dt><dd>{{ sourceLabel(previewAsset.sourceType) }}</dd></div>
              <div><dt>分组</dt><dd>{{ groupLabel(previewAsset.groupPath) }}</dd></div>
              <div><dt>Bucket</dt><dd>{{ previewAsset.bucketName || "—" }}</dd></div>
              <div><dt>Object Key</dt><dd class="technical-value">{{ previewAsset.objectKey || "—" }}</dd></div>
              <div><dt>生命周期</dt><dd>{{ statusLabel(previewAsset.lifecycleStatus) }}</dd></div>
            </dl>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>

    <el-drawer v-model="uploadDialogVisible" title="上传文件" size="min(620px, 92vw)" destroy-on-close>
      <div class="upload-intro">先选择目标分组，再添加文件。文件名会原样保存，系统用文件 ID 区分同名文件。</div>
      <el-form label-position="top">
        <el-form-item label="保存到分组">
          <div class="group-picker">
            <el-select v-model="existingGroup" :disabled="useCustomGroup" placeholder="选择已有分组" class="group-select">
              <el-option label="未分组" value="" />
              <el-option v-for="group in uploadGroups" :key="group" :label="groupLabel(group)" :value="group" />
            </el-select>
            <el-button @click="useCustomGroup = !useCustomGroup">{{ useCustomGroup ? "选择已有分组" : "新建分组" }}</el-button>
          </div>
          <el-input v-if="useCustomGroup" v-model="customGroup" maxlength="128" placeholder="例如 campaign/2026" @blur="validateGroup" />
          <div class="group-breadcrumb">当前路径：manual / {{ targetGroup || "未分组" }}</div>
          <span v-if="groupError" class="validation-error">{{ groupError }}</span>
        </el-form-item>

        <el-form-item label="选择文件">
          <div class="drop-zone" :class="{ 'is-dragover': dragover }" @dragover.prevent="dragover = true" @dragleave.prevent="dragover = false" @drop.prevent="onDrop">
            <el-icon :size="28"><Upload /></el-icon>
            <strong>拖拽文件到这里</strong>
            <span>或点击选择文件</span>
            <small>支持图片、PDF、文档、表格和压缩包，单文件最大 50 MB</small>
            <input ref="fileRef" type="file" multiple :accept="acceptedExtensions" @change="onFileChange" />
          </div>
        </el-form-item>
      </el-form>

      <div v-if="uploadQueue.length" class="upload-queue">
        <div v-for="item in uploadQueue" :key="item.id" class="upload-item">
          <div class="upload-item-icon" :class="previewTypeClass(item.file)"><el-icon><component :is="fileIcon(item.file)" /></el-icon></div>
          <div class="upload-item-main">
            <strong :title="item.file.name">{{ item.file.name }}</strong>
            <span>{{ fileTypeLabel(item.file) }} · {{ formatFileSize(item.file.size) }}</span>
            <el-progress v-if="item.status === 'uploading' || item.status === 'success'" :percentage="item.progress" :status="item.status === 'success' ? 'success' : undefined" :stroke-width="6" />
            <small v-if="item.status === 'error'" class="validation-error">{{ item.error }}</small>
          </div>
          <el-button v-if="item.status !== 'uploading'" link type="danger" @click="removeUploadItem(item.id)"><el-icon><Close /></el-icon></el-button>
        </div>
      </div>

      <div v-if="uploadQueue.length" class="upload-summary">
        <span>待处理 {{ pendingUploadCount }} 个</span>
        <span v-if="uploadErrorCount" class="validation-error">{{ uploadErrorCount }} 个失败，可重试</span>
      </div>

      <template #footer>
        <el-button @click="closeUploadDialog">取消</el-button>
        <el-button v-if="uploadErrorCount" @click="retryFailed">重试失败项</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!pendingUploadCount" @click="submitUpload">开始上传</el-button>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { renderSafeMarkdown } from "@/utils/markdown";
import { Document, Folder, Upload, RefreshRight, WarningFilled, View, Download, CopyDocument, Close, Picture } from "@element-plus/icons-vue";
import {
  createAdminStorageAccessUrl,
  createAdminStoragePreviewUrl,
  deleteAdminStorageObject,
  getAdminStorageObjects,
  reconcileAdminStorageObjects,
  uploadAdminStorageObject,
} from "@/api/storage";

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const acceptedExtensions = ".txt,.md,.markdown,.csv,.json,.xml,.yaml,.yml,.log,.pdf,.doc,.docx,.xlsx,.jpg,.jpeg,.png,.gif,.webp,.zip,.rar,.7z,.tar,.gz,.tgz,.bz2,.xz";
const allowedExtensions = new Set(acceptedExtensions.split(",").map(value => value.slice(1)));

const assets = ref([]);
const rawGroups = ref([]);
const summary = ref({ totalFiles: 0, totalBytes: 0, manualFiles: 0, discoveredFiles: 0 });
const loading = ref(false);
const reconciling = ref(false);
const searchKeyword = ref("");
const appliedKeyword = ref("");
const activeGroup = ref("__all__");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);

const previewVisible = ref(false);
const previewAsset = ref(null);
const previewUrl = ref("");
const previewText = ref("");
const previewMode = ref("rendered");
const previewLoading = ref(false);
const previewError = ref("");

const uploadDialogVisible = ref(false);
const existingGroup = ref("");
const customGroup = ref("");
const useCustomGroup = ref(false);
const groupError = ref("");
const uploadQueue = ref([]);
const uploading = ref(false);
const dragover = ref(false);
const fileRef = ref(null);

const groups = computed(() => [
  { groupPath: "__all__", filterKey: "__all__", fileCount: summary.value.totalFiles, totalBytes: summary.value.totalBytes },
  ...rawGroups.value.map(group => ({ ...group, filterKey: group.groupPath || "__ungrouped__" })),
]);
const uploadGroups = computed(() => rawGroups.value.map(group => group.groupPath).filter(Boolean).sort());
const targetGroup = computed(() => useCustomGroup.value ? customGroup.value.trim() : existingGroup.value);
const pendingUploadCount = computed(() => uploadQueue.value.filter(item => ["queued", "error"].includes(item.status)).length);
const uploadErrorCount = computed(() => uploadQueue.value.filter(item => item.status === "error").length);
const previewHtml = computed(() => renderSafeMarkdown(previewText.value));

function groupLabel(path) {
  if (path === "__all__") return "全部文件";
  return path || "未分组";
}

function sourceLabel(source) {
  return source === "MANUAL" ? "手动上传" : "历史盘点";
}

function statusLabel(status) {
  return { ACTIVE: "可用", DISCOVERED: "只读", PENDING_DELETE: "等待清理", DELETE_FAILED: "清理重试" }[status] || status || "未知";
}

function statusClass(row) {
  return row.lifecycleStatus === "DELETE_FAILED" ? "status-danger" : row.lifecycleStatus === "PENDING_DELETE" ? "status-warning" : "";
}

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${parseFloat((bytes / Math.pow(1024, index)).toFixed(1))} ${units[index]}`;
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function extensionOf(source) {
  const name = source?.originalName || source?.name || "";
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : "";
}

function previewType(row) {
  if (row?.previewType) return row.previewType;
  const extension = extensionOf(row);
  const contentType = row?.contentType || "";
  if (contentType.startsWith("image/") || ["jpg", "jpeg", "png", "gif", "webp"].includes(extension)) return "IMAGE";
  if (contentType === "application/pdf" || extension === "pdf") return "PDF";
  if (contentType === "text/markdown" || ["md", "markdown"].includes(extension)) return "MARKDOWN";
  if (contentType.startsWith("text/") || ["txt", "csv", "json", "xml", "yaml", "yml", "log"].includes(extension)) return "TEXT";
  if (["zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz"].includes(extension)) return "ARCHIVE";
  return "UNSUPPORTED";
}

function previewTypeClass(row) {
  return `preview-${previewType(row).toLowerCase()}`;
}

function fileIcon(row) {
  return previewType(row) === "IMAGE" ? Picture : Document;
}

function fileTypeLabel(row) {
  const extension = extensionOf(row);
  const labels = { IMAGE: "图片", PDF: "PDF 文档", TEXT: "文本", MARKDOWN: "Markdown", ARCHIVE: "压缩包", UNSUPPORTED: "文件" };
  const type = labels[previewType(row)] || "文件";
  return extension ? `${type} · ${extension.toUpperCase()}` : type;
}

async function loadData() {
  loading.value = true;
  try {
    const params = { page: page.value, size: pageSize.value };
    if (activeGroup.value !== "__all__") params.groupPath = activeGroup.value;
    if (appliedKeyword.value) params.keyword = appliedKeyword.value;
    const response = await getAdminStorageObjects(params);
    const data = response.data || {};
    assets.value = data.page?.rows || [];
    total.value = data.page?.total || 0;
    rawGroups.value = data.groups || [];
    summary.value = {
      totalFiles: data.totalFiles || 0,
      totalBytes: data.totalBytes || 0,
      manualFiles: data.manualFiles || 0,
      discoveredFiles: data.discoveredFiles || 0,
    };
  } catch (error) {
    ElMessage.error(error.message || "获取文件资产失败");
  } finally {
    loading.value = false;
  }
}

function selectGroup(groupPath) {
  activeGroup.value = groupPath;
  page.value = 1;
  loadData();
}

function search() {
  appliedKeyword.value = searchKeyword.value.trim();
  page.value = 1;
  loadData();
}

function changePageSize() {
  page.value = 1;
  loadData();
}

async function reconcile() {
  reconciling.value = true;
  try {
    const response = await reconcileAdminStorageObjects();
    const result = response.data || {};
    ElMessage.success(`盘点完成：扫描 ${result.scanned || 0} 个，新登记 ${result.created || 0} 个`);
    page.value = 1;
    await loadData();
  } catch (error) {
    ElMessage.error(error.message || "历史对象盘点失败");
  } finally {
    reconciling.value = false;
  }
}

function openUploadDialog() {
  existingGroup.value = activeGroup.value === "__all__" || activeGroup.value === "__ungrouped__" ? "" : activeGroup.value;
  customGroup.value = "";
  useCustomGroup.value = false;
  groupError.value = "";
  uploadQueue.value = [];
  uploadDialogVisible.value = true;
}

function closeUploadDialog() {
  if (uploading.value) return;
  uploadDialogVisible.value = false;
  uploadQueue.value = [];
  if (fileRef.value) fileRef.value.value = "";
}

function validateGroup() {
  const value = targetGroup.value;
  if (!value) {
    groupError.value = "";
    return true;
  }
  if (!/^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/.test(value) || value.length > 128) {
    groupError.value = "分组仅支持字母、数字、/、_、-，且长度不超过 128 个字符";
    return false;
  }
  groupError.value = "";
  return true;
}

function validateFile(file) {
  const extension = extensionOf(file);
  if (!allowedExtensions.has(extension)) return "暂不支持该文件格式";
  if (file.size > MAX_FILE_SIZE) return "文件超过 50 MB 大小限制";
  return "";
}

function addFiles(files) {
  const additions = Array.from(files || []).map(file => {
    const error = validateFile(file);
    return {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      file,
      progress: 0,
      status: error ? "error" : "queued",
      error,
    };
  });
  uploadQueue.value.push(...additions);
}

function onFileChange(event) {
  addFiles(event.target.files);
  event.target.value = "";
}

function onDrop(event) {
  dragover.value = false;
  addFiles(event.dataTransfer.files);
}

function removeUploadItem(id) {
  uploadQueue.value = uploadQueue.value.filter(item => item.id !== id);
}

async function uploadOne(item) {
  item.status = "uploading";
  item.error = "";
  try {
    await uploadAdminStorageObject(item.file, targetGroup.value, event => {
      item.progress = Math.round(event.total ? (event.loaded / event.total) * 100 : 0);
    });
    item.progress = 100;
    item.status = "success";
  } catch (error) {
    item.status = "error";
    item.error = error.message || "上传失败";
  }
}

async function submitUpload() {
  if (!validateGroup()) return;
  const candidates = uploadQueue.value.filter(item => ["queued", "error"].includes(item.status));
  if (!candidates.length) return;
  uploading.value = true;
  try {
    for (const item of candidates) await uploadOne(item);
    const successCount = uploadQueue.value.filter(item => item.status === "success").length;
    const failedCount = uploadQueue.value.filter(item => item.status === "error").length;
    if (!failedCount) {
      ElMessage.success(`已上传并登记 ${successCount} 个文件`);
      uploadDialogVisible.value = false;
      activeGroup.value = targetGroup.value || "__ungrouped__";
      page.value = 1;
      await loadData();
      uploadQueue.value = [];
    } else {
      ElMessage.warning(`${successCount} 个成功，${failedCount} 个失败，可重试失败项`);
      await loadData();
    }
  } finally {
    uploading.value = false;
  }
}

async function retryFailed() {
  await submitUpload();
}

async function getAccessUrl(row, preview = false) {
  const response = preview
    ? await createAdminStoragePreviewUrl(row.id)
    : await createAdminStorageAccessUrl(row.id);
  return response.data?.url;
}

async function openPreview(row) {
  previewAsset.value = row;
  previewVisible.value = true;
  previewUrl.value = "";
  previewText.value = "";
  previewError.value = "";
  previewMode.value = previewType(row) === "MARKDOWN" ? "rendered" : "source";
  previewLoading.value = true;
  try {
    const url = await getAccessUrl(row, true);
    previewUrl.value = url;
    if (["TEXT", "MARKDOWN"].includes(previewType(row))) {
      const response = await fetch(url);
      if (!response.ok) throw new Error("无法读取文件内容");
      previewText.value = await response.text();
    }
  } catch (error) {
    previewError.value = error.message || "生成预览失败";
  } finally {
    previewLoading.value = false;
  }
}

async function downloadFile(row) {
  try {
    const url = await getAccessUrl(row);
    if (!url) throw new Error("未生成下载链接");
    window.open(url, "_blank", "noopener,noreferrer");
  } catch (error) {
    ElMessage.error(error.message || "生成下载链接失败");
  }
}

async function copyLink(row) {
  try {
    const url = await getAccessUrl(row);
    if (!url) throw new Error("未生成访问链接");
    await copyText(url);
    ElMessage.success("临时访问链接已复制");
  } catch (error) {
    ElMessage.error(error.message || "复制链接失败");
  }
}

async function copyFileId(row) {
  try {
    await copyText(String(row.id));
    ElMessage.success(`文件 ID ${row.id} 已复制`);
  } catch (error) {
    ElMessage.error(error.message || "复制文件 ID 失败");
  }
}

async function copyText(value) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  textarea.remove();
}

async function handleCommand(command, row) {
  if (command === "copy-link") return copyLink(row);
  if (command === "details") {
    await openPreview(row);
    return;
  }
  if (command === "delete") return handleDelete(row);
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定申请删除「${row.originalName}」吗？\n文件 ID：${row.id}\n分组：${groupLabel(row.groupPath)}\n文件大小：${formatFileSize(row.fileSize)}\n\n文件会先进入等待清理状态，宽限期后才从 MinIO 物理删除。`,
      "逻辑删除确认",
      { confirmButtonText: "申请删除", cancelButtonText: "取消", type: "warning" },
    );
    await deleteAdminStorageObject(row.id);
    ElMessage.success("已进入等待清理状态");
    await loadData();
  } catch (error) {
    if (error !== "cancel") ElMessage.error(error.message || "删除失败");
  }
}

onMounted(loadData);
</script>

<style scoped>
.storage-console-card { border: none; box-shadow: none; }
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { display: block; margin-bottom: 4px; color: var(--lm-text-muted); font-size: 10px; letter-spacing: .12em; }
.toolbar-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.panel-title { margin: 0; font-size: 20px; }
.panel-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }
.storage-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.stat-box { display: flex; flex-direction: column; gap: 4px; padding: 13px 16px; background: #fafafa; border: 1px solid var(--lm-border); border-radius: var(--lm-radius-sm); }
.stat-box strong { font-size: 21px; font-family: var(--lm-code-font-family); }
.stat-label { color: var(--lm-text-muted); font-size: 11px; }
.storage-workspace { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 16px; align-items: start; }
.storage-group-panel { padding: 10px; border: 1px solid var(--lm-border); border-radius: var(--lm-radius-sm); background: #fafafa; }
.group-panel-heading { display: flex; flex-direction: column; gap: 3px; padding: 4px 6px 10px; font-size: 13px; font-weight: 600; }
.group-panel-heading small { color: var(--lm-text-muted); font-size: 11px; font-weight: 400; }
.storage-group-item { display: flex; flex-direction: column; width: 100%; gap: 3px; padding: 9px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--lm-text-secondary); cursor: pointer; text-align: left; }
.storage-group-item:hover { background: #f1f5f9; }
.storage-group-item.active { background: color-mix(in srgb, var(--lm-primary) 10%, white); color: var(--lm-primary); }
.group-name { display: flex; align-items: center; gap: 7px; min-width: 0; font-size: 12px; font-weight: 600; }
.group-name span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.group-meta { padding-left: 20px; color: var(--lm-text-muted); font-size: 10.5px; }
.storage-object-panel { min-width: 0; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.filter-bar .el-input { flex: 1; }
.reference-boundary-tip { display: flex; align-items: flex-start; gap: 7px; margin-bottom: 12px; padding: 9px 12px; background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; color: #92400e; font-size: 12px; line-height: 1.5; }
.file-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.file-type-icon, .preview-file-icon, .upload-item-icon { display: grid; place-items: center; flex: 0 0 auto; width: 34px; height: 34px; border-radius: 8px; background: #f4f4f5; color: #71717a; }
.preview-image { background: #ecfdf5; color: #047857; }
.preview-pdf { background: #fff1f2; color: #be123c; }
.preview-markdown, .preview-text { background: #eff6ff; color: #1d4ed8; }
.preview-archive { background: #fff7ed; color: #c2410c; }
.file-main { display: flex; flex-direction: column; min-width: 0; }
.file-name-button { padding: 0; overflow: hidden; border: 0; background: transparent; color: var(--lm-text-primary); cursor: pointer; font: inherit; font-weight: 600; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.file-name-button:hover { color: var(--lm-primary); text-decoration: underline; }
.file-meta, .file-id { margin-top: 3px; color: var(--lm-text-muted); font-size: 11px; }
.file-id { font-family: var(--lm-code-font-family); }
.inline-copy { padding: 0; border: 0; background: transparent; color: var(--lm-primary); cursor: pointer; font-size: 11px; }
.state-cell { display: flex; align-items: center; gap: 7px; font-size: 12px; }
.state-cell small { display: block; }
.status-warning { color: #b45309; }
.status-danger { color: #b91c1c; }
.pagination-row { display: flex; justify-content: flex-end; padding-top: 16px; }
.preview-header { display: flex; align-items: center; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid var(--lm-border); }
.preview-header strong, .preview-header span { display: block; }
.preview-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-header span { margin-top: 4px; color: var(--lm-text-muted); font-size: 12px; }
.preview-actions { display: flex; gap: 8px; flex-wrap: wrap; padding: 12px 0; }
.preview-placeholder, .unsupported-preview { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; min-height: 280px; color: var(--lm-text-muted); text-align: center; }
.image-preview { display: grid; place-items: center; min-height: 280px; padding: 14px; background: #fafafa; border-radius: 8px; }
.image-preview img { max-width: 100%; max-height: 58vh; object-fit: contain; }
.pdf-preview { width: 100%; height: 62vh; border: 1px solid var(--lm-border); border-radius: 8px; }
.text-preview, .markdown-preview { max-height: 62vh; overflow: auto; padding: 16px; background: #fafafa; border-radius: 8px; }
.text-preview pre, .markdown-preview > pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: var(--lm-code-font-family); font-size: 12px; line-height: 1.6; }
.preview-mode-switch { margin-bottom: 12px; }
.markdown-preview .markdown-body { padding: 0; }
.technical-details { margin-top: 18px; }
.technical-details dl { margin: 0; }
.technical-details dl > div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 12px; padding: 7px 0; border-bottom: 1px solid var(--lm-border); font-size: 12px; }
.technical-details dt { color: var(--lm-text-muted); }
.technical-details dd { margin: 0; word-break: break-word; }
.technical-value { font-family: var(--lm-code-font-family); }
.upload-intro { margin-bottom: 18px; color: var(--lm-text-secondary); font-size: 13px; line-height: 1.6; }
.group-picker { display: flex; gap: 8px; }
.group-select { flex: 1; }
.group-breadcrumb { margin-top: 8px; color: var(--lm-text-muted); font-size: 12px; }
.drop-zone { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; min-height: 180px; padding: 18px; border: 1px dashed #a1a1aa; border-radius: 10px; color: var(--lm-text-muted); cursor: pointer; text-align: center; transition: border-color .18s, background .18s; }
.drop-zone:hover, .drop-zone.is-dragover { border-color: var(--lm-primary); background: color-mix(in srgb, var(--lm-primary) 5%, white); }
.drop-zone strong { color: var(--lm-text-primary); }
.drop-zone small { max-width: 340px; font-size: 11px; }
.drop-zone input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.upload-queue { display: flex; flex-direction: column; gap: 10px; margin-top: 8px; }
.upload-item { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid var(--lm-border); border-radius: 8px; }
.upload-item-main { flex: 1; min-width: 0; }
.upload-item-main strong, .upload-item-main span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-item-main span { margin-top: 3px; color: var(--lm-text-muted); font-size: 11px; }
.upload-item-main .el-progress { margin-top: 7px; }
.upload-summary { display: flex; justify-content: space-between; margin-top: 12px; color: var(--lm-text-muted); font-size: 12px; }
.validation-error { color: #b91c1c; }
@media (max-width: 900px) {
  .storage-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .storage-workspace { grid-template-columns: 1fr; }
  .toolbar { flex-direction: column; }
}
</style>
