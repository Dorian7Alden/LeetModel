<template>
  <div class="skill-page">
    <h2 class="page-title">能力分析</h2>
    <p class="page-subtitle">基于你在已完成评审中获得的各维度得分取平均水平</p>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="load-alert">
      <template #default><el-button type="primary" link @click="load">重新加载</el-button></template>
    </el-alert>

    <div v-loading="loading">
      <el-row v-if="hasData" :gutter="24">
        <el-col :lg="12" :sm="24">
          <div class="chart-card">
            <h3 class="chart-title">维度能力雷达</h3>
            <div ref="radarRef" class="chart-container"></div>
          </div>
        </el-col>
        <el-col :lg="12" :sm="24">
          <div class="chart-card">
            <h3 class="chart-title">各维度得分对比</h3>
            <div ref="barRef" class="chart-container"></div>
          </div>
        </el-col>
      </el-row>

      <div v-if="hasData" class="summary-section">
        <h3 class="section-title">评估小结</h3>
        <div class="summary-list">
          <div v-for="item in dimensionSummaries" :key="item.key" class="summary-card" :class="levelClass(item.score)">
            <div class="summary-header">
              <span class="summary-label">{{ item.label }}</span>
              <el-tag :type="itemType(item.score)" size="small">{{ levelLabel(item.score) }}</el-tag>
            </div>
            <div class="summary-bar-wrap">
              <div class="summary-bar"><div class="summary-fill" :style="{ width: item.score + '%', background: item.color }"></div></div>
              <span class="summary-score">{{ item.score }}/100</span>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !hasData" description="暂无已完成评审，完成一次论文评审后这里会展示能力画像" :image-size="90" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getAllMyTeams } from "@/api/team";
import { getTeamReviews } from "@/api/review";

const radarRef = ref(null);
const barRef = ref(null);
const loading = ref(false);
const loadError = ref("");
let radarChart = null;
let barChart = null;

const dimensionDefs = [
  { key: "assumptionRationality", label: "假设合理性", color: "#2563eb" },
  { key: "modelCreativity", label: "建模创造性", color: "#16a34a" },
  { key: "resultCorrectness", label: "结果正确性", color: "#d97706" },
  { key: "expressionClarity", label: "表达清晰性", color: "#7c3aed" },
];
const scores = ref([]);

const hasData = computed(() => scores.value.length > 0);
const dimensionSummaries = computed(() => {
  if (!scores.value.length) return [];
  const sum = {};
  let overall = 0;
  let count = 0;
  for (const dimension of scores.value) {
    for (const def of dimensionDefs) {
      const value = dimension[def.key];
      if (typeof value === "number") {
        sum[def.key] = (sum[def.key] || 0) + value;
        count += 1;
      }
    }
    if (typeof dimension.overall === "number") overall += dimension.overall;
  }
  return dimensionDefs.map((def) => {
    const value = sum[def.key] != null ? (sum[def.key] / scores.value.length) : null;
    return { key: def.key, label: def.label, color: def.color, score: value == null ? 0 : Math.round(value) };
  });
});

function levelLabel(score) {
  if (score >= 85) return "优秀";
  if (score >= 70) return "良好";
  if (score >= 60) return "合格";
  return "待提升";
}

function itemType(score) {
  if (score >= 85) return "success";
  if (score >= 70) return "primary";
  if (score >= 60) return "warning";
  return "danger";
}

function levelClass(score) {
  if (score >= 85) return "good";
  if (score >= 60) return "medium";
  return "weak";
}

function parseResult(review) {
  if (!review?.resultJson) return null;
  try { return typeof review.resultJson === "string" ? JSON.parse(review.resultJson) : review.resultJson; }
  catch { return null; }
}

