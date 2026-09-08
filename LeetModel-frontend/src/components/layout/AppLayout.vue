<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <!-- 左侧：品牌 -->
        <div class="left-area">
          <router-link to="/home" class="nav-item home-icon">
            <img src="@/assets/images/logo-with-en.png" alt="home" />
          </router-link>
          <button type="button" class="mobile-menu-button" aria-label="打开导航菜单" @click="mobileMenuOpen = true">
            <el-icon><Menu /></el-icon><span>菜单</span>
          </button>
        </div>

        <!-- 中间：主导航 -->
        <nav class="navbar">
            <router-link
              to="/home"
              class="nav-item"
              :class="{ active: route.path === '/home' }"
            >
              首页
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

        <!-- 右侧 -->
        <div class="right-area">
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
              <span v-else class="avatar avatar-text">{{ (userStore.nickname || userStore.username || '?').charAt(0) }}</span>
            </div>

            <template #dropdown>
              <el-dropdown-menu class="user-card">
                <div class="user-header">
                  <img v-if="userStore.avatarUrl" class="avatar-big" :src="userStore.avatarUrl" />
                  <span v-else class="avatar-big avatar-text-big">{{ (userStore.nickname || userStore.username || '?').charAt(0) }}</span>
                  <div class="info">
                    <div class="name">{{ userStore.nickname || userStore.username || '用户' }}</div>
                    <div class="desc">{{ userStore.email }}</div>
                    <el-tag
                      class="role-tag"
                      :type="roleTagType"
                      size="small"
                      effect="light"
                    >
                      {{ userStore.roleLabel }}
                    </el-tag>
                  </div>
                </div>

            <div class="menu-group">
              <router-link to="/ranking" class="menu-link">
                <el-dropdown-item class="menu-item">
                  <el-icon class="menu-icon"><Trophy /></el-icon>
                  排行榜
                </el-dropdown-item>
              </router-link>
            </div>

                <div class="divider"></div>

                <div class="menu-group">
                  <router-link to="/profile" class="menu-link">
                    <el-dropdown-item class="menu-item">
                      <el-icon class="menu-icon"><UserFilled /></el-icon>
                      个人中心
                    </el-dropdown-item>
                  </router-link>
                  <router-link to="/profile/settings" class="menu-link">
                    <el-dropdown-item class="menu-item">
                      <el-icon class="menu-icon"><Setting /></el-icon>
                      个人设置
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

    <el-drawer v-model="mobileMenuOpen" title="导航菜单" direction="ltr" size="280px" class="mobile-nav-drawer">
      <nav class="mobile-nav" aria-label="移动端主导航">
        <router-link to="/home" :class="{ active: route.path === '/home' }" @click="mobileMenuOpen = false">首页</router-link>
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" :class="{ active: isActive(item.path) }" @click="mobileMenuOpen = false">{{ item.label }}</router-link>
        <router-link v-if="userStore.isAdmin" to="/admin/dashboard" class="admin-mobile-nav" :class="{ active: isActive('/admin') }" @click="mobileMenuOpen = false">管理端</router-link>
      </nav>
    </el-drawer>

    <!-- 页面内容 -->
    <main class="content" :class="{ 'content-flush': route.path.startsWith('/problem') }">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer v-if="route.path !== '/problem' && route.path !== '/problem/problemListPage'" class="footer">
      <div class="footer-content">
        <div class="footer-brand">
          <img src="@/assets/images/logo-en.png" alt="LeetModel" class="footer-logo-img" />
          <span class="footer-tagline">数学建模在线实训平台</span>
        </div>

        <div class="footer-links">
          <router-link to="/about">关于我们</router-link>
          <span class="footer-sep">·</span>
          <router-link to="/help">使用帮助</router-link>
          <span class="footer-sep">·</span>
          <router-link to="/contact">联系我们</router-link>
        </div>

        <div class="footer-copyright">
          &copy; 2026 数学建模在线评测系统. All rights reserved.
        </div>
      </div>
    </footer>

    <AiAssistantWidget />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useAuth } from '@/composables/useAuth'
