<template>
  <div class="ai-call-page">
    <div class="stat-grid" v-loading="loadingStats">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <span class="stat-label">{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </div>
    </div>

    <div class="insight-grid" v-loading="loadingInsights || loadingStats">
      <section class="insight-card">
        <div class="insight-heading"><div><h3>模型调用占比</h3><p>按当前筛选条件聚合全量 CHAT 与 EMBEDDING 调用</p></div></div>
        <div ref="modelShareRef" class="insight-chart" />
      </section>
      <section class="insight-card">
        <div class="insight-heading"><div><h3>成功与失败</h3><p>按当前筛选条件统计全部调用事实</p></div></div>
        <div ref="statusShareRef" class="insight-chart" />
      </section>
      <section class="insight-card">
        <div class="insight-heading"><div><h3>模型平均响应时间</h3><p>按模型与调用类型聚合，不受当前页影响</p></div></div>
        <div ref="latencyRef" class="insight-chart" />
      </section>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <h2 class="panel-title">AI 调用日志</h2>
          <p class="panel-subtitle">筛选框既可从真实历史选项中选择，也可直接输入精确值</p>
        </div>
        <div class="toolbar-actions">
          <el-select v-model="filters.featureCode" filterable allow-create default-first-option clearable placeholder="功能编码" style="width: 160px">
            <el-option v-for="item in options.featureCodes" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="filters.operationCode" filterable allow-create default-first-option clearable placeholder="操作编码" style="width: 170px">
            <el-option v-for="item in options.operationCodes" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="filters.evaluationTaskId" filterable allow-create default-first-option clearable placeholder="评价任务 ID" style="width: 180px">
            <el-option v-for="item in options.evaluationTaskIds" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="filters.provider" filterable allow-create default-first-option clearable placeholder="供应商" style="width: 140px">
            <el-option v-for="item in options.providers" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="filters.model" filterable allow-create default-first-option clearable placeholder="模型" style="width: 230px">
            <el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="filters.status" filterable allow-create default-first-option clearable placeholder="状态" style="width: 130px">
            <el-option v-for="item in options.statuses" :key="item" :label="statusLabel(item)" :value="item" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="callId" label="调用 ID" min-width="180" />
        <el-table-column prop="featureCode" label="功能" width="140" />
        <el-table-column prop="operationCode" label="操作" width="160" />
        <el-table-column prop="callType" label="类型" width="110">
          <template #default="{ row }"><el-tag :type="row.callType === 'CHAT' ? 'primary' : 'info'" size="small" effect="plain">{{ row.callType || row.modality }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="provider" label="供应商" width="130" />
        <el-table-column prop="model" label="模型" min-width="190" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Tokens" width="130" align="center">
          <template #default="{ row }">{{ row.totalTokens != null ? row.totalTokens : '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="center">
          <template #default="{ row }">{{ row.totalMs != null ? `${row.totalMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下暂无 AI 调用记录" /></template>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadPage"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import {
  getAdminAiCallFilterOptions,
  getAdminAiCallPage,
  getAdminAiCallStats,
  getAdminAiModelStats,
  getAdminProviderModels,
} from "@/api/admin-ai";

const rows = ref([]);
const loading = ref(false);
const loadingStats = ref(false);
const loadingInsights = ref(false);
const stats = ref(null);
const modelStats = ref([]);
const providerModels = ref([]);
const filters = reactive({ featureCode: "", operationCode: "", evaluationTaskId: "", provider: "", model: "", status: "" });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });
const options = reactive({ featureCodes: [], operationCodes: [], evaluationTaskIds: [], providers: ["NEW_API"], models: [], statuses: ["SUCCEEDED", "FAILED", "RUNNING"] });
const modelShareRef = ref(null);
const statusShareRef = ref(null);
const latencyRef = ref(null);
let modelShareChart;
let statusShareChart;
let latencyChart;

const statCards = computed(() => [
  { label: "总调用", value: stats.value?.totalCount ?? "-" },
  { label: "成功", value: stats.value?.successCount ?? "-" },
  { label: "失败", value: stats.value?.failureCount ?? "-" },
  { label: "总 Tokens", value: stats.value?.totalTokens ?? "-" },
  { label: "平均耗时", value: stats.value?.averageTotalMs != null ? `${stats.value.averageTotalMs}ms` : "-" },
]);

const modelOptions = computed(() => {
  const logged = new Set(options.models);
  const available = new Set(providerModels.value.map((item) => item.id));
  return [...new Set([...logged, ...available])].sort().map((value) => ({
    value,
    label: available.has(value) ? `${value} · New-API 可用` : `${value} · 历史调用`,
  }));
});

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

function statusLabel(value) {
  return ({ SUCCEEDED: "成功 · SUCCEEDED", FAILED: "失败 · FAILED", RUNNING: "运行中 · RUNNING" })[value] || value;
}

function queryParams() {
  const params = {};
  Object.entries(filters).forEach(([key, value]) => { if (value) params[key] = value; });
  return params;
}

async function loadOptions() {
  const [filterResult, modelResult] = await Promise.allSettled([
    getAdminAiCallFilterOptions(),
    getAdminProviderModels("NEW_API"),
  ]);
  if (filterResult.status === "fulfilled") {
    const remote = filterResult.value.data || {};
    Object.keys(options).forEach((key) => {
      options[key] = [...new Set([...(options[key] || []), ...(remote[key] || [])])];
    });
  }
  if (modelResult.status === "fulfilled") providerModels.value = modelResult.value.data || [];
}

