<template>
  <div class="dashboard-page">
    <div class="overview-status-bar" :class="{ warning: loadedOnce && sourceIssues.length }">
      <div class="status-leading">
        <AdminStatusBadge
          :status="!loadedOnce ? 'WAITING' : sourceIssues.length ? 'WARNING' : 'HEALTHY'"
          :label="availabilityLabel"
        />
        <span class="status-time">更新于 {{ dataTimestamp }}</span>
        <button
          v-if="loadedOnce && sourceIssues.length"
          class="issue-toggle"
          type="button"
          :aria-expanded="issuesExpanded"
          aria-controls="overview-source-issues"
          @click="issuesExpanded = !issuesExpanded"
        >
          {{ sourceIssues.length }} 项数据缺失
          <el-icon><ArrowUp v-if="issuesExpanded" /><ArrowDown v-else /></el-icon>
        </button>
      </div>
      <el-button size="small" :loading="loading" @click="loadOverview">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

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
      请检查管理聚合服务和下游数据源。
    </AdminStatePanel>

    <template v-else>
      <section class="overview-panel core-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <h2>核心事实</h2>
          <span>AI 调用为最近 24 小时，其余为累计或当前值</span>
        </div>
        <AdminMetricStrip :items="coreMetrics" :loading="loading && !loadedOnce" @select="openMetric" />
      </section>

      <div class="overview-focus-grid">
        <section class="overview-panel attention-panel" v-loading="loading && !loadedOnce">
          <div class="overview-heading">
            <h2>待处理</h2>
            <span>24 小时事实与当前消息水位</span>
          </div>

          <div v-if="attentionItems.length" class="attention-grid">
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
              <b>{{ item.value }}</b>
              <el-icon class="attention-arrow"><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-else-if="attentionCoverageAvailable" class="attention-empty" role="status">
            <el-icon><CircleCheck /></el-icon>
            当前可用口径内没有待处理项
          </div>

          <AdminStatePanel v-else type="error" title="待处理信息暂不可用" />
        </section>

        <section class="overview-panel ai-panel" v-loading="loading && !loadedOnce">
          <div class="overview-heading">
            <h2>AI 运行</h2>
            <span>最近 24 小时</span>
          </div>
          <div class="ai-fact-grid">
            <div v-for="item in aiFacts" :key="item.label" class="ai-fact">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <button class="production-row" type="button" @click="openTarget(aiTarget)">
            <span>
              <small>当前生产工作流</small>
              <strong>{{ productionLabel }}</strong>
            </span>
            <span class="production-version">{{ productionVersion }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </section>
      </div>

      <section class="overview-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <h2>业务流转</h2>
          <span>无统一时间窗口，仅展示当前或累计量级</span>
        </div>
        <AdminMetricStrip :items="businessMetrics" :loading="loading && !loadedOnce" @select="openMetric" />
      </section>

      <section class="overview-panel" v-loading="loading && !loadedOnce">
        <div class="overview-heading">
          <h2>AI 资产</h2>
        </div>
        <AdminMetricStrip :items="assetMetrics" :loading="loading && !loadedOnce" @select="openMetric" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getDashboard } from "@/api/dashboard";
import {
  getAdminAiCallStats,
  getAssistantProductionCurrent,
  listEvaluationTasks,
} from "@/api/admin-ai";
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
const aiStats = ref(null);
const evaluations = ref([]);
const messaging = ref({ services: [], unavailableServices: [] });
const failedAudits = ref({ returned: 0, hasMore: false });
const production = ref(null);
const sourceAvailability = ref({
  ai: false,
  evaluations: false,
  messaging: false,
  audit: false,
  production: false,
});

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
const operationsTarget = { path: "/admin/operations" };
const aiTarget = { path: "/admin/ai", query: { view: "production" } };

const availableMetricCount = computed(() => metricConfigs.filter((item) => isMetricAvailable(item.key)).length);
const availabilityLabel = computed(() => loadedOnce.value
  ? `${availableMetricCount.value}/${metricConfigs.length} 领域指标可用`
  : "正在加载概览");
const dataTimestamp = computed(() => dashboardTimestamp.value || refreshedAt.value || "等待刷新");
const hasAnyAvailableSource = computed(() => dashboardAvailable.value
  || Object.values(sourceAvailability.value).some(Boolean));
const attentionCoverageAvailable = computed(() => sourceAvailability.value.ai
  || sourceAvailability.value.evaluations
  || sourceAvailability.value.messaging
  || sourceAvailability.value.audit);

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

  const supplementalSources = [
    ["ai", "24 小时 AI 统计"],
    ["evaluations", "最近评价任务"],
    ["messaging", "消息运维概览"],
    ["audit", "失败审计查询"],
    ["production", "生产版本"],
  ];
  supplementalSources.forEach(([key, label]) => {
    if (!sourceAvailability.value[key]) {
      issues.push({ key: `supplemental-${key}`, label, message: "暂不可用" });
    }
  });
  (messaging.value.unavailableServices || []).forEach((service) => {
    issues.push({ key: `messaging-${service}`, label: service, message: "消息运维端点不可用" });
  });
  return issues;
});

