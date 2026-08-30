<template>
  <div class="domain-page ai-center-page">
    <section class="domain-hero domain-hero-violet">
      <div class="domain-hero-copy">
        <span class="domain-eyebrow">AI CONTROL PLANE</span>
        <h2>从可观测到可评价，再到安全上线</h2>
        <p>调用事实回答“运行得怎样”，质量评价回答“版本是否更好”，生产版本治理决定“真实流量使用什么”。</p>
      </div>
      <div class="domain-actions">
        <el-button class="hero-button" :loading="summaryLoading" @click="loadSummary"><el-icon><Refresh /></el-icon>刷新状态</el-button>
      </div>
    </section>

    <div class="ai-posture-grid" v-loading="summaryLoading">
      <div class="ai-posture-card emphasis">
        <span class="posture-label">调用成功率</span>
        <strong>{{ successRate }}</strong>
        <el-progress :percentage="successPercentage" :stroke-width="6" :show-text="false" color="#8b5cf6" />
        <small>{{ summary.success }} 成功 / {{ summary.total }} 次调用</small>
      </div>
      <div class="ai-posture-card"><span class="posture-label">累计 Tokens</span><strong>{{ formatNumber(summary.tokens) }}</strong><small>来自网关计量事实</small></div>
      <div class="ai-posture-card"><span class="posture-label">平均耗时</span><strong>{{ summary.latency === '—' ? '—' : `${summary.latency} ms` }}</strong><small>端到端调用耗时</small></div>
      <div class="ai-posture-card"><span class="posture-label">评价任务</span><strong>{{ summary.evaluations }}</strong><small>最近可见实验任务</small></div>
      <div class="ai-posture-card production"><span class="posture-label">生产工作流</span><strong>{{ summary.production }}</strong><small>{{ summary.revision }}</small></div>
    </div>

    <section class="domain-section hub-section">
      <el-tabs v-model="activeView" class="domain-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="item in views" :key="item.key" :name="item.key">
          <template #label><span class="domain-tab-label"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}<small>{{ item.hint }}</small></span></template>
          <component :is="item.component" v-if="activeView === item.key" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AiCallListPage from "./AiCallListPage.vue";
import EvaluationPage from "./EvaluationPage.vue";
import ProductionWorkflowPage from "./ProductionWorkflowPage.vue";
import { getAdminAiCallStats, getAssistantProductionCurrent, listEvaluationTasks } from "@/api/admin-ai";

const route = useRoute();
const router = useRouter();
const summaryLoading = ref(false);
const summary = ref({ total: "—", success: "—", tokens: "—", latency: "—", evaluations: "—", production: "暂不可用", revision: "未取得生产快照" });
const views = [
  { key: "calls", label: "调用观测", hint: "事实", icon: "DataLine", component: AiCallListPage },
  { key: "evaluations", label: "质量评价", hint: "实验", icon: "Histogram", component: EvaluationPage },
  { key: "production", label: "生产版本", hint: "治理", icon: "SetUp", component: ProductionWorkflowPage },
];
const validViews = new Set(views.map((item) => item.key));
const activeView = ref(validViews.has(route.query.view) ? route.query.view : "calls");
const successPercentage = computed(() => {
  const total = Number(summary.value.total);
  const success = Number(summary.value.success);
  return total > 0 ? Math.round(success / total * 1000) / 10 : 0;
});
const successRate = computed(() => summary.value.total === "—" ? "—" : `${successPercentage.value}%`);

function formatNumber(value) { return typeof value === "number" ? value.toLocaleString("zh-CN") : value; }
function handleTabChange(key) { router.replace({ query: { ...route.query, view: key } }); }
async function loadSummary() {
  summaryLoading.value = true;
  const [statsResult, taskResult, productionResult] = await Promise.allSettled([
    getAdminAiCallStats(), listEvaluationTasks(50), getAssistantProductionCurrent(),
  ]);
  const stats = statsResult.status === "fulfilled" ? statsResult.value.data : null;
  const production = productionResult.status === "fulfilled" ? productionResult.value.data : null;
  summary.value = {
    total: stats?.totalCount ?? "—",
    success: stats?.successCount ?? "—",
    tokens: stats?.totalTokens ?? "—",
    latency: stats?.averageTotalMs ?? "—",
    evaluations: taskResult.status === "fulfilled" ? (taskResult.value.data?.length ?? 0) : "—",
    production: production?.workflowName || "暂不可用",
    revision: production ? `${production.workflowVersion} · revision ${production.revision}` : "未取得生产快照",
  };
  summaryLoading.value = false;
}

watch(() => route.query.view, (value) => { if (validViews.has(value)) activeView.value = value; });
onMounted(loadSummary);
</script>

<style scoped>
@import '../style.css';
</style>
