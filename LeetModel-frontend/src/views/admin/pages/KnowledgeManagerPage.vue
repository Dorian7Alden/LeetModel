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

    <!-- 目录导航与文档卡片 -->
    <el-card shadow="never" class="knowledge-browser" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span><strong>知识库目录与文档</strong> · {{ filteredTreeData.length }} 个主题目录</span>
          <span class="sub-text">目录负责组织，标签负责描述，卡片用于快速识别文档</span>
        </div>
      </template>

      <div v-if="currentDirectory" class="browser-layout">
        <aside class="directory-panel" aria-label="知识库目录">
          <div class="panel-heading">
            <span class="panel-kicker">BROWSE</span>
            <strong>目录导航</strong>
          </div>
          <button
            v-for="dir in filteredTreeData"
            :key="dir.path"
            type="button"
            class="directory-item"
            :class="{ 'is-active': dir.path === currentDirectory.path }"
            @click="selectDirectory(dir.path)"
          >
            <span class="directory-icon"><el-icon><Folder /></el-icon></span>
            <span class="directory-copy">
              <strong>{{ dir.title || dir.name }}</strong>
              <small>{{ dir.documentCount || dir.documents?.length || 0 }} 篇文档</small>
            </span>
            <span class="directory-arrow">›</span>
          </button>
        </aside>

        <section class="document-panel" aria-label="知识库文档">
          <div class="document-panel-header">
            <div>
              <span class="panel-kicker">CURRENT DIRECTORY</span>
              <h3>{{ currentDirectory.title || currentDirectory.name }}</h3>
              <code>{{ currentDirectory.path }}</code>
            </div>
            <el-tag type="info" effect="plain">
              {{ currentDirectory.documents?.length || 0 }} 篇文档
            </el-tag>
          </div>

          <div v-if="directoryTags(currentDirectory).length" class="directory-tags">
            <span class="tag-label">目录标签</span>
            <el-tag
              v-for="tag in directoryTags(currentDirectory)"
              :key="tag"
              size="small"
              effect="plain"
            >{{ tag }}</el-tag>
          </div>

          <div v-if="currentDirectory.documents?.length" class="document-grid">
            <article
              v-for="doc in currentDirectory.documents"
              :key="doc.file"
              class="document-card"
              tabindex="0"
              @click="showDocDetail(doc, currentDirectory)"
              @keydown.enter="showDocDetail(doc, currentDirectory)"
            >
              <div class="document-card-topline">
                <span class="document-type-icon"><el-icon><Document /></el-icon></span>
                <el-tag :type="authorityTagType(doc.authorityLevel)" size="small">
                  {{ doc.authorityLevel || 'L4' }}
                </el-tag>
              </div>
              <h4>{{ doc.title || doc.file }}</h4>
              <code class="document-file">{{ doc.file }}</code>
              <p class="doc-summary">{{ doc.summary || '暂无摘要' }}</p>
              <div v-if="documentTags(doc).length" class="document-tags">
                <el-tag
                  v-for="tag in documentTags(doc).slice(0, 5)"
                  :key="tag"
                  size="small"
                  effect="plain"
                >{{ tag }}</el-tag>
                <span v-if="documentTags(doc).length > 5" class="more-tags">
                  +{{ documentTags(doc).length - 5 }}
                </span>
              </div>
              <div class="document-card-footer">
                <span>{{ doc.estimatedTokens ? `约 ${doc.estimatedTokens} tokens` : '原子文档' }}</span>
                <span class="detail-link">查看详情 ›</span>
              </div>
            </article>
          </div>
          <el-empty v-else description="当前目录没有匹配的文档" />
        </section>
      </div>
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
const selectedDirectoryPath = ref("");
const detailVisible = ref(false);
const selectedDoc = ref(null);
const selectedDir = ref(null);

const currentDirectory = computed(() => {
  return filteredTreeData.value.find((dir) => dir.path === selectedDirectoryPath.value)
    || filteredTreeData.value[0]
    || null;
});

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

function selectDirectory(path) {
  selectedDirectoryPath.value = path;
}

function directoryTags(directory) {
  return [...new Set(directory?.tags || [])].filter(Boolean);
}

function documentTags(doc) {
  return [...new Set([...(doc?.docTags || []), ...(doc?.methods || [])])].filter(Boolean);
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
      if (!selectedDirectoryPath.value && treeData.value.length > 0) {
        selectedDirectoryPath.value = treeData.value[0].path;
      }
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

.knowledge-browser {
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

.browser-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 20px;
  min-height: 520px;
}

.directory-panel {
  border-right: 1px solid #e2e8f0;
  padding-right: 16px;
}

.panel-heading,
.document-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-heading {
  justify-content: flex-start;
  gap: 8px;
  padding: 4px 8px 12px;
  color: #0f172a;
}

.panel-kicker {
  display: block;
  margin-bottom: 5px;
  color: #94a3b8;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.directory-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
  padding: 10px 8px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s, border-color 0.15s, color 0.15s;
}

.directory-item:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}

.directory-item.is-active {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #0f172a;
}

.directory-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: #e2e8f0;
  color: #475569;
}

.directory-item.is-active .directory-icon {
  background: #0f172a;
  color: #fff;
}

.directory-copy {
  min-width: 0;
  flex: 1;
}

.directory-copy strong,
.directory-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-copy strong {
  font-size: 13px;
}

.directory-copy small {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
}

.directory-arrow {
  color: #94a3b8;
  font-size: 18px;
}

.document-panel {
  min-width: 0;
}

.document-panel-header {
  align-items: flex-start;
  padding: 4px 0 14px;
  border-bottom: 1px solid #e2e8f0;
}

.document-panel-header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.3;
}

.document-panel-header code {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  color: #64748b;
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.directory-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px 0 4px;
}

.tag-label {
  margin-right: 2px;
  color: #64748b;
  font-size: 12px;
}

.document-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-top: 14px;
}

.document-card {
  display: flex;
  min-height: 218px;
  flex-direction: column;
  padding: 15px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.document-card:hover,
.document-card:focus-visible {
  border-color: #94a3b8;
  box-shadow: 0 8px 20px rgb(15 23 42 / 8%);
  outline: none;
  transform: translateY(-1px);
}

.document-card-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.document-type-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
}

.document-card h4 {
  display: -webkit-box;
  overflow: hidden;
  margin: 14px 0 5px;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.document-file {
  overflow: hidden;
  color: #94a3b8;
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-summary {
  display: -webkit-box;
  overflow: hidden;
  margin: 10px 0 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.document-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: auto;
}

.more-tags {
  align-self: center;
  color: #94a3b8;
  font-size: 11px;
}

.document-card-footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  color: #94a3b8;
  font-size: 11px;
}

.detail-link {
  color: #475569;
  font-weight: 600;
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
  .browser-layout {
    grid-template-columns: 1fr;
  }
  .directory-panel {
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
    padding: 0 0 12px;
  }
  .directory-item {
    display: inline-flex;
    width: calc(50% - 4px);
    margin-right: 4px;
  }
  .document-grid {
    grid-template-columns: 1fr;
  }
}
</style>
