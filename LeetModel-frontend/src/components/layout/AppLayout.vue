<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <!-- 左侧：系统名 + 导航 -->
        <div class="left-area">
          <nav class="navbar">
            <router-link to="/" class="nav-item home-icon">
              <img src="@/assets/images/logo-with-en.png" alt="home" />
            </router-link>

            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: isActive(item.path) }"
            >
              {{ item.label }}
            </router-link>
            <router-link
              v-if="userStore.isAdmin"
              to="/admin/dashboard"
              class="nav-item admin-nav"
              :class="{ active: isActive('/admin') }"
            >
              管理端
            </router-link>
          </nav>
        </div>

        <!-- 右侧 -->
        <div class="right-area">
          <el-input
            v-model="keyword"
            placeholder="搜索题目 / 比赛 / 帖子"
            class="search-input"
            clearable
          />
          <!-- 未登录 -->
          <template v-if="!userStore.isLogin">
            <div class="nav-actions">
              <router-link to="/register" class="register-btn">注册</router-link>
              <router-link to="/login" class="login-btn">登录</router-link>
            </div>
          </template>

          <!-- 已登录 -->
          <el-dropdown v-else trigger="click">
            <div class="user-box">
              <img v-if="userStore.avatarUrl" class="avatar" :src="userStore.avatarUrl" />
              <span v-else class="avatar avatar-text">{{ (userStore.username || '?').charAt(0) }}</span>
            </div>

            <template #dropdown>
              <el-dropdown-menu class="user-card">
                <div class="user-header">
                  <img v-if="userStore.avatarUrl" class="avatar-big" :src="userStore.avatarUrl" />
                  <span v-else class="avatar-big avatar-text-big">{{ (userStore.username || '?').charAt(0) }}</span>
                  <div class="info">
                    <div class="name">{{ userStore.username || '用户' }}</div>
                    <div class="desc">{{ userStore.email }}</div>
                  </div>
                </div>

                <div class="menu-group">
                  <el-dropdown-item class="menu-item" @click="onFeatureWip">
                    <el-icon class="menu-icon"><Collection /></el-icon>
                    我的题单
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item" @click="onFeatureWip">
                    <el-icon class="menu-icon"><StarFilled /></el-icon>
                    我的收藏
                  </el-dropdown-item>
                  <el-dropdown-item class="menu-item" @click="onFeatureWip">
                    <el-icon class="menu-icon"><Document /></el-icon>
                    我的笔记
                  </el-dropdown-item>
                </div>

                <div class="divider"></div>

                <div class="menu-group">
                  <router-link to="/profile" class="menu-link">
                    <el-dropdown-item class="menu-item">
                      <el-icon class="menu-icon"><UserFilled /></el-icon>
                      个人中心
                    </el-dropdown-item>
                  </router-link>
                </div>

                <div class="divider"></div>

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
        <div class="footer-brand">
          <img src="@/assets/images/logo-en.png" alt="LeetModel" class="footer-logo-img" />
          <p class="footer-tagline">以模型会友，以算法相知</p>
        </div>

        <div class="footer-links">
          <div class="footer-col">
            <h4>平台</h4>
            <router-link to="/problem">题库</router-link>
            <router-link to="/contest">赛事</router-link>
            <router-link to="/community">社区</router-link>
            <router-link to="/team">组队</router-link>
          </div>
          <div class="footer-col">
            <h4>支持</h4>
            <router-link to="/about">关于我们</router-link>
            <router-link to="/help">使用帮助</router-link>
            <router-link to="/contact">联系我们</router-link>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <span>&copy; 2026 数学建模在线评测系统. All rights reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useAuth } from '@/composables/useAuth'
import {
  Collection,
  StarFilled,
  Document,
  UserFilled,
  SwitchButton,
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const { handleLogout } = useAuth()

const keyword = ref('')

const navItems = [
  { label: '题库', path: '/problem' },
  { label: '赛事', path: '/contest' },
  { label: '社区', path: '/community' },
  { label: '组队', path: '/team' },
]

function onFeatureWip() {
  ElMessage.info("该功能正在开发中，敬请期待")
}

function isActive(path) {
  if (path === '/admin') return route.path.startsWith('/admin')
  return route.path === path
}
</script>

<style scoped>
/* ========== CSS Variables Defaults ========== */
:root {
  --lm-primary: #409eff;
  --lm-surface: #ffffff;
  --lm-bg: #f8f9fb;
  --lm-border: #e8ecf1;
  --lm-text-primary: #1a1a2e;
  --lm-text-secondary: #666666;
  --lm-text-muted: #999999;
}

/* ========== Layout ========== */
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--lm-bg, #f8f9fb);
}

