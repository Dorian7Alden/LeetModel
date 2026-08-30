<template>
  <div class="ops-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">提交管理</h2>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="提交 ID" width="130" />
        <el-table-column prop="teamId" label="队伍" width="110" />
        <el-table-column prop="problemId" label="题目" width="110" />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">V{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="最终版" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.finalVersion" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button type="primary" link @click="openPreview(row)">预览 PDF</el-button></template>
        </el-table-column>
        <template #empty><el-empty description="暂无提交" /></template>
      </el-table>
    </el-card>

    <el-drawer v-model="previewVisible" size="min(1000px, 88vw)" class="pdf-preview-drawer" destroy-on-close>
      <template #header>
        <div class="pdf-preview-heading"><span>提交 PDF 预览</span><strong>{{ previewFilename || '加载中' }}</strong></div>
      </template>
      <div v-loading="previewLoading" class="pdf-preview-body">
        <template v-if="previewUrl">
          <div class="pdf-preview-toolbar">
            <span>提交 ID {{ previewSubmissionId }} · 临时访问地址按需生成</span>
            <a :href="previewUrl" target="_blank" rel="noopener noreferrer">在新窗口打开</a>
          </div>
          <iframe :src="previewUrl" :title="`PDF 预览：${previewFilename}`" class="pdf-frame"></iframe>
        </template>
        <el-empty v-else-if="!previewLoading" description="PDF 临时预览地址不可用" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminSubmissions, getAdminSubmissionPreview } from "@/api/admin-ops";

const rows = ref([]);
const loading = ref(false);
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewUrl = ref("");
const previewFilename = ref("");
const previewSubmissionId = ref(null);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await getAdminSubmissions(50)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "提交数据加载失败");
  } finally {
    loading.value = false;
  }
}

function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : "";
  } catch {
    return "";
  }
}

async function openPreview(row) {
  previewVisible.value = true;
  previewLoading.value = true;
  previewUrl.value = "";
  previewFilename.value = row.originalFilename || "提交论文.pdf";
  previewSubmissionId.value = row.id;
  try {
    const response = await getAdminSubmissionPreview(row.id);
    previewUrl.value = safeHttpUrl(response.data?.previewUrl);
    previewFilename.value = response.data?.originalFilename || previewFilename.value;
    if (!previewUrl.value) ElMessage.warning("提交服务未返回有效的 PDF 预览地址");
  } catch (error) {
    ElMessage.error(error.message || "PDF 预览加载失败");
  } finally {
    previewLoading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
.pdf-preview-heading { display: flex; min-width: 0; flex-direction: column; }
.pdf-preview-heading span { color: var(--lm-primary); font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.pdf-preview-heading strong { margin-top: 3px; overflow: hidden; color: var(--lm-text-primary); font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.pdf-preview-body { min-height: 500px; height: 100%; }
.pdf-preview-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 12px; padding: 10px 12px; color: var(--lm-text-muted); background: var(--lm-bg-secondary); border-radius: 8px; font-size: 11px; }
.pdf-preview-toolbar a { flex: 0 0 auto; font-weight: 600; }
.pdf-frame { width: 100%; height: calc(100vh - 180px); background: #475569; border: 0; border-radius: 10px; }
.pdf-preview-drawer :deep(.el-drawer__header) { margin-bottom: 0; padding: 18px 22px; border-bottom: 1px solid var(--lm-border); }
.pdf-preview-drawer :deep(.el-drawer__body) { padding: 16px 20px 20px; background: #f8fafc; }
</style>
