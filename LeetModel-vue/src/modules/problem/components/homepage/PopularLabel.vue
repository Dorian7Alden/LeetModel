<template>
  <!-- 热门标签 -->
  <div class="card">
    <div class="title">
      <el-icon><TrendCharts /></el-icon>
      热门标签
    </div>

    <input class="search" placeholder="输入标签" />

    <div class="tags">
      <span class="tag">数学建模</span>
      <span class="tag">优化</span>
      <span class="tag">回归</span>
      <span class="tag">机器学习</span>
    </div>
  </div>
</template>

<script setup>
import { TrendCharts } from "@element-plus/icons-vue";
</script>

<style scoped>
/* 卡片 */
.card {
  background: #fff;
  padding: 16px;
  border-radius: 16px;
  margin-bottom: 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
/* 标签 */
.title {
  font-weight: bold;
  margin-bottom: 10px;
}

.search {
  width: 100%;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #ddd;
  margin-bottom: 10px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 6px 10px;
  background: #f2f2f2;
  border-radius: 12px;
  font-size: 12px;
}
</style>
