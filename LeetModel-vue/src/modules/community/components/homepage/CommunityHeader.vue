<template>
  <div class="community-header">
    <div class="header-left">
      <h2 class="title">社区</h2>
    </div>

    <div class="header-right">
      <!-- 搜索框 -->
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索帖子..."
        @keyup.enter="handleSearch"
      />

      <!-- 发帖按钮 -->
      <button class="post-btn" @click="goPost">发帖</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();

const keyword = ref("");

function handleSearch() {
  console.log("搜索:", keyword.value);
}

function goPost() {
  router.push("/post/create");
}
</script>

<style scoped>
.community-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  margin-bottom: 20px;
}

/* 左侧标题 */
.title {
  font-size: 24px;
  font-weight: 600;
}

/* 右侧区域 */
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 搜索框 */
.search-input {
  width: 220px;

  padding: 6px 10px;

  border: 1px solid #ddd;

  border-radius: 6px;

  outline: none;

  transition: all 0.2s;
}

.search-input:focus {
  border-color: #409eff;
}

/* 发帖按钮 */
.post-btn {
  padding: 6px 14px;

  background: #409eff;

  color: white;

  border: none;

  border-radius: 6px;

  cursor: pointer;

  transition: all 0.2s;
}

.post-btn:hover {
  background: #66b1ff;
}
</style>
