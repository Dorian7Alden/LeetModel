<template>
  <div class="operation-page">
    <!-- 顶部数据大盘 -->
    <div class="suggestion-metrics-strip">
      <div class="metric-card">
        <span class="metric-label">建议任务总量</span>
        <strong class="metric-value">{{ totalCount }}</strong>
        <span class="metric-hint">已采集样本</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">生成成功率</span>
        <strong class="metric-value success-text">{{ successRate }}</strong>
        <span class="metric-hint">已完成 {{ completedCount }} 次</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">平均生成耗时</span>
        <strong class="metric-value">{{ avgDuration }} <small>秒</small></strong>
        <span class="metric-hint">端到端执行</span>
      </div>
      <div class="metric-card">
        <span class="metric-label">失败告警数</span>
        <strong class="metric-value" :class="{ 'danger-text': failedCount > 0 }">{{ failedCount }}</strong>
        <span class="metric-hint">需人工排查/重试</span>
      </div>
    </div>

    <!-- 数据分析图表 -->
    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">建议生成状态分布与失败占比</span>
        </div>
        <div ref="statusPieRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">工作流版本与平均耗时对比</span>
        </div>
        <div ref="workflowBarRef" class="chart-container"></div>
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

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="建议数据加载失败" action-label="重新加载" @action="load" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="pagedRows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="状态" width="116"><template #default="{ row }"><AdminStatusBadge :status="row.status || 'UNKNOWN'" :label="statusLabel(row.status)" /></template></el-table-column>
        <el-table-column label="队伍" min-width="160"><template #default="{ row }"><strong>{{ teamName(teamMap, row.teamId) }}</strong></template></el-table-column>
        <el-table-column label="题目" min-width="210"><template #default="{ row }"><div class="primary-cell"><strong>题号 {{ problemLabel(problemMap, row.problemId).code }}</strong><span>{{ problemLabel(problemMap, row.problemId).title }}</span></div></template></el-table-column>
        <el-table-column label="工作流 / 模型" min-width="250"><template #default="{ row }"><div class="primary-cell"><strong>{{ row.workflowVersion || "—" }}</strong><span>{{ row.modelName || "模型未取得" }}</span></div></template></el-table-column>
        <el-table-column label="创建时间" width="152" sortable prop="createTime"><template #default="{ row }">{{ formatAdminTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="完成时间" width="152" sortable prop="finishedAt"><template #default="{ row }">{{ formatAdminTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetails(row)">详情</el-button>
              <el-button v-if="row.status === 'FAILED'" link type="warning" @click="handleRetrySuggestion(row)">重试</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的建议任务' : '最近没有建议任务'" /></template>
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

    <!-- 建议任务详情抽屉 -->
    <el-drawer v-model="detailsVisible" title="论文建议任务详情与依据" size="min(640px, 92vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>状态</dt><dd><AdminStatusBadge :status="selectedRow.status || 'UNKNOWN'" :label="statusLabel(selectedRow.status)" /></dd></div>
        <div><dt>队伍</dt><dd>{{ teamName(teamMap, selectedRow.teamId) }}</dd></div>
        <div><dt>题目</dt><dd>题号 {{ problemLabel(problemMap, selectedRow.problemId).code }} · {{ problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>工作流</dt><dd>{{ selectedRow.workflowVersion || "—" }}</dd></div>
        <div><dt>模型</dt><dd>{{ selectedRow.modelName || "—" }}</dd></div>

        <!-- 错误依据与诊断 -->
        <div v-if="selectedRow.errorMessage" class="error-detail">
          <dt>失败根因</dt>
          <dd class="error-box">{{ selectedRow.errorMessage }}</dd>
        </div>

        <div><dt>创建时间</dt><dd>{{ formatAdminTime(selectedRow.createTime) }}</dd></div>
        <div><dt>完成时间</dt><dd>{{ formatAdminTime(selectedRow.finishedAt) }}</dd></div>
        <IdentifierLine label="任务 ID" :value="selectedRow.taskId" @copy="copyIdentifier" />
        <IdentifierLine label="提交 ID" :value="selectedRow.submissionId" @copy="copyIdentifier" />
        <IdentifierLine label="队伍 ID" :value="selectedRow.teamId" @copy="copyIdentifier" />
        <IdentifierLine label="题目 ID" :value="selectedRow.problemId" @copy="copyIdentifier" />
        <IdentifierLine label="AI 调用 ID" :value="selectedRow.aiCallId" @copy="copyIdentifier" />

        <div v-if="selectedRow.status === 'FAILED'" class="drawer-action-row">
          <dt>运维干预</dt>
          <dd>
            <el-button type="warning" size="small" @click="handleRetrySuggestion(selectedRow)">重新触发生成</el-button>
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
import { getAdminSuggestions } from "@/api/admin-ops";
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

const statusPieRef = ref(null);
const workflowBarRef = ref(null);
let statusPieChart = null;
let workflowBarChart = null;

const priority = { FAILED: 0, UNKNOWN: 1, RUNNING: 2, WAITING: 3, COMPLETED: 4 };
const statusOptions = computed(() => [...new Set(rows.value.map(item => item.status).filter(Boolean))]);
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    if (status.value && row.status !== status.value) return false;
    if (!query) return true;
    const problem = problemLabel(problemMap.value, row.problemId);
    return [teamName(teamMap.value, row.teamId), problem.code, problem.title, row.workflowVersion, row.modelName, row.errorMessage]
      .some(value => String(value || "").toLowerCase().includes(query));
  }).sort((left, right) => (priority[left.status] ?? 3) - (priority[right.status] ?? 3));
});

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRows.value.slice(start, start + pageSize.value);
});

