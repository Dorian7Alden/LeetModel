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
      :class="{ collapsed: sidebarCollapsed && !isMobile, open: sidebarOpen, resizing: isResizing }"
    >
      <div class="admin-brand-header">
        <router-link
          v-if="!sidebarCollapsed || isMobile"
          to="/admin/dashboard"
          class="admin-brand"
          @click="closeMobileNavigation"
        >
          <span class="brand-mark"><img src="@/assets/images/logo.png" alt="" /></span>
          <span class="brand-name">LeetModel</span>
        </router-link>

        <button
          v-if="!sidebarCollapsed && !isMobile"
          class="brand-collapse-btn"
          type="button"
          aria-label="收起导航"
          title="收起导航"
          @click="toggleSidebar"
        >
          <el-icon><Fold /></el-icon>
        </button>

        <!-- 收起状态下只展示居中的 Logo 图标，hover 时切换为展开图标，点击展开 -->
        <el-tooltip
          v-if="sidebarCollapsed && !isMobile"
          content="展开导航"
          placement="right"
          effect="light"
          popper-class="admin-menu-tooltip"
          :show-after="300"
          :hide-after="50"
          :enterable="false"
        >
          <button
            class="brand-logo-btn"
            type="button"
            aria-label="展开导航"
            @click="toggleSidebar"
          >
            <span class="brand-mark">
              <img src="@/assets/images/logo.png" alt="LeetModel" class="brand-logo-img" />
              <el-icon class="brand-expand-icon"><Expand /></el-icon>
            </span>
          </button>
        </el-tooltip>
      </div>

      <nav class="admin-navigation" aria-label="管理端侧边栏导航">
        <el-menu
          :default-active="activeMenu"
          :default-openeds="defaultOpeneds"
          class="admin-menu"
          :collapse="sidebarCollapsed && !isMobile"
          :collapse-transition="false"
          router
        >
          <!-- 收起模式：平铺所有二级具体功能项，去除一级管理分组 -->
          <template v-if="sidebarCollapsed && !isMobile">
            <el-tooltip
              v-for="item in flatMenuItems"
              :key="item.path"
              :content="item.title"
              placement="right"
              effect="light"
              popper-class="admin-menu-tooltip"
              :show-after="300"
              :hide-after="50"
              :enterable="false"
            >
              <el-menu-item
                :index="item.path"
                @click="closeMobileNavigation"
              >
                <el-icon><component :is="item.icon" /></el-icon>
              </el-menu-item>
            </el-tooltip>
          </template>

          <!-- 展开模式：显示清晰的一二级分组层级，默认全部展开 -->
          <template v-else>
            <template v-for="menu in menuGroups" :key="menu.type === 'item' ? menu.path : menu.key">
              <!-- 一级直达菜单项 -->
              <el-menu-item
                v-if="menu.type === 'item'"
                :index="menu.path"
                @click="closeMobileNavigation"
              >
                <el-icon><component :is="menu.icon" /></el-icon>
                <template #title>{{ menu.title }}</template>
              </el-menu-item>

              <!-- 二级分组菜单 -->
              <el-sub-menu
                v-else
                :index="menu.key"
              >
                <template #title>
                  <el-icon><component :is="menu.icon" /></el-icon>
                  <span>{{ menu.title }}</span>
                </template>
                <el-menu-item
                  v-for="child in menu.children"
                  :key="child.path"
                  :index="child.path"
                  @click="closeMobileNavigation"
                >
                  <el-icon><component :is="child.icon" /></el-icon>
                <template #title>{{ child.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </nav>

      <!-- 侧栏宽度调节条（支持拖拽，双击恢复默认） -->
      <div
        v-if="!sidebarCollapsed && !isMobile"
        class="sidebar-resizer"
        :class="{ resizing: isResizing }"
        title="按住拖拽调节侧栏宽度，双击恢复默认"
        @mousedown="startResize"
        @dblclick="resetWidth"
      ></div>
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
const SIDEBAR_WIDTH_KEY = "lm-admin-sidebar-custom-width";
const MOBILE_BREAKPOINT = 768;
const DEFAULT_EXPANDED_WIDTH = 196;
const MIN_SIDEBAR_WIDTH = 160;
const MAX_SIDEBAR_WIDTH = 320;

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { handleLogout } = useAuth();
const viewportWidth = ref(window.innerWidth);
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true");
const sidebarOpen = ref(false);
const isOnline = ref(navigator.onLine);

