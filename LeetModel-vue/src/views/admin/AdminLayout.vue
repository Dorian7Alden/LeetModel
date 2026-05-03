<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
      <div class="logo" :class="{ collapsed: isCollapse }">
        <el-icon :size="26" class="logo-icon"><DataAnalysis /></el-icon>
        <span v-show="!isCollapse" class="logo-text">LeetModel</span>
      </div>
      <el-menu
        :default-active="$route.path"
        class="el-menu-vertical"
        :collapse="isCollapse"
        background-color="#ffffff"
        text-color="var(--lm-text-secondary)"
        active-text-color="var(--lm-primary)"
        router
      >
        <template v-for="route in filteredRoutes" :key="route.path">
          <el-sub-menu v-if="route.children && route.children.length > 1" :index="`/admin/${route.path}`">
            <template #title>
              <el-icon><component :is="route.meta.icon" /></el-icon>
              <span>{{ route.meta.title }}</span>
            </template>
            <el-menu-item v-for="child in route.children" :key="child.path" :index="`/admin/${route.path}/${child.path}`">
              {{ child.meta.title }}
            </el-menu-item>
          </el-sub-menu>

          <el-menu-item v-else-if="route.children && route.children.length === 1" :index="`/admin/${route.path === '' ? '' : route.path}/${route.children[0].path}`.replace('//', '/')">
            <el-icon><component :is="route.children[0].meta?.icon || route.meta?.icon" /></el-icon>
            <template #title>{{ route.children[0].meta.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- Main area -->
    <el-container>
      <!-- Header -->
      <el-header class="header">
        <div class="header-left">
          <button
            class="collapse-btn"
            :class="{ collapsed: isCollapse }"
            @click="toggleCollapse"
            :title="isCollapse ? '展开侧栏' : '收起侧栏'"
          >
            <el-icon :size="18">
              <Fold v-if="!isCollapse" />
              <Expand v-else />
            </el-icon>
          </button>
          <el-breadcrumb separator="" class="header-breadcrumb">
            <template v-if="generateBreadcrumbs().length > 0">
              <el-icon class="breadcrumb-separator" :size="14"><ArrowRight /></el-icon>
            </template>
            <template v-for="(item, index) in generateBreadcrumbs()" :key="index">
              <el-breadcrumb-item :to="item.to">
                {{ item.title }}
              </el-breadcrumb-item>
              <el-icon
                v-if="index < generateBreadcrumbs().length - 1"
                class="breadcrumb-separator"
                :size="14"
              ><ArrowRight /></el-icon>
            </template>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <router-link to="/" class="back-home-link">
            <el-icon :size="16"><HomeFilled /></el-icon>
            <span>返回首页</span>
          </router-link>

          <el-divider direction="vertical" class="header-divider" />

          <el-dropdown trigger="click">
            <span class="user-dropdown">
              <el-avatar :size="32" class="user-avatar">
                <el-icon :size="18"><UserFilled /></el-icon>
              </el-avatar>
              <span class="user-name">{{ userStore.username || '管理员' }}</span>
              <el-icon :size="14" class="arrow-icon"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="dropdown-greeting">
                  <p class="greeting-name">{{ greeting }}</p>
                  <p class="greeting-role">{{ roleLabel }}</p>
                </div>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon :size="14"><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Content -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/store/user";
import { useAuth } from "@/composables/useAuth";

const isCollapse = ref(false);
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { handleLogout } = useAuth();

const filteredRoutes = computed(() => {
  const adminRoute = router.options.routes.find((r) => r.path === "/admin");
  if (adminRoute && adminRoute.children) {
    return adminRoute.children.filter((r) => !r.hidden);
  }
  return [];
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return `早上好，${userStore.username || '管理员'}`;
  if (hour < 18) return `下午好，${userStore.username || '管理员'}`;
  return `晚上好，${userStore.username || '管理员'}`;
});

const roleLabel = computed(() => {
  const map = { SUPER_ADMIN: "系统管理员", ADMIN: "普通管理员", MEMBER: "成员" };
  return map[userStore.role] || "用户";
});

function generateBreadcrumbs() {
  const crumbs = [];
  const matched = route.matched.filter((r) => r.meta?.title);

  // Always start with Dashboard as the root admin breadcrumb
  crumbs.push({ title: "首页", to: "/admin/dashboard" });

  for (let i = 0; i < matched.length; i++) {
    const r = matched[i];
    // Skip the generic /admin layout route
    if (r.path === "/admin" || r.name === "admin-layout") continue;
    // Skip if already the dashboard
    if (r.path === "/admin/dashboard") continue;
    crumbs.push({
      title: r.meta.title,
      to: r.path,
    });
  }

  // If only one item (just dashboard on the dashboard page), return empty
  if (crumbs.length === 1 && route.path === "/admin/dashboard") {
    return [];
  }

  return crumbs;
}

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
};
</script>

<style scoped>
@import './style.css';
</style>
