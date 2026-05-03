<template>
  <div class="dashboard">
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" :style="{ background: item.bgColor }">
              <el-icon :size="24" :color="item.color"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-title">{{ item.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const loading = ref(true);

const stats = ref([
  { title: '总题目数', value: '124', icon: 'DocumentCopy', color: '#409eff', bgColor: '#ecf5ff' },
  { title: '总作品数', value: '890', icon: 'DataLine', color: '#67c23a', bgColor: '#f0f9eb' },
  { title: '今日提交', value: '12', icon: 'Upload', color: '#e6a23c', bgColor: '#fdf6ec' },
  { title: '待审核', value: '5', icon: 'Warning', color: '#f56c6c', bgColor: '#fef0f0' }
]);

onMounted(() => {
  setTimeout(() => { loading.value = false; }, 600);
});
</script>

<style scoped>
.dashboard {
  padding: 0;
}
.stat-card {
  border-radius: 8px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}
.stat-title {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
</style>
