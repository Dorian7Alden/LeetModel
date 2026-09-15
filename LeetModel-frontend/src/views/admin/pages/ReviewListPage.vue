<template>
  <div class="operation-page">
    <!-- 顶部数据大盘 -->
    <div class="review-metrics-strip">
      <div class="metric-card">
        <span class="metric-label">评审总量</span>
        <strong class="metric-value">{{ totalCount }}</strong>
        <span class="metric-hint">已采集样本</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">成功率</span>
        <strong class="metric-value success-text">{{ successRate }}</strong>
        <span class="metric-hint">已完成 {{ completedCount }} 次</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">平均成绩</span>
        <strong class="metric-value">{{ avgScore }} <small>分</small></strong>
        <span class="metric-hint">最高 {{ maxScore }} / 最低 {{ minScore }}</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">异常与等待</span>
        <strong class="metric-value" :class="{ 'warning-text': abnormalCount > 0 }">{{ abnormalCount }}</strong>
        <span class="metric-hint">需关注失败/未完成</span>
      </div>
    </div>

    <!-- 数据分析可视化图表 -->
    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">全网评审得分分布 (分段统计)</span>
        </div>
        <div ref="histogramRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">AI 评审四维雷达评估模型</span>
        </div>
        <div ref="radarRef" class="chart-container"></div>
      </div>
    </div>

    <div class="operation-toolbar">
      <div class="result-scope"><strong>{{ filteredRows.length }}</strong><span>条结果 · 数据范围：最近 100 条</span></div>
      <div class="filter-actions">
        <el-input v-model="keyword" clearable prefix-icon="Search" placeholder="搜索队伍、题目、工作流或模型" />
        <el-select v-model="status" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="评审数据加载失败" action-label="重新加载" @action="load" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="pagedRows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="状态" width="116"><template #default="{ row }"><AdminStatusBadge :status="row.status || 'UNKNOWN'" :label="statusLabel(row.status)" /></template></el-table-column>
        <el-table-column label="队伍" min-width="160"><template #default="{ row }"><strong>{{ teamName(teamMap, row.teamId) }}</strong></template></el-table-column>
        <el-table-column label="题目" min-width="210"><template #default="{ row }"><div class="primary-cell"><strong>题号 {{ problemLabel(problemMap, row.problemId).code }}</strong><span>{{ problemLabel(problemMap, row.problemId).title }}</span></div></template></el-table-column>
        <el-table-column label="得分" width="78" align="center"><template #default="{ row }"><strong>{{ row.score ?? "—" }}</strong></template></el-table-column>
        <el-table-column label="工作流 / 模型" min-width="230"><template #default="{ row }"><div class="primary-cell"><strong>{{ row.workflowVersion || "—" }}</strong><span>{{ row.modelName || "模型未取得" }}</span></div></template></el-table-column>
        <el-table-column label="完成时间" width="152" sortable prop="finishedAt"><template #default="{ row }">{{ formatAdminTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetails(row)">详情</el-button>
              <el-button v-if="row.status === 'FAILED'" link type="warning" @click="handleRetryReview(row)">重试</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的评审任务' : '最近没有评审任务'" /></template>
      </el-table>

      <div class="pagination-bar" v-if="filteredRows.length > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredRows.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailsVisible" title="评审详情与报告" size="min(640px, 92vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>状态</dt><dd><AdminStatusBadge :status="selectedRow.status || 'UNKNOWN'" :label="statusLabel(selectedRow.status)" /></dd></div>
        <div><dt>队伍</dt><dd>{{ teamName(teamMap, selectedRow.teamId) }}</dd></div>

        <!-- 解析后的评审报告展示 -->
        <div v-if="parsedReviewResult" class="review-report-section">
          <dt>评审摘要</dt>
          <dd class="review-summary-text">{{ parsedReviewResult.summary || '暂无摘要' }}</dd>
        </div>
        <div v-if="parsedReviewResult?.dimensions" class="review-dimensions-section">
          <dt>四维评分</dt>
          <dd>
            <div class="dimensions-grid">
              <div v-for="(val, dimKey) in parsedReviewResult.dimensions" :key="dimKey" class="dimension-box">
                <span class="dim-name">{{ dimensionLabel(dimKey) }}</span>
                <strong class="dim-score">{{ val.score ?? '—' }} 分</strong>
              </div>
            </div>
          </dd>
        </div>
        <div v-if="parsedReviewResult?.strengths?.length" class="review-points-section">
          <dt>主要优势</dt>
          <dd>
            <ul class="points-list positive-list"><li v-for="(p, i) in parsedReviewResult.strengths" :key="i">{{ p }}</li></ul>
          </dd>
        </div>
        <div v-if="parsedReviewResult?.weaknesses?.length" class="review-points-section">
          <dt>待改进点</dt>
          <dd>
            <ul class="points-list negative-list"><li v-for="(p, i) in parsedReviewResult.weaknesses" :key="i">{{ p }}</li></ul>
          </dd>
        </div>

        <div><dt>题目</dt><dd>题号 {{ problemLabel(problemMap, selectedRow.problemId).code }} · {{ problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>得分</dt><dd>{{ selectedRow.score ?? "—" }}</dd></div>
        <div><dt>工作流</dt><dd>{{ selectedRow.workflowVersion || "—" }}</dd></div>
        <div><dt>模型</dt><dd>{{ selectedRow.modelName || "—" }}</dd></div>
        <div v-if="selectedRow.errorMessage" class="error-detail"><dt>失败依据</dt><dd>{{ selectedRow.errorMessage }}</dd></div>
        <div><dt>完成时间</dt><dd>{{ formatAdminTime(selectedRow.finishedAt) }}</dd></div>
        <IdentifierLine label="任务 ID" :value="selectedRow.taskId" @copy="copyIdentifier" />
        <IdentifierLine label="提交 ID" :value="selectedRow.submissionId" @copy="copyIdentifier" />
        <IdentifierLine label="队伍 ID" :value="selectedRow.teamId" @copy="copyIdentifier" />
        <IdentifierLine label="题目 ID" :value="selectedRow.problemId" @copy="copyIdentifier" />
        <IdentifierLine label="AI 调用 ID" :value="selectedRow.aiCallId" @copy="copyIdentifier" />

        <div v-if="selectedRow.status === 'FAILED'" class="drawer-action-row">
          <dt>运维操作</dt>
          <dd>
            <el-button type="warning" size="small" @click="handleRetryReview(selectedRow)">重新发起 AI 评审</el-button>
          </dd>
        </div>
      </dl>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElButton, ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import { getAdminReviews } from "@/api/admin-ops";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { copyAdminText, formatAdminTime, problemLabel, teamName, useAdminReferences } from "../operation-utils";

const props = defineProps({ teamMap: { type: Object, default: () => ({}) }, problemMap: { type: Object, default: () => ({}) } });
const emit = defineEmits(["reference-ids"]);
const { teamMap: refTeamMap, problemMap: refProblemMap, loadProblems, loadTeams } = useAdminReferences();
const teamMap = computed(() => (Object.keys(props.teamMap || {}).length ? props.teamMap : refTeamMap.value));
const problemMap = computed(() => (Object.keys(props.problemMap || {}).length ? props.problemMap : refProblemMap.value));
const rows = ref([]);
const loading = ref(false);
const loadError = ref(false);
const keyword = ref("");
const status = ref("");
const selectedRow = ref(null);
const currentPage = ref(1);
const pageSize = ref(10);
const detailsVisible = ref(false);

const histogramRef = ref(null);
const radarRef = ref(null);
let histogramChart = null;
let radarChart = null;

const priority = { FAILED: 0, UNKNOWN: 1, RUNNING: 2, LEASED: 3, WAITING: 4, COMPLETED: 5 };
const statusOptions = computed(() => [...new Set(rows.value.map(item => item.status).filter(Boolean))]);
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    if (status.value && row.status !== status.value) return false;
    if (!query) return true;
    const problem = problemLabel(problemMap.value, row.problemId);
    return [teamName(teamMap.value, row.teamId), problem.code, problem.title, row.workflowVersion, row.modelName, row.errorMessage]
      .some(value => String(value || "").toLowerCase().includes(query));
  }).sort((left, right) => (priority[left.status] ?? 4) - (priority[right.status] ?? 4));
});

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRows.value.slice(start, start + pageSize.value);
});

