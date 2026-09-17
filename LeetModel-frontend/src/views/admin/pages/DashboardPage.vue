<template>
  <div class="dashboard-page">
    <!-- 1. 顶栏全局状态与运维概况 -->
    <div class="overview-status-bar" :class="{ warning: loadedOnce && sourceIssues.length }">
      <div class="status-leading">
        <AdminStatusBadge
          :status="!loadedOnce ? 'WAITING' : sourceIssues.length ? 'WARNING' : 'HEALTHY'"
          :label="availabilityLabel"
        />
        <span class="status-time">数据刷新于 {{ dataTimestamp }}</span>
        <button
          v-if="loadedOnce && sourceIssues.length"
          class="issue-toggle"
          type="button"
          :aria-expanded="issuesExpanded"
          aria-controls="overview-source-issues"
          @click="issuesExpanded = !issuesExpanded"
        >
          {{ sourceIssues.length }} 项数据缺失 / 异常
          <el-icon><ArrowUp v-if="issuesExpanded" /><ArrowDown v-else /></el-icon>
        </button>
      </div>
      <div class="status-actions">
        <el-button size="small" :loading="loading" @click="loadOverview">
          <el-icon><Refresh /></el-icon>
          刷新大盘
        </el-button>
      </div>
    </div>

    <!-- 数据缺失折叠提示 -->
    <div v-if="issuesExpanded && loadedOnce && sourceIssues.length" id="overview-source-issues" class="source-issue-list">
      <span v-for="item in sourceIssues" :key="item.key">
        <strong>{{ item.label }}</strong>
        {{ item.message }}
      </span>
    </div>

    <AdminStatePanel
      v-if="loadedOnce && !hasAnyAvailableSource"
      type="error"
      title="概览数据暂不可用"
      action-label="重新加载"
      @action="loadOverview"
    >
      请检查管理聚合服务和下游数据源连接状态。
    </AdminStatePanel>

    <template v-else>
      <!-- 2. 全局核心事实指标条 -->
      <section class="overview-panel core-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <div class="heading-lead">
            <span class="panel-tag">CORE FACTS</span>
            <h2>平台核心事实</h2>
          </div>
          <span>累计规模与当前快照 · 点击卡片可快速穿梭至对应管理模块</span>
        </div>
        <AdminMetricStrip :items="coreMetrics" :loading="loading && !loadedOnce" @select="openMetric" />
      </section>

      <!-- 3. 待处理与运维预警中心（异常前置） -->
      <section v-if="attentionItems.length" class="overview-panel warning-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <div class="heading-lead">
            <span class="panel-tag warning">ATTENTION REQUIRED</span>
            <h2>异常与待处理事实</h2>
          </div>
          <span>当前窗口内的真实故障与阻断，点击直达目标工作台处理</span>
        </div>

        <div class="attention-grid">
          <button
            v-for="item in attentionItems"
            :key="item.key"
            class="attention-item"
            :class="`tone-${item.tone}`"
            type="button"
            @click="openTarget(item.target)"
          >
            <span class="attention-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <span class="attention-copy">
              <strong>{{ item.label }}</strong>
              <small>{{ item.scope }}</small>
            </span>
            <b class="attention-value">{{ item.value }}</b>
            <el-icon class="attention-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <!-- 4. 业务流转与实训态势分析 -->
      <section class="overview-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <div class="heading-lead">
            <span class="panel-tag">PRACTICE DYNAMICS</span>
            <h2>数模竞赛与实训流转分析</h2>
          </div>
          <span>覆盖赛题热度天梯、队伍实训流转阶段及组队规模分析</span>
        </div>

        <div class="analysis-grid-two">
          <!-- 图表 1: 赛题热度 Top 5 -->
          <div class="chart-card">
            <div class="chart-card-header">
              <div class="chart-title">
                <strong>热门赛题投入对比 (Top 5)</strong>
                <span class="chart-sub">成功提交量 vs 参赛队伍数</span>
              </div>
              <el-button link type="primary" size="small" @click="router.push('/admin/problems')">
                题目管理 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
            <div ref="problemHotChartRef" class="chart-container" />
          </div>

          <!-- 图表 2: 队伍实训阶段与规模分布 -->
          <div class="chart-card">
            <div class="chart-card-header">
              <div class="chart-title">
                <strong>队伍实训阶段与成队规模</strong>
                <span class="chart-sub">进行中流转漏斗与人数结构</span>
              </div>
              <el-button link type="primary" size="small" @click="router.push('/admin/teams')">
                队伍管理 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
            <div class="team-charts-wrapper">
              <div ref="teamStageChartRef" class="chart-half" />
              <div ref="teamSizeChartRef" class="chart-half" />
            </div>
          </div>
        </div>
      </section>

      <!-- 5. AI 中枢算力与质量态势分析 -->
      <section class="overview-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <div class="heading-lead">
            <span class="panel-tag">AI DISPATCH & PERFORMANCE</span>
            <h2>AI 算力消耗与模型效能</h2>
          </div>
          <span>跨模型调用分布、响应时延与生产流水线治理</span>
        </div>

        <div class="ai-overview-layout">
          <!-- 图表 3: 模型调用量与响应时延 -->
          <div class="chart-card ai-chart-card">
            <div class="chart-card-header">
              <div class="chart-title">
                <strong>主流模型负载与平均耗时</strong>
                <span class="chart-sub">调用频次对比响应延迟 (毫秒)</span>
              </div>
              <el-button link type="primary" size="small" @click="router.push('/admin/ai-calls')">
                调用明细 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
            <div ref="modelPerfChartRef" class="chart-container" />
          </div>

          <!-- AI 状态卡片群与当前生产工作流 -->
          <div class="ai-posture-side">
            <div class="ai-stat-boxes">
              <div class="stat-box">
                <span class="box-label">AI 调用成功率</span>
                <strong class="box-value text-success">{{ successRate(aiStats) }}</strong>
                <small class="box-hint">24h 成功 {{ formatNumber(aiStats?.successCount) }} 次</small>
              </div>
              <div class="stat-box">
                <span class="box-label">平均响应耗时</span>
                <strong class="box-value">{{ aiStats?.totalCount ? formatMilliseconds(aiStats?.averageTotalMs) : "—" }}</strong>
                <small class="box-hint">包含排队与生成耗时</small>
              </div>
              <div class="stat-box">
                <span class="box-label">Tokens 总消耗</span>
                <strong class="box-value">{{ formatNumber(aiStats?.totalTokens) }}</strong>
                <small class="box-hint">输入+输出全量 Token</small>
              </div>
              <div class="stat-box">
                <span class="box-label">客服会话总量</span>
                <strong class="box-value">{{ formatNumber(metrics.assistantConversations?.value) }}</strong>
                <small class="box-hint">累计沉淀答疑会话</small>
              </div>
            </div>

            <!-- 当前生产版本条 -->
            <button class="production-banner" type="button" @click="openTarget(aiTarget)">
              <div class="prod-left">
                <span class="prod-tag">ONLINE WORKFLOW</span>
                <strong class="prod-title">{{ productionLabel }}</strong>
                <span class="prod-desc">生产环境首选客服与推荐决策工作流</span>
              </div>
              <div class="prod-right">
                <span class="prod-ver">{{ productionVersion }}</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </button>
          </div>
        </div>
      </section>

      <!-- 6. 基础设施与系统稳定性监控 (消息队列与服务状态) -->
      <section class="overview-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <div class="heading-lead">
            <span class="panel-tag">RELIABILITY & OPS</span>
            <h2>分布式链路与消息队列态势</h2>
          </div>
          <span>RocketMQ 事务本地消息表 (Outbox) 投递状态与消费者存活</span>
        </div>

        <div class="messaging-overview-grid">
          <!-- 消息全局指标条 -->
          <div class="msg-kpi-bar">
            <div class="msg-kpi">
              <span>待投递事件</span>
              <strong :class="{ 'text-warning': messagingSnapshot.pending > 0 }">{{ formatNumber(messagingSnapshot.pending) }}</strong>
            </div>
            <div class="msg-kpi">
              <span>阻塞异常</span>
              <strong :class="{ 'text-danger': messagingSnapshot.blocked > 0 }">{{ formatNumber(messagingSnapshot.blocked) }}</strong>
            </div>
            <div class="msg-kpi">
              <span>最老积压时延</span>
              <strong>{{ formatDuration(messagingSnapshot.oldestPendingSeconds) }}</strong>
            </div>
            <div class="msg-kpi">
              <span>异常节点数</span>
              <strong :class="{ 'text-danger': messagingSnapshot.alertingServices > 0 }">{{ messagingSnapshot.alertingServices }} 个服务</strong>
            </div>
            <el-button link type="primary" size="small" class="msg-kpi-action" @click="router.push('/admin/messaging')">
              消息队列工作台 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>

          <!-- 各服务 Outbox 状态堆叠柱状图 -->
          <div class="chart-card msg-chart-card">
            <div ref="msgOutboxChartRef" class="chart-container-sm" />
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import * as echarts from "echarts";
import { getDashboard } from "@/api/dashboard";
import {
  getAdminAiCallStats,
  getAdminAiModelStats,
  getAssistantProductionCurrent,
  listEvaluationTasks,
} from "@/api/admin-ai";
import {
  getAdminTeamStats,
  getAdminSubmissionStats,
  getAdminGlobalRankingStats,
} from "@/api/admin-ops";
import { getMessagingOverview } from "@/api/admin-messaging";
import { searchAdminAudit } from "@/api/admin-audit";
import AdminMetricStrip from "../components/AdminMetricStrip.vue";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";

