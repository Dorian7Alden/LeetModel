<template>
  <el-card shadow="never" class="storage-console-card">
    <!-- 顶部标题与说明 -->
    <div class="toolbar">
      <div>
        <h2 class="panel-title">存储桶对象资产与孤儿文件对账</h2>
        <p class="panel-subtitle">扫描 MinIO 对象存储桶，实时对账业务引用；支持手动上传、安全删除与孤儿文件排查。</p>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="loading" @click="loadData">刷新对账</el-button>
        <el-button type="primary" @click="openUploadDialog">
          <el-icon><Upload /></el-icon> 手动上传文件
        </el-button>
      </div>
    </div>

    <!-- 资产大盘统计指标 -->
    <div class="storage-stat-grid">
      <div class="stat-box">
        <div class="stat-num">{{ totalObjects }}</div>
        <div class="stat-label">存储对象总数</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">{{ formatFileSize(totalBytes) }}</div>
        <div class="stat-label">占用物理容量</div>
      </div>
      <div class="stat-box" :class="{ 'has-orphans': orphanCount > 0 }">
        <div class="stat-num orphan-num">{{ orphanCount }}</div>
        <div class="stat-label">孤儿文件（未引用）</div>
      </div>
    </div>

    <!-- 过滤工具栏 -->
    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索文件路径、名称或关联题目..."
        clearable
        style="width: 320px"
        prefix-icon="Search"
        @clear="loadData"
        @keyup.enter="loadData"
      />
      <div class="filter-orphan-toggle">
        <el-switch
          v-model="onlyOrphans"
          active-text="仅看孤儿文件"
          @change="loadData"
        />
      </div>
    </div>

    <!-- 对象资产表格 -->
    <el-table :data="objects" v-loading="loading" stripe style="width: 100%" class="storage-table">
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
      <el-table-column label="业务引用对账" min-width="220">
        <template #default="{ row }">
          <div v-if="row.refCount > 0" class="ref-status in-use">
            <el-tag type="success" size="small" effect="plain">使用中</el-tag>
            <span class="ref-problem" :title="row.refProblemTitle">
              题目: {{ row.refProblemTitle || '#' + row.refProblemId }}
            </span>
          </div>
          <div v-else class="ref-status orphan">
            <el-tag type="warning" size="small" effect="plain">孤儿文件</el-tag>
            <span class="ref-tip">无数据库引用</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <a
            v-if="row.downloadUrl"
            :href="row.downloadUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="table-action-btn"
          >下载</a>
          <el-button
            type="danger"
            link
            size="small"
            @click="handleDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无匹配的存储对象" /></template>
    </el-table>

    <!-- 手动上传文件对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="手动上传文件至存储桶" width="520px" append-to-body destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="存储前缀">
          <el-input v-model="uploadPrefix" placeholder="例如 manual、problems/common 等" />
          <span class="form-tip">文件将存放至指定前缀目录</span>
        </el-form-item>
        <el-form-item label="选择文件">
          <input type="file" ref="fileRef" @change="onManualFileChange" />
          <div v-if="selectedFile" class="file-chosen-tip">
            已选: <strong>{{ selectedFile.name }}</strong> ({{ formatFileSize(selectedFile.size) }})
          </div>
        </el-form-item>
      </el-form>
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
import { Document, Upload } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAdminStorageObjects,
  uploadAdminStorageObject,
  deleteAdminStorageObject,
} from "@/api/storage";

const objects = ref([]);
const loading = ref(false);
const searchKeyword = ref("");
const onlyOrphans = ref(false);

const uploadDialogVisible = ref(false);
const uploadPrefix = ref("manual");
const selectedFile = ref(null);
const uploading = ref(false);
const fileRef = ref(null);

const totalObjects = computed(() => objects.value.length);
const totalBytes = computed(() => objects.value.reduce((acc, cur) => acc + (cur.fileSize || 0), 0));
const orphanCount = computed(() => objects.value.filter(o => o.refCount === 0).length);

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
    const params = {};
    if (searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim();
    if (onlyOrphans.value) params.onlyOrphans = true;

    const res = await getAdminStorageObjects(params);
    objects.value = res.data || [];
  } catch (error) {
    ElMessage.error(error.message || "获取存储对象清单失败");
  } finally {
    loading.value = false;
  }
}

function openUploadDialog() {
  selectedFile.value = null;
  uploadPrefix.value = "manual";
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
    await uploadAdminStorageObject(selectedFile.value, uploadPrefix.value.trim() || "manual");
    ElMessage.success("文件已成功上传至存储桶");
    uploadDialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error.message || "上传失败");
  } finally {
    uploading.value = false;
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

  // 2. 若为孤儿文件：允许二次确认后物理删除
  try {
    await ElMessageBox.confirm(
      `确定要物理删除孤儿文件「${row.objectKey}」吗？删除后不可恢复。`,
      "孤儿文件清理确认",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "danger",
      }
    );
    await deleteAdminStorageObject(row.objectKey);
    ElMessage.success("孤儿文件已彻底清理");
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
  grid-template-columns: repeat(3, 1fr);
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
.stat-box.has-orphans {
  background: #fffbeb;
  border-color: #fef3c7;
}
.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
}
.stat-box.has-orphans .orphan-num {
  color: #b45309;
}
.stat-label {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 4px;
}

/* 过滤栏 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
}
.filter-orphan-toggle {
  display: flex;
  align-items: center;
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
.ref-tip {
  color: #d97706;
  font-size: 11px;
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
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 4px;
}
.file-chosen-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}
</style>
