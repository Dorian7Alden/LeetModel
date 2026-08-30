<template>
  <div class="domain-page content-hub-page">
    <section class="domain-hero domain-hero-emerald">
      <div class="domain-hero-copy">
        <span class="domain-eyebrow">CONTENT WORKSPACE</span>
        <h2>从题面到分类语境，一处完成</h2>
        <p>题目是核心内容；标签负责描述建模领域和方法，赛事提供来源上下文，不再拆成彼此孤立的管理页。</p>
      </div>
      <div class="domain-actions">
        <el-button class="hero-button" @click="openTool('contests')"><el-icon><Trophy /></el-icon>赛事数据</el-button>
        <el-button type="primary" class="hero-primary emerald" @click="openTool('tags')"><el-icon><CollectionTag /></el-icon>维护标签</el-button>
      </div>
    </section>

    <div class="domain-metrics" v-loading="summaryLoading">
      <div v-for="item in summaryCards" :key="item.label" class="domain-metric">
        <span class="domain-metric-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        <span><small>{{ item.label }}</small><strong>{{ item.value }}</strong></span>
      </div>
      <div class="domain-guidance">
        <el-icon><View /></el-icon>
        <span><strong>所见即所得预览</strong><small>在题目行点击“预览”，可直接检查发布后的 Markdown 呈现。</small></span>
      </div>
    </div>

    <section class="domain-section">
      <div class="domain-section-heading">
        <div><span class="section-kicker">主要工作面</span><h3>题目内容</h3></div>
        <span class="section-help">创建、编辑、预览并关联标签</span>
      </div>
      <ProblemListPage />
    </section>

    <el-drawer v-model="toolVisible" :title="activeToolTitle" size="min(900px, 86vw)" destroy-on-close class="admin-tool-drawer">
      <div class="drawer-intro">
        <el-icon :size="20"><component :is="activeToolMeta.icon" /></el-icon>
        <span><strong>{{ activeToolMeta.title }}</strong><small>{{ activeToolMeta.description }}</small></span>
      </div>
      <component :is="activeToolComponent" v-if="activeToolComponent" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import ProblemListPage from "./ProblemListPage.vue";
import TagListPage from "./TagListPage.vue";
import ContestListPage from "./ContestListPage.vue";
import { getAdminContentContests, getAdminContentProblems, getAdminContentTags } from "@/api/problem";

const route = useRoute();
const router = useRouter();
const summaryLoading = ref(false);
const summary = ref({ problems: "—", tags: "—", contests: "—" });
const activeTool = ref("");
const tools = {
  tags: { title: "标签体系", description: "维护题目使用的领域、题型与算法标签。", icon: "CollectionTag", component: TagListPage },
  contests: { title: "赛事基础数据", description: "查看题目可归属的赛事来源。", icon: "Trophy", component: ContestListPage },
};
const toolVisible = computed({ get: () => !!activeTool.value, set: (value) => { if (!value) closeTool(); } });
const activeToolMeta = computed(() => tools[activeTool.value] || { title: "", description: "", icon: "Setting" });
const activeToolTitle = computed(() => activeToolMeta.value.title);
const activeToolComponent = computed(() => activeToolMeta.value.component);
const summaryCards = computed(() => [
  { label: "题目总数", value: summary.value.problems, icon: "Document", tone: "emerald" },
  { label: "标签数量", value: summary.value.tags, icon: "CollectionTag", tone: "violet" },
  { label: "赛事来源", value: summary.value.contests, icon: "Trophy", tone: "amber" },
]);

function openTool(key) {
  if (!tools[key]) return;
  activeTool.value = key;
  router.replace({ query: { ...route.query, view: key } });
}
function closeTool() {
  activeTool.value = "";
  const query = { ...route.query };
  delete query.view;
  router.replace({ query });
}
function syncLegacyView(value) { if (tools[value]) activeTool.value = value; }
async function loadSummary() {
  summaryLoading.value = true;
  const [problemResult, tagResult, contestResult] = await Promise.allSettled([
    getAdminContentProblems({ page: 1, pageSize: 1 }),
    getAdminContentTags(),
    getAdminContentContests(),
  ]);
  summary.value = {
    problems: problemResult.status === "fulfilled" ? (problemResult.value.data?.total ?? 0) : "—",
    tags: tagResult.status === "fulfilled" ? (tagResult.value.data?.length ?? 0) : "—",
    contests: contestResult.status === "fulfilled" ? (contestResult.value.data?.length ?? 0) : "—",
  };
  summaryLoading.value = false;
}

watch(() => route.query.view, syncLegacyView, { immediate: true });
onMounted(loadSummary);
</script>

<style scoped>
@import '../style.css';
</style>