const router = useRouter();
const loading = ref(false);
const loadedOnce = ref(false);
const issuesExpanded = ref(false);
const metrics = ref({});
const dashboardAvailable = ref(false);
const dashboardTimestamp = ref("");
const refreshedAt = ref("");

// 业务与AI数据
const teamStats = ref(null);
const submissionStats = ref(null);
const globalRankingStats = ref(null);
const aiStats = ref(null);
const aiModelStats = ref([]);
const evaluations = ref([]);
const messaging = ref({ services: [], unavailableServices: [] });
const failedAudits = ref({ returned: 0, hasMore: false });
const production = ref(null);

const sourceAvailability = ref({
  dashboard: false,
  teamStats: false,
  submissionStats: false,
  rankingStats: false,
  aiStats: false,
  aiModelStats: false,
  evaluations: false,
  messaging: false,
  audit: false,
  production: false,
});

// 图表 DOM 引用与 ECharts 实例
const problemHotChartRef = ref(null);
const teamStageChartRef = ref(null);
const teamSizeChartRef = ref(null);
const modelPerfChartRef = ref(null);
const msgOutboxChartRef = ref(null);

let problemHotChart = null;
let teamStageChart = null;
let teamSizeChart = null;
let modelPerfChart = null;
let msgOutboxChart = null;

