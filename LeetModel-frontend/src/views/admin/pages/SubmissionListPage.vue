<template>
  <div class="operation-page">
    <!-- 1. 顶部提交大盘统计条 -->
    <div v-if="stats" class="submission-metric-strip">
      <div class="metric-item">
        <span class="metric-label">提交总量</span>
        <strong class="metric-val">{{ stats.totalSubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">成功提交</span>
        <strong class="metric-val text-success">{{ stats.successSubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">最终版</span>
        <strong class="metric-val text-primary">{{ stats.finalSubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">处理中</span>
        <strong class="metric-val text-warning">{{ stats.processingSubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">失败提交</span>
        <strong class="metric-val text-danger">{{ stats.failedSubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">今日新增</span>
        <strong class="metric-val">{{ stats.todaySubmissions ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-action-right">
        <el-button link type="primary" size="small" @click="showCharts = !showCharts">
          <el-icon><DataAnalysis /></el-icon>
          {{ showCharts ? "收起图表分析" : "展开图表分析" }}
        </el-button>
      </div>
    </div>

    <!-- 2. 可视化图表分析区（折叠/展开） -->
    <el-collapse-transition>
      <div v-show="showCharts && stats" class="charts-grid">
        <!-- 图表 1: 提交状态与版本构成 -->
        <div class="chart-card">
          <div class="chart-header">
            <strong>提交状态与终版构成</strong>
            <span class="chart-subtitle">最终版锁定 vs 历史版本</span>
          </div>
          <div ref="statusDonutRef" class="chart-box" />
        </div>

        <!-- 图表 2: 赛题提交量排行 -->
        <div class="chart-card">
          <div class="chart-header">
            <strong>各赛题提交热度 Top 5</strong>
            <span class="chart-subtitle">赛题成果提交量</span>
          </div>
          <div ref="problemSubBarRef" class="chart-box" />
        </div>

        <!-- 卡片 3: 转化与质量指标 -->
        <div class="chart-card kpi-card">
          <div class="chart-header">
            <strong>提交质量与交卷率</strong>
            <span class="chart-subtitle">核心业务转化率</span>
          </div>
          <div class="kpi-body">
            <div class="kpi-metric-row">
              <div class="kpi-label-group">
                <span>上传成功率</span>
                <strong>{{ successRate }}%</strong>
              </div>
              <el-progress :percentage="Number(successRate)" :color="'#10b981'" :stroke-width="8" :show-text="false" />
            </div>
            <div class="kpi-metric-row">
              <div class="kpi-label-group">
                <span>最终版锁定率</span>
                <strong>{{ finalLockRate }}%</strong>
              </div>
              <el-progress :percentage="Number(finalLockRate)" :color="'#2563eb'" :stroke-width="8" :show-text="false" />
            </div>
            <div class="kpi-footer-tips">
              <span>今日活跃提交占比: <strong>{{ todayRate }}%</strong></span>
            </div>
          </div>
        </div>
      </div>
    </el-collapse-transition>

    <!-- 3. 操作与高级筛选工具栏 -->
    <div class="operation-toolbar">
      <div class="result-scope">
        <strong>{{ total }}</strong>
        <span>条提交 · 服务端分页检索</span>
      </div>
      <div class="filter-actions">
        <el-input
          v-model="keyword"
          clearable
          prefix-icon="Search"
          placeholder="搜索文件名 / 提交ID / 队伍ID"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="selectedProblemId"
          clearable
          filterable
          placeholder="全部赛题"
          style="width: 180px;"
          @change="handleSearch"
        >
          <el-option label="全部赛题" :value="null" />
          <el-option
            v-for="p in problemOptions"
            :key="p.id"
            :label="`题号 ${p.code ?? p.problemNumber ?? '—'} · ${p.title}`"
            :value="p.id"
          />
        </el-select>
        <el-select v-model="status" clearable placeholder="全部状态" @change="handleSearch">
          <el-option label="全部状态" :value="null" />
          <el-option label="已成功 (SUCCESS)" value="SUCCESS" />
          <el-option label="处理中 (PROCESSING)" value="PROCESSING" />
          <el-option label="排队中 (PENDING)" value="PENDING" />
          <el-option label="已失败 (FAILED)" value="FAILED" />
        </el-select>
        <el-select v-model="finalOnly" clearable placeholder="版本范围" @change="handleSearch">
          <el-option label="全部版本" :value="null" />
          <el-option label="仅最终版" :value="true" />
          <el-option label="非最终版" :value="false" />
        </el-select>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="提交数据加载失败" action-label="重新加载" @action="loadData" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="rows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="队伍" min-width="170">
          <template #default="{ row }">
            <div class="primary-cell">
              <strong>{{ row.teamName || `队伍 #${row.teamId}` }}</strong>
              <span class="code-id">ID: {{ row.teamId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="赛题" min-width="210">
          <template #default="{ row }">
            <div class="primary-cell">
              <strong>题号 {{ row.problemCode ?? problemLabel(problemMap, row.problemId).code }}</strong>
              <span>{{ row.problemTitle || problemLabel(problemMap, row.problemId).title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交文件 / 大小" min-width="240">
          <template #default="{ row }">
            <div class="file-cell">
              <el-icon class="pdf-icon"><Document /></el-icon>
              <div class="primary-cell min-w0">
                <strong :title="row.originalFilename">{{ row.originalFilename || "未命名文件" }}</strong>
                <span>{{ formatFileSize(row.fileSize) }} · V{{ row.version ?? "—" }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最终版" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.finalVersion ? 'success' : 'info'" size="small" effect="plain">
              {{ row.finalVersion ? "是" : "否" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <AdminStatusBadge :status="normalizedStatus(row.status)" :label="statusLabel(row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="提交人" min-width="150">
          <template #default="{ row }">
            <div class="submitter-cell">
              <el-avatar :size="24" :src="row.submitterAvatarUrl" class="submitter-avatar">
                {{ (row.submitterName || '用').slice(0, 1) }}
              </el-avatar>
              <div class="primary-cell min-w0">
                <strong>{{ row.submitterName || "用户" }}</strong>
                <span class="code-id">ID: {{ row.submitterId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="145">
          <template #default="{ row }">{{ formatAdminTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" @click="openDetails(row)">详情</el-button>
            <el-button
              v-if="!row.finalVersion && row.status === 'SUCCESS'"
              link
              type="warning"
              @click="confirmSetFinal(row)"
            >
              设终版
            </el-button>
            <el-button
              link
              type="info"
              @click="confirmRedispatch(row)"
            >
              派发
            </el-button>
            <el-button
              v-if="row.status !== 'FAILED'"
              link
              type="danger"
              @click="confirmInvalidate(row)"
            >
              作废
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的提交' : '暂无提交记录'" />
        </template>
      </el-table>

      <!-- 服务端分页器 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 4. 提交详情抽屉 -->
    <el-drawer v-model="detailsVisible" title="提交档案详情" size="min(520px, 86vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>队伍</dt><dd><strong>{{ selectedRow.teamName || `队伍 #${selectedRow.teamId}` }}</strong></dd></div>
        <div><dt>赛题</dt><dd>题号 {{ selectedRow.problemCode ?? problemLabel(problemMap, selectedRow.problemId).code }} · {{ selectedRow.problemTitle || problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>文件名</dt><dd>{{ selectedRow.originalFilename || "—" }}</dd></div>
        <div><dt>文件大小</dt><dd>{{ formatFileSize(selectedRow.fileSize) }}</dd></div>
        <div><dt>版本号</dt><dd>V{{ selectedRow.version ?? "—" }}{{ selectedRow.finalVersion ? " · 已锁定最终版" : "" }}</dd></div>
        <div><dt>状态</dt><dd>{{ statusLabel(selectedRow.status) }}</dd></div>
        <div><dt>提交人</dt><dd>{{ selectedRow.submitterName || "—" }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ formatAdminTime(selectedRow.createTime) }}</dd></div>
        <div><dt>文件资产 ID</dt><dd class="code-id">{{ selectedRow.fileId || "—" }}</dd></div>
        <div><dt>提交 ID</dt><dd class="identifier-value"><span>{{ selectedRow.id }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.id)">复制</el-button></dd></div>
        <div><dt>队伍 ID</dt><dd class="identifier-value"><span>{{ selectedRow.teamId }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.teamId)">复制</el-button></dd></div>
        <div><dt>赛题 ID</dt><dd class="identifier-value"><span>{{ selectedRow.problemId }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.problemId)">复制</el-button></dd></div>
        <div><dt>提交者 ID</dt><dd class="identifier-value"><span>{{ selectedRow.submitterId }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.submitterId)">复制</el-button></dd></div>
      </dl>
    </el-drawer>

    <!-- 5. PDF 临时预览抽屉 -->
    <el-drawer v-model="previewVisible" :title="previewFilename || '提交预览'" size="min(1000px, 88vw)" destroy-on-close>
      <div v-loading="previewLoading" class="pdf-preview-body">
        <template v-if="previewUrl">
          <div class="preview-toolbar">
            <span>临时预览签名有效</span>
            <a :href="previewUrl" target="_blank" rel="noopener noreferrer">在新窗口打开</a>
          </div>
          <iframe :src="previewUrl" :title="`PDF 预览：${previewFilename}`" class="pdf-frame" />
        </template>
        <AdminStatePanel v-else-if="previewError" type="error" title="PDF 预览地址不可用" action-label="重试" @action="retryPreview" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import {
  getAdminSubmissionPage,
  getAdminSubmissionStats,
  getAdminSubmissionDetail,
  setAdminFinalSubmission,
  invalidateAdminSubmission,
  redispatchAdminReview,
  getAdminSubmissionPreview,
} from "@/api/admin-ops";
import { getPublicProblemList } from "@/api/problem";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { copyAdminText, formatAdminTime, problemLabel, useAdminReferences } from "../operation-utils";

const props = defineProps({
  problemMap: { type: Object, default: () => ({}) },
});
const { problemMap: refProblemMap, loadProblems } = useAdminReferences();
const problemMap = computed(() => (Object.keys(props.problemMap || {}).length ? props.problemMap : refProblemMap.value));

// 分页与筛选
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const loadError = ref(false);
const keyword = ref("");
const selectedProblemId = ref(null);
const status = ref(null);
const finalOnly = ref(null);
const problemOptions = ref([]);

// 统计大盘与图表
const stats = ref(null);
const showCharts = ref(true);
const statusDonutRef = ref(null);
const problemSubBarRef = ref(null);
let statusDonutChart;
let problemSubBarChart;

// 详情
const selectedRow = ref(null);
const detailsVisible = ref(false);

// 预览
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewUrl = ref("");
const previewFilename = ref("");
const previewError = ref(false);
const previewRow = ref(null);

const successRate = computed(() => {
  if (!stats.value || !stats.value.totalSubmissions) return "0.0";
  return ((stats.value.successSubmissions / stats.value.totalSubmissions) * 100).toFixed(1);
});

const finalLockRate = computed(() => {
  if (!stats.value || !stats.value.totalSubmissions) return "0.0";
  return ((stats.value.finalSubmissions / stats.value.totalSubmissions) * 100).toFixed(1);
});

const todayRate = computed(() => {
  if (!stats.value || !stats.value.totalSubmissions) return "0.0";
  return ((stats.value.todaySubmissions / stats.value.totalSubmissions) * 100).toFixed(1);
});

function statusLabel(value) {
  return ({ SUCCESS: "已成功", FAILED: "已失败", PROCESSING: "处理中", PENDING: "等待中" })[value] || value || "未知";
}
function normalizedStatus(value) {
  return value === "SUCCESS" ? "SUCCEEDED" : value || "UNKNOWN";
}

function formatFileSize(bytes) {
  if (!bytes) return "—";
  const num = Number(bytes);
  if (num < 1024) return num + " B";
  if (num < 1024 * 1024) return (num / 1024).toFixed(1) + " KB";
  return (num / (1024 * 1024)).toFixed(2) + " MB";
}

async function loadStats() {
  try {
    const res = await getAdminSubmissionStats();
    stats.value = res.data;
    renderCharts();
  } catch {
    // ignore
  }
}

function renderCharts() {
  if (!stats.value) return;
  nextTick(() => {
    // 图表 1: 状态环形图
    if (statusDonutRef.value) {
      statusDonutChart = statusDonutChart || echarts.init(statusDonutRef.value);
      const finalCount = stats.value.finalSubmissions ?? 0;
      const historyCount = Math.max(0, (stats.value.successSubmissions ?? 0) - finalCount);
      const data = [
        { name: "最终锁定版", value: finalCount, itemStyle: { color: "#2563eb" } },
        { name: "普通历史版", value: historyCount, itemStyle: { color: "#10b981" } },
        { name: "处理中", value: stats.value.processingSubmissions ?? 0, itemStyle: { color: "#f59e0b" } },
        { name: "失败异常", value: stats.value.failedSubmissions ?? 0, itemStyle: { color: "#ef4444" } },
      ].filter((item) => item.value > 0);

      statusDonutChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}: {c} 条 ({d}%)" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
        series: [
          {
            type: "pie",
            radius: ["42%", "68%"],
            center: ["50%", "44%"],
            data,
          },
        ],
      }, true);
    }

    // 图表 2: 赛题提交量排行
    if (problemSubBarRef.value) {
      problemSubBarChart = problemSubBarChart || echarts.init(problemSubBarRef.value);
      const topList = stats.value.topProblems || [];
      const reversed = [...topList].reverse();

      problemSubBarChart.setOption({
        tooltip: { trigger: "axis", formatter: (items) => `${items[0].name}: ${items[0].value} 次提交` },
        grid: { left: 80, right: 25, top: 15, bottom: 20 },
        xAxis: { type: "value", minInterval: 1 },
        yAxis: {
          type: "category",
          data: reversed.map((d) => `题号 ${d.problemCode ?? "—"}`),
          axisLabel: { color: "#475569", fontSize: 11 },
        },
        series: [
          {
            type: "bar",
            data: reversed.map((d) => d.submissionCount),
            itemStyle: { color: "#3b82f6", borderRadius: [0, 4, 4, 0] },
          },
        ],
      }, true);
    }
  });
}

function disposeCharts() {
  statusDonutChart?.dispose();
  problemSubBarChart?.dispose();
  statusDonutChart = undefined;
  problemSubBarChart = undefined;
}

function resizeCharts() {
  statusDonutChart?.resize();
  problemSubBarChart?.resize();
}

async function loadProblemOptions() {
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 });
    problemOptions.value = res.data?.rows || [];
  } catch {
    // ignore
  }
}

async function loadData() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getAdminSubmissionPage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      problemId: selectedProblemId.value || undefined,
      status: status.value || undefined,
      finalOnly: finalOnly.value != null ? finalOnly.value : undefined,
    });
    rows.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    loadError.value = true;
    if (rows.value.length) ElMessage.error(error.message || "提交数据加载失败");
  } finally {
    loading.value = false;
  }
  loadProblems();
}

function handleSearch() {
  page.value = 1;
  loadData();
}

function handlePageChange(val) {
  page.value = val;
  loadData();
}

function handleSizeChange(val) {
  pageSize.value = val;
  page.value = 1;
  loadData();
}

async function openDetails(row) {
  selectedRow.value = row;
  detailsVisible.value = true;
  try {
    const res = await getAdminSubmissionDetail(row.id);
    if (res.data) selectedRow.value = res.data;
  } catch {
    // fallback
  }
}

function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : "";
  } catch {
    return "";
  }
}

async function openPreview(row) {
  previewRow.value = row;
  previewVisible.value = true;
  previewLoading.value = true;
  previewUrl.value = "";
  previewError.value = false;
  previewFilename.value = row.originalFilename || "提交论文.pdf";
  try {
    const response = await getAdminSubmissionPreview(row.id);
    previewUrl.value = safeHttpUrl(response.data?.previewUrl);
    previewFilename.value = response.data?.originalFilename || previewFilename.value;
    previewError.value = !previewUrl.value;
  } catch (error) {
    previewError.value = true;
    ElMessage.error(error.message || "PDF 预览加载失败");
  } finally {
    previewLoading.value = false;
  }
}

function retryPreview() {
  if (previewRow.value) openPreview(previewRow.value);
}

async function confirmSetFinal(row) {
  try {
    await ElMessageBox.confirm(
      `确定将提交「${row.originalFilename}」(版本 V${row.version}) 设为队伍的最终评审版本吗？这将替换原有的最终版并触发榜单重建。`,
      "设为最终版",
      { confirmButtonText: "确认设为终版", cancelButtonText: "取消", type: "warning" }
    );
    await setAdminFinalSubmission(row.id);
    ElMessage.success("已成功设为最终版");
    loadData();
    loadStats();
  } catch {
    // cancelled
  }
}

async function confirmRedispatch(row) {
  try {
    await ElMessageBox.confirm(
      `确定对提交「${row.originalFilename}」(ID: ${row.id}) 重新派发 AI 评审任务吗？`,
      "重新派发评审",
      { confirmButtonText: "确认派发", cancelButtonText: "取消", type: "info" }
    );
    await redispatchAdminReview(row.id);
    ElMessage.success("评审任务已重新派发");
  } catch {
    // cancelled
  }
}

async function confirmInvalidate(row) {
  try {
    await ElMessageBox.confirm(
      `确定作废提交「${row.originalFilename}」(ID: ${row.id}) 吗？若该提交为最终版，队伍终版锁定将被解除。`,
      "作废提交记录",
      { confirmButtonText: "确认作废", cancelButtonText: "取消", type: "danger" }
    );
    await invalidateAdminSubmission(row.id);
    ElMessage.success("提交已标记作废");
    loadData();
    loadStats();
  } catch {
    // cancelled
  }
}

async function copyIdentifier(value) {
  await copyAdminText(value);
  ElMessage.success("标识已复制");
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts);
  loadStats();
  loadProblemOptions();
  loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  disposeCharts();
});
</script>

<style scoped>
@import './operations-workspace.css';

.submission-metric-strip {
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
.text-success { color: #16a34a; }
.text-warning { color: #d97706; }
.text-primary { color: #2563eb; }
.text-danger { color: #dc2626; }

/* 可视化分析卡片网格 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: var(--lm-admin-space-3);
}
.chart-card {
  padding: 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.chart-header strong { font-size: 12px; color: var(--lm-admin-text-strong); }
.chart-subtitle { font-size: 11px; color: var(--lm-admin-text-muted); }
.chart-box { width: 100%; height: 180px; }

/* KPI 指标卡片 */
.kpi-card { display: flex; flex-direction: column; }
.kpi-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 16px;
  padding: 4px 8px;
}
.kpi-metric-row { display: flex; flex-direction: column; gap: 6px; }
.kpi-label-group { display: flex; justify-content: space-between; font-size: 12px; }
.kpi-label-group span { color: var(--lm-admin-text-muted); }
.kpi-label-group strong { color: var(--lm-admin-text-strong); font-size: 14px; }
.kpi-footer-tips {
  padding-top: 10px;
  border-top: 1px dashed var(--lm-admin-border);
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}
.kpi-footer-tips strong { color: var(--lm-admin-text-strong); }

/* 表格内文件与用户 */
.file-cell { display: flex; align-items: center; gap: 8px; }
.pdf-icon { font-size: 20px; color: #dc2626; flex-shrink: 0; }
.submitter-cell { display: flex; align-items: center; gap: 8px; }
.submitter-avatar { flex-shrink: 0; background: #6366f1; color: #ffffff; font-size: 11px; }
.min-w0 { min-width: 0; }

.code-id {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}
.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0 6px;
}
.pdf-preview-body { min-height: 460px; }
.preview-toolbar { display: flex; justify-content: space-between; margin-bottom: 10px; padding: 8px 10px; color: var(--lm-admin-text-muted); background: var(--lm-admin-surface-subtle); border-radius: 6px; font-size: 12px; }
.pdf-frame { width: 100%; height: calc(100vh - 150px); background: #475569; border: 0; border-radius: 8px; }
</style>