/* ========== Topbar ========== */
.topbar {
  height: 64px;
  background: var(--lm-surface, #ffffff);
  border-bottom: 1px solid var(--lm-border, #eee);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 0;
  z-index: 1000;
  backdrop-filter: blur(8px);
}

.topbar-inner {
  width: 100%;
  max-width: 1200px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
}

/* ========== Navbar ========== */
.left-area {
  display: flex;
  align-items: center;
  gap: 40px;
}

.navbar {
  display: flex;
  align-items: center;
  gap: 28px;
}

.home-icon img {
  height: 22px;
  transition: opacity 0.2s;
}

.home-icon:hover img {
  opacity: 0.8;
}

.home-icon {
  display: flex;
  align-items: center;
}

.nav-item {
  color: var(--lm-text-secondary, #555);
  text-decoration: none;
  font-size: 15px;
  font-weight: 500;
  padding: 8px 6px;
  position: relative;
  transition: color 0.2s, background 0.2s;
  border-radius: 6px;
  white-space: nowrap;
}

.nav-item:hover {
  color: var(--lm-primary, #409eff);
  background: rgba(64, 158, 255, 0.06);
}

/* Active indicator */
.nav-item.active {
  color: var(--lm-primary, #409eff);
  font-weight: 600;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 100%;
  height: 3px;
  background: var(--lm-primary, #409eff);
  border-radius: 3px 3px 0 0;
}

/* Admin nav */
.admin-nav {
  color: #e6a23c !important;
}

.admin-nav:hover {
  color: #cf9236 !important;
  background: rgba(230, 162, 60, 0.06) !important;
}

.admin-nav.active {
  color: #cf9236 !important;
}

.admin-nav.active::after {
  background: #e6a23c;
}

/* ========== Right Area ========== */
.right-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 220px;
}

.nav-actions {
  display: flex;
  align-items: center;
}

.login-btn,
.register-btn {
  padding: 6px 12px;
  margin-right: 12px;
  font-size: 14px;
  color: #555;
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.register-btn {
  margin-right: 8px;
}

.register-btn {
  padding: 6px 14px;
  background: var(--lm-primary, #409eff);
  color: #fff;
  border-radius: 6px;
  font-weight: 500;
}

.register-btn:hover {
  background: #337ecc;
  color: #fff;
}

.login-btn:hover {
  background: var(--lm-bg, #f5f7fa);
  color: var(--lm-primary, #409eff);
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid var(--lm-border, #e0e0e0);
  transition: border-color 0.2s;
  cursor: pointer;
  object-fit: cover;
}

.avatar:hover {
  border-color: var(--lm-primary, #409eff);
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

/* ========== Dropdown ========== */
.user-card {
  width: 260px;
  padding: 0 !important;
  border-radius: 12px !important;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12) !important;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #f0f7ff, #e6f0ff);
}

.avatar-big {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  object-fit: cover;
  flex-shrink: 0;
}

.avatar-text-big {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.info .name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary, #1a1a2e);
}

.info .desc {
  font-size: 12px;
  color: var(--lm-text-muted, #999);
}

.menu-group {
  padding: 4px 0;
}

.menu-item {
  padding: 10px 16px !important;
  font-size: 14px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lm-text-primary, #1a1a2e);
}

.menu-icon {
  font-size: 16px;
  color: var(--lm-text-secondary, #666);
}

.menu-item:hover {
  background: var(--lm-bg, #f5f7fa) !important;
  padding-left: 20px !important;
}

.divider {
  height: 1px;
  background: var(--lm-border, #eee);
  margin: 4px 0;
}

.menu-link {
  text-decoration: none;
  color: inherit;
}

.logout {
  color: #f56c6c !important;
  text-align: center;
  font-weight: 500;
}

.logout:hover {
  background: #fff1f0 !important;
}

/* ========== Content ========== */
.content {
  flex: 1;
  padding: 20px;
}

/* ========== Footer ========== */
.footer {
  background: var(--lm-surface, #fff);
  border-top: 1px solid var(--lm-border, #e8ecf1);
  padding: 0;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px 28px;
  display: flex;
  justify-content: space-between;
  gap: 48px;
  flex-wrap: wrap;
}

.footer-brand {
  max-width: 240px;
}

.footer-logo-img {
  height: 22px;
  width: auto;
}

.footer-tagline {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--lm-text-muted, #999);
}

.footer-links {
  display: flex;
  gap: 48px;
}

.footer-col h4 {
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 10px;
}

.footer-col a {
  display: block;
  font-size: 13px;
  color: var(--lm-text-secondary, #666);
  text-decoration: none;
  padding: 4px 0;
  transition: color 0.2s;
}

.footer-col a:hover {
  color: var(--lm-primary, #409eff);
}

.footer-bottom {
  border-top: 1px solid var(--lm-border, #e8ecf1);
  padding: 14px 20px;
  text-align: center;
  font-size: 12px;
  color: var(--lm-text-muted, #999);
}

@media (max-width: 768px) {
  .navbar {
    gap: 16px;
  }

  .nav-item {
    font-size: 13px;
    padding: 6px 4px;
  }

  .search-input {
    width: 140px;
  }

  .footer-content {
    gap: 24px;
  }

  .footer-links {
    gap: 24px;
  }
}
</style>
