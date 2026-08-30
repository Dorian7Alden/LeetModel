<template>
  <div class="ranking-admin-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">排行榜管理</h2>
        <div class="toolbar-actions">
          <el-select v-model="problemId" filterable placeholder="请选择题目标题" :loading="loadingProblems" style="width: 320px" @change="load">
            <el-option v-for="p in problems" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="按队伍名称筛选" clearable style="width: 220px" @keyup.enter="load" @clear="load" />
          <el-button :loading="loading" @click="load">查询</el-button>
          <el-button type="warning" plain :disabled="!problemId" :loading="rebuilding" @click="rebuild">重建榜单</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="ranking" class="ranking-meta">
          <span>共 {{ ranking.total }} 支队伍上榜</span>
          <span v-if="ranking.computedAt">计算于 {{ formatTime(ranking.computedAt) }}</span>
          <span v-if="ranking.batchId">批次 {{ ranking.batchId }}</span>
        </div>
        <div v-if="ranking?.items?.length" class="ranking-chart-grid">
          <section class="chart-panel chart-panel-wide">
            <div class="chart-heading">
              <div><h3>分数分布</h3><p>0–100 分逐分统计，每根竖条代表该分数的队伍数</p></div>
              <el-tag effect="plain">{{ ranking.items.length }} 个样本</el-tag>
            </div>
            <div ref="histogramRef" class="chart-canvas" />
          </section>
          <section class="chart-panel">
            <div class="chart-heading"><div><h3>分段占比</h3><p>快速识别成绩集中区间</p></div></div>
            <div ref="bandPieRef" class="chart-canvas" />
          </section>
        </div>
        <el-table :data="ranking?.items || []" stripe style="width: 100%">
          <el-table-column label="排名" width="90" align="center">
            <template #default="{ row }">{{ row.rank }}</template>
          </el-table-column>
          <el-table-column prop="teamName" label="队伍" min-width="200" />
          <el-table-column prop="teamId" label="队伍 ID" width="110" />
          <el-table-column label="得分" width="110" align="center">
            <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
          </el-table-column>
          <el-table-column prop="workflowVersion" label="版本" width="130" />
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
          <template #empty><el-empty :description="problemId ? '当前题目暂无上榜提交' : '请先选择题目'" /></template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getPublicProblemList } from "@/api/problem";
import { getAdminRanking, rebuildAdminRanking } from "@/api/admin-ops";

const problems = ref([]);
const problemId = ref(null);
const keyword = ref("");
const loading = ref(false);
const loadingProblems = ref(false);
const rebuilding = ref(false);
const ranking = ref(null);
const histogramRef = ref(null);
const bandPieRef = ref(null);
let histogramChart;
let bandPieChart;

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function loadProblems() {
  loadingProblems.value = true;
  try {
    problems.value = (await getPublicProblemList({ page: 1, pageSize: 100 })).data?.rows || [];
  } catch (error) {
    ElMessage.error(error.message || "题目列表加载失败");
  } finally {
    loadingProblems.value = false;
  }
}

async function load() {
  if (!problemId.value) return;
  loading.value = true;
  try {
    ranking.value = (await getAdminRanking(problemId.value, keyword.value.trim())).data;
    renderCharts();
  } catch (error) {
    ElMessage.error(error.message || "排行榜加载失败");
    ranking.value = null;
  } finally {
    loading.value = false;
  }
}

function normalizedScores() {
  return (ranking.value?.items || [])
    .map((item) => Number(item.score))
    .filter((score) => Number.isFinite(score))
    .map((score) => Math.min(100, Math.max(0, Math.round(score))));
}

function renderCharts() {
  nextTick(() => {
    const scores = normalizedScores();
    if (!scores.length) return;
    const counts = Array.from({ length: 101 }, () => 0);
    scores.forEach((score) => { counts[score] += 1; });

    if (histogramRef.value) {
      histogramChart = histogramChart || echarts.init(histogramRef.value);
      histogramChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (items) => `${items[0].axisValue} 分：${items[0].value} 支队伍` },
        grid: { left: 44, right: 16, top: 18, bottom: 42 },
        xAxis: {
          type: "category",
          data: Array.from({ length: 101 }, (_, index) => index),
          axisTick: { alignWithLabel: true },
          axisLabel: { interval: 9, color: "#64748b" },
          name: "分数",
          nameLocation: "middle",
          nameGap: 28,
        },
        yAxis: { type: "value", minInterval: 1, name: "队伍数", splitLine: { lineStyle: { color: "#eef2f7" } } },
        series: [{ type: "bar", data: counts, barCategoryGap: "8%", itemStyle: { color: "#6366f1", borderRadius: [2, 2, 0, 0] } }],
      }, true);
    }

    if (bandPieRef.value) {
      bandPieChart = bandPieChart || echarts.init(bandPieRef.value);
      const bands = [
        { name: "0–59", min: 0, max: 59, color: "#ef4444" },
        { name: "60–69", min: 60, max: 69, color: "#f59e0b" },
        { name: "70–79", min: 70, max: 79, color: "#eab308" },
        { name: "80–89", min: 80, max: 89, color: "#22c55e" },
        { name: "90–100", min: 90, max: 100, color: "#6366f1" },
      ];
      bandPieChart.setOption({
        tooltip: { trigger: "item", formatter: "{b} 分：{c} 支（{d}%）" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
        series: [{
          type: "pie",
          radius: ["48%", "72%"],
          center: ["50%", "43%"],
          label: { formatter: "{d}%", color: "#475569" },
          data: bands.map((band) => ({
            name: band.name,
            value: scores.filter((score) => score >= band.min && score <= band.max).length,
            itemStyle: { color: band.color },
          })),
        }],
      }, true);
    }
  });
}

function handleResize() {
  histogramChart?.resize();
  bandPieChart?.resize();
}

async function rebuild() {
  if (!problemId.value) return;
  rebuilding.value = true;
  try {
    const res = await rebuildAdminRanking(problemId.value);
    ElMessage.success(`榜单已重建（${res.data} 条）`);
    await load();
  } catch (error) {
    ElMessage.error(error.message || "重建失败");
  } finally {
    rebuilding.value = false;
  }
}

onMounted(() => {
  loadProblems();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  histogramChart?.dispose();
  bandPieChart?.dispose();
});
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.ranking-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; color: var(--lm-text-muted); font-size: 13px; }
.ranking-chart-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr); gap: 16px; margin-bottom: 20px; }
.chart-panel { min-width: 0; padding: 16px; border: 1px solid var(--lm-border); border-radius: 12px; background: var(--lm-surface); }
.chart-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.chart-heading h3 { margin: 0 0 5px; color: var(--lm-text-primary); font-size: 15px; }
.chart-heading p { margin: 0; color: var(--lm-text-muted); font-size: 12px; }
.chart-canvas { width: 100%; height: 280px; }
@media (max-width: 980px) { .ranking-chart-grid { grid-template-columns: 1fr; } }
</style>