const metricConfigs = [
  { key: "users", label: "用户服务" },
  { key: "teams", label: "队伍服务" },
  { key: "problems", label: "题目服务" },
  { key: "submissions", label: "提交服务" },
  { key: "reviews", label: "评审服务" },
  { key: "suggestions", label: "建议服务" },
  { key: "rankings", label: "排行服务" },
  { key: "assistantConversations", label: "客服服务" },
  { key: "evaluationTasks", label: "质量评价服务" },
  { key: "aiCalls", label: "AI 网关" },
];

const terminalEvaluationStatuses = new Set(["COMPLETED", "CANCELLED"]);
const aiTarget = { path: "/admin/ai-assistant" };

const availableMetricCount = computed(() => metricConfigs.filter((item) => isMetricAvailable(item.key)).length);
const availabilityLabel = computed(() => loadedOnce.value
  ? `${availableMetricCount.value}/${metricConfigs.length} 核心服务在线`
  : "正在同步全平台大盘事实");
const dataTimestamp = computed(() => dashboardTimestamp.value || refreshedAt.value || "等待刷新");
const hasAnyAvailableSource = computed(() => dashboardAvailable.value
  || Object.values(sourceAvailability.value).some(Boolean));

const sourceIssues = computed(() => {
  const issues = [];
  if (!dashboardAvailable.value) {
    issues.push({ key: "dashboard", label: "概览聚合", message: "暂不可用" });
  } else {
    metricConfigs.forEach((item) => {
      const metric = metrics.value[item.key];
      if (!metric || metric.available === false) {
        issues.push({ key: item.key, label: item.label, message: metric?.message || "暂不可用" });
      }
    });
  }

  const supplemental = [
    ["teamStats", "队伍深度分析"],
    ["submissionStats", "提交热度分析"],
    ["rankingStats", "天梯排行事实"],
    ["aiStats", "24h AI 网关大盘"],
    ["aiModelStats", "模型效能统计"],
    ["evaluations", "质量评价任务"],
    ["messaging", "RocketMQ 消息事实"],
    ["audit", "安全操作审计"],
    ["production", "生产工作流版本"],
  ];
  supplemental.forEach(([key, label]) => {
    if (!sourceAvailability.value[key]) {
      issues.push({ key: `supplemental-${key}`, label, message: "接口未取得或已熔断" });
    }
  });
  (messaging.value.unavailableServices || []).forEach((service) => {
    issues.push({ key: `messaging-${service}`, label: service, message: "运维探针不可用" });
  });
  return issues;
});

