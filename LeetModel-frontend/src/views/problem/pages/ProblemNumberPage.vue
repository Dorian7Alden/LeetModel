<template>
  <div class="problem-number-page">
    <div class="page-top-nav">
      <button type="button" class="back-link-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <span class="nav-sep">/</span>
      <span class="current-number-crumb">{{ numberLabel }} 专题</span>
    </div>

    <div class="number-two-col-layout">
      <aside class="number-left-sidebar">
        <section class="number-profile-card">
          <div class="profile-header">
            <span class="number-flag-badge">PROBLEM NUMBER</span>
            <span class="number-heat-tag">赛事内完整赛题</span>
          </div>
          <h1 class="number-name">{{ numberLabel }}</h1>
          <p class="number-description">按赛事内题号聚合完整赛题，适合制定只练 A 题、B 题等专项训练策略。</p>
          <div class="number-rule-panel">
            <div class="panel-subtitle">题号分类规则</div>
            <p>标准赛事题号覆盖 A 至 F；无法明确归入字母题号的自定义或历史题目归入 X。</p>
          </div>
          <div class="number-stat-card">
            <strong>{{ problemTotal }}</strong>
            <span>收录题目</span>
          </div>
        </section>
      </aside>

      <main class="number-right-main">
        <ProblemHeader
          :contests="allContests"
          :tags="allTags"
          :problem-numbers="problemNumbers"
          :total="problemTotal"
          :show-contest-cards="false"
          :show-advanced-filters="false"
          :hide-year-sort="true"
          :fixed-problem-number="problemNumber"
          @change="handleSearch"
          @random="handleRandom"
          @sort="handleSort"
        />
        <ProblemList
          ref="listRef"
          :tags="allTags"
          :initial-query="numberQuery"
          :group-by-year="true"
          :show-contest="true"
          :show-problem-number="false"
          @fav-change="handleFavChange"
          @total-change="handleTotalChange"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import ProblemHeader from '../components/ProblemHeader.vue'
import ProblemList from '../components/ProblemList.vue'

const route = useRoute()
const router = useRouter()
const workbench = inject('problemWorkbench', null)
const problemNumber = computed(() => String(route.params.problemNumber || 'X').toUpperCase())
const problemNumbers = ['A', 'B', 'C', 'D', 'E', 'F', 'X']
const numberLabel = computed(() => `${problemNumber.value} 题`)
const allContests = ref([])
const allTags = ref([])
const problemTotal = ref(0)
const listRef = ref(null)

const handleFavChange = (count) => {
  workbench?.updateFavCount?.(count)
}

const routeFilterQuery = () => {
  const params = {}
  if (route.query.keyword) params.keyword = String(route.query.keyword)
  if (route.query.contestId) params.contestId = Number(route.query.contestId)
  if (route.query.year) params.year = Number(route.query.year)
  if (route.query.difficulty) params.difficulty = Number(route.query.difficulty)
  if (route.query.statementLanguage) params.statementLanguage = String(route.query.statementLanguage)
  if (route.query.minScore !== undefined) params.minAverageScore = Number(route.query.minScore)
  if (route.query.maxScore !== undefined) params.maxAverageScore = Number(route.query.maxScore)
  if (route.query.tagIds) {
    params.tagIds = String(route.query.tagIds).split(',').map(Number).filter(Boolean)
  }
  return params
}

const numberQuery = computed(() => ({
  ...routeFilterQuery(),
  problemNumber: problemNumber.value,
}))

const syncMetadata = () => {
  if (workbench?.filterOptions?.tags?.length) {
    allContests.value = workbench.filterOptions.contests || []
    allTags.value = workbench.filterOptions.tags || []
    return true
  }
  return false
}

const handleTotalChange = (value) => { problemTotal.value = Number(value) || 0 }

// 同一组件实例在侧边栏切换 A/B/C 等题号时不会重新挂载，显式刷新固定题号查询。
watch(problemNumber, (value) => {
  listRef.value?.updateQuery({ ...routeFilterQuery(), problemNumber: value })
})

