<template>
  <div class="dashboard">
    <!-- Row 1: Stat cards -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in stats" :key="item.title">
        <el-card shadow="never" class="stat-card-wrapper">
          <StatCard
            :title="item.title"
            :value="item.value"
            :icon="item.icon"
            :color="item.color"
            :bg-color="item.bgColor"
            :trend="item.trend"
            :trend-up="item.trendUp"
            :subtitle="item.subtitle"
            :hover="true"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- Row 2: Submission trend chart -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">提交趋势</span>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Row 3: Recent submissions + Problem status distribution -->
    <el-row :gutter="20" class="bottom-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">最近提交</span>
              <el-button text type="primary" size="small">查看全部</el-button>
            </div>
          </template>
          <el-table
            :data="recentSubmissions"
            size="default"
            stripe
            class="submission-table"
          >
            <el-table-column prop="userName" label="用户" min-width="100">
              <template #default="{ row }">
                <span class="table-user">{{ row.userName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="作品标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="statusType(row.status)"
                  size="small"
                  effect="plain"
                  round
                >
                  {{ statusLabel(row.status) }}
                </el-tag>
              </el-table-column>
            </el-table-column>
            <el-table-column prop="totalScore" label="得分" width="80" align="center">
              <template #default="{ row }">
                <span class="score-cell" :class="{ 'no-score': row.totalScore === null }">
                  {{ row.totalScore !== null ? row.totalScore : '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="submitTime" label="提交时间" width="110" align="center" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">题目状态分布</span>
            </div>
          </template>
          <div ref="pieChartRef" class="chart-container pie-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import * as echarts from "echarts";
import StatCard from "@/components/common/StatCard.vue";
import {
  mockDashboardStats,
  mockSubmissionTrend,
  mockProblemStatusDist,
  mockSubmissions,
} from "@/mock/data.js";

// --- Stats ---
const stats = ref(mockDashboardStats);

// --- Recent submissions (last 5) ---
const recentSubmissions = ref(
  mockSubmissions
    .slice()
    .sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
    .slice(0, 5)
);

function statusType(status) {
  const map = { COMPLETED: "success", EVALUATING: "warning", PENDING: "info", FAILED: "danger" };
  return map[status] || "info";
}

function statusLabel(status) {
  const map = { COMPLETED: "已完成", EVALUATING: "评审中", PENDING: "待评审", FAILED: "未通过" };
  return map[status] || status;
}

// --- ECharts: Submission Trend (line chart) ---
const trendChartRef = ref(null);
let trendChartInstance = null;

function initTrendChart() {
  if (!trendChartRef.value) return;
  trendChartInstance = echarts.init(trendChartRef.value);

  const dates = mockSubmissionTrend.map((d) => d.date);
  const values = mockSubmissionTrend.map((d) => d.count);

  trendChartInstance.setOption({
    tooltip: {
      trigger: "axis",
      backgroundColor: "#fff",
      borderColor: "#e2e8f0",
      textStyle: { color: "#1e293b", fontSize: 13 },
      boxShadow: "0 4px 16px rgba(0,0,0,0.06)",
      formatter: (params) => {
        const item = params[0];
        return `<div style="font-weight:600;margin-bottom:4px">${item.axisValue}</div>
                <div style="color:#64748b">提交数 <span style="color:#2563eb;font-weight:600">${item.value}</span></div>`;
      },
    },
    grid: { left: "3%", right: "4%", bottom: "3%", top: "10%", containLabel: true },
    xAxis: {
      type: "category",
      data: dates,
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisTick: { show: false },
      axisLabel: { color: "#94a3b8", fontSize: 12 },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "#f1f5f9", type: "dashed" } },
      axisLabel: { color: "#94a3b8", fontSize: 12 },
    },
    series: [
      {
        data: values,
        type: "line",
        smooth: true,
        symbol: "circle",
        symbolSize: 6,
        lineStyle: { color: "#2563eb", width: 3 },
        itemStyle: {
          color: "#2563eb",
          borderColor: "#fff",
          borderWidth: 2,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: "rgba(37, 99, 235, 0.12)" },
            { offset: 1, color: "rgba(37, 99, 235, 0.01)" },
          ]),
        },
      },
    ],
  });
}

// --- ECharts: Problem Status Distribution (donut chart) ---
const pieChartRef = ref(null);
let pieChartInstance = null;

function initPieChart() {
  if (!pieChartRef.value) return;
  pieChartInstance = echarts.init(pieChartRef.value);

  pieChartInstance.setOption({
    tooltip: {
      trigger: "item",
      backgroundColor: "#fff",
      borderColor: "#e2e8f0",
      textStyle: { color: "#1e293b", fontSize: 13 },
      formatter: "{b}: {c} 题 ({d}%)",
    },
    series: [
      {
        type: "pie",
        radius: ["55%", "80%"],
        center: ["50%", "48%"],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: "#fff",
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: "bold",
          },
        },
        data: mockProblemStatusDist.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: item.color },
        })),
      },
    ],
  });
}

// --- Lifecycle ---
onMounted(async () => {
  await nextTick();
  initTrendChart();
  await nextTick();
  initPieChart();
});

onBeforeUnmount(() => {
  if (trendChartInstance) {
    trendChartInstance.dispose();
    trendChartInstance = null;
  }
  if (pieChartInstance) {
    pieChartInstance.dispose();
    pieChartInstance = null;
  }
});
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== Stat Row ===== */
.stat-row {
  margin: 0;
}

.stat-card-wrapper {
  border-radius: var(--lm-radius);
  border: 1px solid var(--lm-border);
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.stat-card-wrapper :deep(.el-card__body) {
  padding: 0;
}

/* ===== Chart Card ===== */
.chart-card {
  border-radius: var(--lm-radius);
  border: 1px solid var(--lm-border);
  height: 100%;
}

.chart-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--lm-border-light);
}

.chart-card :deep(.el-card__body) {
  padding: 12px 8px 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary);
}

/* ===== Chart containers ===== */
.chart-container {
  width: 100%;
  height: 340px;
}

.pie-container {
  height: 320px;
}

/* ===== Table Card ===== */
.table-card {
  border-radius: var(--lm-radius);
  border: 1px solid var(--lm-border);
  height: 100%;
}

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--lm-border-light);
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.submission-table {
  font-size: 13px;
}

.table-user {
  color: var(--lm-primary);
  font-weight: 500;
}

.score-cell {
  font-weight: 600;
  color: var(--lm-text-primary);
}

.score-cell.no-score {
  color: var(--lm-text-muted);
}

/* ===== Bottom Row ===== */
.bottom-row {
  margin: 0;
}

.chart-row {
  margin: 0;
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .stat-row .el-col {
    margin-bottom: 16px;
  }
  .chart-container,
  .pie-container {
    height: 260px;
  }
}
</style>
