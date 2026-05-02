<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="topbar">
      <div :class="['topbar-inner', isHome ? 'home-inner' : 'default-inner']">
        <!-- 左侧：系统名 + 导航 -->
        <div class="left-area">
          <!-- 导航栏 -->
          <nav class="navbar">
            <router-link to="/" class="nav-item home-icon">
              <img src="@/assets/images/logo-with-en.png" alt="home" />
            </router-link>

            <router-link to="/problem" class="nav-item">题库</router-link>
            <router-link to="/contest" class="nav-item">赛事</router-link>
            <router-link to="/community" class="nav-item">社区</router-link>
            <router-link to="/team" class="nav-item">组队</router-link>
            <router-link v-if="userStore.isAdmin" to="/admin/dashboard" class="nav-item admin-nav">后台管理</router-link>
          </nav>
        </div>

        <!-- 右侧 -->
        <!-- 右侧用户 -->
        <div class="right-area">
          <el-input
            v-model="keyword"
            placeholder="搜索题目 / 比赛 / 帖子"
            class="search-input"
            clearable
          />
          <!-- 右侧 登录/注册 按钮组 -->

          <!-- 未登录 -->
          <template v-if="!userStore.isLogin">
            <div class="nav-actions">
              <router-link to="/register" class="register-btn"
                >注册</router-link
              >
              <router-link to="/login" class="login-btn">登录</router-link>
            </div>
          </template>

          <!-- 已登录 -->
          <el-dropdown v-else trigger="click">
            <div class="user-box">
              <img class="avatar" src="../../assets/vue.svg" />
            </div>

            <template #dropdown>
              <el-dropdown-menu class="user-card">
                <!-- 顶部用户信息 -->
                <div class="user-header">
                  <img class="avatar-big" src="../../assets/vue.svg" />
                  <div class="info">
                    <div class="name">{{ userStore.username || '用户' }}</div>
                    <div class="desc">{{ userStore.email }}</div>
                  </div>
                </div>

                <!-- 功能区 -->
                <div class="menu-group">
                  <el-dropdown-item class="menu-item">
                    <el-icon class="menu-icon"><Collection /></el-icon>
                    我的题单
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item">
                    <el-icon class="menu-icon"><StarFilled /></el-icon>
                    我的收藏
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item">
                    <el-icon class="menu-icon"><Document /></el-icon>
                    我的笔记
                  </el-dropdown-item>
                </div>

                <!-- 分割 -->
                <div class="divider"></div>

                <!-- 跳转 -->
                <div class="menu-group">
                  <router-link to="/profile" class="menu-link">
                    <el-dropdown-item class="menu-item">
                      <el-icon class="menu-icon"><UserFilled /></el-icon>
                      个人中心
                    </el-dropdown-item>
                  </router-link>
                </div>

                <div class="divider"></div>

                <!-- 退出 -->
                <el-dropdown-item class="logout" @click="handleLogout">
                  <el-icon class="menu-icon"><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 页面内容 -->
    <main class="content">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-left">© 2026 数学建模在线评测系统</div>

        <div class="footer-links">
          <router-link to="/about">关于我们</router-link>

          <router-link to="/help">使用帮助</router-link>

          <router-link to="/contact">联系我们</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { useRoute } from "vue-router";
import { computed } from "vue";
import { logout } from "@/api/user";
import { ElMessage } from "element-plus";
import {
  Collection,
  StarFilled,
  Document,
  UserFilled,
  SwitchButton,
} from "@element-plus/icons-vue";
const route = useRoute();

const isHome = computed(() => route.path === "/");
const userStore = useUserStore();
const isLogin = ref(!!localStorage.getItem("token"));

const router = useRouter();

async function handleLogout() {
  try {
    await logout(); // ✅ 调后端接口

    ElMessage.success("退出成功");
  } catch (err) {
    console.log("退出接口异常", err);
  } finally {
    // ✅ 无论成功失败都执行
    localStorage.removeItem("token");

    // ✅ 清空 pinia 状态（关键！）
    userStore.$reset();

    // ✅ 跳转登录页
    router.push("/login");
  }
}
const keyword = ref("");
</script>

<style scoped>
/* 退出按钮 */
.logout {
  color: #f56c6c !important;
  text-align: center;
  font-weight: 500;
}

.logout:hover {
  background: #fff1f0 !important;
}

