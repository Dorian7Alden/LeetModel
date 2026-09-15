<template>
  <div class="ai-call-page">
    <div class="call-toolbar">
      <div class="call-filters">
        <el-select v-model="filters.featureCode" filterable allow-create default-first-option clearable placeholder="功能" class="filter-control">
          <el-option v-for="item in options.featureCodes" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.operationCode" filterable allow-create default-first-option clearable placeholder="操作" class="filter-control wide">
          <el-option v-for="item in options.operationCodes" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.evaluationTaskId" filterable allow-create default-first-option clearable placeholder="评价任务" class="filter-control wide">
          <el-option v-for="item in options.evaluationTaskIds" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.provider" filterable allow-create default-first-option clearable placeholder="供应商" class="filter-control">
          <el-option v-for="item in options.providers" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.model" filterable allow-create default-first-option clearable placeholder="模型" class="filter-control model-filter">
          <el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" class="filter-control">
          <el-option v-for="item in options.statuses" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
      </div>
      <div class="toolbar-actions">
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button :disabled="!hasFilters" @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div class="scoped-summary" :aria-busy="loadingStats">
      <span><small>当前范围</small><strong>{{ stats?.totalCount ?? "—" }}</strong></span>
      <span><small>成功</small><strong class="success-value">{{ stats?.successCount ?? "—" }}</strong></span>
      <span><small>失败</small><strong class="failure-value">{{ stats?.failureCount ?? "—" }}</strong></span>
      <span><small>平均耗时</small><strong>{{ stats?.averageTotalMs == null ? "—" : `${stats.averageTotalMs} ms` }}</strong></span>
      <span><small>Tokens</small><strong>{{ formatNumber(stats?.totalTokens) }}</strong></span>
      <span v-if="statsError || insightsError" class="summary-error">
        <el-icon><WarningFilled /></el-icon>{{ statsError && insightsError ? "统计与分析暂不可用" : statsError ? "统计暂不可用" : "模型分析暂不可用" }}
      </span>
    </div>

    <!-- 数据分析图表优先展示于数据表格上方 -->
    <div class="analysis-row" v-loading="loadingInsights || loadingStats">
      <section class="analysis-panel">
        <h3>模型调用分布</h3>
        <div ref="modelShareRef" class="analysis-chart" />
      </section>
      <section class="analysis-panel">
        <h3>模型平均耗时</h3>
        <div ref="latencyRef" class="analysis-chart" />
      </section>
      <section class="analysis-panel">
        <h3>业务功能调用流量分析</h3>
        <div ref="featureTrafficRef" class="analysis-chart" />
      </section>
      <section class="analysis-panel">
        <h3>各功能 Token 消耗占比</h3>
        <div ref="tokenShareRef" class="analysis-chart" />
      </section>
    </div>

    <AdminStatePanel
      v-if="loadError && !rows.length"
      type="error"
      title="AI 调用记录加载失败"
      action-label="重新加载"
      @action="loadPage"
    />

    <div v-else class="call-table-wrap">
      <el-table
        :data="rows"
        stripe
        v-loading="loading"
        row-class-name="clickable-row"
        @row-click="openDetails"
      >
        <el-table-column label="功能" min-width="156">
          <template #default="{ row }">
            <strong class="primary-cell">{{ row.featureCode || "—" }}</strong>
            <span class="secondary-cell">{{ row.callerService || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operationCode" label="操作" min-width="190" show-overflow-tooltip />
        <el-table-column label="模型" min-width="210">
          <template #default="{ row }">
            <strong class="primary-cell">{{ row.model || "—" }}</strong>
            <span class="secondary-cell">{{ row.workflowVersion || row.modelExecutionConfigVersion || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="104">
          <template #default="{ row }"><AdminStatusBadge :status="row.status || 'UNKNOWN'" :label="statusLabel(row.status)" /></template>
        </el-table-column>
        <el-table-column label="Tokens" width="96" align="right">
          <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="96" align="right">
          <template #default="{ row }">{{ row.totalMs == null ? "—" : `${row.totalMs} ms` }}</template>
        </el-table-column>
        <el-table-column label="时间" width="148">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="" width="62" align="right">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openDetails(row)">详情</el-button></template>
        </el-table-column>
        <template #empty>
          <AdminStatePanel :type="hasFilters ? 'filtered' : 'empty'" :title="hasFilters ? '没有符合条件的调用记录' : '暂无 AI 调用记录'" />
        </template>
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
    </div>

    <el-drawer v-model="detailsVisible" title="调用详情" size="560px">
      <div v-if="selectedRow" class="call-details">
        <div class="detail-lead">
          <div>
            <strong>{{ selectedRow.featureCode || "—" }} · {{ selectedRow.operationCode || "—" }}</strong>
            <span>{{ formatTime(selectedRow.createTime) }}</span>
          </div>
          <AdminStatusBadge :status="selectedRow.status || 'UNKNOWN'" :label="statusLabel(selectedRow.status)" />
        </div>

        <dl class="detail-grid">
          <div><dt>调用服务</dt><dd>{{ selectedRow.callerService || "—" }}</dd></div>
          <div><dt>类型</dt><dd>{{ selectedRow.callType || selectedRow.modality || "—" }}</dd></div>
          <div><dt>供应商</dt><dd>{{ selectedRow.provider || "—" }}</dd></div>
          <div><dt>模型</dt><dd>{{ selectedRow.model || "—" }}</dd></div>
          <div><dt>工作流版本</dt><dd>{{ selectedRow.workflowVersion || "—" }}</dd></div>
          <div><dt>Prompt 版本</dt><dd>{{ selectedRow.promptVersion || "—" }}</dd></div>
          <div><dt>模型配置</dt><dd>{{ selectedRow.modelExecutionConfigVersion || "—" }}</dd></div>
          <div><dt>RAG 索引</dt><dd>{{ selectedRow.ragIndexVersion || "—" }}</dd></div>
          <div><dt>输入 Tokens</dt><dd>{{ formatNumber(selectedRow.inputTokens) }}</dd></div>
          <div><dt>输出 Tokens</dt><dd>{{ formatNumber(selectedRow.outputTokens) }}</dd></div>
          <div><dt>排队耗时</dt><dd>{{ duration(selectedRow.queueMs) }}</dd></div>
          <div><dt>执行耗时</dt><dd>{{ duration(selectedRow.executionMs) }}</dd></div>
          <div><dt>计费</dt><dd>{{ costText(selectedRow) }}</dd></div>
          <div><dt>计量完整度</dt><dd>{{ selectedRow.usageCompleteness || "—" }}</dd></div>
        </dl>

        <div v-if="selectedRow.errorCode || selectedRow.errorMessage" class="error-block">
          <strong>{{ selectedRow.errorCode || "调用失败" }}</strong>
          <p>{{ selectedRow.errorMessage || "未返回失败说明" }}</p>
        </div>

        <dl class="identity-list">
          <div v-for="item in identityItems" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd><code>{{ item.value || "—" }}</code><el-button v-if="item.value" link type="primary" @click="copyValue(item.value)">复制</el-button></dd>
          </div>
        </dl>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
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
const loadError = ref(false);
const statsError = ref(false);
const insightsError = ref(false);
const stats = ref(null);
const modelStats = ref([]);
const providerModels = ref([]);
const selectedRow = ref(null);
const detailsVisible = ref(false);
const filters = reactive({ featureCode: "", operationCode: "", evaluationTaskId: "", provider: "", model: "", status: "" });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });
const options = reactive({ featureCodes: [], operationCodes: [], evaluationTaskIds: [], providers: ["NEW_API"], models: [], statuses: ["SUCCEEDED", "FAILED", "RUNNING"] });
const modelShareRef = ref(null);
const latencyRef = ref(null);
const featureTrafficRef = ref(null);
const tokenShareRef = ref(null);
let featureTrafficChart = null;
let tokenShareChart = null;
let modelShareChart;
let latencyChart;

const hasFilters = computed(() => Object.values(filters).some(Boolean));
const modelOptions = computed(() => {
  const logged = new Set(options.models);
  const available = new Set(providerModels.value.map(item => item.id));
  return [...new Set([...logged, ...available])].sort().map(value => ({
    value,
    label: available.has(value) ? `${value} · 可用` : `${value} · 历史`,
  }));
});
const identityItems = computed(() => {
  const row = selectedRow.value || {};
  return [
    { label: "Call ID", value: row.callId },
    { label: "Trace ID", value: row.traceId },
    { label: "业务任务", value: row.businessTaskId },
    { label: "评价任务", value: row.evaluationTaskId },
    { label: "供应商响应", value: row.providerResponseId },
    { label: "New-API 请求", value: row.newApiRequestId },
  ];
});

function formatNumber(value) {
  return value == null ? "—" : Number(value).toLocaleString("zh-CN");
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function duration(value) {
  return value == null ? "—" : `${value} ms`;
}

function costText(row) {
  return row.costAmount == null ? "—" : `${row.costAmount} ${row.costCurrency || ""}`.trim();
}

function statusLabel(value) {
  return ({ SUCCEEDED: "成功", FAILED: "失败", RUNNING: "运行中" })[value] || "未知";
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
    Object.keys(options).forEach(key => {
      options[key] = [...new Set([...(options[key] || []), ...(remote[key] || [])])];
    });
  }
  if (modelResult.status === "fulfilled") providerModels.value = modelResult.value.data || [];
}

async function loadStats() {
  loadingStats.value = true;
  statsError.value = false;
  try {
    stats.value = (await getAdminAiCallStats(queryParams())).data;
  } catch {
    statsError.value = true;
  } finally {
    loadingStats.value = false;
    renderCharts();
  }
}

async function loadModelStats() {
  loadingInsights.value = true;
  insightsError.value = false;
  try {
    modelStats.value = (await getAdminAiModelStats(queryParams())).data || [];
  } catch {
    insightsError.value = true;
  } finally {
    loadingInsights.value = false;
    renderCharts();
  }
}

async function loadPage() {
  loading.value = true;
  loadError.value = false;
  try {
    const response = (await getAdminAiCallPage({ ...queryParams(), page: pagination.page, pageSize: pagination.pageSize })).data || {};
    rows.value = response.rows || [];
    pagination.total = response.total || 0;
    renderCharts();
  } catch (error) {
    loadError.value = true;
    if (!rows.value.length) pagination.total = 0;
    ElMessage.error(error.message || "AI 调用记录加载失败");
  } finally {
    loading.value = false;
  }
}

async function search() {
  pagination.page = 1;
  await Promise.all([loadPage(), loadStats(), loadModelStats()]);
}

function resetFilters() {
  Object.keys(filters).forEach(key => { filters[key] = ""; });
  search();
}

function handleSizeChange() {
  pagination.page = 1;
  loadPage();
}

function openDetails(row) {
  selectedRow.value = row;
  detailsVisible.value = true;
}

async function copyValue(value) {
  try {
    await navigator.clipboard.writeText(String(value));
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败");
  }
}

function renderCharts() {
  nextTick(() => {
    const palette = ["#2563eb", "#7c3aed", "#0891b2", "#0f766e", "#d97706", "#dc2626"];
    const aggregate = modelStats.value.map(item => ({
      name: `${item.model || "未标注模型"} · ${item.callType || "UNKNOWN"}`,
      value: Number(item.totalCount || 0),
      average: Number(item.averageTotalMs || 0),
    }));

    if (modelShareRef.value) {
      modelShareChart = modelShareChart || echarts.init(modelShareRef.value);
      modelShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}<br/>{c} 次（{d}%）" },
        legend: { type: "scroll", bottom: 0, itemWidth: 9, itemHeight: 9, textStyle: { fontSize: 10 } },
        title: aggregate.length ? undefined : { text: "暂无数据", left: "center", top: "middle", textStyle: { color: "#94a3b8", fontSize: 12 } },
        series: [{ type: "pie", radius: ["45%", "68%"], center: ["50%", "42%"], label: { show: false }, data: aggregate.map((item, index) => ({ ...item, itemStyle: { color: palette[index % palette.length] } })) }],
      }, true);
    }

    if (latencyRef.value) {
      latencyChart = latencyChart || echarts.init(latencyRef.value);
      const latencyData = aggregate.filter(item => item.average > 0).sort((a, b) => b.average - a.average);
      latencyChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: items => `${items[0].name}<br/>平均 ${items[0].value} ms` },
        grid: { left: 104, right: 16, top: 8, bottom: 22 },
        xAxis: { type: "value", splitLine: { lineStyle: { color: "#eef2f7" } }, axisLabel: { fontSize: 10 } },
        yAxis: { type: "category", data: latencyData.map(item => item.name), axisLabel: { width: 94, overflow: "truncate", fontSize: 10 } },
        series: [{ type: "bar", data: latencyData.map(item => item.average), barMaxWidth: 16, itemStyle: { color: "#2563eb", borderRadius: [0, 3, 3, 0] } }],
      }, true);
    }

    // 3. 业务功能调用流量分析
    if (featureTrafficRef.value && rows.value?.length) {
      featureTrafficChart = featureTrafficChart || echarts.init(featureTrafficRef.value);
      const featMap = {};
      rows.value.forEach(r => {
        const feat = r.featureCode || "其他";
        featMap[feat] = (featMap[feat] || 0) + 1;
      });
      featureTrafficChart.setOption({
        tooltip: { trigger: "axis" },
        grid: { left: 45, right: 20, top: 20, bottom: 26 },
        xAxis: { type: "category", data: Object.keys(featMap), axisLabel: { fontSize: 11, color: "#6b7280" } },
        yAxis: { type: "value", minInterval: 1 },
        series: [{
          name: "调用次数",
          type: "bar",
          data: Object.values(featMap),
          itemStyle: { color: "#10b981", borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 38
        }]
      }, true);
    }

    // 4. Token 消耗占比分析
    if (tokenShareRef.value && rows.value?.length) {
      tokenShareChart = tokenShareChart || echarts.init(tokenShareRef.value);
      const tokenMap = {};
      rows.value.forEach(r => {
        const feat = r.featureCode || "其他";
        tokenMap[feat] = (tokenMap[feat] || 0) + (r.totalTokens || 0);
      });
      tokenShareChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}: {c} Tokens ({d}%)" },
        legend: { bottom: 0, icon: "circle", textStyle: { fontSize: 11 } },
        series: [{
          type: "pie",
          radius: ["40%", "68%"],
          center: ["50%", "45%"],
          data: Object.entries(tokenMap).map(([name, value]) => ({ name, value }))
        }]
      }, true);
    }
  });
}

