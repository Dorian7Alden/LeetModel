<template>
  <div class="content-hub-page">
    <div class="content-nav-panel">
      <AdminSubnav
        :model-value="activeView"
        :items="navigationItems"
        aria-label="内容中心工作面"
        @update:model-value="selectView"
      />
    </div>

    <section class="content-view" :aria-label="activeViewLabel">
      <component :is="activeComponent" :key="activeView" @changed="loadCounts" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AdminSubnav from "../components/AdminSubnav.vue";
import ProblemListPage from "./ProblemListPage.vue";
import ContestListPage from "./ContestListPage.vue";
import TagListPage from "./TagListPage.vue";
import StorageConsolePage from "./StorageConsolePage.vue";
import KnowledgeManagerPage from "./KnowledgeManagerPage.vue";
import { getAdminContentContests, getAdminContentProblems, getAdminContentTags } from "@/api/problem";

const route = useRoute();
const router = useRouter();
const counts = ref({ problems: undefined, contests: undefined, tags: undefined });
const views = {
  problems: { label: "题目", icon: "Document", component: ProblemListPage },
  contests: { label: "赛事", icon: "Trophy", component: ContestListPage },
  tags: { label: "标签", icon: "CollectionTag", component: TagListPage },
  files: { label: "文件", icon: "FolderOpened", component: StorageConsolePage },
  knowledge: { label: "知识库", icon: "Notebook", component: KnowledgeManagerPage },
};

const activeView = computed(() => normalizeView(route.query.view));
const activeComponent = computed(() => views[activeView.value].component);
const activeViewLabel = computed(() => views[activeView.value].label);
const navigationItems = computed(() => Object.entries(views).map(([value, item]) => ({
  value,
  label: item.label,
  icon: item.icon,
  count: counts.value[value],
})));

function normalizeView(value) {
  if (value === "storage") return "files";
  return typeof value === "string" && views[value] ? value : "problems";
}

function selectView(value) {
  if (value === activeView.value) return;
  const query = { ...route.query, view: value };
  if (value === "problems") delete query.view;
  router.replace({ query });
}

async function loadCounts() {
  const results = await Promise.allSettled([
    getAdminContentProblems({ page: 1, pageSize: 1 }),
    getAdminContentContests(),
    getAdminContentTags(),
  ]);
  counts.value = {
    problems: results[0].status === "fulfilled" ? Number(results[0].value.data?.total || 0) : undefined,
    contests: results[1].status === "fulfilled" ? (results[1].value.data || []).length : undefined,
    tags: results[2].status === "fulfilled" ? (results[2].value.data || []).length : undefined,
  };
}

onMounted(() => {
  if (route.query.view === "storage") {
    router.replace({ query: { ...route.query, view: "files" } });
  }
  loadCounts();
});
</script>

<style scoped>
.content-hub-page {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--lm-admin-space-3);
}

.content-nav-panel,
.content-view {
  min-width: 0;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.content-nav-panel {
  padding: 0 var(--lm-admin-space-2);
}

.content-view {
  overflow: hidden;
}
</style>
