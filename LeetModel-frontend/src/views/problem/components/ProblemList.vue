<template>
  <section class="problem-list" v-loading="loading">
    <div v-if="problems.length" class="list-table">
      <div class="table-head">
        <span>题目</span><span>赛事</span><span>标签</span>
        <button class="sortable-head" @click="cycleSort('year')">年份<span class="sort-indicator" :class="sortState('year')"><span>▲</span><span>▼</span></span></button>
        <span>语言</span>
        <button class="sortable-head" @click="cycleSort('difficulty')">难度<span class="sort-indicator" :class="sortState('difficulty')"><span>▲</span><span>▼</span></span></button>
        <button class="sortable-head" @click="cycleSort('averageScore')">平均分<span class="sort-indicator" :class="sortState('averageScore')"><span>▲</span><span>▼</span></span></button>
        <span></span>
      </div>
      <button v-for="(item, index) in problems" :key="item.id" class="problem-row" @click="$router.push(`/problem/${item.id}`)">
        <div class="problem-main">
          <h3 :title="item.title"><span class="row-index">{{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}</span>{{ item.title }}</h3>
        </div>
        <span class="contest-name" :title="item.contestName || '未分类赛事'">{{ item.contestName || '未分类赛事' }}</span>
        <div class="problem-tags"><span v-for="tag in item.tagNames?.slice(0, 3)" :key="tag" class="problem-tag" :title="tag">{{ tag }}</span></div>
        <span class="year-value">{{ item.year || '—' }}</span>
        <span class="language-value">{{ item.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
        <el-tag :type="difficultyType(item.difficulty)" size="small" effect="plain">{{ difficultyLabel(item.difficulty) }}</el-tag>
        <div class="average-score"><el-icon><StarFilled /></el-icon><strong>{{ formatScore(item.averageScore) }}</strong></div>
        <el-icon class="row-arrow"><ArrowRight /></el-icon>
      </button>
    </div>

    <el-empty v-else-if="!loading" description="暂无符合条件的题目" />
    <div v-if="total > 0" class="pagination-wrap">
      <div class="page-size-control">
        <span>每页</span>
        <el-select
          v-model="pageSizeChoice"
          class="page-size-select"
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="输入数量"
          @change="changePageSize"
        >
          <el-option v-for="size in pageSizeOptions" :key="size" :label="String(size)" :value="size" />
        </el-select>
        <span>条</span>
      </div>
      <span class="pagination-total">共 {{ total }} 条 · {{ totalPages }} 页</span>
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" background layout="prev, pager, next, jumper" @current-change="fetchProblems" />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemList } from '@/api/problem'

const problems = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(5)
const pageSizeOptions = [5, 10, 15, 20]
const pageSizeChoice = ref(5)
const total = ref(0)
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
const query = ref({})
const sortBy = ref('')
const sortOrder = ref('')
const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatScore = (score) => Number(score) > 0 ? Number(score).toFixed(1) : '暂无评分'
const fetchProblems = async () => {
  loading.value = true
  try {
    const response = await getPublicProblemList({ page: page.value, pageSize: pageSize.value, ...query.value, ...(sortBy.value ? { sortBy: sortBy.value, sortOrder: sortOrder.value } : {}) })
    problems.value = response.data?.rows || []
    total.value = response.data?.total || 0
  } catch (error) {
    problems.value = []
    total.value = 0
    ElMessage.error(error.message || '获取题目列表失败')
  } finally { loading.value = false }
}
const updateQuery = (params) => {
  query.value = Object.fromEntries(Object.entries(params || {}).filter(([, value]) => value !== '' && value != null))
  page.value = 1
  fetchProblems()
}
const sortState = (field) => sortBy.value === field ? sortOrder.value : 'none'
const cycleSort = (field) => {
  if (sortBy.value !== field) { sortBy.value = field; sortOrder.value = 'desc' }
  else if (sortOrder.value === 'desc') sortOrder.value = 'asc'
  else { sortBy.value = ''; sortOrder.value = '' }
  page.value = 1
  fetchProblems()
}
const changePageSize = (value) => {
  const inputSize = Number(value)
  if (!Number.isInteger(inputSize) || inputSize < 1 || inputSize > 100) {
    pageSizeChoice.value = pageSize.value
    ElMessage.warning('每页条数请输入 1–100 的整数')
    return
  }
  pageSizeChoice.value = inputSize
  pageSize.value = inputSize
  page.value = 1
  fetchProblems()
}
onMounted(fetchProblems)
defineExpose({ updateQuery })
</script>

<style scoped>
.problem-list { min-height: 320px; }
.list-table { overflow: hidden; background: var(--lm-surface); border: 1px solid #dbe3ef; border-radius: 14px; box-shadow: 0 12px 35px rgba(30, 64, 175, 0.06); }
.table-head, .problem-row { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(150px, .65fr) minmax(180px, .8fr) 66px 58px 66px 76px 18px; align-items: center; gap: 10px; padding: 0 16px; }
.table-head > :nth-child(n + 4):nth-child(-n + 7) { text-align: center; }
.table-head { min-height: 42px; background: var(--lm-bg); border-bottom: 1px solid var(--lm-border); color: var(--lm-text-muted); font-size: 11px; font-weight: 700; }
.sortable-head { display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.sortable-head:hover { color: var(--lm-primary); }
.sort-indicator { display: inline-flex; flex-direction: column; align-items: center; justify-content: center; width: 9px; color: #b8c0cc; font-size: 7px; line-height: 6px; }
.sort-indicator.asc span:first-child { color: var(--lm-primary); }
.sort-indicator.desc span:last-child { color: var(--lm-primary); }
.problem-row { position: relative; width: 100%; min-height: 62px; border: 0; border-bottom: 1px solid var(--lm-border-light); background: transparent; color: inherit; text-align: left; cursor: pointer; transition: background var(--lm-transition), box-shadow var(--lm-transition); }
.problem-row::before { content: ''; position: absolute; inset: 14px auto 14px 0; width: 3px; border-radius: 0 3px 3px 0; background: #3b82f6; opacity: 0; transition: opacity var(--lm-transition); }
.problem-row:last-child { border-bottom: 0; }
.problem-row:hover { background: linear-gradient(90deg, #eff6ff, #f8fbff); box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.08); }
.problem-row:hover::before { opacity: 1; }
.problem-main { min-width: 0; }
.problem-main h3 { margin: 0; overflow: hidden; color: var(--lm-text-primary); font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.row-index { display: inline-block; min-width: 26px; margin-right: 9px; color: #3b82f6; font-size: 10px; font-weight: 800; letter-spacing: 0.05em; }
.contest-name { overflow: hidden; color: var(--lm-text-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.problem-tags { display: flex; align-items: center; gap: 5px; min-width: 0; overflow: hidden; }
.problem-tag { max-width: 82px; overflow: hidden; padding: 2px 7px; border-radius: 999px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.year-value, .language-value { text-align: center; color: var(--lm-text-secondary); font-size: 12px; }
.problem-row > .el-tag { justify-self: center; }
.average-score { display: flex; align-items: center; justify-content: center; gap: 5px; color: #f59e0b; }
.average-score strong { color: var(--lm-text-primary); font-size: 16px; }
.row-arrow { color: var(--lm-text-muted); transition: transform var(--lm-transition), color var(--lm-transition); }
.problem-row:hover .row-arrow { color: var(--lm-primary); transform: translateX(3px); }
.pagination-wrap { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 24px; }
.page-size-control { display: flex; align-items: center; gap: 7px; color: var(--lm-text-secondary); font-size: 12px; white-space: nowrap; }
.page-size-select { width: 92px; }
.pagination-total { color: var(--lm-text-secondary); font-size: 12px; }
@media (max-width: 1000px) { .table-head { display: none; } .problem-row { grid-template-columns: minmax(0, 1fr) 90px 70px 20px; gap: 12px; padding: 12px 16px; } .problem-row > .el-tag, .year-value, .language-value, .problem-tags { display: none; } }
@media (max-width: 760px) { .pagination-wrap { flex-wrap: wrap; gap: 10px 14px; } }
@media (max-width: 600px) { .problem-row { grid-template-columns: minmax(0, 1fr) 60px 18px; } .contest-name { display: none; } }
</style>
