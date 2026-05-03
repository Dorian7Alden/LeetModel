<template>
  <div class="discussion-page">
    <!-- 页面容器 -->
    <div class="page-container">
      <!-- 左侧：主内容区 -->
      <div class="main-content">
        <DiscussionList />
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
import DiscussionList from "./components/DiscussionList.vue";
import SidebarExplore from "./components/SidebarExplore.vue";
</script>

<style scoped>
@import './style.css';
</style>
