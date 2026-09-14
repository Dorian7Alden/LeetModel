<template>
  <div class="ranking-page">
    <div class="ranking-toolbar">
      <div v-if="!problemId && globalStats" class="metric-strip">
        <span><strong>{{ globalStats.totalSubmissions ?? "—" }}</strong> 成功提交</span>
        <span><strong>{{ globalStats.reviewedSubmissions ?? "—" }}</strong> 已评审</span>
        <span><strong>{{ globalStats.problemCount ?? "—" }}</strong> 题目</span>
        <span><strong>{{ globalStats.overallAverageScore ?? "—" }}</strong> 平均分</span>
        <span><strong>{{ globalStats.rankedTeams ?? "—" }}</strong> 上榜队伍</span>
      </div>
      <div v-else-if="ranking" class="metric-strip">
        <span><strong>{{ ranking.total ?? 0 }}</strong> 支队伍</span>
        <span v-if="ranking.computedAt">计算于 {{ formatAdminTime(ranking.computedAt) }}</span>
      </div>
      <div class="ranking-actions">
        <el-select v-model="problemId" filterable clearable placeholder="全部题目" :loading="loadingProblems" @change="handleProblemChange">
          <el-option v-for="problem in problems" :key="problem.id" :label="problemOptionLabel(problem)" :value="String(problem.id)" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索队伍" clearable :disabled="!problemId" @keyup.enter="loadDetail" @clear="loadDetail" />
        <el-button :loading="loading" @click="reload">刷新</el-button>
        <el-button v-if="problemId" type="danger" plain :loading="rebuilding" @click="rebuild">重建榜单</el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError" type="error" title="排行数据加载失败" action-label="重新加载" @action="reload" />
    <div v-else v-loading="loading">
      <template v-if="!problemId">
        <el-table :data="globalStats?.items || []" stripe class="ranking-table">
          <el-table-column label="题目" min-width="300"><template #default="{ row }"><div class="primary-cell"><strong>{{ globalProblemLabel(row) }}</strong></div></template></el-table-column>
          <el-table-column prop="submissionCount" label="成功提交" width="104" align="center" sortable />
          <el-table-column prop="reviewedSubmissionCount" label="已评审" width="92" align="center" />
          <el-table-column label="平均分" width="92" align="center" sortable><template #default="{ row }">{{ row.averageScore ?? "—" }}</template></el-table-column>
          <el-table-column label="最高分" width="92" align="center"><template #default="{ row }">{{ row.highestScore ?? "—" }}</template></el-table-column>
          <el-table-column prop="rankedTeamCount" label="上榜队伍" width="104" align="center" />
          <el-table-column label="操作" width="96" fixed="right" align="right"><template #default="{ row }"><el-button link type="primary" @click="openProblem(row.problemId)">查看榜单</el-button></template></el-table-column>
          <template #empty><AdminStatePanel type="empty" title="暂无全局排行事实" /></template>
        </el-table>
      </template>

      <template v-else>
        <div v-if="ranking?.items?.length" class="analysis-grid">
          <section class="chart-panel"><div class="chart-title"><strong>分数分布</strong><span>{{ ranking.items.length }} 个样本</span></div><div ref="histogramRef" class="chart-canvas" /></section>
          <section class="chart-panel"><div class="chart-title"><strong>成绩分段</strong></div><div ref="bandPieRef" class="chart-canvas" /></section>
        </div>
        <el-table :data="ranking?.items || []" stripe class="ranking-table">
          <el-table-column label="排名" width="76" align="center"><template #default="{ row }"><strong>{{ row.rank }}</strong></template></el-table-column>
          <el-table-column prop="teamName" label="队伍" min-width="240" />
          <el-table-column label="得分" width="92" align="center"><template #default="{ row }"><strong>{{ row.score ?? "—" }}</strong></template></el-table-column>
          <el-table-column prop="workflowVersion" label="评审版本" min-width="200" />
          <el-table-column label="提交时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.submittedAt) }}</template></el-table-column>
          <template #empty><AdminStatePanel type="empty" title="当前题目暂无上榜提交" /></template>
        </el-table>
      </template>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import { getPublicProblemList } from "@/api/problem";
import { getAdminGlobalRankingStats, getAdminRanking, rebuildAdminRanking } from "@/api/admin-ops";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import { formatAdminTime } from "../operation-utils";

const problems = ref([]);
const problemId = ref("");
const keyword = ref("");
const loading = ref(false);
const loadError = ref(false);
const loadingProblems = ref(false);
const rebuilding = ref(false);
const ranking = ref(null);
const globalStats = ref(null);
const histogramRef = ref(null);
const bandPieRef = ref(null);
let histogramChart;
let bandPieChart;

