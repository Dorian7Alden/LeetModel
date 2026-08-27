<template>
  <div class="ops-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">提交管理</h2>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="提交 ID" width="130" />
        <el-table-column prop="teamId" label="队伍" width="110" />
        <el-table-column prop="problemId" label="题目" width="110" />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">V{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="最终版" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.finalVersion" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无提交" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminSubmissions } from "@/api/admin-ops";

const rows = ref([]);
const loading = ref(false);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await getAdminSubmissions(50)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "提交数据加载失败");
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
