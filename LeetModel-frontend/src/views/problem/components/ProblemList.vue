<template>
  <div class="problem-list">
    <div class="list-header">
      <span class="result-count">共 {{ filteredList.length }} 题</span>
    </div>

    <div v-if="pagedList.length > 0" class="list-cards">
      <div
        class="problem-card card-hover"
        v-for="(item, idx) in pagedList"
        :key="item.problemId"
        @click="$router.push(`/problem/${item.problemId}`)"
      >
        <div class="card-left">
          <span class="card-index">{{ (pageNum - 1) * pageSize + idx + 1 }}</span>
        </div>
        <div class="card-body">
          <div class="card-top">
            <h3 class="card-title">{{ item.problemTitle }}</h3>
            <el-tag
              :type="difficultyType(item.difficulty)"
              size="small"
              effect="plain"
            >{{ item.difficulty }}</el-tag>
          </div>
          <div class="card-tags" v-if="item.tags">
            <span class="card-tag" v-for="t in item.tags.slice(0, 4)" :key="t">{{ t }}</span>
          </div>
          <div class="card-meta">
            <span class="meta-item">
              <el-icon :size="14"><StarFilled /></el-icon>
              {{ item.aveScore }} 分
            </span>
            <span class="meta-item">
              <el-icon :size="14"><User /></el-icon>
              {{ item.submissionCount }} 提交
            </span>
            <span class="meta-item">
              通过率 {{ (item.passRate * 100).toFixed(0) }}%
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-empty description="暂无符合条件的题目" />
    </div>

    <div class="pagination-wrap" v-if="filteredList.length > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="filteredList.length"
        background
        layout="prev, pager, next, total"
        @current-change="scrollToTop"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { StarFilled, User } from '@element-plus/icons-vue'
import { mockProblems } from '@/mock/data.js'

const allProblems = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const queryParams = ref({})

onMounted(() => {
  allProblems.value = mockProblems
})

const filteredList = computed(() => {
  let list = [...allProblems.value]
  const q = queryParams.value
  if (!q) return list

  if (q.keyword) {
    const kw = q.keyword.toLowerCase()
    list = list.filter(p => p.problemTitle.toLowerCase().includes(kw) || (p.description && p.description.toLowerCase().includes(kw)))
  }
  if (q.difficulty) list = list.filter(p => p.difficulty === q.difficulty)
  if (q.language) list = list.filter(p => p.language === q.language)
  if (q.tags && q.tags.length > 0) {
    list = list.filter(p => q.tags.some(t => p.tags && p.tags.includes(t)))
  }
  if (q.minAveScore != null) list = list.filter(p => p.aveScore >= q.minAveScore)
  if (q.maxAveScore != null) list = list.filter(p => p.aveScore <= q.maxAveScore)

  if (q.sortOrder === 'asc') list.sort((a, b) => a.aveScore - b.aveScore)
  else if (q.sortOrder === 'desc') list.sort((a, b) => b.aveScore - a.aveScore)
  else if (q.sortOrder === 'newest') list.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))

  return list
})

const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

function updateQuery(params) {
  queryParams.value = { ...params }
  pageNum.value = 1
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function difficultyType(d) {
  const map = { '入门': 'success', '中等': 'warning', '困难': 'danger', '挑战': 'danger' }
  return map[d] || 'info'
}

defineExpose({ updateQuery })
</script>

<style scoped>
.problem-list { }

.list-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}

.result-count {
  font-size: 13px; color: var(--lm-text-secondary); font-weight: 500;
}

.list-cards {
  display: flex; flex-direction: column; gap: 8px;
}

.problem-card {
  display: flex; gap: 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 16px 18px;
  cursor: pointer;
  align-items: flex-start;
}

.card-left {
  display: flex; align-items: center; padding-top: 2px;
}

.card-index {
  font-size: 14px; font-weight: 600; color: var(--lm-text-muted);
  width: 28px; text-align: center;
}

.card-body { flex: 1; min-width: 0; }

.card-top {
  display: flex; align-items: center; gap: 10px; margin-bottom: 8px;
}

.card-title {
  font-size: 15px; font-weight: 600; color: var(--lm-text-primary);
  margin: 0; line-height: 1.4;
}

.card-title:hover { color: var(--lm-primary); }

.card-tags {
  display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px;
}

.card-tag {
  font-size: 11px; color: var(--lm-text-secondary);
  background: var(--lm-bg-secondary);
  padding: 2px 8px; border-radius: 4px;
}

.card-meta {
  display: flex; gap: 16px;
}

.meta-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--lm-text-muted);
}

.pagination-wrap {
  display: flex; justify-content: center; margin-top: 24px;
}
</style>