const totalCount = computed(() => rows.value.length);
const completedCount = computed(() => rows.value.filter(r => r.status === "COMPLETED").length);
const abnormalCount = computed(() => rows.value.filter(r => r.status === "FAILED" || r.status === "UNKNOWN").length);
const successRate = computed(() => {
  if (!rows.value.length) return "0%";
  return Math.round((completedCount.value / rows.value.length) * 1000) / 10 + "%";
});
const validScores = computed(() => rows.value.map(r => Number(r.score)).filter(s => Number.isFinite(s)));
const avgScore = computed(() => {
  if (!validScores.value.length) return "—";
  const sum = validScores.value.reduce((acc, cur) => acc + cur, 0);
  return (sum / validScores.value.length).toFixed(1);
});
const maxScore = computed(() => validScores.value.length ? Math.max(...validScores.value).toFixed(1) : "—");
const minScore = computed(() => validScores.value.length ? Math.min(...validScores.value).toFixed(1) : "—");

const parsedReviewResult = computed(() => {
  if (!selectedRow.value?.resultJson) return null;
  try {
    return JSON.parse(selectedRow.value.resultJson);
  } catch {
    return null;
  }
});

function dimensionLabel(key) {
  const map = {
    modelCreativity: "创新性 (Creativity)",
    expressionClarity: "条理性 (Clarity)",
    resultCorrectness: "正确性 (Correctness)",
    assumptionRationality: "合理性 (Rationality)"
  };
  return map[key] || key;
}

