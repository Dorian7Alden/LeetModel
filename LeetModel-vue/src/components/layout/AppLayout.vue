<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="topbar">
      <!-- 左侧：系统名 + 导航 -->
      <div class="left-area">
        <!-- 导航栏 -->
        <nav class="navbar">
          <router-link to="/" class="nav-item home-icon">
            <img src="@/assets/icon/home.png" alt="home" />
          </router-link>
          <router-link to="/training" class="nav-item">训练</router-link>
          <router-link to="/problem" class="nav-item">题库</router-link>
          <router-link to="/contest" class="nav-item">赛事</router-link>
          <router-link to="/community" class="nav-item">社区</router-link>
          <router-link to="/team" class="nav-item">组队</router-link>
          <router-link to="/profile" class="nav-item">个人中心</router-link>
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

        <!-- 未登录 -->
        <template v-if="!userStore.isLogin">
          <router-link to="/login">
            <button class="btn">登录</button>
          </router-link>

          <router-link to="/register">
            <button class="btn register">注册</button>
          </router-link>
        </template>

        <!-- 已登录 -->
        <el-dropdown v-else trigger="click">
          <div class="user-box">
            <img class="avatar" src="../../assets/vue.svg" />
          </div>

          <template #dropdown>
            <el-dropdown-menu class="user-card">
              <div class="user-info">
                <img class="avatar-big" src="../../assets/vue.svg" />
                <div class="name">Kind EasleyaAJ</div>
              </div>

              <el-dropdown-item>题单</el-dropdown-item>
              <el-dropdown-item>收藏夹</el-dropdown-item>
              <el-dropdown-item>笔记本</el-dropdown-item>

              <el-dropdown-item divided @click="logout">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
const userStore = useUserStore();
const isLogin = ref(!!localStorage.getItem("token"));

const router = useRouter();

function logout() {
  userStore.logout();
}

const keyword = ref("");
</script>

<style scoped>
.user-box {
  cursor: pointer;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.user-card {
  width: 260px;
  padding: 10px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
}

.avatar-big {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.name {
  font-size: 16px;
  font-weight: 600;
}
.home-icon img {
  width: 20px;
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

/* 顶部栏 */
.topbar {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #eee;

  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 0 40px;

  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

  position: sticky;
  top: 0;
  z-index: 1000;
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

/* 按钮 */

.btn {
  background: #409eff;
  color: white;

  padding: 6px 14px;
  border-radius: 6px;

  text-decoration: none;
  font-size: 14px;

  transition: 0.2s;
}
.btn:hover {
  background: #2f7de1;
}
.register {
  background: #409eff;
  color: white;

  padding: 6px 14px;
  border-radius: 6px;

  text-decoration: none;
  font-size: 14px;

  transition: 0.2s;
}

.register:hover {
  background: #2f7de1;
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
