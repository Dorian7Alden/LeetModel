<template>
  <el-container class="admin-shell">
    <button
      v-if="isMobile && sidebarOpen"
      class="nav-scrim"
      type="button"
      aria-label="关闭管理端导航"
      @click="closeMobileNavigation"
    ></button>

    <el-aside
      :width="sidebarWidth"
      class="admin-sidebar"
      :class="{ collapsed: sidebarCollapsed && !isMobile, open: sidebarOpen }"
    >
      <router-link to="/admin/dashboard" class="admin-brand" @click="closeMobileNavigation">
        <span class="brand-mark"><img src="@/assets/images/logo.png" alt="" /></span>
        <span v-if="!sidebarCollapsed || isMobile" class="brand-name">LeetModel</span>
      </router-link>

      <nav class="admin-navigation" aria-label="管理端一级导航">
        <el-menu
          :default-active="$route.path"
          class="admin-menu"
          :collapse="sidebarCollapsed && !isMobile"
          :collapse-transition="false"
          router
        >
          <el-menu-item
            v-for="item in navigation"
            :key="item.path"
            :index="`/admin/${item.path}`"
            @click="closeMobileNavigation"
          >
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>{{ item.meta.navTitle || item.meta.title }}</template>
          </el-menu-item>
        </el-menu>
      </nav>

      <button
        v-if="!isMobile"
        class="sidebar-toggle"
        type="button"
        :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
        :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
        @click="toggleSidebar"
      >
        <el-icon><Expand v-if="sidebarCollapsed" /><Fold v-else /></el-icon>
        <span v-if="!sidebarCollapsed">收起导航</span>
      </button>
    </el-aside>

    <el-container class="admin-workspace">
      <el-header class="admin-topbar">
        <div class="topbar-leading">
          <button
            v-if="isMobile"
            class="icon-button"
            type="button"
            aria-label="打开管理端导航"
            :aria-expanded="sidebarOpen"
            @click="sidebarOpen = true"
          >
            <el-icon><Menu /></el-icon>
          </button>
          <h1>{{ currentTitle }}</h1>
        </div>

        <div class="topbar-actions">
          <span v-if="!isOnline" class="network-state" role="status">
            <el-icon><WarningFilled /></el-icon>
            网络已断开
          </span>
          <router-link to="/home" class="site-link" aria-label="返回站点">
            <el-icon><HomeFilled /></el-icon>
            <span>返回站点</span>
          </router-link>
          <el-dropdown trigger="click">
            <button class="account-button" type="button">
              <el-avatar :size="28" class="account-avatar" :src="userStore.avatarUrl || undefined">
                {{ (userStore.username || "管").charAt(0) }}
              </el-avatar>
              <span class="account-name">{{ userStore.username || "管理员" }}</span>
              <el-icon class="account-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="account-summary">
                  <strong>{{ userStore.username || "管理员" }}</strong>
                  <span>{{ roleLabel }}</span>
                </div>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <div v-if="!isOnline" class="offline-banner" role="alert">
        当前处于离线状态，数据可能不是最新。网络恢复后请重新加载。
      </div>

      <el-main class="admin-main">
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="admin-view" mode="out-in">
            <keep-alive>
              <component :is="Component" :key="viewRoute.name" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { useAuth } from "@/composables/useAuth";

const SIDEBAR_STORAGE_KEY = "lm-admin-sidebar-collapsed";
const MOBILE_BREAKPOINT = 768;

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { handleLogout } = useAuth();
const viewportWidth = ref(window.innerWidth);
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true");
const sidebarOpen = ref(false);
const isOnline = ref(navigator.onLine);

const isMobile = computed(() => viewportWidth.value < MOBILE_BREAKPOINT);
const sidebarWidth = computed(() => {
  if (isMobile.value) return "224px";
  return sidebarCollapsed.value ? "56px" : "224px";
});
const navigation = computed(() => {
  const adminRoute = router.options.routes.find((item) => item.path === "/admin");
  return (adminRoute?.children || []).filter((item) => !item.meta?.hidden && item.component);
});
const currentTitle = computed(() => route.meta?.navTitle || route.meta?.title || "管理控制台");
const roleLabel = computed(() => userStore.roleLabel);

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarCollapsed.value));
}

function closeMobileNavigation() {
  if (isMobile.value) sidebarOpen.value = false;
}

function syncViewport() {
  viewportWidth.value = window.innerWidth;
  if (!isMobile.value) sidebarOpen.value = false;
}

function markOnline() {
  isOnline.value = true;
}

function markOffline() {
  isOnline.value = false;
}

onMounted(() => {
  syncViewport();
  window.addEventListener("resize", syncViewport);
  window.addEventListener("online", markOnline);
  window.addEventListener("offline", markOffline);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", syncViewport);
  window.removeEventListener("online", markOnline);
  window.removeEventListener("offline", markOffline);
});
</script>

<style scoped>
@import "./style.css";
</style>
