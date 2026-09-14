<template>
  <div class="knowledge-console">
    <div class="knowledge-toolbar">
      <el-input v-model="searchKeyword" placeholder="目录 / 文档 / 标签" clearable class="knowledge-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="toolbar-actions">
        <el-button :loading="exporting" :disabled="treeState !== 'ready'" @click="handleExport"><el-icon><Download /></el-icon>导出 ZIP</el-button>
        <el-upload :show-file-list="false" :before-upload="handleBeforeUpload" accept=".zip">
          <el-button :loading="importing"><el-icon><Upload /></el-icon>导入 ZIP</el-button>
        </el-upload>
        <el-button :loading="rebuilding" :disabled="indexState !== 'ready'" @click="handleRebuild"><el-icon><Refresh /></el-icon>重建索引</el-button>
        <el-button :loading="loading" @click="loadData"><el-icon><RefreshRight /></el-icon>刷新</el-button>
      </div>
    </div>

    <div class="index-strip" :class="`is-${indexState}`" aria-live="polite">
      <template v-if="indexState === 'ready'">
        <span><small>索引</small><strong>{{ indexStatus.indexAlias || '—' }}</strong></span>
        <span><small>版本</small><code>{{ indexStatus.activeIndexVersion || '—' }}</code></span>
        <span><small>文档</small><strong>{{ formatCount(indexStatus.totalDocuments) }}</strong></span>
        <span><small>分块</small><strong>{{ formatCount(indexStatus.totalChunks) }}</strong></span>
        <AdminStatusBadge :status="indexStatus.healthy === true ? 'HEALTHY' : indexStatus.healthy === false ? 'FAILED' : 'UNKNOWN'" :label="healthLabel" />
      </template>
      <template v-else-if="indexState === 'loading'">
        <el-icon class="is-loading"><Loading /></el-icon><span>正在读取索引状态</span>
      </template>
      <template v-else>
        <el-icon><WarningFilled /></el-icon><span>索引状态未取得</span><el-button link type="primary" @click="loadIndexStatus">重试</el-button>
      </template>
    </div>

    <AdminStatePanel
      v-if="treeState === 'error' && !treeData.length"
      type="error"
      title="知识库目录加载失败"
      action-label="重新加载"
      @action="loadTree"
    />

    <template v-else>
      <div v-if="treeState === 'error'" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>目录刷新失败，当前保留上次取得的数据
      </div>
      <div class="knowledge-workspace" v-loading="treeState === 'loading'">
        <aside class="directory-panel" aria-label="知识库目录">
          <div class="directory-summary">{{ filteredTreeData.length }} 个目录</div>
          <button
            v-for="directory in filteredTreeData"
            :key="directory.path"
            type="button"
            class="directory-item"
            :class="{ active: directory.path === currentDirectory?.path }"
            @click="selectDirectory(directory.path)"
          >
            <el-icon><Folder /></el-icon>
            <span><strong>{{ directory.title || directory.name }}</strong><small>{{ directory.documents?.length || directory.documentCount || 0 }} 篇</small></span>
          </button>
          <div v-if="treeState === 'ready' && !filteredTreeData.length" class="directory-empty">没有匹配目录</div>
        </aside>

        <section class="document-panel" aria-label="知识库文档">
          <template v-if="currentDirectory">
            <div class="directory-context">
              <div><strong>{{ currentDirectory.title || currentDirectory.name }}</strong><code>{{ currentDirectory.path }}</code></div>
              <div class="directory-tags">
                <el-tag v-for="tag in directoryTags(currentDirectory).slice(0, 6)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
            </div>
            <div class="document-table-scroll">
              <el-table :data="currentDirectory.documents || []" row-key="file" table-layout="fixed">
                <el-table-column label="文档" min-width="290">
                  <template #default="{ row }">
                    <button class="document-link" type="button" @click="showDocDetail(row, currentDirectory)">
                      <strong>{{ row.title || row.file }}</strong><code>{{ row.file }}</code>
                    </button>
                  </template>
                </el-table-column>
                <el-table-column label="权威" width="74">
                  <template #default="{ row }"><el-tag :type="authorityTagType(row.authorityLevel)" size="small">{{ row.authorityLevel || '未知' }}</el-tag></template>
                </el-table-column>
                <el-table-column label="标签 / 方法" min-width="250">
                  <template #default="{ row }">
                    <div class="document-tags">
                      <el-tag v-for="tag in documentTags(row).slice(0, 3)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                      <span v-if="documentTags(row).length > 3">+{{ documentTags(row).length - 3 }}</span>
                      <span v-if="!documentTags(row).length">—</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="预估 Token" width="110" align="right">
                  <template #default="{ row }">{{ formatCount(row.estimatedTokens) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="72" align="right">
                  <template #default="{ row }"><el-button link type="primary" @click="showDocDetail(row, currentDirectory)">详情</el-button></template>
                </el-table-column>
                <template #empty><div class="table-empty">当前目录没有匹配文档</div></template>
              </el-table>
            </div>
          </template>
          <div v-else class="workspace-empty">{{ searchKeyword ? '没有匹配的目录或文档' : '知识库暂无目录' }}</div>
        </section>
      </div>
    </template>

    <el-drawer v-model="detailVisible" :title="selectedDoc?.title || selectedDoc?.file || '文档详情'" size="520px" destroy-on-close>
      <dl v-if="selectedDoc" class="detail-list">
        <div><dt>所属目录</dt><dd>{{ selectedDir?.title || selectedDir?.name || '—' }}</dd></div>
        <div><dt>相对路径</dt><dd><code>{{ selectedDoc.path || selectedDoc.file || '—' }}</code></dd></div>
        <div><dt>权威层级</dt><dd><el-tag :type="authorityTagType(selectedDoc.authorityLevel)" size="small">{{ selectedDoc.authorityLevel || '未知' }}</el-tag></dd></div>
        <div><dt>预估 Token</dt><dd>{{ formatCount(selectedDoc.estimatedTokens) }}</dd></div>
        <div><dt>标签</dt><dd><el-tag v-for="tag in selectedDoc.docTags || []" :key="tag" size="small" effect="plain">{{ tag }}</el-tag><span v-if="!selectedDoc.docTags?.length">—</span></dd></div>
        <div><dt>方法</dt><dd><el-tag v-for="method in selectedDoc.methods || []" :key="method" size="small" type="success" effect="plain">{{ method }}</el-tag><span v-if="!selectedDoc.methods?.length">—</span></dd></div>
        <div><dt>摘要</dt><dd>{{ selectedDoc.summary || '—' }}</dd></div>
      </dl>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { exportKnowledgeZip, getKnowledgeIndexStatus, getKnowledgeTree, importKnowledgeZip, rebuildKnowledgeIndex } from "@/api/knowledge";

