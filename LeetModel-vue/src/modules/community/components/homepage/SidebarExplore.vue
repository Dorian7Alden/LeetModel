<template>
  <div class="sidebar-container">
    <!-- 搜索框 -->
    <div class="search-box">
      <span class="search-icon">
        <el-icon><Search /></el-icon>
      </span>
      <input type="text" placeholder="搜索" class="search-input" />
    </div>

    <!-- 探索模块 -->
    <div class="explore-section">
      <h2 class="section-title">探索</h2>

      <!-- 技术交流分类 -->
      <div class="category">
        <div class="category-header">
          <span class="category-tag">#技术交流</span>
          <div class="avatar-group">
            <img
              src="https://picsum.photos/32/32?1"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?2"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?3"
              alt="avatar"
              class="avatar"
            />
          </div>
        </div>
        <div class="post-item" v-for="item in techPosts" :key="item.id">
          <span class="post-prefix">分享 | </span>
          <span class="post-title">{{ item.title }}</span>
        </div>
      </div>

      <!-- 学习分享分类 -->
      <div class="category">
        <div class="category-header">
          <span class="category-tag">#学习分享</span>
          <div class="avatar-group">
            <img
              src="https://picsum.photos/32/32?4"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?5"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?6"
              alt="avatar"
              class="avatar"
            />
          </div>
        </div>
        <div class="post-item" v-for="item in studyPosts" :key="item.id">
          <span class="post-prefix">分享 | </span>
          <span class="post-title">{{ item.title }}</span>
        </div>
      </div>

      <!-- 求职面试分类 -->
      <div class="category">
        <div class="category-header">
          <span class="category-tag">#求职面试</span>
          <div class="avatar-group">
            <img
              src="https://picsum.photos/32/32?7"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?8"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?9"
              alt="avatar"
              class="avatar"
            />
          </div>
        </div>
        <div class="post-item" v-for="item in jobPosts" :key="item.id">
          <span class="post-prefix">{{ item.prefix }} | </span>
          <span class="post-title">{{ item.title }}</span>
        </div>
      </div>

      <!-- Java分类 -->
      <div class="category">
        <div class="category-header">
          <span class="category-tag">#Java</span>
          <div class="avatar-group">
            <img
              src="https://picsum.photos/32/32?10"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?11"
              alt="avatar"
              class="avatar"
            />
            <img
              src="https://picsum.photos/32/32?12"
              alt="avatar"
              class="avatar"
            />
          </div>
        </div>
        <div class="post-item" v-for="item in javaPosts" :key="item.id">
          <span class="post-prefix">{{ item.prefix }} | </span>
          <span class="post-title">{{ item.title }}</span>
        </div>
      </div>

      <!-- 更多按钮 -->
      <div class="more-btn">更多</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { Search } from "@element-plus/icons-vue";

// 分类数据
const techPosts = ref([
  { id: 1, title: "50天400题" },
  { id: 2, title: "50题纪念" },
]);

const studyPosts = ref([
  { id: 3, title: "我恨你dp" },
  { id: 4, title: "9题纪念，终于做9道题了开心" },
]);

const jobPosts = ref([
  { id: 5, prefix: "求助", title: "求捞" },
  { id: 6, prefix: "内推", title: "小米内推码 4Q6GKUB | 校招 ..." },
]);

const javaPosts = ref([
  { id: 7, prefix: "内推", title: "米哈游2026社招+校招直通..." },
  { id: 8, prefix: "分享", title: "如何科学刷题?" },
]);
</script>

<style scoped>
.sidebar-container {
  width: 100%;
  max-width: 400px;
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue",
    Arial, sans-serif;
  background-color: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.search-box {
  display: flex;
  align-items: center;
  background-color: #f5f5f5;
  border-radius: 20px;
  padding: 10px 16px;
  margin-bottom: 20px;
}

.search-icon {
  margin-right: 8px;
  color: #999;
}

.search-input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 16px;
  color: #333;
  width: 100%;
}

.search-input::placeholder {
  color: #999;
}

.explore-section {
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 16px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: #222;
}

.category {
  margin-bottom: 20px;
}

.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.category-tag {
  font-size: 14px;
  color: #999;
  font-weight: 500;
}

.avatar-group {
  display: flex;
  margin-left: auto;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  margin-left: -8px;
  border: 2px solid #fff;
}

.post-item {
  font-size: 16px;
  color: #222;
  margin-bottom: 12px;
  line-height: 1.5;
}

.post-prefix {
  color: #666;
  font-weight: 500;
}

.post-title {
  color: #222;
}

.more-btn {
  color: #007fff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  margin-top: 8px;
}

.more-btn:hover {
  text-decoration: underline;
}
</style>
