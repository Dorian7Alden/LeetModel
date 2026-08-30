<template>
  <div class="ranking-admin-page">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <h2 class="panel-title">排行榜分析</h2>
          <p class="panel-subtitle">{{ problemId ? '查看指定题目的队伍名次与分数结构' : '全局比较各题目的提交热度与评审成绩' }}</p>
        </div>
        <div class="toolbar-actions">
          <el-select v-model="problemId" filterable clearable placeholder="全部题目（全局统计）" :loading="loadingProblems" style="width: 340px" @change="handleProblemChange">
            <el-option v-for="p in problems" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="按队伍名称筛选" clearable :disabled="!problemId" style="width: 210px" @keyup.enter="loadDetail" @clear="loadDetail" />
          <el-button :loading="loading" @click="reload">刷新</el-button>
          <el-button type="warning" plain :disabled="!problemId" :loading="rebuilding" @click="rebuild">重建榜单</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <template v-if="!problemId">
          <div class="global-stat-grid">
            <div class="global-stat"><span>成功提交</span><strong>{{ globalStats?.totalSubmissions ?? '—' }}</strong><small>所有题目累计</small></div>
            <div class="global-stat"><span>已评审提交</span><strong>{{ globalStats?.reviewedSubmissions ?? '—' }}</strong><small>按提交取最新完成评审</small></div>
            <div class="global-stat"><span>覆盖题目</span><strong>{{ globalStats?.problemCount ?? '—' }}</strong><small>存在业务事实的题目</small></div>
            <div class="global-stat"><span>整体平均分</span><strong>{{ globalStats?.overallAverageScore ?? '—' }}</strong><small>全部已评审提交</small></div>
            <div class="global-stat"><span>当前上榜队伍</span><strong>{{ globalStats?.rankedTeams ?? '—' }}</strong><small>跨题目去重</small></div>
          </div>

          <div v-if="globalStats?.items?.length" class="global-chart-grid">
            <section class="chart-panel">
              <div class="chart-heading"><div><h3>题目提交热度</h3><p>成功提交次数越高，题目参与活跃度越高</p></div></div>
              <div ref="submissionChartRef" class="global-chart-canvas" />
            </section>
            <section class="chart-panel">
              <div class="chart-heading"><div><h3>题目平均得分</h3><p>仅统计已经完成评审的提交</p></div></div>
              <div ref="averageChartRef" class="global-chart-canvas" />
            </section>
          </div>

          <el-table :data="globalStats?.items || []" stripe style="width: 100%">
            <el-table-column label="题目" min-width="250">
              <template #default="{ row }"><strong>{{ row.problemTitle }}</strong><div class="cell-muted">题号 {{ row.problemCode || '—' }}</div></template>
            </el-table-column>
            <el-table-column prop="submissionCount" label="成功提交" width="110" align="center" sortable />
            <el-table-column prop="reviewedSubmissionCount" label="已评审" width="100" align="center" />
            <el-table-column prop="averageScore" label="平均分" width="100" align="center" sortable>
              <template #default="{ row }">{{ row.averageScore ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="highestScore" label="最高分" width="100" align="center" />
            <el-table-column prop="rankedTeamCount" label="上榜队伍" width="110" align="center" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }"><el-button type="primary" link @click="openProblem(row.problemId)">查看题目榜</el-button></template>
            </el-table-column>
            <template #empty><el-empty description="暂无全局排行统计" /></template>
          </el-table>
        </template>

        <template v-else>
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
            <el-table-column label="排名" width="90" align="center"><template #default="{ row }">{{ row.rank }}</template></el-table-column>
            <el-table-column prop="teamName" label="队伍" min-width="200" />
            <el-table-column prop="teamId" label="队伍 ID" width="190" />
            <el-table-column label="得分" width="110" align="center"><template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template></el-table-column>
            <el-table-column prop="workflowVersion" label="版本" width="150" />
            <el-table-column label="提交时间" width="170"><template #default="{ row }">{{ formatTime(row.submittedAt) }}</template></el-table-column>
            <template #empty><el-empty description="当前题目暂无上榜提交，可尝试重建榜单" /></template>
          </el-table>
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getPublicProblemList } from "@/api/problem";
import { getAdminGlobalRankingStats, getAdminRanking, rebuildAdminRanking } from "@/api/admin-ops";

