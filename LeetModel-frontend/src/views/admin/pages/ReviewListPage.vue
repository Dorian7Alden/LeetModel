<template>
  <div class="ops-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">评审管理</h2>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="taskId" label="任务 ID" width="130" />
        <el-table-column prop="submissionId" label="提交" width="110" />
        <el-table-column prop="teamId" label="队伍" width="110" />
        <el-table-column prop="problemId" label="题目" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90" align="center">
          <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
        </el-table-column>
        <el-table-column prop="workflowVersion" label="评审版本" width="130" />
        <el-table-column prop="modelName" label="模型" width="150" />
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无评审任务" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminReviews } from "@/api/admin-ops";

const rows = ref([]);
const loading = ref(false);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function statusLabel(status) {
  return ({ WAITING: "等待", LEASED: "已领取", RUNNING: "进行中", COMPLETED: "已完成", FAILED: "失败", UNKNOWN: "结果待确认" })[status] || status;
}
function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", UNKNOWN: "warning", RUNNING: "warning", LEASED: "warning" })[status] || "info";
}
async function load() {
  loading.value = true;
  try {
    rows.value = (await getAdminReviews(50)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "评审数据加载失败");
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
</style>