import { getCurrentAuthorization } from '@/api/user'
import AiAssistantWidget from '@/components/common/AiAssistantWidget.vue'
import {
  Trophy,
  Menu,
  UserFilled,
  Setting,
  SwitchButton,
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const { handleLogout } = useAuth()

const mobileMenuOpen = ref(false)
const roleTagType = computed(() => {
  if (userStore.primaryRole === 'admin') return 'danger'
  if (userStore.primaryRole === 'vip') return 'warning'
  return 'info'
})

const navItems = [
  { label: '题库', path: '/problem' },
  { label: '队伍', path: '/team' },
  { label: '排行榜', path: '/ranking' },
]

function isActive(path) {
  if (path === '/team') {
    return route.path.startsWith('/team')
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

onMounted(async () => {
  if (!userStore.isLogin || userStore.roles.length > 0) return

  try {
    const res = await getCurrentAuthorization()
    userStore.updateAuthorization(res.data)
  } catch (error) {
    console.warn('用户身份信息加载失败', error)
  }
})
</script>

<style scoped>
/* ========== Layout ========== */
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--lm-bg);
}

/* ========== Topbar ========== */
.topbar {
  height: 56px;
  background: var(--lm-surface);
  border-bottom: 1px solid var(--lm-border);
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
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

/* ========== Navbar ========== */
.left-area {
  display: flex;
  align-items: center;
  gap: 32px;
}

.right-area { justify-self: end; }

.navbar {
  display: flex;
  align-items: center;
  gap: 20px;
}

.mobile-menu-button { display: none; }

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
  margin-right: 8px;
}

.nav-item {
  color: var(--lm-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 6px;
  position: relative;
  transition: color 0.2s, background 0.2s;
  border-radius: 6px;
  white-space: nowrap;
}

.nav-item:hover {
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

/* Active indicator */
.nav-item.active {
  color: var(--lm-primary);
  font-weight: 600;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 100%;
  height: 2px;
  background: var(--lm-primary);
  border-radius: 2px 2px 0 0;
}

/* Admin nav */
.admin-nav {
  color: #d97706 !important;
}

.admin-nav:hover {
  color: #b45309 !important;
  background: #fffbeb !important;
}

.admin-nav.active {
  color: #b45309 !important;
}

.admin-nav.active::after {
  background: #d97706;
}

/* ========== Right Area ========== */
.right-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-btn,
.register-btn {
  padding: 5px 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--lm-radius-sm);
  cursor: pointer;
  transition: all var(--lm-transition);
  text-decoration: none;
}

.login-btn {
  color: var(--lm-text-secondary);
  background: transparent;
  border: 1px solid var(--lm-border);
}

.login-btn:hover {
  color: var(--lm-primary);
  border-color: var(--lm-primary-light);
  background: var(--lm-primary-bg);
}

.register-btn {
  background: var(--lm-primary);
  color: #fff;
  border: 1px solid var(--lm-primary);
}

.register-btn:hover {
  background: var(--lm-primary-dark);
  border-color: var(--lm-primary-dark);
  color: #fff;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--lm-border);
  transition: border-color 0.2s;
  cursor: pointer;
  object-fit: cover;
}

.avatar:hover {
  border-color: var(--lm-primary);
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lm-primary);
  color: #fff;
  font-size: 14px;
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
  background: var(--lm-primary-bg);
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
  background: var(--lm-primary);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.info .name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary);
}

.info .desc {
  font-size: 12px;
  color: var(--lm-text-muted);
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
  color: var(--lm-text-primary);
}

.menu-icon {
  font-size: 16px;
  color: var(--lm-text-secondary);
}

.menu-item:hover {
  background: var(--lm-bg) !important;
  padding-left: 20px !important;
}

.divider {
  height: 1px;
  background: var(--lm-border);
  margin: 4px 0;
}

.menu-link {
  text-decoration: none;
  color: inherit;
}

.logout {
  color: var(--lm-danger) !important;
  text-align: center;
  font-weight: 500;
}

.logout:hover {
  background: var(--lm-danger-bg) !important;
}

/* ========== Content ========== */
.content {
  flex: 1;
  padding: 24px;
}

.content.content-flush {
  padding: 0;
}

/* ========== Compact Modern Footer ========== */
.footer {
  background: var(--lm-surface);
  border-top: 1px solid var(--lm-border);
  padding: 0;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-logo-img {
  height: 18px;
  width: auto;
}

.footer-tagline {
  font-size: 12px;
  color: var(--lm-text-muted);
  border-left: 1px solid var(--lm-border);
  padding-left: 12px;
}

.footer-links {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.footer-links a {
  color: var(--lm-text-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.footer-links a:hover {
  color: var(--lm-primary);
}

.footer-sep {
  color: var(--lm-border);
  font-size: 12px;
}

.footer-copyright {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.mobile-nav { display: flex; flex-direction: column; gap: 6px; }
.mobile-nav a { padding: 12px 14px; border-radius: 8px; color: var(--lm-text-secondary); text-decoration: none; font-size: 15px; }
.mobile-nav a:hover, .mobile-nav a.active { background: var(--lm-primary-bg); color: var(--lm-primary); font-weight: 600; }
.mobile-nav .admin-mobile-nav { color: #d97706; }

@media (max-width: 768px) {
  .topbar { height: 56px; padding: 0; }
  .topbar-inner { gap: 10px; padding: 0 16px; }
  .left-area { min-width: 0; gap: 12px; }
  .navbar { gap: 0; }
  .navbar > .nav-item:not(.home-icon) { display: none; }
  .home-icon { display: flex; padding: 4px; }
  .home-icon img { max-width: 124px; height: 20px; object-fit: contain; }
  .mobile-menu-button { display: inline-flex; align-items: center; gap: 5px; padding: 7px 9px; border: 1px solid var(--lm-border); border-radius: 8px; background: var(--lm-surface); color: var(--lm-text-secondary); font: inherit; font-size: 13px; cursor: pointer; }
  .right-area { margin-left: auto; gap: 6px; }
  .login-btn,
  .register-btn {
    margin-right: 0;
    padding: 5px 10px;
  }

  .footer-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }
}
</style>
