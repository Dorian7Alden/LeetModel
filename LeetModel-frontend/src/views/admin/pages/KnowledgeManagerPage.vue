<template>
  <div class="knowledge-manager-page">
    <!-- 顶部状态卡片 -->
    <div class="metrics-grid">
      <div class="metric-box">
        <span class="label">物理索引别名</span>
        <strong class="value text-primary">{{ indexStatus.indexAlias || "leetmodel-rag-v1-read" }}</strong>
      </div>
      <div class="metric-box">
        <span class="label">索引快照版本</span>
        <strong class="value font-mono">{{ indexStatus.activeIndexVersion || "MANIFEST_..." }}</strong>
      </div>
      <div class="metric-box">
        <span class="label">原子文档总量</span>
        <strong class="value">{{ indexStatus.totalDocuments || treeData.reduce((acc, d) => acc + (d.documentCount || 0), 0) }} 篇</strong>
      </div>
      <div class="metric-box">
        <span class="label">索引运行状态</span>
        <el-tag :type="indexStatus.healthy ? 'success' : 'warning'" effect="light">
          {{ indexStatus.healthy ? '健康 (就绪)' : '离线' }}
        </el-tag>
      </div>
    </div>

    <!-- 操作工具条 -->
    <div class="toolbar">
      <div class="search-filter">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索目录、文档或算法标签..."
          clearable
          style="width: 280px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="action-buttons">
        <el-button :loading="exporting" type="primary" plain @click="handleExport">
          <el-icon><Download /></el-icon>导出自包含 ZIP
        </el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleBeforeUpload"
          accept=".zip"
          style="display: inline-block"
        >
          <el-button :loading="importing" type="success" plain>
            <el-icon><Upload /></el-icon>上传 ZIP 导入
          </el-button>
        </el-upload>
        <el-button :loading="rebuilding" type="warning" plain @click="handleRebuild">
          <el-icon><Refresh /></el-icon>重建物理索引
        </el-button>
        <el-button circle @click="loadData">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 目录大纲与多维标签树 -->
    <el-card shadow="never" class="tree-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span><strong>知识库层级大纲与原子文档清单</strong> (共 {{ filteredTreeData.length }} 个主题目录)</span>
          <span class="sub-text">自包含 README.yaml 元数据驱动，与物理目录严格对齐</span>
        </div>
      </template>

      <el-collapse v-if="filteredTreeData.length > 0" v-model="activeCollapse">
        <el-collapse-item
          v-for="dir in filteredTreeData"
          :key="dir.path"
          :name="dir.path"
        >
          <template #title>
            <div class="collapse-title-row">
              <span class="dir-name">
                <el-icon><Folder /></el-icon>
                {{ dir.title }} ({{ dir.name }})
              </span>
              <span class="dir-path text-muted">{{ dir.path }}</span>
              <el-tag size="small" type="info" class="count-badge">{{ dir.documentCount }} 篇文档</el-tag>
              <div class="tag-group" v-if="dir.tags && dir.tags.length > 0">
                <el-tag
                  v-for="tag in dir.tags.slice(0, 3)"
                  :key="tag"
                  size="small"
                  effect="plain"
                  class="custom-tag"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
          </template>

          <!-- 目录下属文档列表 -->
          <div class="doc-list">
            <div
              v-for="doc in dir.documents"
              :key="doc.file"
              class="doc-item"
              @click="showDocDetail(doc, dir)"
            >
              <div class="doc-main">
                <span class="doc-title">
                  <el-icon><Document /></el-icon>
                  {{ doc.title }}
                </span>
                <span class="doc-file text-muted">{{ doc.file }}</span>
                <el-tag :type="authorityTagType(doc.authorityLevel)" size="small">
                  {{ doc.authorityLevel || 'L4' }}
                </el-tag>
              </div>
              <p class="doc-summary text-muted">{{ doc.summary }}</p>
              <div class="doc-tags" v-if="doc.docTags && doc.docTags.length > 0">
                <span v-for="t in doc.docTags" :key="t" class="tag-pill">{{ t }}</span>
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-else description="未找到匹配的知识库目录或文档" />
    </el-card>

    <!-- 文档详情查看弹窗 -->
    <el-dialog v-model="detailVisible" :title="selectedDoc?.title || '文档详情'" width="640px" destroy-on-close>
      <div v-if="selectedDoc" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="所属目录">{{ selectedDir?.title }} ({{ selectedDir?.name }})</el-descriptions-item>
          <el-descriptions-item label="相对路径">{{ selectedDoc.path || selectedDoc.file }}</el-descriptions-item>
          <el-descriptions-item label="权威层级">
            <el-tag :type="authorityTagType(selectedDoc.authorityLevel)">{{ selectedDoc.authorityLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预估 Token">{{ selectedDoc.estimatedTokens || '—' }}</el-descriptions-item>
          <el-descriptions-item label="专有标签" :span="2">
            <el-tag v-for="t in selectedDoc.docTags" :key="t" size="small" class="mr-1">{{ t }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="算法方法" :span="2">
            <el-tag v-for="m in selectedDoc.methods" :key="m" size="small" type="success" class="mr-1">{{ m }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="一句话摘要" :span="2">
            <p class="summary-box">{{ selectedDoc.summary }}</p>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Download, Upload, Refresh, RefreshRight, Folder, Document } from "@element-plus/icons-vue";
import {
  getKnowledgeTree,
  exportKnowledgeZip,
  importKnowledgeZip,
  getKnowledgeIndexStatus,
  rebuildKnowledgeIndex,
} from "@/api/knowledge";

const loading = ref(false);
const exporting = ref(false);
const importing = ref(false);
const rebuilding = ref(false);
const searchKeyword = ref("");
const treeData = ref([]);
const indexStatus = ref({
  indexAlias: "leetmodel-rag-v1-read",
  activeIndexVersion: "MANIFEST_...",
  totalDocuments: 0,
  totalChunks: 0,
  healthy: true,
});
const activeCollapse = ref([]);
const detailVisible = ref(false);
const selectedDoc = ref(null);
const selectedDir = ref(null);

const filteredTreeData = computed(() => {
  const kw = (searchKeyword.value || "").trim().toLowerCase();
  if (!kw) return treeData.value;
  return treeData.value
    .map((dir) => {
      const dirMatches = (dir.title || "").toLowerCase().includes(kw) ||
        (dir.name || "").toLowerCase().includes(kw) ||
        (dir.tags || []).some((t) => t.toLowerCase().includes(kw));
      const matchedDocs = (dir.documents || []).filter((doc) =>
        (doc.title || "").toLowerCase().includes(kw) ||
        (doc.file || "").toLowerCase().includes(kw) ||
        (doc.summary || "").toLowerCase().includes(kw) ||
        (doc.docTags || []).some((t) => t.toLowerCase().includes(kw)) ||
        (doc.methods || []).some((m) => m.toLowerCase().includes(kw))
      );
      if (dirMatches || matchedDocs.length > 0) {
        return { ...dir, documents: dirMatches ? dir.documents : matchedDocs };
      }
      return null;
    })
    .filter(Boolean);
});

function authorityTagType(level) {
  switch (level) {
    case "L1":
    case "L2": return "danger";
    case "L3": return "warning";
    case "L4": return "primary";
    default: return "info";
  }
}

function showDocDetail(doc, dir) {
  selectedDoc.value = doc;
  selectedDir.value = dir;
  detailVisible.value = true;
}

async function loadData() {
  loading.value = true;
  try {
    const [treeRes, statusRes] = await Promise.allSettled([
      getKnowledgeTree(),
      getKnowledgeIndexStatus(),
    ]);
    if (treeRes.status === "fulfilled" && treeRes.value.data) {
      treeData.value = treeRes.value.data;
      activeCollapse.value = treeData.value.map((d) => d.path);
    }
    if (statusRes.status === "fulfilled" && statusRes.value.data) {
      indexStatus.value = statusRes.value.data;
    }
  } catch (e) {
    ElMessage.error("加载知识库数据失败: " + e.message);
  } finally {
    loading.value = false;
  }
}

async function handleExport() {
  exporting.value = true;
  try {
    const blob = await exportKnowledgeZip();
    const url = window.URL.createObjectURL(new Blob([blob]));
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", "knowledge-base-self-contained.zip");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    ElMessage.success("知识库自包含 ZIP 打包导出成功");
  } catch (e) {
    ElMessage.error("导出知识包失败: " + (e.message || "网络异常"));
  } finally {
    exporting.value = false;
  }
}

async function handleBeforeUpload(file) {
  if (!file.name.endsWith(".zip")) {
    ElMessage.error("只能上传 .zip 格式的知识库归档包");
    return false;
  }
  try {
    await ElMessageBox.confirm(
      `确定要上传并无损导入知识包「${file.name}」吗？系统将校验 README.yaml 并更新内存 Manifest。`,
      "导入确认",
      { confirmButtonText: "确认导入", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return false;
  }

  importing.value = true;
  const formData = new FormData();
  formData.append("file", file);
  try {
    const res = await importKnowledgeZip(formData);
    if (res.code === 200 || res.data) {
      const report = res.data;
      ElMessage.success(
        `导入成功！解析目录 ${report.totalDirectories} 个，文档 ${report.totalDocuments} 篇，新版本 ${report.manifestVersion}`
      );
      await loadData();
    } else {
      ElMessage.error(res.message || "导入失败");
    }
  } catch (e) {
    ElMessage.error("导入知识包失败: " + (e.message || "网络异常"));
  } finally {
    importing.value = false;
  }
  return false; // 阻止 el-upload 自带提交
}

async function handleRebuild() {
  try {
    await ElMessageBox.confirm(
      "手动触发全量蓝绿重建将对当前知识库所有文档重新执行分块切片并原子切换别名，确定继续吗？",
      "重建索引确认",
      { confirmButtonText: "确认重建", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return;
  }

  rebuilding.value = true;
  try {
    const res = await rebuildKnowledgeIndex();
    if (res.code === 200 || res.data) {
      ElMessage.success("索引蓝绿重建已完成！" + (res.data?.message || ""));
      await loadData();
    }
  } catch (e) {
    ElMessage.error("触发索引重建失败: " + (e.message || "网络异常"));
  } finally {
    rebuilding.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.knowledge-manager-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.metric-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-box .label {
  font-size: 12px;
  color: #64748b;
}

.metric-box .value {
  font-size: 15px;
  color: #0f172a;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-card {
  border-radius: 6px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header .sub-text {
  font-size: 12px;
  color: #94a3b8;
}

.collapse-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.dir-name {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dir-path {
  font-size: 12px;
  font-family: monospace;
  color: #64748b;
}

.count-badge {
  margin-left: auto;
}

.tag-group {
  display: flex;
  gap: 4px;
  margin-right: 16px;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
}

.doc-item {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background-color 0.15s, border-color 0.15s;
}

.doc-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.doc-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-title {
  font-weight: 500;
  font-size: 13px;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 4px;
}

.doc-file {
  font-size: 12px;
  font-family: monospace;
  color: #64748b;
}

.doc-summary {
  margin: 6px 0 4px 0;
  font-size: 12px;
  line-height: 1.4;
  color: #475569;
}

.doc-tags {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}

.tag-pill {
  font-size: 11px;
  background: #e2e8f0;
  color: #475569;
  padding: 1px 6px;
  border-radius: 4px;
}

.detail-content .summary-box {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}

.mr-1 {
  margin-right: 4px;
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: 1fr 1fr;
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
