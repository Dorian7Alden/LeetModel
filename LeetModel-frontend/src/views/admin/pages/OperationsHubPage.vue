<template>
  <div class="domain-page operations-hub-page">
    <section class="domain-hero domain-hero-orange">
      <div class="domain-hero-copy">
        <span class="domain-eyebrow">BUSINESS OPERATIONS</span>
        <h2>沿真实业务链观察，而不是逐表巡检</h2>
        <p>把队伍、提交、评审、改进建议和排行串成同一条运营链，先判断流转是否顺畅，再进入明细处理。</p>
      </div>
      <div class="domain-actions">
        <el-button class="hero-button" :loading="summaryLoading" @click="loadSummary"><el-icon><Refresh /></el-icon>刷新链路</el-button>
      </div>
    </section>

    <div class="operation-flow" v-loading="summaryLoading">
      <template v-for="(item, index) in flowItems" :key="item.key">
        <button class="flow-node" :class="{ active: activeView === item.key }" @click="setView(item.key)">
          <span class="flow-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <span class="flow-copy"><small>{{ item.step }}</small><strong>{{ item.title }}</strong><em>{{ item.value }}</em></span>
        </button>
        <span v-if="index < flowItems.length - 1" class="flow-arrow"><el-icon><ArrowRight /></el-icon></span>
      </template>
    </div>

    <section class="domain-section hub-section">
      <el-tabs v-model="activeView" class="domain-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="item in views" :key="item.key" :name="item.key">
          <template #label><span class="domain-tab-label"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</span></template>
          <component :is="item.component" v-if="activeView === item.key" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import SubmissionListPage from "./SubmissionListPage.vue";
import TeamListPage from "./TeamListPage.vue";
import ReviewListPage from "./ReviewListPage.vue";
import SuggestionListPage from "./SuggestionListPage.vue";
import RankingAdminPage from "./RankingAdminPage.vue";
import { getAdminReviews, getAdminSubmissions, getAdminSuggestions, getAdminTeams } from "@/api/admin-ops";

const route = useRoute();
const router = useRouter();
const summaryLoading = ref(false);
const counts = ref({ teams: "—", submissions: "—", reviews: "—", suggestions: "—" });
const views = [
  { key: "teams", label: "队伍", icon: "UserFilled", component: TeamListPage },
  { key: "submissions", label: "提交", icon: "UploadFilled", component: SubmissionListPage },
  { key: "reviews", label: "评审", icon: "DataAnalysis", component: ReviewListPage },
  { key: "suggestions", label: "建议", icon: "ChatLineSquare", component: SuggestionListPage },
  { key: "rankings", label: "排行", icon: "Trophy", component: RankingAdminPage },
];
const validViews = new Set(views.map((item) => item.key));
const activeView = ref(validViews.has(route.query.view) ? route.query.view : "submissions");
const flowItems = computed(() => [
  { key: "teams", step: "01", title: "组建队伍", value: formatCount(counts.value.teams), icon: "UserFilled" },
  { key: "submissions", step: "02", title: "提交论文", value: formatCount(counts.value.submissions), icon: "UploadFilled" },
  { key: "reviews", step: "03", title: "AI 评审", value: formatCount(counts.value.reviews), icon: "DataAnalysis" },
  { key: "suggestions", step: "04", title: "改进建议", value: formatCount(counts.value.suggestions), icon: "ChatLineSquare" },
  { key: "rankings", step: "05", title: "形成排行", value: "按题目查看", icon: "Trophy" },
]);

function formatCount(value) { return value === "—" ? value : `${value} 条近期记录`; }
function setView(key) { activeView.value = key; handleTabChange(key); }
function handleTabChange(key) { router.replace({ query: { ...route.query, view: key } }); }
async function loadSummary() {
  summaryLoading.value = true;
  const results = await Promise.allSettled([
    getAdminTeams(50), getAdminSubmissions(50), getAdminReviews(50), getAdminSuggestions(50),
  ]);
  const readLength = (result) => result.status === "fulfilled" ? (result.value.data?.length ?? 0) : "—";
  counts.value = { teams: readLength(results[0]), submissions: readLength(results[1]), reviews: readLength(results[2]), suggestions: readLength(results[3]) };
  summaryLoading.value = false;
}

watch(() => route.query.view, (value) => { if (validViews.has(value)) activeView.value = value; });
onMounted(loadSummary);
</script>

<style scoped>
@import '../style.css';
</style>
