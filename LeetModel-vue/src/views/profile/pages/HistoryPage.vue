<template>
  <div class="history-page">
    <div class="page-header-row">
      <h2 class="page-title">提交历史</h2>
      <div class="filter-row">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="default"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          class="date-picker"
        />
        <el-select
          v-model="statusFilter"
          placeholder="状态筛选"
          size="default"
          class="status-select"
          clearable
        >
          <el-option label="全部状态" value="" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="评审中" value="EVALUATING" />
        </el-select>
      </div>
    </div>

    <div class="table-card">
      <el-table
        :data="filteredList"
        stripe
        style="width: 100%"
        v-loading="loading"
        empty-text="暂无提交记录"
      >
        <el-table-column prop="title" label="题目名称" min-width="180">
          <template #default="{ row }">
            <span class="problem-link">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="userName" label="提交者" width="130" />

        <el-table-column prop="submitTime" label="提交时间" width="140" sortable>
          <template #default="{ row }">
            <span class="time-text">{{ row.submitTime }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="totalScore" label="得分" width="100" sortable align="center">
          <template #default="{ row }">
            <span v-if="row.totalScore !== null" :class="scoreClass(row.totalScore)">
              {{ row.totalScore.toFixed(1) }}
            </span>
            <el-tag v-else type="info" size="small">评审中</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'COMPLETED' ? 'success' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ row.status === 'COMPLETED' ? '已完成' : '评审中' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="totalPages > 1">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="filteredTotal"
        layout="prev, pager, next, total"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { mockSubmissions } from '@/mock/data.js'

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(5)
const dateRange = ref(null)
const statusFilter = ref('')

const submissions = ref([...mockSubmissions])

const filteredList = computed(() => {
  let list = submissions.value

  // 状态筛选
  if (statusFilter.value) {
    list = list.filter((s) => s.status === statusFilter.value)
  }

  // 日期筛选
  if (dateRange.value && dateRange.value.length === 2) {
    const [start, end] = dateRange.value
    list = list.filter((s) => s.submitTime >= start && s.submitTime <= end)
  }

  // 分页
  const start = (currentPage.value - 1) * pageSize.value
  return list.slice(start, start + pageSize.value)
})

const filteredTotal = computed(() => {
  let list = submissions.value
  if (statusFilter.value) {
    list = list.filter((s) => s.status === statusFilter.value)
  }
  if (dateRange.value && dateRange.value.length === 2) {
    const [start, end] = dateRange.value
    list = list.filter((s) => s.submitTime >= start && s.submitTime <= end)
  }
  return list.length
})

const totalPages = computed(() => Math.ceil(filteredTotal.value / pageSize.value))

function scoreClass(score) {
  if (score >= 90) return 'score-high'
  if (score >= 80) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.history-page {
  padding: 24px 30px 40px;
}

.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0;
}

.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.date-picker {
  width: 250px;
}

.status-select {
  width: 130px;
}

.table-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  overflow: hidden;
}

.problem-link {
  color: var(--lm-primary, #409eff);
  cursor: pointer;
  font-weight: 500;
  font-size: 14px;
}

.problem-link:hover {
  text-decoration: underline;
}

.time-text {
  font-size: 13px;
  color: var(--lm-text-secondary, #666);
}

.score-high {
  font-weight: 700;
  color: #67c23a;
  font-size: 15px;
}

.score-mid {
  font-weight: 700;
  color: var(--lm-primary, #409eff);
  font-size: 15px;
}

.score-low {
  font-weight: 700;
  color: #f56c6c;
  font-size: 15px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 768px) {
  .page-header-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-row {
    flex-direction: column;
    width: 100%;
  }

  .date-picker {
    width: 100%;
  }

  .status-select {
    width: 100%;
  }
}
</style>