// 核心事实指标条
const coreMetrics = computed(() => [
  dashboardMetric("users", "全平台用户", "累计注册", "/admin/access"),
  dashboardMetric("problems", "赛题库规模", "已上线赛题", "/admin/problems"),
  dashboardMetric("teams", "进行中队伍", "活跃参赛中", "/admin/teams"),
  dashboardMetric("submissions", "论文提交量", "累计提交成果", "/admin/submissions"),
  {
    key: "ai-calls-24h",
    label: "AI 网关调用",
    value: aiStats.value?.totalCount ?? metrics.value.aiCalls?.value,
    available: sourceAvailability.value.aiStats || isMetricAvailable("aiCalls"),
    meta: "累计/24h活跃",
    clickable: true,
    target: { path: "/admin/ai-calls" },
  },
  {
    key: "ranking-avg-score",
    label: "全平台平均成绩",
    value: globalRankingStats.value?.overallAverageScore ? `${globalRankingStats.value.overallAverageScore} 分` : "—",
    available: sourceAvailability.value.rankingStats,
    meta: "天梯入榜队伍",
    clickable: true,
    target: { path: "/admin/rankings" },
  },
]);

// 消息队列事实快照
const messagingSnapshot = computed(() => {
  const services = messaging.value.services || [];
  let pending = 0;
  let blocked = 0;
  let oldestPendingSeconds = 0;
  let alertingServices = 0;

  services.forEach((service) => {
    const servicePending = number(service.outbox?.PENDING) + number(service.outbox?.SENDING);
    const serviceBlocked = number(service.outbox?.BLOCKED);
    const serviceOldest = number(service.oldestPendingSeconds);
    const hasPausedConsumer = (service.consumers || []).some((consumer) => consumer.paused);
    pending += servicePending;
    blocked += serviceBlocked;
    oldestPendingSeconds = Math.max(oldestPendingSeconds, serviceOldest);
    if (serviceBlocked > 0 || serviceOldest >= 30 || servicePending >= 200 || hasPausedConsumer) {
      alertingServices += 1;
    }
  });

  return { pending, blocked, oldestPendingSeconds, alertingServices };
});

// 待处理异常项
const attentionItems = computed(() => {
  const items = [];
  if (sourceAvailability.value.aiStats && number(aiStats.value?.failureCount) > 0) {
    items.push({
      key: "ai-failures",
      label: "AI 调用失败",
      value: formatNumber(aiStats.value.failureCount),
      scope: "最近 24 小时失败调用，需排查网关或上游网络",
      tone: "danger",
      icon: "WarningFilled",
      target: { path: "/admin/ai-calls", query: { status: "FAILED" } },
    });
  }

  if (sourceAvailability.value.evaluations) {
    const actionable = evaluations.value.filter((item) => !terminalEvaluationStatuses.has(item.status));
    if (actionable.length > 0) {
      items.push({
        key: "evaluations",
        label: "未完成评测任务",
        value: formatNumber(actionable.length),
        scope: "包含处理中或异常评测，点击调起治理工作台",
        tone: actionable.some((item) => item.status === "FAILED") ? "danger" : "warning",
        icon: "Aim",
        target: { path: "/admin/ai-evaluations" },
      });
    }
  }

  if (sourceAvailability.value.messaging && messagingSnapshot.value.alertingServices > 0) {
    const snapshot = messagingSnapshot.value;
    items.push({
      key: "messaging",
      label: "消息队列异常积压",
      value: `${snapshot.alertingServices} 个服务受影响`,
      scope: snapshot.blocked > 0
        ? `${formatNumber(snapshot.blocked)} 条事件阻塞重试`
        : `${formatNumber(snapshot.pending)} 条待投递 · 最老 ${formatDuration(snapshot.oldestPendingSeconds)}`,
      tone: snapshot.blocked > 0 || snapshot.oldestPendingSeconds >= 300 ? "danger" : "warning",
      icon: "Connection",
      target: { path: "/admin/messaging" },
    });
  }

  if (sourceAvailability.value.audit && number(failedAudits.value.returned) > 0) {
    items.push({
      key: "audit-failures",
      label: "高风险操作审计告警",
      value: failedAudits.value.hasMore ? "100+" : formatNumber(failedAudits.value.returned),
      scope: "检测到失败的操作审计流水，点击定位责任人与入参",
      tone: "danger",
      icon: "DocumentChecked",
      target: { path: "/admin/audit", query: { outcome: "FAILED" } },
    });
  }
  return items;
});

const productionLabel = computed(() => sourceAvailability.value.production
  ? production.value?.workflowName || production.value?.workflowVersion || "未配置"
  : "暂未配置生产工作流");

const productionVersion = computed(() => {
  if (!sourceAvailability.value.production) return "—";
  const version = production.value?.workflowVersion || production.value?.productionConfigVersion || "—";
  const revision = production.value?.revision;
  return revision === null || revision === undefined ? version : `${version} (r${revision})`;
});