const coreMetrics = computed(() => [
  dashboardMetric("users", "用户", "累计", "/admin/access"),
  dashboardMetric("problems", "题目", "累计", "/admin/content"),
  dashboardMetric("teams", "进行中队伍", "当前", operationsTarget),
  dashboardMetric("submissions", "提交", "累计", operationsTarget),
  {
    key: "ai-calls-24h",
    label: "AI 调用",
    value: aiStats.value?.totalCount,
    available: sourceAvailability.value.ai,
    meta: "最近 24 小时",
    clickable: true,
    target: { path: "/admin/ai", query: { view: "calls" } },
  },
]);

const businessMetrics = computed(() => [
  dashboardMetric("teams", "进行中队伍", "当前", { path: "/admin/operations", query: { view: "teams" } }),
  dashboardMetric("submissions", "提交", "累计", { path: "/admin/operations", query: { view: "submissions" } }),
  dashboardMetric("reviews", "评审", "累计", { path: "/admin/operations", query: { view: "reviews" } }),
  dashboardMetric("suggestions", "改进建议", "累计", { path: "/admin/operations", query: { view: "suggestions" } }),
  dashboardMetric("rankings", "排行记录", "当前", { path: "/admin/operations", query: { view: "rankings" } }),
]);

const assetMetrics = computed(() => [
  dashboardMetric("assistantConversations", "客服会话", "累计", { path: "/admin/ai", query: { view: "calls" } }),
  dashboardMetric("evaluationTasks", "评价任务", "累计", { path: "/admin/ai", query: { view: "evaluations" } }),
]);

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

const attentionItems = computed(() => {
  const items = [];
  if (sourceAvailability.value.ai && number(aiStats.value?.failureCount) > 0) {
    items.push({
      key: "ai-failures",
      label: "AI 调用失败",
      value: formatNumber(aiStats.value.failureCount),
      scope: "最近 24 小时",
      tone: "danger",
      icon: "WarningFilled",
      target: { path: "/admin/ai", query: { view: "calls" } },
    });
  }

  if (sourceAvailability.value.evaluations) {
    const actionable = evaluations.value.filter((item) => !terminalEvaluationStatuses.has(item.status));
    if (actionable.length > 0) {
      items.push({
        key: "evaluations",
        label: "待处理评价",
        value: formatNumber(actionable.length),
        scope: "最近 100 项检查",
        tone: actionable.some((item) => item.status === "FAILED") ? "danger" : "warning",
        icon: "Histogram",
        target: { path: "/admin/ai", query: { view: "evaluations" } },
      });
    }
  }

  if (sourceAvailability.value.messaging && messagingSnapshot.value.alertingServices > 0) {
    const snapshot = messagingSnapshot.value;
    items.push({
      key: "messaging",
      label: "消息链路需关注",
      value: `${snapshot.alertingServices} 个服务`,
      scope: snapshot.blocked > 0
        ? `${formatNumber(snapshot.blocked)} 条阻塞`
        : `${formatNumber(snapshot.pending)} 条待投递 · 最老 ${formatDuration(snapshot.oldestPendingSeconds)}`,
      tone: snapshot.blocked > 0 || snapshot.oldestPendingSeconds >= 300 ? "danger" : "warning",
      icon: "Connection",
      target: { path: "/admin/ai", query: { view: "messaging" } },
    });
  }

  if (sourceAvailability.value.audit && number(failedAudits.value.returned) > 0) {
    items.push({
      key: "audit-failures",
      label: "失败治理操作",
      value: failedAudits.value.hasMore ? "100+" : formatNumber(failedAudits.value.returned),
      scope: "最近 24 小时",
      tone: "danger",
      icon: "DocumentChecked",
      target: { path: "/admin/audit", query: { outcome: "FAILED" } },
    });
  }
  return items;
});

const aiFacts = computed(() => [
  { label: "成功率", value: sourceAvailability.value.ai ? successRate(aiStats.value) : "—" },
  { label: "失败", value: sourceAvailability.value.ai ? formatNumber(aiStats.value?.failureCount) : "—" },
  {
    label: "平均耗时",
    value: sourceAvailability.value.ai && number(aiStats.value?.totalCount) > 0
      ? formatMilliseconds(aiStats.value?.averageTotalMs)
      : "—",
  },
  { label: "Tokens", value: sourceAvailability.value.ai ? formatNumber(aiStats.value?.totalTokens) : "—" },
]);

const productionLabel = computed(() => sourceAvailability.value.production
  ? production.value?.workflowName || production.value?.workflowVersion || "未配置"
  : "暂不可用");
