<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="topbar">
      <!-- 左侧系统名 -->
      <div class="logo">数学建模在线评测系统</div>

      <!-- 右侧区域 -->
      <div class="right-area">
        <!-- 搜索框 -->
        <el-input
          v-model="keyword"
          placeholder="搜索题目 / 比赛 / 帖子"
          class="search-input"
          clearable
        />

        <!-- 登录注册 -->
        <router-link to="/login">
          <button class="btn">登录</button>
        </router-link>

        <router-link to="/register">
          <button class="btn register">注册</button>
        </router-link>
      </div>
    </header>

    <!-- 导航栏 -->
    <nav class="navbar">
      <div class="nav-container">
        <router-link to="/" custom v-slot="{ isExactActive, navigate }">
          <a
            @click="navigate"
            :class="['nav-item', { 'nav-active': isExactActive }]"
          >
            首页
          </a>
        </router-link>
        <router-link to="/training" class="nav-item">训练</router-link>
        <router-link to="/problem" class="nav-item">题库</router-link>
        <router-link to="/contest" class="nav-item">赛事</router-link>
        <router-link to="/community" class="nav-item">社区</router-link>
        <router-link to="/team" class="nav-item">组队</router-link>
        <router-link to="/profile" class="nav-item">个人中心</router-link>
      </div>
    </nav>

    <!-- 页面内容 -->
    <main class="content">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer class="footer">© 2026 Math Modeling Platform</footer>
  </div>
</template>

<script setup>
import { ref } from "vue";

const keyword = ref("");
</script>

<style scoped>
/* 导航栏样式 */
.navbar {
  background: #34495e;
  height: 55px;
  display: flex;
  align-items: center;
}

/* 容器 */

.nav-container {
  display: flex;
  gap: 30px;
  padding: 0 40px;
}

/* 导航按钮 */

.nav-item {
  color: white;
  text-decoration: none;
  font-size: 15px;
  transition: 0.2s;
}

/* hover效果 */

.nav-item:hover {
  color: #409eff;
}

/* 当前页面高亮 */

.router-link-active {
  color: #409eff;
  font-weight: bold;
}
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 顶部栏 */

.topbar {
  height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  background: #2c3e50;
  color: white;
}

.logo {
  font-size: 18px;
  font-weight: bold;
}

/* 右侧区域 */

.right-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 搜索框 */

.search-input {
  width: 260px;
}

/* 按钮 */

.btn {
  padding: 6px 14px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.register {
  background: #409eff;
  color: white;
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
  background: #eee;
  text-align: center;
  padding: 20px;
}
</style>
