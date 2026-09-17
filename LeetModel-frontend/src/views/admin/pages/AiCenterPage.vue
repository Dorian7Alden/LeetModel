<template>
  <div class="ai-center-page">
    <AdminMetricStrip :items="summaryItems" :loading="summaryLoading && !summaryLoaded" />

    <div v-if="summaryIssue" class="summary-warning" role="status">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ summaryIssue }}</span>
      <el-button link type="primary" :loading="summaryLoading" @click="loadSummary">重试</el-button>
    </div>

    <div class="ai-nav-panel">
      <AdminSubnav
        :model-value="activeView"
        :items="navigationItems"
        aria-label="AI 中枢工作面"
        @update:model-value="selectView"
      />
      <el-button class="refresh-button" link :loading="summaryLoading" @click="loadSummary">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <section class="ai-view" :aria-label="activeViewLabel">
      <component :is="activeComponent" :key="activeView" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminMetricStrip from "../components/AdminMetricStrip.vue";
import AdminSubnav from "../components/AdminSubnav.vue";
import AiCallListPage from "./AiCallListPage.vue";
import EvaluationPage from "./EvaluationPage.vue";
import ProductionWorkflowPage from "./ProductionWorkflowPage.vue";
import AiVersionCatalogPage from "./AiVersionCatalogPage.vue";
import MessagingOperationsPage from "./MessagingOperationsPage.vue";
import { getAdminAiCallStats, getAssistantProductionCurrent } from "@/api/admin-ai";

const route = useRoute();
const router = useRouter();
const summaryLoading = ref(false);
const summaryLoaded = ref(false);
const statsAvailable = ref(false);
const productionAvailable = ref(false);
const summary = ref({
  successRate: "—",
  failures: "—",
  tokens: "—",
  latency: "—",
  production: "—",
});

const views = {
  calls: { label: "调用", icon: "DataLine", component: AiCallListPage },
  versions: { label: "版本", icon: "Tickets", component: AiVersionCatalogPage },
  evaluations: { label: "质量评价", icon: "Histogram", component: EvaluationPage },
  production: { label: "生产版本", icon: "SetUp", component: ProductionWorkflowPage },
  messaging: { label: "消息运维", icon: "Connection", component: MessagingOperationsPage },
};

const activeView = computed(() => typeof route.query.view === "string" && views[route.query.view]
  ? route.query.view
  : "calls");
const activeComponent = computed(() => views[activeView.value].component);
const activeViewLabel = computed(() => views[activeView.value].label);
const navigationItems = Object.entries(views).map(([value, item]) => ({
  value,
  label: item.label,
  icon: item.icon,
}));
const summaryItems = computed(() => [
  { key: "success", label: "成功率", value: summary.value.successRate, available: statsAvailable.value },
  { key: "failures", label: "失败调用", value: summary.value.failures, available: statsAvailable.value },
  { key: "latency", label: "平均耗时", value: summary.value.latency, available: statsAvailable.value },
  { key: "tokens", label: "Tokens", value: summary.value.tokens, available: statsAvailable.value },
  { key: "production", label: "当前生产版本", value: summary.value.production, available: productionAvailable.value },
]);
const summaryIssue = computed(() => {
  if (!summaryLoaded.value || (statsAvailable.value && productionAvailable.value)) return "";
  if (!statsAvailable.value && !productionAvailable.value) return "运行指标与生产版本暂不可用";
  return statsAvailable.value ? "当前生产版本暂不可用" : "运行指标暂不可用";
});

function selectView(value) {
  if (value === activeView.value) return;
  const query = { ...route.query, view: value };
  if (value === "calls") delete query.view;
  router.replace({ query });
}

async function loadSummary() {
  summaryLoading.value = true;
  const [statsResult, productionResult] = await Promise.allSettled([
    getAdminAiCallStats(),
    getAssistantProductionCurrent(),
  ]);

  statsAvailable.value = statsResult.status === "fulfilled";
  productionAvailable.value = productionResult.status === "fulfilled";

  if (statsAvailable.value) {
    const stats = statsResult.value.data || {};
    const total = Number(stats.totalCount || 0);
    const success = Number(stats.successCount || 0);
    summary.value.successRate = total > 0 ? `${Math.round(success / total * 1000) / 10}%` : "0%";
    summary.value.failures = Number(stats.failureCount || 0);
    summary.value.tokens = Number(stats.totalTokens || 0).toLocaleString("zh-CN");
    summary.value.latency = stats.averageTotalMs == null ? "—" : `${stats.averageTotalMs} ms`;
  }

  if (productionAvailable.value) {
    const production = productionResult.value.data;
    summary.value.production = production?.workflowName || production?.workflowVersion || "—";
  }

  summaryLoaded.value = true;
  summaryLoading.value = false;
}

watch(() => route.query.view, value => {
  if (value && !views[value]) router.replace({ query: { ...route.query, view: undefined } });
});

onMounted(loadSummary);
</script>

<style scoped>
.ai-center-page {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--lm-admin-space-3);
}

.ai-nav-panel,
.ai-view {
  min-width: 0;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.ai-nav-panel {
  display: flex;
  align-items: center;
  padding-left: var(--lm-admin-space-2);
}

.ai-nav-panel :deep(.admin-subnav) {
  min-width: 0;
  flex: 1;
  border-bottom: 0;
}

.refresh-button {
  flex: 0 0 auto;
  margin: 0 10px;
}

.ai-view {
  overflow: hidden;
}

.summary-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--lm-admin-radius-control);
  font-size: 12px;
}

.summary-warning span {
  flex: 1;
}

.ai-view > :deep(*) {
  animation: ai-view-enter var(--lm-admin-transition);
}

@keyframes ai-view-enter {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .ai-view > :deep(*) { animation: none; }
}
</style>