function renderCharts() {
  nextTick(() => {
    if (!rows.value.length) return;
    
    // 1. 直方图（分段统计）
    const bins = [
      { label: "0-59 分", min: 0, max: 60, count: 0 },
      { label: "60-69 分", min: 60, max: 70, count: 0 },
      { label: "70-79 分", min: 70, max: 80, count: 0 },
      { label: "80-89 分", min: 80, max: 90, count: 0 },
      { label: "90-100 分", min: 90, max: 101, count: 0 },
    ];
    validScores.value.forEach(s => {
      const bin = bins.find(b => s >= b.min && s < b.max);
      if (bin) bin.count++;
    });

    if (histogramRef.value) {
      histogramChart = histogramChart || echarts.init(histogramRef.value);
      histogramChart.setOption({
        tooltip: { trigger: "axis" },
        grid: { left: 40, right: 20, top: 20, bottom: 26 },
        xAxis: {
          type: "category",
          data: bins.map(b => b.label),
          axisLabel: { color: "#6b7280", fontSize: 11 },
        },
        yAxis: { type: "value", minInterval: 1 },
        series: [{
          type: "bar",
          data: bins.map(b => b.count),
          itemStyle: { color: "#2563eb", borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 38
        }]
      }, true);
    }

    // 2. 四维雷达图
    const dims = { modelCreativity: [], expressionClarity: [], resultCorrectness: [], assumptionRationality: [] };
    rows.value.forEach(r => {
      if (r.resultJson) {
        try {
          const res = JSON.parse(r.resultJson);
          if (res.dimensions) {
            Object.keys(dims).forEach(k => {
              if (res.dimensions[k]?.score != null) dims[k].push(Number(res.dimensions[k].score));
            });
          }
        } catch {}
      }
    });
    const avgDim = key => dims[key].length ? (dims[key].reduce((a, b) => a + b, 0) / dims[key].length).toFixed(1) : 70;

    if (radarRef.value) {
      radarChart = radarChart || echarts.init(radarRef.value);
      radarChart.setOption({
        tooltip: {},
        radar: {
          indicator: [
            { name: "模型创新", max: 100 },
            { name: "表达清晰", max: 100 },
            { name: "结果准确", max: 100 },
            { name: "假设合理", max: 100 },
          ],
          radius: "68%",
          axisName: { color: "#475569", fontSize: 11 }
        },
        series: [{
          type: "radar",
          data: [{
            value: [avgDim("modelCreativity"), avgDim("expressionClarity"), avgDim("resultCorrectness"), avgDim("assumptionRationality")],
            name: "全网平均能力",
            areaStyle: { color: "rgba(37, 99, 235, 0.2)" },
            itemStyle: { color: "#2563eb" }
          }]
        }]
      }, true);
    }
  });
}

function disposeCharts() {
  histogramChart?.dispose();
  radarChart?.dispose();
  histogramChart = null;
  radarChart = null;
}

function resizeCharts() {
  histogramChart?.resize();
  radarChart?.resize();
}

const IdentifierLine = defineComponent({
  props: { label: String, value: [String, Number] },
  emits: ["copy"],
  setup(lineProps, { emit }) {
    return () => h("div", [h("dt", lineProps.label), h("dd", { class: "identifier-value" }, [h("span", String(lineProps.value ?? "—")), lineProps.value != null ? h(ElButton, { link: true, type: "primary", onClick: () => emit("copy", lineProps.value) }, () => "复制") : null])]);
  },
});

function statusLabel(value) { return ({ WAITING: "等待", LEASED: "已领取", RUNNING: "进行中", COMPLETED: "已完成", FAILED: "失败", UNKNOWN: "结果待确认" })[value] || value || "未知"; }
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    rows.value = (await getAdminReviews(100)).data || [];
    loadProblems();
    loadTeams(rows.value.map(item => item.teamId));
    emit("reference-ids", rows.value.map(item => item.teamId));
    renderCharts();
  }
  catch (error) { loadError.value = true; if (rows.value.length) ElMessage.error(error.message || "评审数据刷新失败"); }
  finally { loading.value = false; }
}
function openDetails(row) { selectedRow.value = row; detailsVisible.value = true; }
async function copyIdentifier(value) { await copyAdminText(value); ElMessage.success("标识已复制"); }

