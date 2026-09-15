<template>
  <div class="ranking-page">
    <!-- 1. 全局或单题指标大盘条 -->
    <div v-if="!problemId && globalStats" class="ranking-metric-strip">
      <div class="metric-item">
        <span class="metric-label">成功提交</span>
        <strong class="metric-val">{{ globalStats.totalSubmissions ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">已评审</span>
        <strong class="metric-val text-success">{{ globalStats.reviewedSubmissions ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">上榜队伍</span>
        <strong class="metric-val text-primary">{{ globalStats.rankedTeams ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">覆盖题目</span>
        <strong class="metric-val">{{ globalStats.problemCount ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">全平台均分</span>
        <strong class="metric-val text-warning">{{ globalStats.overallAverageScore ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-action-right">
        <el-button link type="primary" size="small" @click="showGlobalCharts = !showGlobalCharts">
          <el-icon><DataAnalysis /></el-icon>
          {{ showGlobalCharts ? "收起大盘图表" : "展开大盘图表" }}
        </el-button>
      </div>
    </div>

    <div v-else-if="problemStats" class="ranking-metric-strip">
      <div class="metric-item">
        <span class="metric-label">上榜队伍</span>
        <strong class="metric-val text-primary">{{ problemStats.totalTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">平均分</span>
        <strong class="metric-val">{{ problemStats.averageScore ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">中位数</span>
        <strong class="metric-val">{{ problemStats.medianScore ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">最高 / 最低</span>
        <strong class="metric-val text-success">{{ problemStats.highestScore ?? "—" }}</strong>
        <span class="text-muted">/</span>
        <strong class="metric-val text-danger">{{ problemStats.lowestScore ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">标准差</span>
        <strong class="metric-val">{{ problemStats.stdDevScore ?? "—" }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item tier-tag-item">
        <span class="metric-label">奖项预测线</span>
        <el-tag size="small" type="danger" effect="light">国一: ≥{{ problemStats.nationalFirstTierScore ?? '—' }}</el-tag>
        <el-tag size="small" type="warning" effect="light">国二: ≥{{ problemStats.nationalSecondTierScore ?? '—' }}</el-tag>
        <el-tag size="small" type="primary" effect="light">省一: ≥{{ problemStats.provincialFirstTierScore ?? '—' }}</el-tag>
      </div>
    </div>

    <!-- 2. 全局大盘图表分析区（当处于全局模式时） -->
    <el-collapse-transition>
      <div v-show="!problemId && showGlobalCharts && globalStats?.items?.length" class="analysis-grid">
        <section class="chart-panel">
          <div class="chart-title">
            <strong>各赛题均分与最高分对比</strong>
            <span>全平台赛题天梯</span>
          </div>
          <div ref="globalScoreChartRef" class="chart-canvas" />
        </section>
        <section class="chart-panel">
          <div class="chart-title">
            <strong>各赛题提交量与上榜队伍数</strong>
            <span>参赛与达标转化</span>
          </div>
          <div ref="globalVolumeChartRef" class="chart-canvas" />
        </section>
      </div>
    </el-collapse-transition>

    <!-- 3. 操作与筛选工具栏 -->
    <div class="ranking-toolbar">
      <div class="toolbar-left">
        <el-button v-if="problemId" @click="backToGlobal">
          <el-icon><Back /></el-icon>返回赛题总览
        </el-button>
        <span v-if="!problemId" class="scope-title">全平台赛题榜单概览</span>
        <span v-else class="scope-title">{{ selectedProblemTitle }}</span>
      </div>
      <div class="ranking-actions">
        <el-select
          v-model="problemId"
          filterable
          clearable
          placeholder="选择或搜索赛题"
          style="width: 280px;"
          :loading="loadingProblems"
          @change="handleProblemChange"
        >
          <el-option label="全平台赛题总览" :value="''" />
          <el-option
            v-for="p in problems"
            :key="p.id"
            :label="`题号 ${p.code ?? p.problemNumber ?? '—'} · ${p.title}`"
            :value="String(p.id)"
          />
        </el-select>

        <!-- 单题模式下的专属过滤器 -->
        <template v-if="problemId">
          <el-input
            v-model="keyword"
            placeholder="搜索队伍"
            clearable
            style="width: 150px;"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-input
            v-model="minScore"
            placeholder="最低分"
            clearable
            style="width: 85px;"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="maxScore"
            placeholder="最高分"
            clearable
            style="width: 85px;"
            @keyup.enter="handleSearch"
          />
        </template>

        <el-button :loading="loading" @click="reload">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button v-if="problemId" type="danger" plain :loading="rebuilding" @click="rebuildSingle">
          重建本题榜单
        </el-button>
        <el-button v-if="!problemId" type="warning" plain :loading="rebuildingAll" @click="rebuildAll">
          一键重建全部
        </el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError" type="error" title="排行数据加载失败" action-label="重新加载" @action="reload" />
    <div v-else v-loading="loading">
      <!-- 视图 A：全局题目总览事实表 -->
      <template v-if="!problemId">
        <el-table :data="globalStats?.items || []" stripe class="ranking-table">
          <el-table-column label="题目" min-width="280">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>题号 {{ row.problemCode ?? "—" }} · {{ row.problemTitle || `赛题 #${row.problemId}` }}</strong>
                <span class="code-id">ID: {{ row.problemId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="submissionCount" label="成功提交" width="100" align="center" sortable />
          <el-table-column prop="reviewedSubmissionCount" label="已评审" width="95" align="center" />
          <el-table-column label="平均分" width="95" align="center" sortable>
            <template #default="{ row }">
              <strong>{{ row.averageScore ?? "—" }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="最高分" width="95" align="center">
            <template #default="{ row }">
              <span class="text-success">{{ row.highestScore ?? "—" }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rankedTeamCount" label="上榜队伍" width="100" align="center" />
          <el-table-column label="操作" width="160" fixed="right" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openProblem(row.problemId)">查看榜单</el-button>
              <el-button link type="danger" @click="rebuildDirect(row.problemId)">重建</el-button>
            </template>
          </el-table-column>
          <template #empty><AdminStatePanel type="empty" title="暂无全局排行事实" /></template>
        </el-table>
      </template>

      <!-- 视图 B：单题榜单明细与深度统计 -->
      <template v-else>
        <!-- ECharts 统计图分析区 -->
        <div class="analysis-grid">
          <section class="chart-panel">
            <div class="chart-title">
              <strong>分数分布直方图</strong>
              <span>样本队伍: {{ problemStats?.totalTeams ?? rankingTotal }} 支</span>
            </div>
            <div ref="histogramRef" class="chart-canvas" />
          </section>
          <section class="chart-panel">
            <div class="chart-title">
              <strong>成绩分段占比</strong>
            </div>
            <div ref="bandPieRef" class="chart-canvas" />
          </section>
        </div>

        <!-- 榜单明细列表 -->
        <el-table :data="rankingRows" stripe class="ranking-table">
          <el-table-column label="排名" width="80" align="center">
            <template #default="{ row }">
              <strong class="rank-pill" :class="`rank-${row.rank}`">{{ row.rank }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="队伍" min-width="200">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.teamName }}</strong>
                <span class="code-id">队伍 ID: {{ row.teamId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="110" align="center">
            <template #default="{ row }">
              <strong class="score-text">{{ row.score ?? "—" }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="workflowVersion" label="评审版本" min-width="170">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" type="info">{{ row.workflowVersion || "默认流水线" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="145">
            <template #default="{ row }">{{ formatAdminTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="评审完成时间" width="145">
            <template #default="{ row }">{{ formatAdminTime(row.reviewFinishedAt) }}</template>
          </el-table-column>
          <el-table-column label="治理操作" width="160" fixed="right" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOverrideDialog(row)">复议调分</el-button>
              <el-button link type="danger" @click="confirmDisqualify(row)">剔除</el-button>
            </template>
          </el-table-column>
          <template #empty><AdminStatePanel type="empty" title="当前筛选条件下无上榜队伍" /></template>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="rankingPage"
            v-model:page-size="rankingPageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="rankingTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleRankingSizeChange"
            @current-change="handleRankingPageChange"
          />
        </div>
      </template>
    </div>

    <!-- 3. 人工复议调分对话框 -->
    <el-dialog v-model="overrideDialogVisible" title="人工复议成绩修正" width="460px">
      <el-form :model="overrideForm" label-width="90px" class="dialog-form">
        <el-form-item label="当前队伍">
          <strong>{{ overrideTarget?.teamName }}</strong>
          <span class="code-id block-text">原始得分: {{ overrideTarget?.score }}</span>
        </el-form-item>
        <el-form-item label="修正成绩" required>
          <el-input-number
            v-model="overrideForm.newScore"
            :min="0"
            :max="100"
            :precision="2"
            :step="1"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="复议原因" required>
          <el-input
            v-model="overrideForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请填写详细复议修正依据（将记录操作审计）"
            maxlength="256"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="overrideSubmitting" @click="submitOverride">确认修正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import { getPublicProblemList } from "@/api/problem";
import {
  getAdminGlobalRankingStats,
  getAdminRankingPage,
  getAdminProblemRankingStats,
  rebuildAdminRanking,
  rebuildAllAdminRankings,
  disqualifyAdminRankingEntry,
  overrideAdminRankingScore,
} from "@/api/admin-ops";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import { formatAdminTime } from "../operation-utils";

const problems = ref([]);
const problemId = ref("");
const keyword = ref("");
const minScore = ref("");
const maxScore = ref("");
const loading = ref(false);
const loadError = ref(false);
const loadingProblems = ref(false);
const rebuilding = ref(false);
const rebuildingAll = ref(false);

// 全局统计与图表
const globalStats = ref(null);
const showGlobalCharts = ref(true);
const globalScoreChartRef = ref(null);
const globalVolumeChartRef = ref(null);
let globalScoreChart;
let globalVolumeChart;

// 单题明细与统计
const rankingRows = ref([]);
const rankingTotal = ref(0);
const rankingPage = ref(1);
const rankingPageSize = ref(20);
const problemStats = ref(null);

// 单题图表
const histogramRef = ref(null);
const bandPieRef = ref(null);
let histogramChart;
let bandPieChart;

// 成绩修正
const overrideDialogVisible = ref(false);
const overrideSubmitting = ref(false);
const overrideTarget = ref(null);
const overrideForm = reactive({ newScore: 0, reason: "" });

const selectedProblemTitle = computed(() => {
  const p = problems.value.find((item) => String(item.id) === String(problemId.value));
  if (p) return `题号 ${p.code ?? p.problemNumber ?? "—"} · ${p.title}`;
  if (problemStats.value) return `题号 ${problemStats.value.problemCode ?? "—"} · ${problemStats.value.problemTitle}`;
  return `赛题 #${problemId.value}`;
});

async function loadProblems() {
  loadingProblems.value = true;
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 });
    problems.value = res.data?.rows || [];
  } catch {
    // ignore
  } finally {
    loadingProblems.value = false;
  }
}

async function loadGlobal() {
  loading.value = true;
  loadError.value = false;
  disposeDetailCharts();
  try {
    const res = await getAdminGlobalRankingStats();
    globalStats.value = res.data;
    renderGlobalCharts();
  } catch (error) {
    loadError.value = true;
    globalStats.value = null;
  } finally {
    loading.value = false;
  }
}

function renderGlobalCharts() {
  if (!globalStats.value?.items?.length) return;
  nextTick(() => {
    const items = globalStats.value.items.slice(0, 8); // Top 8 problems
    const names = items.map((i) => `题号 ${i.problemCode ?? i.problemId}`);
    const avgScores = items.map((i) => Number(i.averageScore) || 0);
    const maxScores = items.map((i) => Number(i.highestScore) || 0);
    const subCounts = items.map((i) => i.submissionCount || 0);
    const rankCounts = items.map((i) => i.rankedTeamCount || 0);

    // 图表 1: 均分 vs 最高分
    if (globalScoreChartRef.value) {
      globalScoreChart = globalScoreChart || echarts.init(globalScoreChartRef.value);
      globalScoreChart.setOption({
        tooltip: { trigger: "axis" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
        grid: { left: 40, right: 15, top: 15, bottom: 28 },
        xAxis: { type: "category", data: names, axisLabel: { color: "#6b7280", fontSize: 11 } },
        yAxis: { type: "value", min: 0, max: 100 },
        series: [
          { name: "平均分", type: "bar", data: avgScores, itemStyle: { color: "#3b82f6", borderRadius: [4, 4, 0, 0] } },
          { name: "最高分", type: "bar", data: maxScores, itemStyle: { color: "#10b981", borderRadius: [4, 4, 0, 0] } },
        ],
      }, true);
    }

    // 图表 2: 提交量 vs 上榜队伍数
    if (globalVolumeChartRef.value) {
      globalVolumeChart = globalVolumeChart || echarts.init(globalVolumeChartRef.value);
      globalVolumeChart.setOption({
        tooltip: { trigger: "axis" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
        grid: { left: 40, right: 15, top: 15, bottom: 28 },
        xAxis: { type: "category", data: names, axisLabel: { color: "#6b7280", fontSize: 11 } },
        yAxis: { type: "value", minInterval: 1 },
        series: [
          { name: "成功提交", type: "bar", data: subCounts, itemStyle: { color: "#6366f1", borderRadius: [4, 4, 0, 0] } },
          { name: "上榜队伍", type: "bar", data: rankCounts, itemStyle: { color: "#f59e0b", borderRadius: [4, 4, 0, 0] } },
        ],
      }, true);
    }
  });
}

async function loadDetail() {
  if (!problemId.value) return loadGlobal();
  loading.value = true;
  loadError.value = false;
  disposeGlobalCharts();
  try {
    const [pageRes, statsRes] = await Promise.all([
      getAdminRankingPage(problemId.value, {
        page: rankingPage.value,
        pageSize: rankingPageSize.value,
        keyword: keyword.value.trim() || undefined,
        minScore: minScore.value !== "" ? Number(minScore.value) : undefined,
        maxScore: maxScore.value !== "" ? Number(maxScore.value) : undefined,
      }),
      getAdminProblemRankingStats(problemId.value),
    ]);
    rankingRows.value = pageRes.data?.rows || [];
    rankingTotal.value = pageRes.data?.total || 0;
    problemStats.value = statsRes.data;
    renderDetailCharts();
  } catch (error) {
    loadError.value = true;
    rankingRows.value = [];
    disposeDetailCharts();
  } finally {
    loading.value = false;
  }
}

function handleProblemChange(val) {
  keyword.value = "";
  minScore.value = "";
  maxScore.value = "";
  rankingPage.value = 1;
  if (val) loadDetail();
  else loadGlobal();
}

function handleSearch() {
  rankingPage.value = 1;
  loadDetail();
}

function handleRankingPageChange(val) {
  rankingPage.value = val;
  loadDetail();
}

function handleRankingSizeChange(val) {
  rankingPageSize.value = val;
  rankingPage.value = 1;
  loadDetail();
}

function reload() {
  if (problemId.value) loadDetail();
  else loadGlobal();
}

function openProblem(id) {
  problemId.value = String(id);
  keyword.value = "";
  minScore.value = "";
  maxScore.value = "";
  rankingPage.value = 1;
  loadDetail();
}

function backToGlobal() {
  problemId.value = "";
  loadGlobal();
}

function renderDetailCharts() {
  nextTick(() => {
    const dist = problemStats.value?.scoreDistribution || [];
    if (!dist.length) return disposeDetailCharts();

    if (histogramRef.value) {
      histogramChart = histogramChart || echarts.init(histogramRef.value);
      histogramChart.setOption(
        {
          tooltip: { trigger: "axis", formatter: "{b} 分：{c} 支队伍" },
          grid: { left: 40, right: 15, top: 20, bottom: 26 },
          xAxis: {
            type: "category",
            data: dist.map((d) => d.range),
            axisLabel: { color: "#6b7280", fontSize: 11 },
          },
          yAxis: { type: "value", minInterval: 1 },
          series: [
            {
              type: "bar",
              data: dist.map((d) => d.count),
              itemStyle: { color: "#2563eb", borderRadius: [4, 4, 0, 0] },
            },
          ],
        },
        true
      );
    }

    if (bandPieRef.value) {
      const colors = ["#dc2626", "#d97706", "#ca8a04", "#16a34a", "#2563eb"];
      bandPieChart = bandPieChart || echarts.init(bandPieRef.value);
      bandPieChart.setOption(
        {
          tooltip: { trigger: "item", formatter: "{b} 分：{c} 支（{d}%）" },
          legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
          series: [
            {
              type: "pie",
              radius: ["42%", "68%"],
              center: ["50%", "44%"],
              data: dist.map((d, idx) => ({
                name: d.range,
                value: d.count,
                itemStyle: { color: colors[idx % colors.length] },
              })),
            },
          ],
        },
        true
      );
    }
  });
}

function disposeDetailCharts() {
  histogramChart?.dispose();
  bandPieChart?.dispose();
  histogramChart = undefined;
  bandPieChart = undefined;
}

function disposeGlobalCharts() {
  globalScoreChart?.dispose();
  globalVolumeChart?.dispose();
  globalScoreChart = undefined;
  globalVolumeChart = undefined;
}

function resizeCharts() {
  histogramChart?.resize();
  bandPieChart?.resize();
  globalScoreChart?.resize();
  globalVolumeChart?.resize();
}

async function rebuildSingle() {
  if (!problemId.value) return;
  try {
    await ElMessageBox.confirm(
      `将重新计算「${selectedProblemTitle.value}」的榜单快照，是否继续？`,
      "重建榜单",
      { confirmButtonText: "确认重建", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return;
  }
  rebuilding.value = true;
  try {
    const res = await rebuildAdminRanking(problemId.value);
    ElMessage.success(`榜单已重建，共 ${res.data ?? 0} 支队伍上榜`);
    await loadDetail();
  } catch (error) {
    ElMessage.error(error.message || "重建失败");
  } finally {
    rebuilding.value = false;
  }
}

async function rebuildDirect(pId) {
  try {
    await ElMessageBox.confirm(`将重新计算赛题 #${pId} 的当前榜单，是否继续？`, "重建榜单", {
      confirmButtonText: "确认重建",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await rebuildAdminRanking(pId);
    ElMessage.success(`赛题 #${pId} 榜单已重建，共 ${res.data ?? 0} 条`);
    loadGlobal();
  } catch {
    // cancelled
  }
}

async function rebuildAll() {
  try {
    await ElMessageBox.confirm(
      "将批量重建全平台所有赛题的榜单快照。任务在后台执行，是否继续？",
      "一键重建全部榜单",
      { confirmButtonText: "确认全量重建", cancelButtonText: "取消", type: "warning" }
    );
  } catch {
    return;
  }
  rebuildingAll.value = true;
  try {
    const res = await rebuildAllAdminRankings();
    ElMessage.success(`已完成 ${res.data ?? 0} 道题目的榜单全量重建`);
    loadGlobal();
  } catch (error) {
    ElMessage.error(error.message || "全量重建失败");
  } finally {
    rebuildingAll.value = false;
  }
}

function openOverrideDialog(row) {
  overrideTarget.value = row;
  overrideForm.newScore = Number(row.score) || 0;
  overrideForm.reason = "";
  overrideDialogVisible.value = true;
}

async function submitOverride() {
  if (!overrideForm.reason.trim()) return ElMessage.warning("请填写复议修正原因");
  overrideSubmitting.value = true;
  try {
    await overrideAdminRankingScore(overrideTarget.value.id, {
      newScore: overrideForm.newScore,
      reason: overrideForm.reason.trim(),
    });
    ElMessage.success("成绩已修正，榜单名次已自动重新排序");
    overrideDialogVisible.value = false;
    loadDetail();
  } catch (error) {
    ElMessage.error(error.message || "成绩修正失败");
  } finally {
    overrideSubmitting.value = false;
  }
}

async function confirmDisqualify(row) {
  try {
    await ElMessageBox.confirm(
      `确定将队伍「${row.teamName}」从当前榜单中剔除吗？剩余队伍名次将自动向上顺延。`,
      "剔除违规榜单条目",
      { confirmButtonText: "确认剔除", cancelButtonText: "取消", type: "danger" }
    );
    await disqualifyAdminRankingEntry(row.id);
    ElMessage.success("该队伍已从榜单中剔除");
    loadDetail();
  } catch {
    // cancelled
  }
}

onMounted(async () => {
  window.addEventListener("resize", resizeCharts);
  await Promise.all([loadProblems(), loadGlobal()]);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  disposeDetailCharts();
  disposeGlobalCharts();
});
</script>

<style scoped>
@import './operations-workspace.css';

.ranking-page { min-width: 0; padding: var(--lm-admin-space-3); }
.ranking-metric-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 18px;
  padding: 10px 14px;
  margin-bottom: var(--lm-admin-space-3);
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  font-size: 12px;
}
.metric-item { display: flex; align-items: baseline; gap: 6px; }
.metric-label { color: var(--lm-admin-text-muted); font-size: 11px; }
.metric-val { color: var(--lm-admin-text-strong); font-size: 15px; font-variant-numeric: tabular-nums; }
.metric-divider { width: 1px; height: 14px; background: var(--lm-admin-border); }
.metric-action-right { margin-left: auto; }
.tier-tag-item { align-items: center; gap: 4px; }
.text-success { color: #16a34a; }
.text-warning { color: #d97706; }
.text-primary { color: #2563eb; }
.text-danger { color: #dc2626; }
.text-muted { color: #6b7280; }

.ranking-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 14px; margin-bottom: var(--lm-admin-space-3); }
.toolbar-left { display: flex; align-items: center; gap: 10px; }
.scope-title { font-size: 14px; font-weight: 600; color: var(--lm-admin-text-strong); }
.ranking-actions { display: flex; min-width: 0; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }

.analysis-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(270px, 1fr); gap: 12px; margin-bottom: 14px; }
.chart-panel { min-width: 0; padding: 12px; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); background: var(--lm-admin-surface); }
.chart-title { display: flex; justify-content: space-between; color: var(--lm-admin-text-muted); font-size: 11px; }
.chart-title strong { color: var(--lm-admin-text-strong); font-size: 13px; }
.chart-canvas { width: 100%; height: 220px; }

.rank-pill { display: inline-block; width: 26px; height: 26px; line-height: 26px; text-align: center; border-radius: 50%; font-size: 12px; background: #f1f5f9; color: #475569; }
.rank-1 { background: #fef3c7; color: #b45309; font-weight: bold; }
.rank-2 { background: #f1f5f9; color: #475569; font-weight: bold; }
.rank-3 { background: #ffedd5; color: #c2410c; font-weight: bold; }
.score-text { font-size: 14px; color: #2563eb; }

.code-id { font-family: var(--lm-code-font-family); font-size: 11px; color: var(--lm-admin-text-muted); }
.pagination-container { display: flex; justify-content: flex-end; padding: 14px 0 6px; }
.dialog-form { padding: 8px 12px 0 0; }
.block-text { display: block; margin-top: 4px; }
</style>