const productionVersion = computed(() => {
  if (!sourceAvailability.value.production) return "—";
  const version = production.value?.workflowVersion || production.value?.productionConfigVersion || "—";
  const revision = production.value?.revision;
  return revision === null || revision === undefined ? version : `${version} · r${revision}`;
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
    target,
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
  ]);

  const dashboardResult = results[0];
  dashboardAvailable.value = dashboardResult.status === "fulfilled";
  if (dashboardAvailable.value) {
    metrics.value = dashboardResult.value.data?.metrics || {};
    dashboardTimestamp.value = formatTimestamp(dashboardResult.value.data?.generatedAt);
  } else {
    metrics.value = {};
    dashboardTimestamp.value = "";
  }

  sourceAvailability.value = {
    ai: results[1].status === "fulfilled",
    evaluations: results[2].status === "fulfilled",
    messaging: results[3].status === "fulfilled",
    audit: results[4].status === "fulfilled",
    production: results[5].status === "fulfilled",
  };
  aiStats.value = results[1].status === "fulfilled" ? results[1].value.data : null;
  evaluations.value = results[2].status === "fulfilled" ? results[2].value.data || [] : [];
  messaging.value = results[3].status === "fulfilled"
    ? results[3].value.data || { services: [], unavailableServices: [] }
    : { services: [], unavailableServices: [] };
  failedAudits.value = results[4].status === "fulfilled"
    ? results[4].value.data || { returned: 0, hasMore: false }
    : { returned: 0, hasMore: false };
  production.value = results[5].status === "fulfilled" ? results[5].value.data : null;

  refreshedAt.value = formatTimestamp(localDateTime(new Date()));
  issuesExpanded.value = false;
  loadedOnce.value = true;
  loading.value = false;
}

onMounted(loadOverview);
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-variant-numeric: tabular-nums;
}

.overview-status-bar {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px 6px 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.overview-status-bar.warning {
  border-color: #fde68a;
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
  padding: 4px 6px;
  color: var(--lm-admin-warning);
  background: transparent;
  border: 0;
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
  font-size: 11px;
}

.issue-toggle:hover,
.issue-toggle:focus-visible {
  background: #fffbeb;
}

.issue-toggle:focus-visible,
.attention-item:focus-visible,
.production-row:focus-visible {
  outline: 2px solid var(--lm-admin-primary);
  outline-offset: 2px;
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

.overview-panel {
  min-width: 0;
  padding: 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.overview-heading {
  display: flex;
  min-height: 28px;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.overview-heading h2 {
  margin: 0;
  color: var(--lm-admin-text-strong);
  font-size: 14px;
  font-weight: 700;
}

.overview-heading span {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
  text-align: right;
}

.overview-focus-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 1fr);
  gap: 12px;
}

.attention-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.attention-item {
  --attention-color: var(--lm-admin-warning);
  display: flex;
  min-width: 0;
  min-height: 58px;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  color: var(--lm-admin-text-default);
  text-align: left;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-left: 3px solid var(--attention-color);
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
  transition: background var(--lm-admin-transition-fast), border-color var(--lm-admin-transition-fast);
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
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  color: var(--attention-color);
  background: color-mix(in srgb, var(--attention-color) 9%, white);
  border-radius: var(--lm-admin-radius-control);
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
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attention-copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attention-item b {
  flex: 0 0 auto;
  color: var(--attention-color);
  font-size: 15px;
}

.attention-arrow {
  flex: 0 0 auto;
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

.attention-empty {
  display: flex;
  min-height: 58px;
  align-items: center;
  gap: 8px;
  color: var(--lm-admin-success);
  font-size: 12px;
}

.ai-fact-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.ai-fact {
  display: flex;
  min-width: 0;
  min-height: 54px;
  justify-content: center;
  flex-direction: column;
  padding: 8px 10px;
  border-right: 1px solid var(--lm-admin-border);
}

.ai-fact:last-child {
  border-right: 0;
}

.ai-fact span,
.production-row small {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.ai-fact strong {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-strong);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-row {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding: 7px 10px;
  color: var(--lm-admin-text-default);
  text-align: left;
  background: var(--lm-admin-surface-subtle);
  border: 0;
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
}

.production-row > span:first-child {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.production-row strong,
.production-version {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-version {
  max-width: 42%;
  color: var(--lm-admin-text-muted);
}

@media (max-width: 1100px) {
  .overview-focus-grid {
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

  .status-leading {
    width: calc(100% - 70px);
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 5px 8px;
  }

  .overview-panel {
    padding: 10px;
  }

  .overview-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 1px;
    margin-bottom: 6px;
  }

  .overview-heading span {
    text-align: left;
  }

  .attention-grid {
    grid-template-columns: 1fr;
  }

  .ai-fact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-fact:nth-child(2) {
    border-right: 0;
  }

  .ai-fact:nth-child(-n + 2) {
    border-bottom: 1px solid var(--lm-admin-border);
  }
}
</style>