/* 去掉 router-link 默认样式 */
.menu-link {
  text-decoration: none;
  color: inherit;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

/* 整体卡片 */
.user-card {
  width: 260px;
  padding: 0;
  border-radius: 12px;
  overflow: hidden;

  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1) !important;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
}
/* 头像 */
.avatar-big {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 2px solid #fff;
}

/* 信息 */
.info .name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  justify-content: center; /* 水平 */
  align-items: center; /* 垂直 */
}

.info .desc {
  font-size: 12px;
  opacity: 0.85;
  display: flex;
  justify-content: center; /* 水平 */
  align-items: center; /* 垂直 */
}
/* 功能分组 */
.menu-group {
  padding: 6px 0;
}

/* 菜单项 */
.menu-item {
  padding: 10px 16px !important;
  font-size: 14px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-icon {
  font-size: 16px;
  color: #666;
}
/* hover 效果 */
.menu-item:hover {
  background: #f5f7fa !important;
  padding-left: 20px !important;
}

/* 分割线 */
.divider {
  height: 1px;
  background: #eee;
  margin: 6px 0;
}

.name {
  font-size: 16px;
  font-weight: 600;
}
.home-icon img {
  height: 20px;
}
.home-icon {
  display: flex;
  align-items: center;
}
.left-area {
  display: flex;
  align-items: center;
  gap: 40px;
}
/* 导航栏 */

.navbar {
  display: flex;
  align-items: center;
  gap: 22px;
}
/* 导航项 */
.nav-item {
  color: #444;
  text-decoration: none;
  font-size: 15px;

  padding: 6px 4px;

  position: relative;
  transition: all 0.2s;
}

.nav-item:hover {
  color: #409eff;
}
.nav-item::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: -6px;

  width: 0;
  height: 2px;

  background: #409eff;
  transition: 0.25s;
}

.nav-item:hover::after {
  width: 100%;
}
/* 后台管理入口样式 */
.admin-nav {
  color: #e6a23c !important;
}
.admin-nav:hover {
  color: #cf9236 !important;
}
.admin-nav::after {
  background: #e6a23c;
}
/* 当前高亮 */

.router-link-active {
  color: #409eff;
  font-weight: 600;
}
/* 容器 */

.nav-container {
  display: flex;
  gap: 30px;
  padding: 0 40px;
}

/* 外层 topbar 保持全宽（不要动） */
.topbar {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #eee;

  display: flex;
  align-items: center;
  justify-content: center; /* ⭐ 关键：让内部居中 */

  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

  position: sticky;
  top: 0;
  z-index: 1000;
}

/* 新增：内部容器 */
.topbar-inner {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 首页：1200px 居中 */
.home-inner {
  max-width: 1200px;
}

/* 其他页面：保持原来 padding */
.default-inner {
  padding: 0 40px;
}

.logo {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  text-decoration: none;
}

.logo:hover {
  color: #409eff;
}

/* 右侧区域 */

.right-area {
  display: flex;
  align-items: center;
  gap: 16px;
}
/* 搜索框 */

.search-input {
  width: 220px;
}

/* 登录按钮 - LeetCode 原版样式 */
.login-btn {
  padding: 6px 12px;
  margin-right: 12px;
  font-size: 14px;
  color: #4a4a4a;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

/* 登录按钮 hover 效果 */
.login-btn:hover {
  background-color: #f5f5f5;
  color: #0099cc;
}

/* 注册按钮 - LeetCode 原版样式 */
.register-btn {
  padding: 6px 12px;
  margin-right: 12px;
  font-size: 14px;
  color: #4a4a4a;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

/* 注册按钮 hover 效果 */
.register-btn:hover {
  background-color: #e6f7ff;
  border-color: #33adff;
  color: #33adff;
}
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh; /* 页面最小高度 = 视口高度 */
}

/* 内容区域自动撑开 */

.content {
  flex: 1;
  padding: 20px;
}

/* 页脚 */

.footer {
  background: #f8f9fa;
  border-top: 1px solid #eee;
  padding: 18px 0;
}
.footer-content {
  max-width: 1200px;
  margin: 0 auto;

  display: flex;
  justify-content: space-between;
  align-items: center;

  font-size: 14px;
  color: #666;
}
.footer-links {
  display: flex;
  gap: 20px;
}

.footer-links a {
  text-decoration: none;
  color: #666;
  transition: 0.2s;
}

.footer-links a:hover {
  color: #409eff;
}
</style>
