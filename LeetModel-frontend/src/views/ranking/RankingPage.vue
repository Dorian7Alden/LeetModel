<template>
  <div class="ranking-page">
    <PageHeader title="排行榜" description="查看指定题目下已完成评审的最终提交排名">
    </PageHeader>

    <div class="toolbar">
      <el-select
        v-model="selectedProblemId"
        filterable
        placeholder="请选择题目标题"
        :loading="loadingProblems"
        class="problem-select"
        @change="loadRanking"
      >
        <el-option
          v-for="problem in problems"
          :key="problem.id"
          :label="problem.title"
          :value="problem.id"
        />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="按队伍名称筛选"
        clearable
        class="keyword-input"
        @clear="loadRanking"
        @keyup.enter="loadRanking"
      />
      <el-button type="primary" :loading="loading" @click="loadRanking">查询</el-button>
    </div>

    <div v-loading="loading" class="ranking-body">
      <template v-if="overview">
        <div class="ranking-meta">
          <span>共 {{ overview.total }} 支队伍上榜</span>
          <span v-if="overview.computedAt">计算于 {{ formatDate(overview.computedAt) }}</span>
        </div>

        <el-table :data="overview.items" class="ranking-table" empty-text="当前题目暂无上榜提交">
          <el-table-column label="排名" width="90" align="center">
            <template #default="{ row }">
              <span class="rank-badge" :class="`rank-${row.rank}`">{{ row.rank }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="teamName" label="队伍" min-width="200" />
          <el-table-column label="得分" width="120" align="center">
            <template #default="{ row }">
              <strong class="score-cell">{{ row.score != null ? row.score : '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="workflowVersion" label="评审版本" width="140" />
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatDate(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="评审完成" width="170">
            <template #default="{ row }">{{ formatDate(row.reviewFinishedAt) }}</template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-else-if="!loading" description="请先选择一个题目查看排行榜" :image-size="90" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/common/PageHeader.vue";
import { getPublicProblemList } from "@/api/problem";
import { getRanking } from "@/api/ranking";

const loadingProblems = ref(false);
const loading = ref(false);
const problems = ref([]);
const selectedProblemId = ref(null);
const keyword = ref("");
const overview = ref(null);

function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function loadProblems() {
  loadingProblems.value = true;
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 });
    problems.value = res.data?.rows || [];
  } catch (error) {
    ElMessage.error(error.message || "题目列表加载失败");
  } finally {
    loadingProblems.value = false;
  }
}

async function loadRanking() {
  if (!selectedProblemId.value) return;
  loading.value = true;
  try {
    const res = await getRanking(selectedProblemId.value, keyword.value.trim());
    overview.value = res.data;
  } catch (error) {
    ElMessage.error(error.message || "排行榜加载失败");
    overview.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(loadProblems);
</script>

<style scoped>
.ranking-page { max-width: 1120px; margin: 0 auto; padding: 20px 0; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.problem-select { width: 340px; }
.keyword-input { max-width: 260px; }
.ranking-body { min-height: 240px; }
.ranking-meta { display: flex; gap: 16px; margin-bottom: 12px; color: var(--lm-text-muted); font-size: 13px; }
.ranking-table { background: var(--lm-surface); }
.rank-badge { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 50%; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-weight: 700; }
.rank-badge.rank-1 { background: #fef3c7; color: #b45309; }
.rank-badge.rank-2 { background: #e5e7eb; color: #4b5563; }
.rank-badge.rank-3 { background: #fed7aa; color: #9a3412; }
.score-cell { color: var(--lm-primary); font-size: 16px; }
@media (max-width: 720px) { .toolbar { flex-direction: column; } .problem-select, .keyword-input { width: 100%; } }
</style>