const emit = defineEmits(["changed"]);
const treeState = ref("loading");
const indexState = ref("loading");
const exporting = ref(false);
const importing = ref(false);
const rebuilding = ref(false);
const searchKeyword = ref("");
const treeData = ref([]);
const indexStatus = ref({});
const selectedDirectoryPath = ref("");
const detailVisible = ref(false);
const selectedDoc = ref(null);
const selectedDir = ref(null);

const loading = computed(() => treeState.value === "loading" || indexState.value === "loading");
const healthLabel = computed(() => indexStatus.value.healthy === true ? "就绪" : indexStatus.value.healthy === false ? "异常" : "未知");
const filteredTreeData = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) return treeData.value;
  return treeData.value.map((directory) => {
    const directoryMatched = [directory.title, directory.name, ...(directory.tags || [])]
      .some((value) => String(value || "").toLowerCase().includes(keyword));
    const documents = (directory.documents || []).filter((document) => [
      document.title,
      document.file,
      document.summary,
      ...(document.docTags || []),
      ...(document.methods || []),
    ].some((value) => String(value || "").toLowerCase().includes(keyword)));
    if (!directoryMatched && !documents.length) return null;
    return { ...directory, documents: directoryMatched ? directory.documents : documents };
  }).filter(Boolean);
});
const currentDirectory = computed(() => filteredTreeData.value.find((directory) => directory.path === selectedDirectoryPath.value)
  || filteredTreeData.value[0]
  || null);

function formatCount(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString("zh-CN") : "—";
}

function authorityTagType(level) {
  return { L1: "danger", L2: "danger", L3: "warning", L4: "primary" }[level] || "info";
}

function directoryTags(directory) {
  return [...new Set(directory?.tags || [])].filter(Boolean);
}

function documentTags(document) {
  return [...new Set([...(document?.docTags || []), ...(document?.methods || [])])].filter(Boolean);
}

function selectDirectory(path) {
  selectedDirectoryPath.value = path;
}

function showDocDetail(document, directory) {
  selectedDoc.value = document;
  selectedDir.value = directory;
  detailVisible.value = true;
}

async function loadTree() {
  treeState.value = "loading";
  try {
    const response = await getKnowledgeTree();
    treeData.value = Array.isArray(response.data) ? response.data : [];
    if (!treeData.value.some((directory) => directory.path === selectedDirectoryPath.value)) {
      selectedDirectoryPath.value = treeData.value[0]?.path || "";
    }
    treeState.value = "ready";
  } catch (error) {
    treeState.value = "error";
    if (treeData.value.length) ElMessage.error(error.message || "知识库目录刷新失败");
  }
}

async function loadIndexStatus() {
  indexState.value = "loading";
  try {
    const response = await getKnowledgeIndexStatus();
    if (!response.data) throw new Error("索引状态为空");
    indexStatus.value = response.data;
    indexState.value = "ready";
  } catch (error) {
    indexStatus.value = {};
    indexState.value = "error";
  }
}

async function loadData() {
  await Promise.all([loadTree(), loadIndexStatus()]);
}

async function handleExport() {
  exporting.value = true;
  try {
    const blob = await exportKnowledgeZip();
    const url = window.URL.createObjectURL(new Blob([blob]));
    const link = document.createElement("a");
    link.href = url;
    link.download = "knowledge-base-self-contained.zip";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
    ElMessage.success("知识库已导出");
  } catch (error) {
    ElMessage.error(error.message || "知识库导出失败");
  } finally {
    exporting.value = false;
  }
}