async function loadStats() {
  loadingStats.value = true;
  try {
    stats.value = (await getAdminAiCallStats(queryParams())).data;
  } catch (error) {
    ElMessage.error(error.message || "AI 调用统计加载失败");
  } finally {
    loadingStats.value = false;
    renderCharts();
  }
}

async function loadModelStats() {
  loadingInsights.value = true;
  try {
    modelStats.value = (await getAdminAiModelStats(queryParams())).data || [];
  } catch (error) {
    ElMessage.error(error.message || "模型调用统计加载失败");
    modelStats.value = [];
  } finally {
    loadingInsights.value = false;
    renderCharts();
  }
}

async function loadPage() {
  loading.value = true;
  try {
    const response = (await getAdminAiCallPage({ ...queryParams(), page: pagination.page, pageSize: pagination.pageSize })).data || {};
    rows.value = response.rows || [];
    pagination.total = response.total || 0;
  } catch (error) {
    ElMessage.error(error.message || "AI 调用日志加载失败");
    rows.value = [];
    pagination.total = 0;
  } finally {
    loading.value = false;
  }
}

async function search() {
  pagination.page = 1;
  await Promise.all([loadPage(), loadStats(), loadModelStats()]);
}

function resetFilters() {
  Object.keys(filters).forEach((key) => { filters[key] = ""; });
  search();
}

function handleSizeChange() {
  pagination.page = 1;
  loadPage();
}

function renderCharts() {
  nextTick(() => {
    const palette = ["#6366f1", "#8b5cf6", "#0ea5e9", "#14b8a6", "#f59e0b", "#ef4444"];
    const aggregate = modelStats.value.map((item) => ({
      name: `${item.model || "未标注模型"} · ${item.callType || "UNKNOWN"}`,
      value: Number(item.totalCount || 0),
      average: Number(item.averageTotalMs || 0),
    }));

    if (modelShareRef.value) {
      modelShareChart = modelShareChart || echarts.init(modelShareRef.value);
      modelShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}<br/>{c} 次（{d}%）" },
        legend: { type: "scroll", bottom: 0, itemWidth: 10, itemHeight: 10 },
        title: aggregate.length ? undefined : { text: "暂无调用事实", left: "center", top: "middle", textStyle: { color: "#94a3b8", fontSize: 13 } },
        series: [{ type: "pie", radius: ["48%", "72%"], center: ["50%", "43%"], label: { show: false }, data: aggregate.map((item, index) => ({ ...item, itemStyle: { color: palette[index % palette.length] } })) }],
      }, true);
    }

    if (statusShareRef.value) {
      statusShareChart = statusShareChart || echarts.init(statusShareRef.value);
      statusShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}<br/>{c} 次（{d}%）" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
        series: [{ type: "pie", radius: ["48%", "72%"], center: ["50%", "43%"], label: { formatter: "{d}%", color: "#475569" }, data: [
          { name: "成功", value: stats.value?.successCount || 0, itemStyle: { color: "#22c55e" } },
          { name: "失败", value: stats.value?.failureCount || 0, itemStyle: { color: "#ef4444" } },
        ] }],
      }, true);
    }

    if (latencyRef.value) {
      latencyChart = latencyChart || echarts.init(latencyRef.value);
      const latencyData = aggregate.filter((item) => item.average > 0).sort((a, b) => b.average - a.average);
      latencyChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (items) => `${items[0].name}<br/>平均 ${items[0].value} ms` },
        grid: { left: 106, right: 18, top: 16, bottom: 30 },
        xAxis: { type: "value", name: "ms", splitLine: { lineStyle: { color: "#eef2f7" } } },
        yAxis: { type: "category", data: latencyData.map((item) => item.name), axisLabel: { width: 120, overflow: "truncate" } },
        series: [{ type: "bar", data: latencyData.map((item) => item.average), barMaxWidth: 22, itemStyle: { color: "#8b5cf6", borderRadius: [0, 4, 4, 0] } }],
      }, true);
    }
  });
}

function handleResize() {
  modelShareChart?.resize();
  statusShareChart?.resize();
  latencyChart?.resize();
}

onMounted(async () => {
  await loadOptions();
  await search();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  modelShareChart?.dispose();
  statusShareChart?.dispose();
  latencyChart?.dispose();
});
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.stat-card { display: flex; flex-direction: column; gap: 6px; padding: 18px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.stat-label { color: var(--lm-text-muted); font-size: 13px; }
.stat-card strong { color: var(--lm-text-primary); font-size: 24px; }
.insight-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.insight-card { min-width: 0; padding: 16px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.insight-heading h3 { margin: 0 0 5px; color: var(--lm-text-primary); font-size: 15px; }
.insight-heading p, .panel-subtitle { margin: 0; color: var(--lm-text-muted); font-size: 12px; }
.insight-chart { width: 100%; height: 280px; }
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 14px; margin-bottom: 16px; }
.panel-title { margin: 0 0 4px; font-size: 18px; }
.toolbar-actions { display: flex; justify-content: flex-end; flex: 1 1 760px; flex-wrap: wrap; gap: 10px; }
.pagination-row { display: flex; justify-content: flex-end; padding-top: 18px; }
@media (max-width: 1100px) { .insight-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } .pagination-row { justify-content: flex-start; overflow-x: auto; } }
</style>