function dashboardMetric(key, label, meta, target) {
  const metric = metrics.value[key];
  return {
    key,
    label,
    value: metric?.value,
    available: isMetricAvailable(key),
    meta,
    clickable: true,
    target: typeof target === "string" ? { path: target } : target,
  };
}

function isMetricAvailable(key) {
  const metric = metrics.value[key];
  return dashboardAvailable.value && !!metric && metric.available !== false;
}

function number(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString("zh-CN") : "—";
}

function formatMilliseconds(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toLocaleString("zh-CN")} ms` : "—";
}

function formatDuration(seconds) {
  const value = number(seconds);
  if (value < 60) return `${value} 秒`;
  if (value < 3600) return `${Math.floor(value / 60)} 分钟`;
  return `${Math.floor(value / 3600)} 小时`;
}

function successRate(stats) {
  const total = number(stats?.totalCount);
  if (total === 0) return "—";
  return `${Math.round(number(stats?.successCount) / total * 1000) / 10}%`;
}

function formatTimestamp(value) {
  if (!value) return "";
  return String(value).replace("T", " ").replace("Z", "").slice(0, 19);
}

function localDateTime(value) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 19);
}

function timeRange() {
  const to = new Date();
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);
  return {
    ai: { createdFrom: localDateTime(from), createdTo: localDateTime(to) },
    audit: { from: from.toISOString(), to: to.toISOString() },
  };
}

function openMetric(item) {
  openTarget(item.target);
}

function openTarget(target) {
  if (target) router.push(target);
}

// -------------------------------------------------------------
// ECharts 数据渲染逻辑
// -------------------------------------------------------------
function renderAllCharts() {
  nextTick(() => {
    renderProblemHotChart();
    renderTeamCharts();
    renderModelPerfChart();
    renderMsgOutboxChart();
  });
}

// 1. 赛题热度 Top 5 柱状图 (双指标对齐)
function renderProblemHotChart() {
  if (!problemHotChartRef.value) return;
  problemHotChart = problemHotChart || echarts.init(problemHotChartRef.value);

  const topProblems = submissionStats.value?.topProblems || [];
  const teamMap = {};
  (teamStats.value?.topProblems || []).forEach((t) => {
    teamMap[t.problemId] = t.teamCount;
  });

  if (!topProblems.length) {
    problemHotChart.setOption({
      title: { text: "暂无赛题流转数据", left: "center", top: "middle", textStyle: { color: "#9ca3af", fontSize: 12 } },
    }, true);
    return;
  }

  const names = topProblems.map((p) => {
    const code = p.problemCode ? `题号 ${p.problemCode}` : `赛题 #${p.problemId}`;
    return `${code}\n${p.problemTitle ? p.problemTitle.slice(0, 8) + '...' : ''}`;
  });
  const subCounts = topProblems.map((p) => p.submissionCount || 0);
  const teamCounts = topProblems.map((p) => teamMap[p.problemId] || 0);

  problemHotChart.setOption({
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#6b7280" } },
    grid: { left: 42, right: 15, top: 16, bottom: 52 },
    xAxis: {
      type: "category",
      data: names,
      axisLabel: { fontSize: 10, color: "#6b7280", interval: 0, lineHeight: 14 },
    },
    yAxis: { type: "value", minInterval: 1, splitLine: { lineStyle: { color: "#f1f5f9" } } },
    series: [
      {
        name: "论文提交量",
        type: "bar",
        data: subCounts,
        itemStyle: { color: "#2563eb", borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 26,
      },
      {
        name: "参赛队伍数",
        type: "bar",
        data: teamCounts,
        itemStyle: { color: "#10b981", borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 26,
      },
    ],
  }, true);
}

// 2. 队伍阶段分布与成队规模
function renderTeamCharts() {
  const ts = teamStats.value;
  if (teamStageChartRef.value) {
    teamStageChart = teamStageChart || echarts.init(teamStageChartRef.value);
    const stageData = [
      { name: "组建中", value: ts?.preparingTeams || 0, itemStyle: { color: "#f59e0b" } },
      { name: "进行中", value: ts?.inProgressTeams || 0, itemStyle: { color: "#2563eb" } },
      { name: "已结束", value: ts?.endedTeams || 0, itemStyle: { color: "#10b981" } },
      { name: "已解散", value: ts?.disbandedTeams || 0, itemStyle: { color: "#9ca3af" } },
    ].filter((d) => d.value > 0);

    teamStageChart.setOption({
      tooltip: { trigger: "item", formatter: "{b}: {c} 支 ({d}%)" },
      legend: { bottom: 4, icon: "circle", itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
      title: { text: "实训流转阶段", left: "center", top: 0, textStyle: { fontSize: 12, fontWeight: 600, color: "#374151" } },
      series: [{
        type: "pie",
        radius: ["38%", "62%"],
        center: ["50%", "46%"],
        data: stageData.length ? stageData : [{ name: "暂无数据", value: 0 }],
        label: { show: false },
      }],
    }, true);
  }

  if (teamSizeChartRef.value) {
    teamSizeChart = teamSizeChart || echarts.init(teamSizeChartRef.value);
    const sizeDist = ts?.memberSizeDistribution || {};
    const sizeData = [
      { name: "单人独立", value: Number(sizeDist["1"] || 0), itemStyle: { color: "#6366f1" } },
      { name: "双人搭档", value: Number(sizeDist["2"] || 0), itemStyle: { color: "#06b6d4" } },
      { name: "三人满员", value: Number(sizeDist["3"] || 0), itemStyle: { color: "#10b981" } },
    ].filter((d) => d.value > 0);

    teamSizeChart.setOption({
      tooltip: { trigger: "item", formatter: "{b}: {c} 支 ({d}%)" },
      legend: { bottom: 4, icon: "circle", itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
      title: { text: "队伍人数结构", left: "center", top: 0, textStyle: { fontSize: 12, fontWeight: 600, color: "#374151" } },
      series: [{
        type: "pie",
        radius: ["38%", "62%"],
        center: ["50%", "46%"],
        data: sizeData.length ? sizeData : [{ name: "暂无数据", value: 0 }],
        label: { show: false },
      }],
    }, true);
  }
}

// 3. AI 主流模型负载与平均响应耗时 (双 Y 轴对比)
function renderModelPerfChart() {
  if (!modelPerfChartRef.value) return;
  modelPerfChart = modelPerfChart || echarts.init(modelPerfChartRef.value);

  const list = (aiModelStats.value || []).slice(0, 6);
  if (!list.length) {
    modelPerfChart.setOption({
      title: { text: "暂无模型分析数据", left: "center", top: "middle", textStyle: { color: "#9ca3af", fontSize: 12 } },
    }, true);
    return;
  }

  const modelNames = list.map((m) => m.model.replace("deepseek-", "ds-").replace("qwen-", "qw-"));
  const callCounts = list.map((m) => m.totalCount || 0);
  const latencies = list.map((m) => m.averageTotalMs || 0);

  modelPerfChart.setOption({
    tooltip: { trigger: "axis" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#6b7280" } },
    grid: { left: 45, right: 48, top: 22, bottom: 36 },
    xAxis: {
      type: "category",
      data: modelNames,
      axisLabel: { fontSize: 10, color: "#6b7280", interval: 0 },
    },
    yAxis: [
      {
        type: "value",
        name: "调用量",
        nameTextStyle: { fontSize: 10, color: "#6b7280" },
        minInterval: 1,
        splitLine: { lineStyle: { color: "#f1f5f9" } },
      },
      {
        type: "value",
        name: "耗时(ms)",
        nameTextStyle: { fontSize: 10, color: "#6b7280" },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: "调用次数",
        type: "bar",
        data: callCounts,
        itemStyle: { color: "#2563eb", borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 24,
      },
      {
        name: "平均耗时",
        type: "line",
        yAxisIndex: 1,
        data: latencies,
        smooth: true,
        itemStyle: { color: "#f59e0b" },
        lineStyle: { width: 2 },
      },
    ],
  }, true);
}

// 4. 各微服务 Outbox 状态堆叠柱状图
function renderMsgOutboxChart() {
  if (!msgOutboxChartRef.value) return;
  msgOutboxChart = msgOutboxChart || echarts.init(msgOutboxChartRef.value);

  const services = messaging.value.services || [];
  if (!services.length) {
    msgOutboxChart.setOption({
      title: { text: "暂无消息服务监控数据", left: "center", top: "middle", textStyle: { color: "#9ca3af", fontSize: 12 } },
    }, true);
    return;
  }

  const srvNames = services.map((s) => s.service.replace("-service", ""));
  const pendingData = services.map((s) => number(s.outbox?.PENDING));
  const sendingData = services.map((s) => number(s.outbox?.SENDING));
  const blockedData = services.map((s) => number(s.outbox?.BLOCKED));

  msgOutboxChart.setOption({
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: "#6b7280" } },
    grid: { left: 45, right: 20, top: 16, bottom: 36 },
    xAxis: {
      type: "category",
      data: srvNames,
      axisLabel: { fontSize: 10, color: "#6b7280", interval: 0 },
    },
    yAxis: { type: "value", minInterval: 1, splitLine: { lineStyle: { color: "#f1f5f9" } } },
    series: [
      {
        name: "待投递 (PENDING)",
        type: "bar",
        stack: "outbox",
        data: pendingData,
        itemStyle: { color: "#3b82f6" },
        barMaxWidth: 30,
      },
      {
        name: "发送中 (SENDING)",
        type: "bar",
        stack: "outbox",
        data: sendingData,
        itemStyle: { color: "#f59e0b" },
        barMaxWidth: 30,
      },
      {
        name: "阻塞死信 (BLOCKED)",
        type: "bar",
        stack: "outbox",
        data: blockedData,
        itemStyle: { color: "#ef4444" },
        barMaxWidth: 30,
      },
    ],
  }, true);
}

function resizeCharts() {
  problemHotChart?.resize();
  teamStageChart?.resize();
  teamSizeChart?.resize();
  modelPerfChart?.resize();
  msgOutboxChart?.resize();
}

function disposeCharts() {
  problemHotChart?.dispose();
  teamStageChart?.dispose();
  teamSizeChart?.dispose();
  modelPerfChart?.dispose();
  msgOutboxChart?.dispose();
  problemHotChart = null;
  teamStageChart = null;
  teamSizeChart = null;
  modelPerfChart = null;
  msgOutboxChart = null;
}

// -------------------------------------------------------------
// 加载全量大盘数据
// -------------------------------------------------------------
async function loadOverview() {
  if (loading.value) return;
  loading.value = true;
  const range = timeRange();

  const results = await Promise.allSettled([
    getDashboard(),
    getAdminAiCallStats(range.ai),
    listEvaluationTasks(100),
    getMessagingOverview(),
    searchAdminAudit({ ...range.audit, outcome: "FAILED", limit: 100 }),
    getAssistantProductionCurrent(),
    getAdminTeamStats(),
    getAdminSubmissionStats(),
    getAdminGlobalRankingStats(),
    getAdminAiModelStats(),
  ]);

  // 1. Dashboard 指标
  const dashboardResult = results[0];
  dashboardAvailable.value = dashboardResult.status === "fulfilled";
  if (dashboardAvailable.value) {
    metrics.value = dashboardResult.value.data?.metrics || {};
    dashboardTimestamp.value = formatTimestamp(dashboardResult.value.data?.generatedAt);
  } else {
    metrics.value = {};
    dashboardTimestamp.value = "";
  }

  // 2. AI 24h 指标
  sourceAvailability.value.aiStats = results[1].status === "fulfilled";
  aiStats.value = results[1].status === "fulfilled" ? results[1].value.data : null;

  // 3. 评测
  sourceAvailability.value.evaluations = results[2].status === "fulfilled";
  evaluations.value = results[2].status === "fulfilled" ? results[2].value.data || [] : [];

  // 4. 消息队列
  sourceAvailability.value.messaging = results[3].status === "fulfilled";
  messaging.value = results[3].status === "fulfilled"
    ? results[3].value.data || { services: [], unavailableServices: [] }
    : { services: [], unavailableServices: [] };

  // 5. 审计
  sourceAvailability.value.audit = results[4].status === "fulfilled";
  failedAudits.value = results[4].status === "fulfilled"
    ? results[4].value.data || { returned: 0, hasMore: false }
    : { returned: 0, hasMore: false };

  // 6. 生产工作流
  sourceAvailability.value.production = results[5].status === "fulfilled";
  production.value = results[5].status === "fulfilled" ? results[5].value.data : null;

  // 7. 队伍统计
  sourceAvailability.value.teamStats = results[6].status === "fulfilled";
  teamStats.value = results[6].status === "fulfilled" ? results[6].value.data : null;

  // 8. 提交统计
  sourceAvailability.value.submissionStats = results[7].status === "fulfilled";
  submissionStats.value = results[7].status === "fulfilled" ? results[7].value.data : null;

  // 9. 榜单统计
  sourceAvailability.value.rankingStats = results[8].status === "fulfilled";
  globalRankingStats.value = results[8].status === "fulfilled" ? results[8].value.data : null;

  // 10. AI 模型多维统计
  sourceAvailability.value.aiModelStats = results[9].status === "fulfilled";
  aiModelStats.value = results[9].status === "fulfilled" ? results[9].value.data || [] : [];

  refreshedAt.value = formatTimestamp(localDateTime(new Date()));
  issuesExpanded.value = false;
  loadedOnce.value = true;
  loading.value = false;

  renderAllCharts();
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts);
  loadOverview();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  disposeCharts();
});
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-variant-numeric: tabular-nums;
}

