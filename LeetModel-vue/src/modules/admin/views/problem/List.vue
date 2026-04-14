<template>
  <div class="problem-list">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索题目"
          style="width: 300px"
          prefix-icon="Search"
          clearable
        />
        <el-button type="primary" @click="$router.push('/problem/upload')">
          <el-icon><Plus /></el-icon> 新增题目
        </el-button>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="题目名称" min-width="200" />
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="scope">
            <el-tag :type="getDifficultyType(scope.row.difficulty)">
              {{ getDifficultyLabel(scope.row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-switch v-model="scope.row.status" active-color="#13ce66" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link>编辑</el-button>
            <el-button size="small" type="danger" link>删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="12"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const searchQuery = ref('');

const tableData = ref([
  { id: 1, title: '2023高教社杯A题 定日镜场的优化设计', difficulty: 'hard', updateTime: '2023-09-08 10:00:00', status: true },
  { id: 2, title: '投资的收益和风险', difficulty: 'medium', updateTime: '2023-10-12 14:20:00', status: true },
  { id: 3, title: '最优路径规划问题', difficulty: 'easy', updateTime: '2023-11-05 09:15:00', status: false },
  { id: 4, title: '传染病模型与预测', difficulty: 'medium', updateTime: '2024-01-20 16:45:00', status: true },
  { id: 5, title: '车间调度问题研究', difficulty: 'hard', updateTime: '2024-02-18 11:30:00', status: true }
]);

const getDifficultyType = (level) => {
  const map = { easy: 'success', medium: 'warning', hard: 'danger' };
  return map[level] || 'info';
};

const getDifficultyLabel = (level) => {
  const map = { easy: '简单', medium: '中等', hard: '困难' };
  return map[level] || level;
};
</script>

<style scoped>
.action-bar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