const handleSearch = (params) => {
  listRef.value?.updateQuery({ ...params, problemNumber: problemNumber.value })
  const nextQuery = {}
  if (params.keyword?.trim()) nextQuery.keyword = params.keyword.trim()
  if (params.contestId) nextQuery.contestId = params.contestId
  if (params.year) nextQuery.year = params.year
  if (params.difficulty) nextQuery.difficulty = params.difficulty
  if (params.statementLanguage) nextQuery.statementLanguage = params.statementLanguage
  if (params.minAverageScore != null) nextQuery.minScore = params.minAverageScore
  if (params.maxAverageScore != null) nextQuery.maxScore = params.maxAverageScore
  if (Array.isArray(params.tagIds) && params.tagIds.length) nextQuery.tagIds = params.tagIds.join(',')
  router.replace({ query: nextQuery })
}

const handleSort = (field) => {
  if (field === 'clear') listRef.value?.clearSort()
  else listRef.value?.cycleSort(field)
}

const handleRandom = async (params = {}) => {
  try {
    const response = await getRandomPublicProblem({ ...params, problemNumber: problemNumber.value })
    if (response.data?.id) await router.push({ path: `/problem/${response.data.id}`, query: route.query })
  } catch (error) {
    ElMessage.error(error.message || '暂时没有符合条件的题目')
  }
}

const goBack = () => {
  if (window.history.state?.back) router.back()
  else router.push('/problem')
}

onMounted(async () => {
  if (syncMetadata()) return
  try {
    const response = await getPublicProblemFilterOptions()
    allContests.value = response.data?.contests || []
    allTags.value = response.data?.tags || []
  } catch {
    allContests.value = []
    allTags.value = []
  }
})
</script>

<style scoped>
.problem-number-page { width: 100%; padding-bottom: 32px; }
.page-top-nav { display: flex; align-items: center; gap: 8px; margin-bottom: 18px; font-size: 13px; }
.back-link-btn { display: inline-flex; align-items: center; gap: 4px; border: 0; background: transparent; color: var(--lm-primary); font-size: 13px; font-weight: 600; cursor: pointer; padding: 4px 8px; border-radius: var(--lm-radius-sm); }
.back-link-btn:hover { background: var(--lm-primary-bg); }
.nav-sep { color: var(--lm-border); }
.current-number-crumb { color: var(--lm-text-muted); }
.number-two-col-layout { display: grid; grid-template-columns: 350px 1fr; gap: 32px; align-items: start; }
.number-left-sidebar { position: sticky; top: 72px; max-height: calc(100vh - 88px); overflow-y: auto; }
.number-profile-card { display: flex; flex-direction: column; gap: 14px; padding: 18px 18px 20px; background: #fff; border: 1px solid var(--lm-border); border-radius: var(--lm-radius); box-shadow: 0 1px 4px rgba(0,0,0,.03); }
.profile-header { display: flex; justify-content: space-between; align-items: center; }
.number-flag-badge { padding: 3px 8px; border-radius: var(--lm-radius-sm); background: #18181b; color: #fff; font: 800 11px var(--lm-code-font-family); letter-spacing: .04em; }
.number-heat-tag { color: var(--lm-text-secondary); font-size: 11px; font-weight: 600; }
.number-name { margin: 0; color: var(--lm-text-primary); font-size: 24px; }
.number-description, .number-rule-panel p { margin: 0; color: var(--lm-text-secondary); font-size: 12px; line-height: 1.65; }
.panel-subtitle { margin-bottom: 7px; color: var(--lm-text-muted); font-size: 11px; font-weight: 700; }
.number-rule-panel { padding-top: 14px; border-top: 1px dashed var(--lm-border); }
.number-stat-card { display: flex; align-items: baseline; gap: 7px; padding: 12px; border: 1px solid var(--lm-border); border-radius: var(--lm-radius-sm); }
.number-stat-card strong { color: var(--lm-primary); font: 700 22px var(--lm-code-font-family); }
.number-stat-card span { color: var(--lm-text-muted); font-size: 11px; }
.number-right-main { min-width: 0; }
@media (max-width: 960px) { .number-two-col-layout { grid-template-columns: 1fr; } .number-left-sidebar { position: static; max-height: none; overflow: visible; } }
</style>