/* 顶栏与状态条 */
.overview-status-bar {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.overview-status-bar.warning {
  border-color: #fde68a;
  background: #fffdf5;
}

.status-leading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.status-time {
  color: var(--lm-admin-text-muted);
  font-size: 11px;
}

.issue-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  color: var(--lm-admin-warning);
  background: transparent;
  border: 0;
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
  font-size: 11px;
}

.issue-toggle:hover {
  background: #fffbeb;
}

.source-issue-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 6px 16px;
  padding: 10px 12px;
  color: var(--lm-admin-text-muted);
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--lm-admin-radius-panel);
  font-size: 11px;
}

.source-issue-list strong {
  margin-right: 4px;
  color: var(--lm-admin-warning);
}

/* 面板通用样式 */
.overview-panel {
  min-width: 0;
  padding: 14px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.overview-panel.warning-panel {
  border-color: #fed7aa;
  background: #fffcf9;
}

.overview-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.heading-lead {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.panel-tag {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.8px;
  color: var(--lm-admin-primary);
}

.panel-tag.warning {
  color: #ea580c;
}

.overview-heading h2 {
  margin: 0;
  color: var(--lm-admin-text-strong);
  font-size: 15px;
  font-weight: 700;
}

.overview-heading span {
  color: var(--lm-admin-text-muted);
  font-size: 11px;
}

/* 待处理网格 */
.attention-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
}

.attention-item {
  --attention-color: var(--lm-admin-warning);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  color: var(--lm-admin-text-default);
  text-align: left;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-left: 3px solid var(--attention-color);
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
  transition: all var(--lm-admin-transition-fast);
}

.attention-item:hover {
  background: var(--lm-admin-surface-subtle);
  border-color: var(--lm-admin-border-strong);
  border-left-color: var(--attention-color);
}

.attention-item.tone-danger {
  --attention-color: var(--lm-admin-danger);
}

.attention-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  color: var(--attention-color);
  background: color-mix(in srgb, var(--attention-color) 10%, white);
  border-radius: var(--lm-admin-radius-control);
  font-size: 16px;
}

