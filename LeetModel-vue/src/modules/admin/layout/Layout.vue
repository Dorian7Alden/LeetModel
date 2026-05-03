<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '200px'" class="aside">
      <div class="logo" :class="{ collapsed: isCollapse }">
        <el-icon :size="isCollapse ? 22 : 24" class="logo-icon"><HomeFilled /></el-icon>
        <span v-if="!isCollapse" class="logo-text">LeetModel</span>
      </div>
      <el-menu
        :default-active="$route.path"
        class="el-menu-vertical"
        :collapse="isCollapse"
        background-color="#ffffff"
        text-color="#444"
        active-text-color="#409EFF"
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

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="toggle-btn" @click="toggleCollapse"><Fold v-if="!isCollapse"/><Expand v-else/></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.path !== '/admin/dashboard'">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <router-link to="/" class="back-home-link">
            <el-icon><HomeFilled /></el-icon>
            返回首页
          </router-link>
          <el-dropdown>
            <span class="user-dropdown">
              {{ userStore.username || '管理员' }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

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
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useUserStore } from '@/store/user';
import { ElMessage } from 'element-plus';

const isCollapse = ref(false);
const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const filteredRoutes = computed(() => {
  const adminRoute = router.options.routes.find(r => r.path === '/admin');
  if (adminRoute && adminRoute.children) {
    return adminRoute.children.filter((r) => !r.hidden);
  }
  return [];
});

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value;
};

const handleLogout = () => {
  userStore.logout();
  ElMessage.success('已退出登录');
  router.push('/login');
};
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.aside {
  background-color: #ffffff;
  border-right: 1px solid #eee;
  transition: width 0.3s;
  overflow: hidden;
}
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #303133;
  font-size: 18px;
  font-weight: 700;
  background-color: #ffffff;
  border-bottom: 1px solid #eee;
  letter-spacing: 0.5px;
}
.logo.collapsed {
  justify-content: center;
}
.logo-icon {
  color: #409eff;
  flex-shrink: 0;
}
.logo-text {
  white-space: nowrap;
}
.el-menu-vertical {
  border-right: none;
}
.header {
  height: 64px;
  background-color: #fff;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
}
.header-right {
  display: flex;
  align-items: center;
}
.toggle-btn {
  font-size: 20px;
  cursor: pointer;
  margin-right: 20px;
  color: #444;
}
.user-dropdown {
  cursor: pointer;
  display: flex;
  align-items: center;
  color: #444;
  font-weight: 500;
}
.back-home-link {
  display: flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: #444;
  font-size: 14px;
  margin-right: 16px;
  transition: color 0.2s;
}
.back-home-link:hover {
  color: #409eff;
}
.main-content {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