function renderCharts() {
  if (scores.value.length === 0) return;
  nextTick(() => {
    if (radarRef.value) {
      radarChart = echarts.init(radarRef.value);
      radarChart.setOption({
        tooltip: { trigger: "item" },
        radar: {
          indicator: dimensionSummaries.value.map((d) => ({ name: d.label, max: 100 })),
          radius: "72%",
          axisName: { color: "#64748b", fontSize: 12 },
        },
        series: [{
          type: "radar",
          data: [{
            value: dimensionSummaries.value.map((d) => d.score),
            name: "你的能力值",
            areaStyle: { color: "rgba(37, 99, 235, 0.2)" },
            lineStyle: { color: "#2563eb", width: 2 },
            itemStyle: { color: "#2563eb" },
            symbol: "circle",
            symbolSize: 6,
          }],
        }],
      });
    }
    if (barRef.value) {
      barChart = echarts.init(barRef.value);
      barChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        grid: { left: "3%", right: "8%", bottom: "5%", top: "8%", containLabel: true },
        xAxis: { type: "category", data: dimensionSummaries.value.map((d) => d.label), axisLabel: { fontSize: 11, color: "#64748b" } },
        yAxis: { type: "value", min: 0, max: 100, interval: 20 },
        series: [{
          type: "bar",
          data: dimensionSummaries.value.map((d) => ({ value: d.score, itemStyle: { color: d.color, borderRadius: [6, 6, 0, 0] } })),
          barWidth: "48%",
          label: { show: true, position: "top", formatter: "{c}", fontSize: 12, color: "#1e293b" },
        }],
      });
    }
  });
}

function handleResize() {
  radarChart?.resize();
  barChart?.resize();
}

async function load() {
  loading.value = true;
  loadError.value = "";
  const results = [];
  try {
    const { rows: myTeams } = await getAllMyTeams({ page: 1, pageSize: 50 });
    for (const team of myTeams) {
      try {
        const res = await getTeamReviews(team.id);
        for (const review of res.data || []) {
          if (review.status !== "COMPLETED") continue;
          const parsed = parseResult(review);
          if (!parsed?.dimensions) continue;
          const dimensions = parsed.dimensions;
          results.push({
            assumptionRationality: dimensions.assumptionRationality,
            modelCreativity: dimensions.modelCreativity,
            resultCorrectness: dimensions.resultCorrectness,
            expressionClarity: dimensions.expressionClarity,
            overall: review.score,
          });
        }
      } catch {
        // 跳过单个队伍
      }
    }
    scores.value = results;
    renderCharts();
  } catch (error) {
    loadError.value = error.message || "能力分析加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  load();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  radarChart?.dispose();
  barChart?.dispose();
});
</script>

<style scoped>
.skill-page { padding: 24px 30px 40px; }
.page-title { margin: 0; font-size: 22px; color: var(--lm-text-primary); }
.page-subtitle { margin: 6px 0 22px; color: var(--lm-text-muted); font-size: 13px; }
.load-alert { margin-bottom: 18px; }
.chart-card { padding: 24px; margin-bottom: 24px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.chart-title { margin: 0 0 16px; font-size: 16px; color: var(--lm-text-primary); }
.chart-container { width: 100%; height: 340px; }
.summary-section { margin-top: 8px; }
.section-title { margin: 0 0 16px; font-size: 18px; color: var(--lm-text-primary); }
.summary-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.summary-card { padding: 18px 20px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-left-width: 4px; border-radius: 10px; }
.summary-card.weak { border-left-color: #dc2626; }
.summary-card.medium { border-left-color: #d97706; }
.summary-card.good { border-left-color: #16a34a; }
.summary-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.summary-label { font-size: 15px; font-weight: 600; color: var(--lm-text-primary); }
.summary-bar-wrap { display: flex; align-items: center; gap: 12px; }
.summary-bar { flex: 1; height: 8px; border-radius: 8px; overflow: hidden; background: var(--lm-bg-secondary); }
.summary-fill { height: 100%; border-radius: 8px; transition: width .6s ease; }
.summary-score { font-size: 14px; font-weight: 700; color: var(--lm-text-primary); }
@media (max-width: 768px) { .chart-container { height: 260px; } .summary-list { grid-template-columns: 1fr; } }
</style>
