<template>
  <div class="team-list-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">队伍管理</h2>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="队伍 ID" width="130" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="leaderId" label="队长" width="120" />
        <el-table-column label="成员数" width="90" align="center">
          <template #default="{ row }">{{ row.memberCount ?? '1' }}</template>
        </el-table-column>
        <el-table-column prop="problemId" label="题目" width="120" />
        <el-table-column label="生命周期" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.practiceStatus)" size="small" effect="light">{{ statusLabel(row.practiceStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="截止时间" width="170">
          <template #default="{ row }">{{ formatTime(row.deadlineAt) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无队伍" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminTeams } from "@/api/admin-ops";

const rows = ref([]);
const loading = ref(false);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function statusLabel(status) {
  return ({ PREPARING: "组建中", IN_PROGRESS: "练习中", ENDED: "已结束" })[status] || status;
}
function statusType(status) {
  return ({ PREPARING: "info", IN_PROGRESS: "warning", ENDED: "success" })[status] || "info";
}
async function load() {
  loading.value = true;
  try {
    rows.value = (await getAdminTeams(50)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "队伍数据加载失败");
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
