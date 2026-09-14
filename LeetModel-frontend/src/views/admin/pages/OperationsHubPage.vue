<template>
  <div class="operations-hub-page">
    <div class="operations-nav-panel">
      <AdminSubnav
        :model-value="activeView"
        :items="navigationItems"
        aria-label="业务运营工作面"
        @update:model-value="selectView"
      />
    </div>

    <div v-if="referenceIssue" class="reference-warning" role="status">
      <el-icon><WarningFilled /></el-icon>
      <span>部分队伍或题目名称未取得，相关位置暂显示为“—”</span>
      <el-button link type="primary" :loading="referenceLoading" @click="loadReferences">重试</el-button>
    </div>

    <section class="operations-view" :aria-label="activeViewLabel">
      <component
        :is="activeComponent"
        :key="activeView"
        :team-map="teamMap"
        :problem-map="problemMap"
        @reference-ids="loadTeamReferences"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminSubnav from "../components/AdminSubnav.vue";
import SubmissionListPage from "./SubmissionListPage.vue";
import TeamListPage from "./TeamListPage.vue";
import ReviewListPage from "./ReviewListPage.vue";
import SuggestionListPage from "./SuggestionListPage.vue";
import RankingAdminPage from "./RankingAdminPage.vue";
import { getAdminTeamReferences } from "@/api/admin-ops";
import { getPublicProblemList } from "@/api/problem";

const route = useRoute();
const router = useRouter();
const referenceRequests = ref(0);
const teamReferenceError = ref(false);
const problemReferenceError = ref(false);
const requestedTeamIds = ref([]);
const teamMap = ref({});
const problemMap = ref({});
const referenceLoading = computed(() => referenceRequests.value > 0);
const referenceIssue = computed(() => teamReferenceError.value || problemReferenceError.value);

const views = {
  submissions: { label: "提交", icon: "UploadFilled", component: SubmissionListPage },
  teams: { label: "队伍", icon: "UserFilled", component: TeamListPage },
  reviews: { label: "评审", icon: "DataAnalysis", component: ReviewListPage },
  suggestions: { label: "建议", icon: "ChatLineSquare", component: SuggestionListPage },
  rankings: { label: "排行", icon: "Trophy", component: RankingAdminPage },
};

const activeView = computed(() => typeof route.query.view === "string" && views[route.query.view]
  ? route.query.view
  : "submissions");
const activeComponent = computed(() => views[activeView.value].component);
const activeViewLabel = computed(() => views[activeView.value].label);
const navigationItems = Object.entries(views).map(([value, item]) => ({
  value,
  label: item.label,
  icon: item.icon,
}));

function selectView(value) {
  if (value === activeView.value) return;
  const query = { ...route.query, view: value };
  if (value === "submissions") delete query.view;
  router.replace({ query });
}

async function loadProblemReferences() {
  referenceRequests.value += 1;
  problemReferenceError.value = false;
  try {
    const response = await getPublicProblemList({ page: 1, pageSize: 100 });
    problemMap.value = Object.fromEntries((response.data?.rows || []).map(item => [String(item.id), item]));
  } catch {
    problemReferenceError.value = true;
  } finally {
    referenceRequests.value -= 1;
  }
}

async function loadTeamReferences(teamIds, force = false) {
  const normalizedIds = [...new Set((teamIds || []).filter(Boolean).map(String))];
  requestedTeamIds.value = [...new Set([...requestedTeamIds.value, ...normalizedIds])];
  const targetIds = force ? normalizedIds : normalizedIds.filter(id => !teamMap.value[id]);
  if (!targetIds.length) return;
  referenceRequests.value += 1;
  teamReferenceError.value = false;
  try {
    const response = await getAdminTeamReferences(targetIds);
    const additions = Object.fromEntries((response.data || []).map(item => [String(item.id), item]));
    teamMap.value = { ...teamMap.value, ...additions };
  } catch {
    teamReferenceError.value = true;
  } finally {
    referenceRequests.value -= 1;
  }
}

async function loadReferences() {
  await Promise.all([
    loadProblemReferences(),
    loadTeamReferences(requestedTeamIds.value, true),
  ]);
}

watch(() => route.query.view, value => {
  if (value && !views[value]) router.replace({ query: { ...route.query, view: undefined } });
});

onMounted(loadProblemReferences);
</script>

<style scoped>
.operations-hub-page { display: flex; min-width: 0; flex-direction: column; gap: var(--lm-admin-space-3); }
.operations-nav-panel, .operations-view { min-width: 0; background: var(--lm-admin-surface); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-panel); }
.operations-nav-panel { padding: 0 var(--lm-admin-space-2); }
.operations-view { overflow: hidden; }
.reference-warning { display: flex; align-items: center; gap: 8px; padding: 8px 12px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.reference-warning span { flex: 1; }
</style>
