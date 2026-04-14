<template>
  <div class="submission-list">
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="filters.query" placeholder="搜索题目ID或作者" style="width: 250px; margin-right: 15px" />
        <el-select v-model="filters.status" placeholder="评测状态" clearable style="width: 150px; margin-right: 15px">
          <el-option label="Pending" value="Pending" />
          <el-option label="Evaluating" value="Evaluating" />
          <el-option label="Finished" value="Finished" />
        </el-select>
        <el-button type="primary" icon="Search">筛选</el-button>
        <el-button icon="Refresh" @click="fetchData">刷新</el-button>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe>
        <el-table-column prop="id" label="作品流水" width="100" />
        <el-table-column prop="problemTitle" label="所属题目" min-width="180" />
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column prop="score" label="AI评分" width="80">
          <template #default="scope">
            <span :style="{ color: scope.row.score >= 80 ? '#67C23A' : '#E6A23C' }">
              {{ scope.row.score || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link>查看详情</el-button>
            <el-button size="small" type="danger" link>重新评测</el-button>
          </template>
        </el-table-column>
      </el-table>

       <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="45"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

const filters = reactive({ query: '', status: '' });
const loading = ref(false);

const tableData = ref([
  { id: 'S1001', problemTitle: '2023高教社杯A题', author: 'Team Alpha', submitTime: '10:05:00', score: 85, status: 'Finished' },
  { id: 'S1002', problemTitle: '2023高教社杯A题', author: 'Team Beta', submitTime: '10:12:30', score: null, status: 'Evaluating' },
  { id: 'S1003', problemTitle: '最优路径规划', author: 'User123', submitTime: '11:00:22', score: null, status: 'Pending' },
  { id: 'S1004', problemTitle: '车间调度问题研究', author: 'Matrix', submitTime: '11:30:10', score: 92, status: 'Finished' },
  { id: 'S1005', problemTitle: '传染病模型与预测', author: 'Omega', submitTime: '12:05:15', score: 76, status: 'Finished' },
]);

const getStatusType = (status) => {
  const map = { Pending: 'info', Evaluating: 'warning', Finished: 'success' };
  return map[status] || 'info';
};

const fetchData = () => {
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    ElMessage.success('刷新成功');
  }, 500);
};
</script>

<style scoped>
.filter-bar {
  display: flex;
  margin-bottom: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
