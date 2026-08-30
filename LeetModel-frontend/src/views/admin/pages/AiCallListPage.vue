<template>
  <div class="ai-call-page">
    <div class="stat-grid" v-loading="loadingStats">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <span class="stat-label">{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </div>
    </div>

    <div class="insight-grid" v-loading="loading || loadingStats">
      <section class="insight-card">
        <div class="insight-heading"><div><h3>模型调用占比</h3><p>基于当前查询返回的最近 50 条调用</p></div></div>
        <div ref="modelShareRef" class="insight-chart" />
      </section>
      <section class="insight-card">
        <div class="insight-heading"><div><h3>成功与失败</h3><p>基于系统累计调用事实</p></div></div>
        <div ref="statusShareRef" class="insight-chart" />
      </section>
      <section class="insight-card">
        <div class="insight-heading"><div><h3>模型平均响应时间</h3><p>基于当前查询中具有耗时记录的调用</p></div></div>
        <div ref="latencyRef" class="insight-chart" />
      </section>
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
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getAdminAiCalls, getAdminAiCallStats } from "@/api/admin-ai";

const rows = ref([]);
const loading = ref(false);
const loadingStats = ref(false);
const stats = ref(null);
const modelShareRef = ref(null);
const statusShareRef = ref(null);
const latencyRef = ref(null);
let modelShareChart;
let statusShareChart;
let latencyChart;
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
    renderCharts();
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
    renderCharts();
  } catch (error) {
    ElMessage.error(error.message || "AI 调用日志加载失败");
  } finally {
    loading.value = false;
  }
}

function renderCharts() {
  nextTick(() => {
    const modelCounts = new Map();
    const latencyGroups = new Map();
    rows.value.forEach((row) => {
      const model = row.model || "未标注模型";
      modelCounts.set(model, (modelCounts.get(model) || 0) + 1);
      const totalMs = Number(row.totalMs);
      if (Number.isFinite(totalMs)) {
        const group = latencyGroups.get(model) || { sum: 0, count: 0 };
        group.sum += totalMs;
        group.count += 1;
        latencyGroups.set(model, group);
      }
    });
    const palette = ["#6366f1", "#8b5cf6", "#0ea5e9", "#14b8a6", "#f59e0b", "#ef4444"];

    if (modelShareRef.value) {
      modelShareChart = modelShareChart || echarts.init(modelShareRef.value);
      const modelData = [...modelCounts.entries()].map(([name, value], index) => ({ name, value, itemStyle: { color: palette[index % palette.length] } }));
      modelShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}<br/>{c} 次（{d}%）" },
        legend: { type: "scroll", bottom: 0, itemWidth: 10, itemHeight: 10 },
        title: modelData.length ? undefined : { text: "暂无调用样本", left: "center", top: "middle", textStyle: { color: "#94a3b8", fontSize: 13 } },
        series: [{ type: "pie", radius: ["48%", "72%"], center: ["50%", "43%"], label: { show: false }, data: modelData }],
      }, true);
    }

    if (statusShareRef.value) {
      statusShareChart = statusShareChart || echarts.init(statusShareRef.value);
      const statusData = [
        { name: "成功", value: stats.value?.successCount || 0, itemStyle: { color: "#22c55e" } },
        { name: "失败", value: stats.value?.failureCount || 0, itemStyle: { color: "#ef4444" } },
      ];
      statusShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}<br/>{c} 次（{d}%）" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
        series: [{ type: "pie", radius: ["48%", "72%"], center: ["50%", "43%"], label: { formatter: "{d}%", color: "#475569" }, data: statusData }],
      }, true);
    }

    if (latencyRef.value) {
      latencyChart = latencyChart || echarts.init(latencyRef.value);
      const latencyData = [...latencyGroups.entries()]
        .map(([name, group]) => ({ name, value: Math.round(group.sum / group.count) }))
        .sort((a, b) => b.value - a.value);
      latencyChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (items) => `${items[0].name}<br/>平均 ${items[0].value} ms` },
        grid: { left: 76, right: 18, top: 16, bottom: 30 },
        xAxis: { type: "value", name: "ms", splitLine: { lineStyle: { color: "#eef2f7" } } },
        yAxis: { type: "category", data: latencyData.map((item) => item.name), axisLabel: { width: 92, overflow: "truncate" } },
        series: [{ type: "bar", data: latencyData.map((item) => item.value), barMaxWidth: 22, itemStyle: { color: "#8b5cf6", borderRadius: [0, 4, 4, 0] } }],
      }, true);
    }
  });
}

function handleResize() {
  modelShareChart?.resize();
  statusShareChart?.resize();
  latencyChart?.resize();
}

onMounted(() => {
  load();
  loadStats();
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
.insight-heading p { margin: 0; color: var(--lm-text-muted); font-size: 12px; }
.insight-chart { width: 100%; height: 260px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 10px; }
@media (max-width: 1100px) { .insight-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
