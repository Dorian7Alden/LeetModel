<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" :style="{ color: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">{{ item.title }}</div>
              <div class="stat-value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const stats = ref([
  { title: '总题目数', value: '124', icon: 'DocumentCopy', color: '#409eff' },
  { title: '总作品数', value: '890', icon: 'DataLine', color: '#67c23a' },
  { title: '今日提交', value: '12', icon: 'Upload', color: '#e6a23c' },
  { title: '待审核', value: '5', icon: 'Warning', color: '#f56c6c' }
]);
</script>

<style scoped>
.dashboard {
  padding: 20px;
}
.stat-item {
  display: flex;
  align-items: center;
}
.stat-icon {
  font-size: 48px;
  margin-right: 20px;
}
.stat-info {
  flex-grow: 1;
}
.stat-title {
  color: #909399;
  font-size: 14px;
}
.stat-value {
  color: #303133;
  font-size: 24px;
  font-weight: bold;
  margin-top: 5px;
}
</style>
