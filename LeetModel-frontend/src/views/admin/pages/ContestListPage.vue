<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div>
        <h2 class="panel-title">赛事基础数据</h2>
        <p class="panel-subtitle">当前 MVP 仅做只读展示</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-table :data="contests" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="赛事 ID" width="180" />
      <el-table-column prop="code" label="编码" width="180" />
      <el-table-column prop="name" label="名称" min-width="260" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <template #empty><el-empty description="暂无赛事数据" /></template>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminContentContests } from "@/api/problem";

const contests = ref([]);
const loading = ref(false);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    contests.value = (await getAdminContentContests()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "赛事数据加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.panel-title { margin: 0; font-size: 18px; }
.panel-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }
</style>