const problems = ref([]);
const problemId = ref(null);
const keyword = ref("");
const loading = ref(false);
const loadingProblems = ref(false);
const rebuilding = ref(false);
const ranking = ref(null);
const globalStats = ref(null);
const histogramRef = ref(null);
const bandPieRef = ref(null);
const submissionChartRef = ref(null);
const averageChartRef = ref(null);
let histogramChart;
let bandPieChart;
let submissionChart;
let averageChart;

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

function shortTitle(item) {
  const prefix = item.problemCode ? `${item.problemCode} · ` : "";
  const title = item.problemTitle || `题目 ${item.problemId}`;
  return `${prefix}${title.length > 18 ? `${title.slice(0, 18)}…` : title}`;
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

async function loadGlobal() {
  loading.value = true;
  ranking.value = null;
  disposeDetailCharts();
  try {
    globalStats.value = (await getAdminGlobalRankingStats()).data;
    renderGlobalCharts();
  } catch (error) {
    ElMessage.error(error.message || "全局排行统计加载失败");
    globalStats.value = null;
  } finally {
    loading.value = false;
  }
}

async function loadDetail() {
  if (!problemId.value) return loadGlobal();
  loading.value = true;
  disposeGlobalCharts();
  try {
    ranking.value = (await getAdminRanking(problemId.value, keyword.value.trim())).data;
    renderDetailCharts();
  } catch (error) {
    ElMessage.error(error.message || "排行榜加载失败");
    ranking.value = null;
  } finally {
    loading.value = false;
  }
}

function handleProblemChange(value) {
  keyword.value = "";
  if (value) loadDetail(); else loadGlobal();
}

function reload() {
  if (problemId.value) loadDetail(); else loadGlobal();
}

function openProblem(id) {
  problemId.value = id;
  keyword.value = "";
  loadDetail();
}

function renderGlobalCharts() {
  nextTick(() => {
    const items = globalStats.value?.items || [];
    if (!items.length) {
      disposeGlobalCharts();
      return;
    }
    const submissionData = [...items].sort((a, b) => Number(a.submissionCount) - Number(b.submissionCount));
    const scoreData = items.filter((item) => item.averageScore != null).sort((a, b) => Number(a.averageScore) - Number(b.averageScore));
    if (submissionChartRef.value) {
      submissionChart = submissionChart || echarts.init(submissionChartRef.value);
      submissionChart.setOption(horizontalBarOption(submissionData, "submissionCount", "次", "#0ea5e9"), true);
    }
    if (averageChartRef.value) {
      averageChart = averageChart || echarts.init(averageChartRef.value);
      averageChart.setOption(horizontalBarOption(scoreData, "averageScore", "分", "#8b5cf6", 100), true);
    }
  });
}

function horizontalBarOption(items, field, unit, color, max) {
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (values) => `${values[0].name}<br/>${values[0].value} ${unit}` },
    grid: { left: 150, right: 28, top: 14, bottom: 28 },
    xAxis: { type: "value", max, minInterval: field === "submissionCount" ? 1 : undefined, splitLine: { lineStyle: { color: "#eef2f7" } } },
    yAxis: { type: "category", data: items.map(shortTitle), axisLabel: { width: 138, overflow: "truncate" } },
    series: [{ type: "bar", data: items.map((item) => Number(item[field] || 0)), barMaxWidth: 24, itemStyle: { color, borderRadius: [0, 4, 4, 0] }, label: { show: true, position: "right" } }],
  };
}

function normalizedScores() {
  return (ranking.value?.items || []).map((item) => Number(item.score)).filter(Number.isFinite)
    .map((score) => Math.min(100, Math.max(0, Math.round(score))));
}

function disposeGlobalCharts() {
  submissionChart?.dispose();
  averageChart?.dispose();
  submissionChart = undefined;
  averageChart = undefined;
}

function disposeDetailCharts() {
  histogramChart?.dispose();
  bandPieChart?.dispose();
  histogramChart = undefined;
  bandPieChart = undefined;
}

