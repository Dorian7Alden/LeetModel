<template>
  <div class="discussion-page">
    <!-- 页面容器 -->
    <div class="page-container">
      <!-- 左侧：主内容区 -->
      <div class="main-content">
        <Discussionlist />
      </div>

      <!-- 右侧：侧边栏 -->
      <div class="sidebar">
        <SidebarExplore />
      </div>
    </div>
  </div>
</template>

<script setup>
// 引入你之前的两个组件
import Discussionlist from "../components/homepage/Discussionlist.vue";
import SidebarExplore from "../components/homepage/SidebarExplore.vue";
</script>

<style scoped>
.discussion-page {
  width: 100%;
  min-height: 100vh;
  background-color: #f7f8fa;
  padding: 20px 0;
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* 核心左右布局 */
.page-container {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  gap: 24px;
  align-items: flex-start;
  padding: 0 20px;
}

/* 左侧主内容：占大部分宽度 */
.main-content {
  flex: 1;
  min-width: 0; /* 防止flex拉伸异常 */
}

/* 右侧侧边栏：固定宽度 */
.sidebar {
  width: 380px;
  flex-shrink: 0;
}

/* 响应式：小屏幕自动堆叠 */
@media (max-width: 1024px) {
  .page-container {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
  }
}
</style>