function problemOptionLabel(problem) { return `题号 ${problem.code ?? problem.problemNumber ?? "—"} · ${problem.title || "未命名题目"}`; }
function globalProblemLabel(row) {
  const problem = problems.value.find(item => String(item.id) === String(row.problemId));
  if (problem) return problemOptionLabel(problem);
  const hasBusinessIdentity = row.problemCode || (row.problemTitle && !/^赛题\s*#/.test(row.problemTitle));
  if (hasBusinessIdentity) return `题号 ${row.problemCode ?? "—"} · ${row.problemTitle || "题目资料未取得"}`;
  return "题号 — · 题目资料未取得";
}
async function loadProblems() {
  loadingProblems.value = true;
  try { problems.value = (await getPublicProblemList({ page: 1, pageSize: 100 })).data?.rows || []; }
  catch (error) { ElMessage.error(error.message || "题目列表加载失败"); }
  finally { loadingProblems.value = false; }
}
async function loadGlobal() {
  loading.value = true;
  loadError.value = false;
  ranking.value = null;
  disposeCharts();
  try { globalStats.value = (await getAdminGlobalRankingStats()).data; }
  catch (error) { loadError.value = true; globalStats.value = null; }
  finally { loading.value = false; }
}
async function loadDetail() {
  if (!problemId.value) return loadGlobal();
  loading.value = true;
  loadError.value = false;
  try {
    ranking.value = (await getAdminRanking(problemId.value, keyword.value.trim())).data;
    renderCharts();
  } catch (error) {
    loadError.value = true;
    ranking.value = null;
    disposeCharts();
  } finally { loading.value = false; }
}
function handleProblemChange(value) { keyword.value = ""; if (value) loadDetail(); else loadGlobal(); }
function reload() { if (problemId.value) loadDetail(); else loadGlobal(); }
function openProblem(id) { problemId.value = String(id); keyword.value = ""; loadDetail(); }
function selectedProblemLabel() {
  const problem = problems.value.find(item => String(item.id) === String(problemId.value));
  if (problem) return problemOptionLabel(problem);
  const globalItem = globalStats.value?.items?.find(item => String(item.problemId) === String(problemId.value));
  if (globalItem && globalProblemLabel(globalItem) !== "题号 — · 题目资料未取得") {
    return globalProblemLabel(globalItem);
  }
  return `题目资料未取得（内部 ID ${problemId.value}）`;
}
function normalizedScores() {
  return (ranking.value?.items || []).map(item => Number(item.score)).filter(Number.isFinite)
    .map(score => Math.min(100, Math.max(0, Math.round(score))));
}
function renderCharts() {
  nextTick(() => {
    const scores = normalizedScores();
    if (!scores.length) return disposeCharts();
    const counts = Array.from({ length: 101 }, () => 0);
    scores.forEach(score => { counts[score] += 1; });
    if (histogramRef.value) {
      histogramChart = histogramChart || echarts.init(histogramRef.value);
      histogramChart.setOption({ tooltip: { trigger: "axis", formatter: items => `${items[0].axisValue} 分：${items[0].value} 支队伍` }, grid: { left: 38, right: 12, top: 14, bottom: 32 }, xAxis: { type: "category", data: Array.from({ length: 101 }, (_, index) => index), axisLabel: { interval: 9 } }, yAxis: { type: "value", minInterval: 1 }, series: [{ type: "bar", data: counts, itemStyle: { color: "#2563eb" } }] }, true);
    }
    if (bandPieRef.value) {
      const bands = [{ name: "0–59", min: 0, max: 59, color: "#dc2626" }, { name: "60–69", min: 60, max: 69, color: "#d97706" }, { name: "70–79", min: 70, max: 79, color: "#ca8a04" }, { name: "80–89", min: 80, max: 89, color: "#16a34a" }, { name: "90–100", min: 90, max: 100, color: "#2563eb" }];
      bandPieChart = bandPieChart || echarts.init(bandPieRef.value);
      bandPieChart.setOption({ tooltip: { trigger: "item", formatter: "{b} 分：{c} 支（{d}%）" }, legend: { bottom: 0, itemWidth: 9, itemHeight: 9 }, series: [{ type: "pie", radius: ["44%", "70%"], center: ["50%", "43%"], data: bands.map(band => ({ name: band.name, value: scores.filter(score => score >= band.min && score <= band.max).length, itemStyle: { color: band.color } })).filter(band => band.value) }] }, true);
    }
  });
}
function disposeCharts() { histogramChart?.dispose(); bandPieChart?.dispose(); histogramChart = undefined; bandPieChart = undefined; }
function resizeCharts() { histogramChart?.resize(); bandPieChart?.resize(); }
async function rebuild() {
  if (!problemId.value) return;
  try {
    await ElMessageBox.confirm(`将重新计算「${selectedProblemLabel()}」的当前榜单。计算期间旧榜单仍可能被读取，是否继续？`, "重建榜单", { type: "warning", confirmButtonText: "确认重建", cancelButtonText: "取消" });
  } catch { return; }
  rebuilding.value = true;
  try {
    const response = await rebuildAdminRanking(problemId.value);
    ElMessage.success(`榜单已重建，共 ${response.data ?? 0} 条`);
    await loadDetail();
  } catch (error) { ElMessage.error(error.message || "重建失败"); }
  finally { rebuilding.value = false; }
}

onMounted(async () => { window.addEventListener("resize", resizeCharts); await Promise.all([loadProblems(), loadGlobal()]); });
onBeforeUnmount(() => { window.removeEventListener("resize", resizeCharts); disposeCharts(); });
</script>

<style scoped>
@import './operations-workspace.css';
.ranking-page { min-width: 0; padding: var(--lm-admin-space-3); }
.ranking-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 14px; margin-bottom: var(--lm-admin-space-3); }
.metric-strip { display: flex; min-width: 0; align-items: center; gap: 13px; color: var(--lm-admin-text-muted); font-size: 11px; white-space: nowrap; }
.metric-strip span + span { padding-left: 13px; border-left: 1px solid var(--lm-admin-border); }
.metric-strip strong { color: var(--lm-admin-text-strong); font-size: 15px; font-variant-numeric: tabular-nums; }
.ranking-actions { display: flex; min-width: 0; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.ranking-actions .el-select { width: 310px; }
.ranking-actions .el-input { width: 170px; }
.analysis-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(270px, 1fr); gap: 12px; margin-bottom: 12px; }
.chart-panel { min-width: 0; padding: 12px; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.chart-title { display: flex; justify-content: space-between; color: var(--lm-admin-text-muted); font-size: 11px; }
.chart-title strong { color: var(--lm-admin-text-strong); font-size: 13px; }
.chart-canvas { width: 100%; height: 240px; }
.ranking-table :deep(.admin-state-panel) { margin: 18px; }
</style>
