<template>
  <div class="dashboard-page" v-loading="loading">
    <el-alert
      v-if="partialFailure"
      title="部分下游服务不可用，以下指标未显示真实数值"
      type="warning"
      :closable="false"
      show-icon
      class="partial-alert"
    />

    <div class="panel-heading">
      <div>
        <h2 class="page-title">运行概况</h2>
        <p class="page-subtitle">来自各领域服务的真实统计摘要，更新于 {{ generatedAt }}</p>
      </div>
      <el-button :loading="loading" @click="fetchDashboard">刷新</el-button>
    </div>

    <div class="metric-grid">
      <div
        v-for="item in metricCards"
        :key="item.key"
        class="metric-card"
        :class="{ unavailable: !item.available && !item.loading, loading: item.loading }"
      >
        <div class="metric-icon" :style="{ background: item.bgColor, color: item.color }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-title">{{ item.title }}</span>
          <strong class="metric-value">{{ item.loading ? '···' : (item.available ? item.value : '--') }}</strong>
          <span v-if="!item.available" class="metric-message">{{ item.loading ? '加载中' : item.message }}</span>
        </div>
      </div>
    </div>

    <section class="charts-section">
      <div class="chart-row">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-head">
              <h3 class="chart-title">各领域业务量</h3>
              <span class="chart-sub">来自各服务的真实统计</span>
            </div>
          </template>
          <div ref="metricChartRef" class="chart-container"></div>
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-head">
              <h3 class="chart-title">AI 调用状态</h3>
              <span class="chart-sub">最近一次统计结果</span>
            </div>
          </template>
          <div ref="aiChartRef" class="chart-container"></div>
        </el-card>
      </div>
    </section>

    <section class="quick-section">
      <h3 class="section-title">管理入口</h3>
      <div class="quick-grid">
        <router-link v-for="item in quickLinks" :key="item.path" :to="item.path" class="quick-link">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </router-link>
      </div>
    </section>

    <section class="note-section">
      <el-card shadow="never">
        <template #header>关于数据</template>
        <p>所有指标均由 admin-service 实时聚合，单个下游失败会独立标记为不可用，不会用零值掩盖故障。</p>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getDashboard } from "@/api/dashboard";
import { getAdminAiCallStats } from "@/api/admin-ai";

const loading = ref(false);
const metrics = ref({});
const partialFailure = ref(false);
const generatedAt = ref("");
const aiStats = ref(null);

const metricChartRef = ref(null);
const aiChartRef = ref(null);
let metricChart = null;
let aiChart = null;

const cardConfigs = [
  { key: "users", title: "用户", icon: "User", color: "#2563eb", bgColor: "#eff6ff" },
  { key: "teams", title: "队伍", icon: "UserFilled", color: "#0891b2", bgColor: "#ecfeff" },
  { key: "problems", title: "题目", icon: "Document", color: "#16a34a", bgColor: "#f0fdf4" },
  { key: "submissions", title: "提交", icon: "Upload", color: "#d97706", bgColor: "#fffbeb" },
  { key: "reviews", title: "评审", icon: "DataAnalysis", color: "#7c3aed", bgColor: "#f5f3ff" },
  { key: "suggestions", title: "建议", icon: "ChatDotRound", color: "#db2777", bgColor: "#fdf2f8" },
  { key: "rankings", title: "排行", icon: "Trophy", color: "#d97706", bgColor: "#fffbeb" },
  { key: "assistantConversations", title: "客服会话", icon: "PieChart", color: "#0d9488", bgColor: "#f0fdfa" },
  { key: "evaluationTasks", title: "质量评价", icon: "Histogram", color: "#475569", bgColor: "#f8fafc" },
  { key: "aiCalls", title: "AI 调用", icon: "Cpu", color: "#2563eb", bgColor: "#eff6ff" },
];

const quickLinks = [
  { path: "/admin/problem/list", title: "题目管理", icon: "Document" },
  { path: "/admin/tags/list", title: "标签管理", icon: "Collection" },
  { path: "/admin/users/list", title: "用户管理", icon: "User" },
  { path: "/admin/role/list", title: "角色管理", icon: "UserFilled" },
  { path: "/admin/submissions/list", title: "提交管理", icon: "Upload" },
  { path: "/admin/teams/list", title: "队伍管理", icon: "UserFilled" },
  { path: "/admin/reviews/list", title: "评审管理", icon: "DataAnalysis" },
  { path: "/admin/suggestions/list", title: "建议管理", icon: "ChatDotRound" },
  { path: "/admin/rankings/list", title: "排行榜管理", icon: "Trophy" },
  { path: "/admin/ai-calls/list", title: "AI 调用", icon: "Cpu" },
  { path: "/admin/evaluations/list", title: "质量评价", icon: "Histogram" },
];

