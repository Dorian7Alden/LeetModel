<template>
  <div class="dashboard" v-loading="loading">
    <!-- Row 1: Stat cards -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in stats" :key="item.title">
        <el-card shadow="never" class="stat-card-wrapper">
          <StatCard :title="item.title" :value="item.value" :icon="item.icon" :color="item.color"
            :bg-color="item.bgColor" :hover="true" />
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
          <el-table :data="recentSubmissions" size="default" stripe class="submission-table" empty-text="暂无提交">
            <el-table-column prop="username" label="用户" min-width="100">
              <template #default="{ row }">
                <span class="table-user">{{ row.username }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="作品标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small" effect="plain" round>
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
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
import { ElMessage } from "element-plus";
import StatCard from "@/components/common/StatCard.vue";
import { getDashboard } from "@/api/dashboard";

const statusColorMap = {
  "已发布": "#16a34a",
  "草稿": "#d97706",
  "已下线": "#64748b",
  "已归档": "#dc2626",
};

const cardConfigs = [
  { key: "totalProblems", title: "总题目数", icon: "DocumentCopy", color: "#2563eb", bgColor: "#eff6ff" },
  { key: "totalSubmissions", title: "总作品数", icon: "DataLine", color: "#16a34a", bgColor: "#f0fdf4" },
  { key: "todaySubmissions", title: "今日提交", icon: "Upload", color: "#d97706", bgColor: "#fffbeb" },
  { key: "pendingReviews", title: "待审核", icon: "Warning", color: "#dc2626", bgColor: "#fef2f2" },
];

const loading = ref(true);
const stats = ref([]);
const recentSubmissions = ref([]);
const submissionTrendData = ref([]);
const problemStatusDistData = ref([]);

function statusType(status) {
  const map = { COMPLETED: "success", EVALUATING: "warning", PENDING: "info", FAILED: "danger" };
  return map[status] || "info";
}

function statusLabel(status) {
  const map = { COMPLETED: "已完成", EVALUATING: "评审中", PENDING: "待评审", FAILED: "未通过" };
  return map[status] || status;
}

async function fetchDashboard() {
  try {
    const res = await getDashboard();
    if (res.code === 20000) {
      const data = res.data;
      stats.value = cardConfigs.map((c) => ({
        ...c,
        value: data.stats[c.key],
      }));
      recentSubmissions.value = data.recentSubmissions || [];
      submissionTrendData.value = data.submissionTrend || [];
      problemStatusDistData.value = (data.problemStatusDist || []).map((item) => ({
        ...item,
        color: statusColorMap[item.name] || "#64748b",
      }));
      await nextTick();
      initTrendChart();
      initPieChart();
    } else {
      ElMessage.error(res.msg || "获取概览数据失败");
    }
  } catch {
    ElMessage.error("获取概览数据失败，请检查网络");
  } finally {
    loading.value = false;
  }
}

// --- ECharts: Submission Trend (line chart) ---
const trendChartRef = ref(null);
let trendChartInstance = null;

function initTrendChart() {
  if (!trendChartRef.value) return;
  if (trendChartInstance) {
    trendChartInstance.dispose();
    trendChartInstance = null;
  }
  trendChartInstance = echarts.init(trendChartRef.value);

  const dates = submissionTrendData.value.map((d) => d.date);
  const values = submissionTrendData.value.map((d) => d.count);

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
      minInterval: 1,
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
  if (pieChartInstance) {
    pieChartInstance.dispose();
    pieChartInstance = null;
  }
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
        data: problemStatusDistData.value.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: item.color },
        })),
      },
    ],
  });
}

onMounted(() => {
  fetchDashboard();
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
@import '../style.css';
</style>