.attention-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.attention-copy strong {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attention-copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attention-value {
  flex: 0 0 auto;
  color: var(--attention-color);
  font-size: 16px;
  font-weight: 700;
}

.attention-arrow {
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

/* 图表容器与布局 */
.analysis-grid-two {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 12px;
}

.chart-card {
  padding: 12px 14px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.chart-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chart-title strong {
  font-size: 13px;
  color: var(--lm-admin-text-strong);
}

.chart-sub {
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}

.chart-container {
  width: 100%;
  height: 235px;
}

.chart-container-sm {
  width: 100%;
  height: 195px;
}

.team-charts-wrapper {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  height: 235px;
}

.chart-half {
  width: 100%;
  height: 100%;
}

/* AI 态势布局 */
.ai-overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 1fr);
  gap: 12px;
}

.ai-posture-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-stat-boxes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.box-label {
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}

.box-value {
  margin: 2px 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-admin-text-strong);
}

.box-hint {
  font-size: 10px;
  color: var(--lm-admin-text-muted);
}

.production-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: var(--lm-admin-radius-control);
  color: #fff;
  cursor: pointer;
  text-align: left;
  transition: background var(--lm-admin-transition-fast);
}

.production-banner:hover {
  background: #1e293b;
}

.prod-left {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.prod-tag {
  font-size: 9px;
  letter-spacing: 0.8px;
  font-weight: 700;
  color: #38bdf8;
}

.prod-title {
  font-size: 13px;
  color: #f8fafc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prod-desc {
  font-size: 10px;
  color: #94a3b8;
}

.prod-right {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #38bdf8;
}

.prod-ver {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}

/* 消息态势 */
.messaging-overview-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg-kpi-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 20px;
  padding: 10px 14px;
  background: var(--lm-admin-surface-subtle);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.msg-kpi {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

.msg-kpi span {
  color: var(--lm-admin-text-muted);
  font-size: 11px;
}

.msg-kpi strong {
  font-size: 15px;
  color: var(--lm-admin-text-strong);
}

.msg-kpi-action {
  margin-left: auto;
  font-size: 12px;
}

.text-success { color: #16a34a !important; }
.text-warning { color: #d97706 !important; }
.text-danger { color: #dc2626 !important; }

@media (max-width: 1100px) {
  .analysis-grid-two {
    grid-template-columns: 1fr;
  }
  .ai-overview-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .dashboard-page {
    gap: 8px;
  }
  .overview-status-bar {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .overview-heading {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }
  .team-charts-wrapper {
    grid-template-columns: 1fr;
    height: 380px;
  }
  .msg-kpi-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .msg-kpi-action {
    margin-left: 0;
  }
}
</style>
