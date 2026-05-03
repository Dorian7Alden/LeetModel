<template>
  <div class="team-header">
    <!-- 左侧标题 -->
    <h2 class="title">组队大厅</h2>

    <!-- 右侧搜索 -->
    <div class="header-right">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索队伍..."
        @keyup.enter="handleSearch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";

const keyword = ref("");

function handleSearch() {
  console.log("搜索队伍:", keyword.value);
}
</script>

<style scoped>
.team-header {
  display: flex;

  justify-content: space-between;

  align-items: center;

  margin-bottom: 20px;
}

/* 标题 */

.title {
  font-size: 24px;

  font-weight: 600;
}

/* 右侧区域 */

.header-right {
  display: flex;

  align-items: center;
}

/* 搜索框 */

.search-input {
  width: 240px;

  padding: 6px 10px;

  border: 1px solid #ddd;

  border-radius: 6px;

  outline: none;

  transition: all 0.2s;
}

.search-input:focus {
  border-color: #409eff;
}
</style>