async function handleBeforeUpload(file) {
  if (!file.name.toLowerCase().endsWith(".zip")) {
    ElMessage.error("请选择 ZIP 知识包");
    return false;
  }
  try {
    await ElMessageBox.confirm(`导入“${file.name}”将校验并更新知识库 Manifest。`, "导入知识包？", { type: "warning", confirmButtonText: "开始导入", cancelButtonText: "取消" });
  } catch {
    return false;
  }
  importing.value = true;
  const formData = new FormData();
  formData.append("file", file);
  try {
    await importKnowledgeZip(formData);
    ElMessage.success("知识包已导入");
    await loadData();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "知识包导入失败");
  } finally {
    importing.value = false;
  }
  return false;
}

async function handleRebuild() {
  try {
    await ElMessageBox.confirm("将对全部知识文档重新分块并切换物理索引。", "重建索引？", { type: "warning", confirmButtonText: "开始重建", cancelButtonText: "取消" });
  } catch {
    return;
  }
  rebuilding.value = true;
  try {
    await rebuildKnowledgeIndex();
    ElMessage.success("索引重建已完成");
    await loadIndexStatus();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "索引重建失败");
  } finally {
    rebuilding.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.knowledge-console { min-width: 0; padding: var(--lm-admin-space-3); }
.knowledge-toolbar, .toolbar-actions, .index-strip, .index-strip > span, .inline-warning, .directory-context, .directory-tags, .document-tags { display: flex; align-items: center; }
.knowledge-toolbar { justify-content: space-between; gap: var(--lm-admin-space-3); margin-bottom: var(--lm-admin-space-3); }
.knowledge-search { width: 300px; }
.toolbar-actions { gap: var(--lm-admin-space-2); }
.index-strip { min-height: 42px; gap: 18px; margin-bottom: var(--lm-admin-space-3); padding: 7px 10px; background: var(--lm-admin-surface-subtle); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.index-strip > span { gap: 6px; }
.index-strip small { color: var(--lm-admin-text-muted); }
.index-strip strong, .index-strip code { color: var(--lm-admin-text-strong); font-size: 12px; }
.index-strip.is-error { color: #92400e; background: #fffbeb; border-color: #fde68a; }
.knowledge-workspace { display: grid; min-height: 500px; grid-template-columns: 220px minmax(0, 1fr); overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.directory-panel { min-width: 0; padding: 8px; background: var(--lm-admin-surface-subtle); border-right: 1px solid var(--lm-admin-border); }
.directory-summary { padding: 4px 8px 8px; color: var(--lm-admin-text-muted); font-size: 11px; }
.directory-item { display: flex; width: 100%; min-width: 0; align-items: center; gap: 8px; padding: 8px; color: var(--lm-admin-text-default); background: transparent; border: 0; border-radius: var(--lm-admin-radius-control); cursor: pointer; text-align: left; }
.directory-item:hover { background: #fff; }
.directory-item.active { color: var(--lm-admin-primary); background: #eaf2ff; }
.directory-item > span { display: flex; min-width: 0; flex: 1; align-items: center; justify-content: space-between; gap: 8px; }
.directory-item strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.directory-item small { flex: 0 0 auto; color: var(--lm-admin-text-muted); font-size: 10px; }
.directory-empty { padding: 26px 8px; color: var(--lm-admin-text-muted); font-size: 12px; text-align: center; }
.document-panel { min-width: 0; padding: 12px; }
.directory-context { min-height: 36px; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.directory-context > div:first-child { min-width: 0; }
.directory-context strong, .directory-context code { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.directory-context strong { color: var(--lm-admin-text-strong); font-size: 13px; }
.directory-context code { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 10px; }
.directory-tags, .document-tags { gap: 4px; }
.document-table-scroll { min-width: 0; overflow-x: auto; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.document-table-scroll :deep(.el-table) { min-width: 780px; }
.document-link { display: flex; max-width: 100%; flex-direction: column; padding: 0; color: inherit; background: transparent; border: 0; cursor: pointer; text-align: left; }
.document-link strong, .document-link code { overflow: hidden; max-width: 100%; text-overflow: ellipsis; white-space: nowrap; }
.document-link strong { color: var(--lm-admin-text-strong); font-size: 12px; }
.document-link code { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 10px; }
.document-link:hover strong { color: var(--lm-admin-primary); }
.document-tags span { color: var(--lm-admin-text-muted); font-size: 10px; }
.workspace-empty { display: grid; min-height: 420px; place-items: center; color: var(--lm-admin-text-muted); font-size: 12px; }
.detail-list { margin: 0; }
.detail-list > div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--lm-admin-border); font-size: 12px; }
.detail-list dt { color: var(--lm-admin-text-muted); }
.detail-list dd { display: flex; flex-wrap: wrap; gap: 5px; margin: 0; color: var(--lm-admin-text-strong); line-height: 1.6; word-break: break-word; }
.inline-warning { gap: 6px; margin-bottom: 8px; padding: 8px 10px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.table-empty { padding: 34px 0; color: var(--lm-admin-text-muted); font-size: 12px; }
</style>