function handleResize() {
  modelShareChart?.resize();
  latencyChart?.resize();
  featureTrafficChart?.resize();
  tokenShareChart?.resize();
}

onMounted(async () => {
  await loadOptions();
  await search();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  modelShareChart?.dispose();
  latencyChart?.dispose();
  featureTrafficChart?.dispose();
  tokenShareChart?.dispose();
});
</script>

<style scoped>
.ai-call-page { display: flex; min-width: 0; flex-direction: column; gap: 12px; padding: 12px; }
.call-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.call-filters { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 8px; }
.filter-control { width: 132px; }.filter-control.wide { width: 164px; }.model-filter { width: 204px; }
.toolbar-actions { display: flex; flex: 0 0 auto; gap: 8px; }
.scoped-summary { display: flex; min-height: 44px; align-items: stretch; overflow-x: auto; background: var(--lm-admin-surface-subtle); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.scoped-summary > span { display: flex; min-width: 118px; align-items: center; gap: 8px; padding: 8px 12px; border-right: 1px solid var(--lm-admin-border); white-space: nowrap; }
.scoped-summary small { color: var(--lm-admin-text-muted); font-size: 11px; }.scoped-summary strong { color: var(--lm-admin-text-strong); font-size: 15px; }
.success-value { color: var(--lm-admin-success) !important; }.failure-value { color: var(--lm-admin-danger) !important; }
.scoped-summary .summary-error { min-width: max-content; margin-left: auto; color: var(--lm-admin-warning); border-right: 0; font-size: 11px; }
.call-table-wrap { min-width: 0; overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.call-table-wrap :deep(.clickable-row) { cursor: pointer; }.call-table-wrap :deep(.clickable-row:hover > td.el-table__cell) { background: #f8fafc; }
.primary-cell, .secondary-cell { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.primary-cell { color: var(--lm-admin-text-strong); font-size: 12px; }.secondary-cell { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 10px; }
.pagination-row { display: flex; justify-content: flex-end; padding: 10px 12px; border-top: 1px solid var(--lm-admin-border); }
.analysis-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.analysis-panel { min-width: 0; padding: 12px; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.analysis-panel h3 { margin: 0 0 4px; color: var(--lm-admin-text-strong); font-size: 13px; }.analysis-chart { width: 100%; height: 190px; }
.detail-lead { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 16px; border-bottom: 1px solid var(--lm-admin-border); }
.detail-lead > div { display: flex; min-width: 0; flex-direction: column; gap: 4px; }.detail-lead strong { color: var(--lm-admin-text-strong); font-size: 15px; }.detail-lead span { color: var(--lm-admin-text-muted); font-size: 12px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; margin: 16px 0; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.detail-grid > div { min-width: 0; padding: 10px 12px; border-right: 1px solid var(--lm-admin-border); border-bottom: 1px solid var(--lm-admin-border); }.detail-grid > div:nth-child(2n) { border-right: 0; }.detail-grid > div:nth-last-child(-n + 2) { border-bottom: 0; }
.detail-grid dt, .identity-list dt { color: var(--lm-admin-text-muted); font-size: 11px; }.detail-grid dd { overflow: hidden; margin: 3px 0 0; color: var(--lm-admin-text-strong); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.identity-list { margin: 0; }.identity-list > div { padding: 10px 0; border-bottom: 1px solid var(--lm-admin-border); }.identity-list dd { display: flex; min-width: 0; align-items: center; gap: 8px; margin: 4px 0 0; }.identity-list code { min-width: 0; flex: 1; overflow-wrap: anywhere; color: var(--lm-admin-text-default); font-size: 11px; }
.error-block { margin-bottom: 16px; padding: 12px; color: var(--lm-admin-danger); background: #fef2f2; border: 1px solid #fecaca; border-radius: var(--lm-admin-radius-control); }.error-block p { margin: 4px 0 0; font-size: 12px; }
@media (max-width: 1100px) { .call-toolbar { align-items: stretch; flex-direction: column; }.toolbar-actions { justify-content: flex-end; }.analysis-row { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .filter-control, .filter-control.wide, .model-filter { width: calc(50% - 4px); }.pagination-row { justify-content: flex-start; overflow-x: auto; }.detail-grid { grid-template-columns: 1fr; }.detail-grid > div { border-right: 0; }.detail-grid > div:nth-last-child(-n + 2) { border-bottom: 1px solid var(--lm-admin-border); }.detail-grid > div:last-child { border-bottom: 0; } }
</style>
