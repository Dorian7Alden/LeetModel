<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '72px' : '248px'" class="aside">
      <router-link to="/admin/dashboard" class="logo" :class="{ collapsed: isCollapse }">
        <span class="logo-mark"><img src="@/assets/images/logo.png" alt="" /></span>
        <span v-show="!isCollapse" class="logo-copy">
          <strong>LeetModel</strong>
          <small>ADMIN CONSOLE</small>
        </span>
      </router-link>

      <div v-show="!isCollapse" class="nav-label">工作空间</div>
      <div class="nav-scroll">
        <el-menu :default-active="$route.path" class="el-menu-vertical" :collapse="isCollapse" :collapse-transition="false" router>
          <el-menu-item v-for="item in navigation" :key="item.path" :index="`/admin/${item.path}`">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>
              <span class="nav-copy">
                <strong>{{ item.meta.navTitle || item.meta.title }}</strong>
                <small>{{ item.meta.description }}</small>
              </span>
            </template>
          </el-menu-item>
        </el-menu>
      </div>

      <div class="aside-footer" :class="{ collapsed: isCollapse }">
        <span class="status-dot"></span>
        <span v-show="!isCollapse" class="aside-status">
          <strong>管理端已连接</strong>
          <small>数据来自实时服务</small>
        </span>
      </div>
    </el-aside>

    <el-container class="workspace">
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="toggleCollapse" :title="isCollapse ? '展开侧栏' : '收起侧栏'">
            <el-icon :size="18"><Expand v-if="isCollapse" /><Fold v-else /></el-icon>
          </button>
          <div class="route-heading">
            <h1>{{ currentTitle }}</h1>
            <p>{{ currentDescription }}</p>
          </div>
        </div>
        <div class="header-right">
          <router-link to="/" class="back-home-link">
            <el-icon :size="16"><HomeFilled /></el-icon><span>返回站点</span>
          </router-link>
          <span class="header-divider"></span>
          <el-dropdown trigger="click">
            <span class="user-dropdown">
              <el-avatar :size="34" class="user-avatar" :src="userStore.avatarUrl || undefined">{{ (userStore.username || '管').charAt(0) }}</el-avatar>
              <span class="user-meta"><strong>{{ userStore.username || '管理员' }}</strong><small>{{ roleLabel }}</small></span>
              <el-icon :size="14" class="arrow-icon"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="dropdown-greeting"><p class="greeting-name">{{ greeting }}</p><p class="greeting-role">{{ roleLabel }}</p></div>
                <el-dropdown-item divided @click="handleLogout"><el-icon :size="14"><SwitchButton /></el-icon>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }"><keep-alive><component :is="Component" /></keep-alive></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { useAuth } from "@/composables/useAuth";

const isCollapse = ref(false);
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const { handleLogout } = useAuth();
const navigation = computed(() => {
  const adminRoute = router.options.routes.find((item) => item.path === "/admin");
  return (adminRoute?.children || []).filter((item) => !item.meta?.hidden && item.component);
});
const currentTitle = computed(() => route.meta?.title || "管理控制台");
const currentDescription = computed(() => route.meta?.description || "LeetModel 管理工作空间");
const roleLabel = computed(() => userStore.roleLabel);
const greeting = computed(() => {
  const hour = new Date().getHours();
  const prefix = hour < 12 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
  return `${prefix}，${userStore.username || "管理员"}`;
});
function syncViewport() { if (window.innerWidth < 1100) isCollapse.value = true; }
function toggleCollapse() { isCollapse.value = !isCollapse.value; }
onMounted(() => { syncViewport(); window.addEventListener("resize", syncViewport); });
onBeforeUnmount(() => window.removeEventListener("resize", syncViewport));
</script>

<style scoped>
@import './style.css';
</style>
