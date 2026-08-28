<template>
  <div class="ai-call-page">
    <div class="stat-grid" v-loading="loadingStats">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <span class="stat-label">{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">AI 调用日志</h2>
        <div class="toolbar-actions">
          <el-input v-model="filters.featureCode" placeholder="功能编码" clearable style="width: 150px" />
          <el-input v-model="filters.operationCode" placeholder="操作编码" clearable style="width: 150px" />
          <el-input v-model="filters.evaluationTaskId" placeholder="评价任务 ID" clearable style="width: 160px" />
          <el-input v-model="filters.provider" placeholder="供应商" clearable style="width: 140px" />
          <el-input v-model="filters.model" placeholder="模型" clearable style="width: 160px" />
          <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px">
            <el-option label="成功" value="SUCCEEDED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="callId" label="调用 ID" min-width="180" />
        <el-table-column prop="featureCode" label="功能" width="140" />
        <el-table-column prop="operationCode" label="操作" width="160" />
        <el-table-column prop="modality" label="模态" width="120" />
        <el-table-column prop="provider" label="供应商" width="130" />
        <el-table-column prop="model" label="模型" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Tokens" width="150" align="center">
          <template #default="{ row }">{{ row.totalTokens != null ? row.totalTokens : '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="center">
          <template #default="{ row }">{{ row.totalMs != null ? `${row.totalMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无 AI 调用记录" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminAiCalls, getAdminAiCallStats } from "@/api/admin-ai";

const rows = ref([]);
const loading = ref(false);
const loadingStats = ref(false);
const stats = ref(null);
const filters = reactive({ featureCode: "", operationCode: "", evaluationTaskId: "", provider: "", model: "", status: "" });

const statCards = computed(() => [
  { label: "总调用", value: stats.value?.totalCount ?? "-" },
  { label: "成功", value: stats.value?.successCount ?? "-" },
  { label: "失败", value: stats.value?.failureCount ?? "-" },
  { label: "总 Tokens", value: stats.value?.totalTokens ?? "-" },
  { label: "平均耗时", value: stats.value?.averageTotalMs != null ? `${stats.value.averageTotalMs}ms` : "-" },
]);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function loadStats() {
  loadingStats.value = true;
  try {
    stats.value = (await getAdminAiCallStats()).data;
  } catch (error) {
    ElMessage.error(error.message || "AI 调用统计加载失败");
  } finally {
    loadingStats.value = false;
  }
}

async function load() {
  loading.value = true;
  const params = { limit: 50 };
  const { featureCode, operationCode, evaluationTaskId, provider, model, status } = filters;
  if (featureCode) params.featureCode = featureCode;
  if (operationCode) params.operationCode = operationCode;
  if (evaluationTaskId) params.evaluationTaskId = evaluationTaskId;
  if (provider) params.provider = provider;
  if (model) params.model = model;
  if (status) params.status = status;
  try {
    rows.value = (await getAdminAiCalls(params)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "AI 调用日志加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  load();
  loadStats();
});
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.stat-card { display: flex; flex-direction: column; gap: 6px; padding: 18px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.stat-label { color: var(--lm-text-muted); font-size: 13px; }
.stat-card strong { color: var(--lm-text-primary); font-size: 24px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 10px; }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
