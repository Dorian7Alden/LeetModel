<template>
  <div class="domain-page access-control-page">
    <section class="domain-hero domain-hero-blue">
      <div class="domain-hero-copy">
        <span class="domain-eyebrow">IDENTITY & ACCESS</span>
        <h2>让人员与权限在一个工作面闭环</h2>
        <p>以用户为主线处理账号状态和角色分配；低频的角色、权限与授权策略作为辅助任务随时展开。</p>
      </div>
      <div class="domain-actions">
        <el-button class="hero-button" @click="openTool('roles')"><el-icon><UserFilled /></el-icon>角色设置</el-button>
        <el-button class="hero-button" @click="openTool('permissions')"><el-icon><Key /></el-icon>权限目录</el-button>
        <el-button type="primary" class="hero-primary" @click="openTool('authorization')"><el-icon><Connection /></el-icon>角色授权</el-button>
      </div>
    </section>

    <div class="domain-metrics" v-loading="summaryLoading">
      <div v-for="item in summaryCards" :key="item.label" class="domain-metric">
        <span class="domain-metric-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        <span><small>{{ item.label }}</small><strong>{{ item.value }}</strong></span>
      </div>
      <div class="domain-guidance">
        <el-icon><InfoFilled /></el-icon>
        <span><strong>最小权限原则</strong><small>先定义权限，再授权给角色，最后把角色分配给用户。</small></span>
      </div>
    </div>

    <section class="domain-section">
      <div class="domain-section-heading">
        <div><span class="section-kicker">主要工作面</span><h3>用户与账号</h3></div>
        <span class="section-help">在列表中直接完成状态切换与角色分配</span>
      </div>
      <UserListPage />
    </section>

    <el-drawer v-model="toolVisible" :title="activeToolTitle" size="min(960px, 86vw)" destroy-on-close class="admin-tool-drawer">
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
import UserListPage from "./UserListPage.vue";
import RoleListPage from "./RoleListPage.vue";
import PermissionListPage from "./PermissionListPage.vue";
import AuthIndexPage from "./AuthIndexPage.vue";
import { adminPageUsers } from "@/api/user";
import { getRoleList } from "@/api/role";
import { getPermissionList } from "@/api/permission";

const route = useRoute();
const router = useRouter();
const summaryLoading = ref(false);
const summary = ref({ users: "—", roles: "—", permissions: "—" });
const activeTool = ref("");
const toolVisible = computed({
  get: () => !!activeTool.value,
  set: (value) => { if (!value) closeTool(); },
});
const tools = {
  roles: { title: "角色设置", description: "维护角色定义与职责说明。", icon: "UserFilled", component: RoleListPage },
  permissions: { title: "权限目录", description: "维护系统内可授权的最小操作单元。", icon: "Key", component: PermissionListPage },
  authorization: { title: "角色授权", description: "把权限按业务域组合到角色。", icon: "Connection", component: AuthIndexPage },
};
const activeToolMeta = computed(() => tools[activeTool.value] || { title: "", description: "", icon: "Setting" });
const activeToolTitle = computed(() => activeToolMeta.value.title);
const activeToolComponent = computed(() => activeToolMeta.value.component);
const summaryCards = computed(() => [
  { label: "用户总数", value: summary.value.users, icon: "User", tone: "blue" },
  { label: "角色定义", value: summary.value.roles, icon: "UserFilled", tone: "violet" },
  { label: "权限条目", value: summary.value.permissions, icon: "Key", tone: "amber" },
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
function syncLegacyView(value) {
  if (tools[value]) activeTool.value = value;
}
async function loadSummary() {
  summaryLoading.value = true;
  const [userResult, roleResult, permissionResult] = await Promise.allSettled([
    adminPageUsers({ page: 1, pageSize: 1 }),
    getRoleList(),
    getPermissionList(),
  ]);
  summary.value = {
    users: userResult.status === "fulfilled" ? (userResult.value.data?.total ?? 0) : "—",
    roles: roleResult.status === "fulfilled" ? (roleResult.value.data?.length ?? 0) : "—",
    permissions: permissionResult.status === "fulfilled" ? (permissionResult.value.data?.length ?? 0) : "—",
  };
  summaryLoading.value = false;
}

watch(() => route.query.view, syncLegacyView, { immediate: true });
onMounted(loadSummary);
</script>

<style scoped>
@import '../style.css';
</style>