const savedWidth = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY));
const customExpandedWidth = ref(
  savedWidth >= MIN_SIDEBAR_WIDTH && savedWidth <= MAX_SIDEBAR_WIDTH
    ? savedWidth
    : DEFAULT_EXPANDED_WIDTH
);
const isResizing = ref(false);

const isMobile = computed(() => viewportWidth.value < MOBILE_BREAKPOINT);
const sidebarWidth = computed(() => {
  if (isMobile.value) return `${customExpandedWidth.value}px`;
  return sidebarCollapsed.value ? "56px" : `${customExpandedWidth.value}px`;
});

function startResize(e) {
  if (sidebarCollapsed.value || isMobile.value) return;
  e.preventDefault();
  isResizing.value = true;
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";

  const onMouseMove = (moveEvent) => {
    if (!isResizing.value) return;
    const newWidth = Math.min(
      Math.max(moveEvent.clientX, MIN_SIDEBAR_WIDTH),
      MAX_SIDEBAR_WIDTH
    );
    customExpandedWidth.value = newWidth;
  };

  const onMouseUp = () => {
    if (!isResizing.value) return;
    isResizing.value = false;
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
    localStorage.setItem(SIDEBAR_WIDTH_KEY, String(customExpandedWidth.value));
    window.removeEventListener("mousemove", onMouseMove);
    window.removeEventListener("mouseup", onMouseUp);
  };

  window.addEventListener("mousemove", onMouseMove);
  window.addEventListener("mouseup", onMouseUp);
}

function resetWidth() {
  if (sidebarCollapsed.value || isMobile.value) return;
  customExpandedWidth.value = DEFAULT_EXPANDED_WIDTH;
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(DEFAULT_EXPANDED_WIDTH));
}

const menuGroups = [
  {
    type: "item",
    path: "/admin/dashboard",
    title: "运行概览",
    icon: "DataBoard",
  },
  {
    type: "group",
    key: "biz",
    title: "业务管理",
    icon: "Briefcase",
    children: [
      { path: "/admin/problems", title: "题目管理", icon: "Document" },
      { path: "/admin/contests", title: "赛事管理", icon: "Trophy" },
      { path: "/admin/tags", title: "标签管理", icon: "CollectionTag" },
      { path: "/admin/teams", title: "队伍管理", icon: "UserFilled" },
      { path: "/admin/submissions", title: "提交管理", icon: "Files" },
      { path: "/admin/rankings", title: "榜单管理", icon: "Histogram" },
    ],
  },
  {
    type: "group",
    key: "ai",
    title: "AI管理",
    icon: "Cpu",
    children: [
      { path: "/admin/ai-reviews", title: "AI评审", icon: "Checked" },
      { path: "/admin/ai-suggestions", title: "AI建议", icon: "ChatDotRound" },
      { path: "/admin/ai-assistant", title: "AI客服", icon: "Service" },
      { path: "/admin/ai-evaluations", title: "AI评测", icon: "Aim" },
      { path: "/admin/ai-calls", title: "AI调用", icon: "Connection" },
    ],
  },
  {
    type: "group",
    key: "asset",
    title: "资产管理",
    icon: "Box",
    children: [
      { path: "/admin/knowledge", title: "知识库管理", icon: "Notebook" },
      { path: "/admin/storage", title: "文件管理", icon: "FolderOpened" },
    ],
  },
  {
    type: "group",
    key: "ops",
    title: "运维管理",
    icon: "Operation",
    children: [
      { path: "/admin/audit", title: "操作审计", icon: "DocumentChecked" },
      { path: "/admin/messaging", title: "消息队列", icon: "MessageBox" },
    ],
  },
  {
    type: "item",
    path: "/admin/access",
    title: "访问控制",
    icon: "Lock",
  },
];

const flatMenuItems = computed(() => {
  const items = [];
  menuGroups.forEach((menu) => {
    if (menu.type === "item") {
      items.push(menu);
    } else if (menu.type === "group" && menu.children) {
      items.push(...menu.children);
    }
  });
  return items;
});

const defaultOpeneds = ["biz", "ai", "asset", "ops"];
const activeMenu = computed(() => route.path);
const currentTitle = computed(() => route.meta?.title || "管理控制台");
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