const metricCards = computed(() =>
  cardConfigs.map((config) => {
    const metric = metrics.value[config.key];
    if (!metric) return { ...config, available: false, loading: loading.value, value: "--", message: "暂不可用" };
    return {
      ...config,
      available: metric.available !== false,
      value: metric.value,
      message: metric.message || "暂不可用",
    };
  }),
);

async function fetchDashboard() {
  loading.value = true;
  try {
    const res = await getDashboard();
    metrics.value = res.data?.metrics || {};
    partialFailure.value = !!res.data?.partialFailure;
    if (res.data?.generatedAt) {
      generatedAt.value = String(res.data.generatedAt).replace("T", " ").slice(0, 19);
    }
  } catch (error) {
    ElMessage.error(error.message || "概览数据加载失败");
    metrics.value = {};
  } finally {
    loading.value = false;
  }
}

async function loadAiStats() {
  try {
    aiStats.value = (await getAdminAiCallStats()).data;
  } catch {
    aiStats.value = null;
  }
}

function renderCharts() {
  nextTick(() => {
    // 各领域业务量柱状图
    if (metricChartRef.value) {
      metricChart = metricChart ? metricChart : echarts.init(metricChartRef.value);
      const data = cardConfigs
        .map((config) => {
          const metric = metrics.value[config.key];
          const available = metric && metric.available !== false;
          return { name: config.title, value: available ? (metric.value ?? 0) : 0, color: config.color, show: available };
        })
        .filter((item) => item.show);
      metricChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        grid: { left: "3%", right: "4%", bottom: "3%", top: "12%", containLabel: true },
        xAxis: { type: "category", data: data.map((d) => d.name), axisLabel: { color: "#64748b", fontSize: 11 } },
        yAxis: { type: "value", minInterval: 1, splitLine: { lineStyle: { color: "#f1f5f9" } } },
        series: [{
          type: "bar",
          data: data.map((d) => ({ value: d.value, itemStyle: { color: d.color, borderRadius: [4, 4, 0, 0] } })),
          barWidth: "52%",
        }],
      }, true);
    }

    // AI 调用状态环形图
    if (aiChartRef.value) {
      aiChart = aiChart ? aiChart : echarts.init(aiChartRef.value);
      const stats = aiStats.value;
      const success = stats?.successCount ?? 0;
      const failed = stats?.failureCount ?? 0;
      aiChart.setOption({
        tooltip: { trigger: "item" },
        legend: { bottom: 4, textStyle: { color: "#64748b" } },
        series: [{
          type: "pie",
          radius: ["52%", "74%"],
          center: ["50%", "44%"],
          avoidLabelOverlap: false,
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: "bold" } },
          data: [
            { name: "成功", value: success, itemStyle: { color: "#16a34a" } },
            { name: "失败", value: failed, itemStyle: { color: "#dc2626" } },
          ],
        }],
      }, true);
    }
  });
}

function handleResize() {
  metricChart?.resize();
  aiChart?.resize();
}

onMounted(async () => {
  await fetchDashboard();
  await loadAiStats();
  renderCharts();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  metricChart?.dispose();
  aiChart?.dispose();
  metricChart = null;
  aiChart = null;
});
</script>

<style scoped>
.dashboard-page { padding: 24px; }
.partial-alert { margin-bottom: 20px; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 22px; color: var(--lm-text-primary); }
.page-subtitle { margin: 6px 0 0; font-size: 13px; color: var(--lm-text-muted); }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
.metric-card { display: flex; align-items: center; gap: 14px; padding: 18px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.metric-card.unavailable { border-style: dashed; background: var(--lm-bg-secondary); }
.metric-icon { display: flex; width: 42px; height: 42px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 10px; }
.metric-body { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.metric-title { font-size: 12px; color: var(--lm-text-muted); }
.metric-value { font-size: 24px; line-height: 1.2; color: var(--lm-text-primary); }
.metric-message { font-size: 11px; color: var(--lm-warning); }
.charts-section { margin-top: 28px; }
.chart-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.chart-card :deep(.el-card__header) { padding: 14px 18px; }
.chart-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.chart-title { margin: 0; font-size: 16px; color: var(--lm-text-primary); }
.chart-sub { color: var(--lm-text-muted); font-size: 12px; }
.chart-container { width: 100%; height: 260px; }
.quick-section { margin-top: 32px; }
.section-title { margin: 0 0 14px; font-size: 16px; color: var(--lm-text-primary); }
.quick-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.quick-link { display: flex; align-items: center; gap: 8px; padding: 14px 16px; color: var(--lm-text-secondary); background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 10px; text-decoration: none; transition: border-color var(--lm-transition), color var(--lm-transition); }
.quick-link:hover { color: var(--lm-primary); border-color: var(--lm-primary); }
.note-section { margin-top: 32px; }
.note-section p { margin: 0; color: var(--lm-text-secondary); font-size: 13px; line-height: 1.7; }
@media (max-width: 1200px) { .metric-grid, .quick-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1200px) { .chart-row { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .metric-grid, .quick-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .dashboard-page { padding: 16px; } }
</style>