const totalCount = computed(() => rows.value.length);
const completedCount = computed(() => rows.value.filter(r => r.status === "COMPLETED").length);
const failedCount = computed(() => rows.value.filter(r => r.status === "FAILED" || r.status === "UNKNOWN").length);
const successRate = computed(() => {
  if (!rows.value.length) return "0%";
  return Math.round((completedCount.value / rows.value.length) * 1000) / 10 + "%";
});

const avgDuration = computed(() => {
  const completed = rows.value.filter(r => r.status === "COMPLETED" && r.createTime && r.finishedAt);
  if (!completed.length) return "—";
  const totalSec = completed.reduce((acc, cur) => {
    const diff = (new Date(cur.finishedAt) - new Date(cur.createTime)) / 1000;
    return acc + (diff > 0 ? diff : 0);
  }, 0);
  return (totalSec / completed.length).toFixed(1);
});

function renderCharts() {
  nextTick(() => {
    if (!rows.value.length) return;

    // 1. 状态分布饼图
    if (statusPieRef.value) {
      statusPieChart = statusPieChart || echarts.init(statusPieRef.value);
      const statusMap = {};
      rows.value.forEach(r => {
        const s = statusLabel(r.status);
        statusMap[s] = (statusMap[s] || 0) + 1;
      });
      statusPieChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
        legend: { bottom: 0, icon: "circle", textStyle: { fontSize: 11 } },
        series: [{
          type: "pie",
          radius: ["40%", "68%"],
          center: ["50%", "45%"],
          avoidLabelOverlap: false,
          label: { show: false },
          data: Object.entries(statusMap).map(([name, value]) => ({
            name,
            value,
            itemStyle: {
              color: name.includes("完成") ? "#10b981" : name.includes("失败") ? "#ef4444" : "#f59e0b"
            }
          }))
        }]
      }, true);
    }

    // 2. 工作流版本数量及平均耗时柱状图
    if (workflowBarRef.value) {
      workflowBarChart = workflowBarChart || echarts.init(workflowBarRef.value);
      const wfMap = {};
      rows.value.forEach(r => {
        const wf = r.workflowVersion || "未知版本";
        if (!wfMap[wf]) wfMap[wf] = { count: 0, durationSum: 0, validCount: 0 };
        wfMap[wf].count++;
        if (r.createTime && r.finishedAt) {
          const diff = (new Date(r.finishedAt) - new Date(r.createTime)) / 1000;
          if (diff > 0) {
            wfMap[wf].durationSum += diff;
            wfMap[wf].validCount++;
          }
        }
      });
      const categories = Object.keys(wfMap);
      workflowBarChart.setOption({
        tooltip: { trigger: "axis" },
        grid: { left: 45, right: 20, top: 20, bottom: 26 },
        xAxis: { type: "category", data: categories, axisLabel: { fontSize: 10, color: "#6b7280" } },
        yAxis: { type: "value", name: "秒", minInterval: 1 },
        series: [{
          name: "平均耗时 (s)",
          type: "bar",
          data: categories.map(k => wfMap[k].validCount ? Math.round(wfMap[k].durationSum / wfMap[k].validCount) : 0),
          itemStyle: { color: "#6366f1", borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 38
        }]
      }, true);
    }
  });
}

function disposeCharts() {
  statusPieChart?.dispose();
  workflowBarChart?.dispose();
  statusPieChart = null;
  workflowBarChart = null;
}

function resizeCharts() {
  statusPieChart?.resize();
  workflowBarChart?.resize();
}

const IdentifierLine = defineComponent({
  props: { label: String, value: [String, Number] },
  emits: ["copy"],
  setup(lineProps, { emit }) {
    return () => h("div", [h("dt", lineProps.label), h("dd", { class: "identifier-value" }, [h("span", String(lineProps.value ?? "—")), lineProps.value != null ? h(ElButton, { link: true, type: "primary", onClick: () => emit("copy", lineProps.value) }, () => "复制") : null])]);
  },
});

function statusLabel(value) { return ({ WAITING: "等待", RUNNING: "进行中", COMPLETED: "已完成", FAILED: "失败", UNKNOWN: "结果待确认" })[value] || value || "未知"; }
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    rows.value = (await getAdminSuggestions(100)).data || [];
    loadProblems();
    loadTeams(rows.value.map(item => item.teamId));
    emit("reference-ids", rows.value.map(item => item.teamId));
    renderCharts();
  }
  catch (error) { loadError.value = true; if (rows.value.length) ElMessage.error(error.message || "建议数据刷新失败"); }
  finally { loading.value = false; }
}
function openDetails(row) { selectedRow.value = row; detailsVisible.value = true; }
async function copyIdentifier(value) { await copyAdminText(value); ElMessage.success("标识已复制"); }

async function handleRetrySuggestion(row) {
  try {
    await ElMessageBox.confirm(`确认重新触发建议任务 ID: ${row.taskId} 的 AI 分析生成？`, "重试建议任务", {
      confirmButtonText: "确认重试",
      cancelButtonText: "取消",
      type: "warning"
    });
    // 触发提交服务的 redispatch 或重新生成
    const { redispatchAdminReview } = await import("@/api/admin-ops");
    await redispatchAdminReview(row.submissionId);
    ElMessage.success("已重新调度派发生成任务");
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
.error-box { background: #fef2f2; border: 1px solid #fecaca; border-radius: 4px; padding: 8px 10px; line-height: 1.5; }

.suggestion-metrics-strip {
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
.danger-text { color: var(--lm-admin-danger); }

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