async function handleRetryReview(row) {
  try {
    await ElMessageBox.confirm(`确认重新派发任务 ID: ${row.taskId} 的 AI 评审？`, "重试评审任务", {
      confirmButtonText: "确认重试",
      cancelButtonText: "取消",
      type: "warning"
    });
    const { redispatchAdminReview } = await import("@/api/admin-ops");
    await redispatchAdminReview(row.submissionId);
    ElMessage.success("已重新排队并派发评审");
    await load();
  } catch (err) {
    if (err === "cancel" || err === "close") return;
    ElMessage.error(err.message || "重试请求失败");
  }
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts);
  load();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  disposeCharts();
});
</script>

<style scoped>
@import './operations-workspace.css';
.error-detail dd { color: var(--lm-admin-danger); }

.review-metrics-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.metric-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.metric-label { font-size: 11px; color: var(--lm-admin-text-muted); }
.metric-value { font-size: 20px; color: var(--lm-admin-text-strong); font-weight: 700; font-variant-numeric: tabular-nums; }
.metric-value small { font-size: 12px; font-weight: normal; color: var(--lm-admin-text-muted); }
.metric-hint { font-size: 11px; color: var(--lm-admin-text-muted); }
.success-text { color: var(--lm-admin-success); }
.warning-text { color: var(--lm-admin-warning); }

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.chart-card {
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.chart-header { margin-bottom: 8px; }
.chart-title { font-size: 13px; font-weight: 600; color: var(--lm-admin-text-strong); }
.chart-container { width: 100%; height: 210px; }

.table-actions { display: inline-flex; align-items: center; gap: 8px; }
.review-summary-text { line-height: 1.6; color: var(--lm-admin-text-strong); background: #f8fafc; padding: 8px 10px; border-radius: 4px; }
.dimensions-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.dimension-box { display: flex; flex-direction: column; padding: 6px 10px; background: #f1f5f9; border-radius: 4px; }
.dim-name { font-size: 11px; color: var(--lm-admin-text-muted); }
.dim-score { font-size: 15px; color: var(--lm-admin-primary); }
.points-list { margin: 0; padding-left: 18px; font-size: 12px; line-height: 1.6; }
.positive-list { color: var(--lm-admin-success); }
.negative-list { color: #d97706; }
.drawer-action-row { margin-top: 14px; }

@media (max-width: 900px) {
  .charts-row { grid-template-columns: 1fr; }
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--lm-admin-border);
  border-top: 0;
  border-radius: 0 0 var(--lm-admin-radius-control) var(--lm-admin-radius-control);
}
</style>
