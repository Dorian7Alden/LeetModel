<template>
  <div class="ranking-admin-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">排行榜管理</h2>
        <div class="toolbar-actions">
          <el-select v-model="problemId" filterable placeholder="请选择题目标题" :loading="loadingProblems" style="width: 320px" @change="load">
            <el-option v-for="p in problems" :key="p.id" :label="p.title" :value="p.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="按队伍名称筛选" clearable style="width: 220px" @keyup.enter="load" @clear="load" />
          <el-button :loading="loading" @click="load">查询</el-button>
          <el-button type="warning" plain :disabled="!problemId" :loading="rebuilding" @click="rebuild">重建榜单</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="ranking" class="ranking-meta">
          <span>共 {{ ranking.total }} 支队伍上榜</span>
          <span v-if="ranking.computedAt">计算于 {{ formatTime(ranking.computedAt) }}</span>
          <span v-if="ranking.batchId">批次 {{ ranking.batchId }}</span>
        </div>
        <el-table :data="ranking?.items || []" stripe style="width: 100%">
          <el-table-column label="排名" width="90" align="center">
            <template #default="{ row }">{{ row.rank }}</template>
          </el-table-column>
          <el-table-column prop="teamName" label="队伍" min-width="200" />
          <el-table-column prop="teamId" label="队伍 ID" width="110" />
          <el-table-column label="得分" width="110" align="center">
            <template #default="{ row }">{{ row.score != null ? row.score : '-' }}</template>
          </el-table-column>
          <el-table-column prop="workflowVersion" label="版本" width="130" />
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
          </el-table-column>
          <template #empty><el-empty :description="problemId ? '当前题目暂无上榜提交' : '请先选择题目'" /></template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getPublicProblemList } from "@/api/problem";
import { getAdminRanking, rebuildAdminRanking } from "@/api/admin-ops";

const problems = ref([]);
const problemId = ref(null);
const keyword = ref("");
const loading = ref(false);
const loadingProblems = ref(false);
const rebuilding = ref(false);
const ranking = ref(null);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function loadProblems() {
  loadingProblems.value = true;
  try {
    problems.value = (await getPublicProblemList({ page: 1, pageSize: 100 })).data?.rows || [];
  } catch (error) {
    ElMessage.error(error.message || "题目列表加载失败");
  } finally {
    loadingProblems.value = false;
  }
}

async function load() {
  if (!problemId.value) return;
  loading.value = true;
  try {
    ranking.value = (await getAdminRanking(problemId.value, keyword.value.trim())).data;
  } catch (error) {
    ElMessage.error(error.message || "排行榜加载失败");
    ranking.value = null;
  } finally {
    loading.value = false;
  }
}

async function rebuild() {
  if (!problemId.value) return;
  rebuilding.value = true;
  try {
    const res = await rebuildAdminRanking(problemId.value);
    ElMessage.success(`榜单已重建（${res.data} 条）`);
    await load();
  } catch (error) {
    ElMessage.error(error.message || "重建失败");
  } finally {
    rebuilding.value = false;
  }
}

onMounted(loadProblems);
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.ranking-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; color: var(--lm-text-muted); font-size: 13px; }
</style>
