<template>
  <div class="access-control-page">
    <div class="access-nav-panel">
      <AdminSubnav
        :model-value="activeView"
        :items="navigationItems"
        aria-label="访问控制工作面"
        @update:model-value="selectView"
      />
    </div>

    <section class="access-view" :aria-label="activeViewLabel">
      <component
        :is="activeComponent"
        :key="activeView"
        @changed="loadCounts"
        @dirty-change="authorizationDirty = $event"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import AdminSubnav from "../components/AdminSubnav.vue";
import UserListPage from "./UserListPage.vue";
import RoleListPage from "./RoleListPage.vue";
import PermissionListPage from "./PermissionListPage.vue";
import AuthIndexPage from "./AuthIndexPage.vue";
import { adminPageUsers } from "@/api/user";
import { getRoleList } from "@/api/role";
import { getPermissionList } from "@/api/permission";

const route = useRoute();
const router = useRouter();
const authorizationDirty = ref(false);
const counts = ref({ users: undefined, roles: undefined, permissions: undefined });

const views = {
  users: { label: "用户", icon: "User", component: UserListPage },
  roles: { label: "角色", icon: "UserFilled", component: RoleListPage },
  permissions: { label: "权限", icon: "Key", component: PermissionListPage },
  authorization: { label: "角色授权", icon: "Connection", component: AuthIndexPage },
};

const activeView = computed(() => normalizeView(route.query.view));
const activeComponent = computed(() => views[activeView.value].component);
const activeViewLabel = computed(() => views[activeView.value].label);
const navigationItems = computed(() => Object.entries(views).map(([value, item]) => ({
  value,
  label: item.label,
  icon: item.icon,
  count: value === "authorization" ? undefined : counts.value[value],
})));

function normalizeView(value) {
  return typeof value === "string" && views[value] ? value : "users";
}

function selectView(value) {
  if (value === activeView.value) return;
  const query = { ...route.query, view: value };
  if (value === "users") delete query.view;
  router.replace({ query });
}

async function confirmDiscardAuthorization() {
  if (!authorizationDirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "当前角色的权限修改尚未保存，离开后将丢失。",
      "放弃未保存修改？",
      {
        type: "warning",
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
      },
    );
    authorizationDirty.value = false;
    return true;
  } catch {
    return false;
  }
}

async function loadCounts() {
  const results = await Promise.allSettled([
    adminPageUsers({ page: 1, pageSize: 1 }),
    getRoleList(),
    getPermissionList(),
  ]);
  counts.value = {
    users: results[0].status === "fulfilled" ? Number(results[0].value.data?.total || 0) : undefined,
    roles: results[1].status === "fulfilled" ? (results[1].value.data || []).length : undefined,
    permissions: results[2].status === "fulfilled" ? (results[2].value.data || []).length : undefined,
  };
}

onBeforeRouteUpdate(async (to) => {
  const targetView = normalizeView(to.query.view);
  if (targetView === activeView.value) return true;
  return confirmDiscardAuthorization();
});

onBeforeRouteLeave(confirmDiscardAuthorization);
onMounted(loadCounts);
</script>

<style scoped>
.access-control-page {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--lm-admin-space-3);
}

.access-nav-panel,
.access-view {
  min-width: 0;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.access-nav-panel {
  padding: 0 var(--lm-admin-space-2);
}

.access-view {
  overflow: hidden;
}
</style>
