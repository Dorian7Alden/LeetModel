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
import { ElMessage } from "element-plus";

const isCollapse = ref(false);
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

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

const handleLogout = () => {
  userStore.logout();
  ElMessage.success("已退出登录");
  router.push("/login");
};
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

/* ===== Aside ===== */
.aside {
  background-color: var(--lm-surface);
  border-right: 1px solid var(--lm-border);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background-color: var(--lm-surface);
  border-bottom: 1px solid var(--lm-border);
  padding: 0 16px;
}

.logo.collapsed {
  padding: 0;
}

.logo-icon {
  color: var(--lm-primary);
  flex-shrink: 0;
}

.logo-text {
  font-size: 19px;
  font-weight: 800;
  color: var(--lm-text-primary);
  letter-spacing: -0.3px;
  white-space: nowrap;
}

.el-menu-vertical {
  border-right: none;
  padding: 8px 0;
}

.el-menu-vertical :deep(.el-menu-item),
.el-menu-vertical :deep(.el-sub-menu__title) {
  border-radius: 8px;
  margin: 2px 8px;
  height: 44px;
  line-height: 44px;
  transition: all var(--lm-transition);
}

.el-menu-vertical :deep(.el-menu-item.is-active) {
  background: var(--lm-primary-bg) !important;
  font-weight: 600;
}

.el-menu-vertical :deep(.el-menu-item:hover),
.el-menu-vertical :deep(.el-sub-menu__title:hover) {
  background: var(--lm-bg-secondary) !important;
}

.el-menu-vertical.el-menu--collapse :deep(.el-menu-item),
.el-menu-vertical.el-menu--collapse :deep(.el-sub-menu__title) {
  margin: 2px 6px;
  justify-content: center;
}

/* ===== Header ===== */
.header {
  height: 64px;
  background-color: var(--lm-surface);
  border-bottom: 1px solid var(--lm-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ---- Collapse Button ---- */
.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  background: var(--lm-surface);
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
  flex-shrink: 0;
  padding: 0;
}

.collapse-btn:hover {
  border-color: var(--lm-primary);
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

.collapse-btn.collapsed {
  background: var(--lm-bg-secondary);
}

/* ---- Breadcrumb ---- */
.header-breadcrumb {
  display: flex;
  align-items: center;
  gap: 0;
}

.header-breadcrumb :deep(.el-breadcrumb__inner) {
  font-size: 13px;
  color: var(--lm-text-secondary);
  font-weight: 400;
  transition: color var(--lm-transition);
}

.header-breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--lm-primary);
}

.header-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--lm-text-primary);
  font-weight: 600;
}

.breadcrumb-separator {
  color: var(--lm-text-muted);
  margin: 0 6px;
  flex-shrink: 0;
}

/* ===== Header Right ===== */
.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.back-home-link {
  display: flex;
  align-items: center;
  gap: 5px;
  text-decoration: none;
  color: var(--lm-text-secondary);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: var(--lm-radius-sm);
  transition: all var(--lm-transition);
}

.back-home-link:hover {
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

.header-divider {
  height: 20px;
  margin: 0 8px;
}

/* ---- User Dropdown ---- */
.user-dropdown {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: var(--lm-radius);
  transition: background var(--lm-transition);
}

.user-dropdown:hover {
  background: var(--lm-bg-secondary);
}

.user-avatar {
  background: var(--lm-primary-bg);
  color: var(--lm-primary);
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--lm-text-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow-icon {
  color: var(--lm-text-muted);
  transition: transform var(--lm-transition);
}

.user-dropdown:hover .arrow-icon {
  color: var(--lm-text-secondary);
}

/* ---- Dropdown Greeting ---- */
.dropdown-greeting {
  padding: 12px 16px 8px;
  text-align: center;
  min-width: 160px;
}

.greeting-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 2px;
}

.greeting-role {
  font-size: 12px;
  color: var(--lm-text-muted);
  margin: 0;
}

/* ===== Main Content ===== */
.main-content {
  background-color: var(--lm-bg);
  padding: 24px;
  overflow-y: auto;
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .header {
    padding: 0 16px;
  }
  .user-name {
    display: none;
  }
  .back-home-link span {
    display: none;
  }
  .main-content {
    padding: 16px;
  }
}
</style>