function renderDetailCharts() {
  nextTick(() => {
    const scores = normalizedScores();
    if (!scores.length) {
      disposeDetailCharts();
      return;
    }
    const counts = Array.from({ length: 101 }, () => 0);
    scores.forEach((score) => { counts[score] += 1; });
    if (histogramRef.value) {
      histogramChart = histogramChart || echarts.init(histogramRef.value);
      histogramChart.setOption({
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: (items) => `${items[0].axisValue} 分：${items[0].value} 支队伍` },
        grid: { left: 44, right: 16, top: 18, bottom: 42 },
        xAxis: { type: "category", data: Array.from({ length: 101 }, (_, index) => index), axisTick: { alignWithLabel: true }, axisLabel: { interval: 9, color: "#64748b" }, name: "分数", nameLocation: "middle", nameGap: 28 },
        yAxis: { type: "value", minInterval: 1, name: "队伍数", splitLine: { lineStyle: { color: "#eef2f7" } } },
        series: [{ type: "bar", data: counts, barCategoryGap: "8%", itemStyle: { color: "#6366f1", borderRadius: [2, 2, 0, 0] } }],
      }, true);
    }
    if (bandPieRef.value) {
      bandPieChart = bandPieChart || echarts.init(bandPieRef.value);
      const bands = [
        { name: "0–59", min: 0, max: 59, color: "#ef4444" }, { name: "60–69", min: 60, max: 69, color: "#f59e0b" },
        { name: "70–79", min: 70, max: 79, color: "#eab308" }, { name: "80–89", min: 80, max: 89, color: "#22c55e" },
        { name: "90–100", min: 90, max: 100, color: "#6366f1" },
      ];
      bandPieChart.setOption({
        tooltip: { trigger: "item", formatter: "{b} 分：{c} 支（{d}%）" }, legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
        series: [{ type: "pie", radius: ["48%", "72%"], center: ["50%", "43%"], label: { formatter: "{d}%", color: "#475569" }, data: bands.map((band) => ({ name: band.name, value: scores.filter((score) => score >= band.min && score <= band.max).length, itemStyle: { color: band.color } })).filter((band) => band.value > 0) }],
      }, true);
    }
  });
}

function handleResize() {
  histogramChart?.resize(); bandPieChart?.resize(); submissionChart?.resize(); averageChart?.resize();
}

async function rebuild() {
  if (!problemId.value) return;
  rebuilding.value = true;
  try {
    const res = await rebuildAdminRanking(problemId.value);
    ElMessage.success(`榜单已重建（${res.data} 条）`);
    await loadDetail();
  } catch (error) {
    ElMessage.error(error.message || "重建失败");
  } finally {
    rebuilding.value = false;
  }
}

onMounted(async () => {
  window.addEventListener("resize", handleResize);
  await Promise.all([loadProblems(), loadGlobal()]);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  disposeDetailCharts();
  disposeGlobalCharts();
});
</script>

<style scoped>
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
.panel-title { margin: 0 0 4px; font-size: 18px; }
.panel-subtitle { margin: 0; color: var(--lm-text-muted); font-size: 12px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.ranking-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; color: var(--lm-text-muted); font-size: 13px; }
.global-stat-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.global-stat { display: flex; flex-direction: column; gap: 4px; padding: 15px; border: 1px solid var(--lm-border); border-radius: 11px; background: #f8fafc; }
.global-stat span, .global-stat small { color: var(--lm-text-muted); font-size: 12px; }
.global-stat strong { color: var(--lm-text-primary); font-size: 24px; }
.global-chart-grid, .ranking-chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.ranking-chart-grid { grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr); }
.chart-panel { min-width: 0; padding: 16px; border: 1px solid var(--lm-border); border-radius: 12px; background: var(--lm-surface); }
.chart-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.chart-heading h3 { margin: 0 0 5px; color: var(--lm-text-primary); font-size: 15px; }
.chart-heading p { margin: 0; color: var(--lm-text-muted); font-size: 12px; }
.chart-canvas { width: 100%; height: 280px; }
.global-chart-canvas { width: 100%; height: 330px; }
.cell-muted { margin-top: 3px; color: var(--lm-text-muted); font-size: 11px; }
@media (max-width: 1100px) { .global-stat-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 980px) { .global-chart-grid, .ranking-chart-grid { grid-template-columns: 1fr; } .global-stat-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
