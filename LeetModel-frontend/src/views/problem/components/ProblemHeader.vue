<template>
  <section class="problem-filter">
    <div class="filter-heading">
      <div class="heading-copy"><h1>题库</h1><p>选择条件，快速找到适合的建模题目</p></div>
      <div class="heading-actions">
        <el-input v-model="filters.keyword" placeholder="搜索题目标题" :prefix-icon="Search" clearable class="keyword-input" @keyup.enter="emitChange" @clear="emitChange" />
        <el-button type="primary" :icon="Search" @click="emitChange">搜索</el-button>
        <el-button plain :loading="randomLoading" @click="handleRandom">随机一题</el-button>
      </div>
    </div>

    <div class="filter-options" v-loading="optionsLoading">
      <div class="option-row">
        <span class="option-label">赛事</span>
        <div class="option-list">
          <button class="option-item" :class="{ active: filters.contestId == null }" @click="selectContest(null)">全部</button>
          <button v-for="contest in contests" :key="contest.id" class="option-item" :class="{ active: filters.contestId === contest.id }" @click="selectContest(contest.id)">{{ contest.name }}</button>
        </div>
      </div>

      <div class="option-row">
        <span class="option-label">年份</span>
        <div class="option-list">
          <button class="option-item" :class="{ active: filters.year == null }" @click="selectValue('year', null)">全部</button>
          <button v-for="year in recentYears" :key="year" class="option-item" :class="{ active: filters.year === year }" @click="selectValue('year', year)">{{ year }}</button>
        </div>
      </div>

      <div class="option-row">
        <span class="option-label">语言</span>
        <div class="option-list">
          <button v-for="item in languageOptions" :key="String(item.value)" class="option-item" :class="{ active: filters.statementLanguage === item.value }" @click="selectValue('statementLanguage', item.value)">{{ item.label }}</button>
        </div>
      </div>

      <div class="option-row">
        <span class="option-label">难度</span>
        <div class="option-list">
          <button v-for="item in difficultyOptions" :key="String(item.value)" class="option-item" :class="{ active: filters.difficulty === item.value }" @click="selectValue('difficulty', item.value)">{{ item.label }}</button>
        </div>
      </div>

      <div v-for="group in tagGroups" :key="group.type" class="option-row">
        <span class="option-label">{{ group.label }}</span>
        <div class="option-list">
          <button class="option-item" :class="{ active: isTagActive(group.type, null) }" @click="selectTag(group.type, null)">全部</button>
          <button v-for="tag in group.tags" :key="tag.id" class="option-item" :class="{ active: isTagActive(group.type, tag.id) }" @click="selectTag(group.type, tag.id)">{{ tag.name }}</button>
        </div>
      </div>

      <div class="option-row">
        <span class="option-label">平均分</span>
        <div class="option-list">
          <button v-for="item in scoreOptions" :key="item.label" class="option-item" :class="{ active: isScoreActive(item) }" @click="selectScore(item)">{{ item.label }}</button>
        </div>
      </div>

    </div>

    <div class="selected-bar">
      <span class="selected-label">已选条件</span>
      <button v-for="item in selectedConditions" :key="item.key" class="selected-chip" @click="removeCondition(item)">{{ item.label }}<el-icon><Close /></el-icon></button>
      <span v-if="!selectedConditions.length" class="empty-selection">暂未选择筛选条件</span>
      <button v-if="selectedConditions.length" class="clear-button" @click="reset"><el-icon><Refresh /></el-icon>清空</button>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Close, Refresh, Search } from '@element-plus/icons-vue'

const props = defineProps({ contests: { type: Array, default: () => [] }, tags: { type: Array, default: () => [] }, optionsLoading: { type: Boolean, default: false } })
const emit = defineEmits(['change', 'random'])
const currentYear = new Date().getFullYear()
const recentYears = Array.from({ length: 5 }, (_, index) => currentYear - index)
const languageOptions = [{ label: '全部', value: '' }, { label: '中文', value: 'ZH' }, { label: '英文', value: 'EN' }]
const difficultyOptions = [{ label: '全部', value: null }, { label: '简单', value: 1 }, { label: '中等', value: 2 }, { label: '困难', value: 3 }]
const scoreOptions = [
  { label: '全部', min: null, max: null },
  { label: '60 分以下', min: null, max: 59.9 },
  { label: '60–79 分', min: 60, max: 79.9 },
  { label: '80–89 分', min: 80, max: 89.9 },
  { label: '90 分以上', min: 90, max: null },
]
const filters = reactive({ keyword: '', contestId: null, difficulty: null, year: null, statementLanguage: '', minAverageScore: null, maxAverageScore: null, selectedTags: { BACKGROUND_DOMAIN: null, PROBLEM_TYPE: null, MODEL_ALGORITHM: [] } })
const randomLoading = ref(false)
const tagGroups = computed(() => [
  { type: 'BACKGROUND_DOMAIN', label: '背景领域', tags: props.tags.filter((tag) => tag.type === 'BACKGROUND_DOMAIN') },
  { type: 'PROBLEM_TYPE', label: '题目类型', tags: props.tags.filter((tag) => tag.type === 'PROBLEM_TYPE') },
  { type: 'MODEL_ALGORITHM', label: '模型算法', tags: props.tags.filter((tag) => tag.type === 'MODEL_ALGORITHM') },
])
const isScoreActive = (item) => filters.minAverageScore === item.min && filters.maxAverageScore === item.max
const selectedConditions = computed(() => {
  const items = []
  if (filters.keyword.trim()) items.push({ key: 'keyword', label: `关键词：${filters.keyword.trim()}`, field: 'keyword', empty: '' })
  const contest = props.contests.find((item) => item.id === filters.contestId)
  if (contest) items.push({ key: 'contestId', label: contest.name, field: 'contestId', empty: null })
  if (filters.year) items.push({ key: 'year', label: `${filters.year} 年`, field: 'year', empty: null })
  if (filters.statementLanguage) items.push({ key: 'statementLanguage', label: filters.statementLanguage === 'EN' ? '英文' : '中文', field: 'statementLanguage', empty: '' })
  if (filters.difficulty != null) items.push({ key: 'difficulty', label: difficultyOptions.find((item) => item.value === filters.difficulty).label, field: 'difficulty', empty: null })
  for (const group of tagGroups.value) {
    const selectedIds = Array.isArray(filters.selectedTags[group.type]) ? filters.selectedTags[group.type] : [filters.selectedTags[group.type]]
    for (const tagId of selectedIds.filter(Boolean)) {
      const tag = group.tags.find((item) => item.id === tagId)
      if (tag) items.push({ key: `${group.type}-${tag.id}`, label: tag.name, tagType: group.type, tagId: tag.id })
    }
  }
  const score = scoreOptions.find((item) => isScoreActive(item))
  if (score && (score.min != null || score.max != null)) items.push({ key: 'averageScore', label: `平均分：${score.label}`, score: true })
  return items
})
const buildParams = () => ({ keyword: filters.keyword, contestId: filters.contestId, difficulty: filters.difficulty, year: filters.year, statementLanguage: filters.statementLanguage, minAverageScore: filters.minAverageScore, maxAverageScore: filters.maxAverageScore, tagIds: Object.values(filters.selectedTags).flatMap((value) => Array.isArray(value) ? value : [value]).filter(Boolean) })
const emitChange = () => emit('change', buildParams())
const selectContest = (value) => { filters.contestId = value; emitChange() }
const selectValue = (field, value) => { filters[field] = value; emitChange() }
const isTagActive = (type, value) => {
  const selected = filters.selectedTags[type]
  return Array.isArray(selected) ? (value == null ? selected.length === 0 : selected.includes(value)) : selected === value
}
const selectTag = (type, value) => {
  if (type !== 'MODEL_ALGORITHM') filters.selectedTags[type] = value
  else if (value == null) filters.selectedTags[type] = []
  else {
    const selected = filters.selectedTags[type]
    filters.selectedTags[type] = selected.includes(value) ? selected.filter((id) => id !== value) : [...selected, value]
  }
  emitChange()
}
const selectScore = (item) => { filters.minAverageScore = item.min; filters.maxAverageScore = item.max; emitChange() }
const removeCondition = (item) => {
  if (item.tagType === 'MODEL_ALGORITHM') filters.selectedTags[item.tagType] = filters.selectedTags[item.tagType].filter((id) => id !== item.tagId)
  else if (item.tagType) filters.selectedTags[item.tagType] = null
  else if (item.score) { filters.minAverageScore = null; filters.maxAverageScore = null }
  else filters[item.field] = item.empty
  emitChange()
}
const reset = () => {
  Object.assign(filters, { keyword: '', contestId: null, difficulty: null, year: null, statementLanguage: '', minAverageScore: null, maxAverageScore: null })
  filters.selectedTags.BACKGROUND_DOMAIN = null
  filters.selectedTags.PROBLEM_TYPE = null
  filters.selectedTags.MODEL_ALGORITHM = []
  emitChange()
}
const handleRandom = async () => { randomLoading.value = true; try { await emit('random', buildParams()) } finally { randomLoading.value = false } }
</script>

<style scoped>
.problem-filter { margin-bottom: 14px; }
.filter-heading { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 12px; }
.heading-copy { display: flex; align-items: baseline; gap: 12px; }
.filter-heading h1 { margin: 0; font-size: 27px; letter-spacing: -0.03em; color: var(--lm-text-primary); }
.filter-heading p { margin: 0; font-size: 12px; color: var(--lm-text-muted); }
.heading-actions { display: flex; align-items: center; gap: 8px; }
.keyword-input { width: 280px; }
.filter-options { overflow: hidden; background: var(--lm-surface); border: 1px solid #dbe3ef; border-radius: 14px; box-shadow: 0 12px 35px rgba(30, 64, 175, 0.07); }
.option-row { display: grid; grid-template-columns: 76px 1fr; min-height: 38px; border-bottom: 1px solid var(--lm-border-light); }
.option-row:last-child { border-bottom: 0; }
.option-label { display: flex; align-items: center; justify-content: center; padding: 10px 8px; background: linear-gradient(90deg, #f1f5f9, #f8fafc); border-right: 1px solid var(--lm-border-light); font-size: 11px; font-weight: 700; color: #475569; text-align: center; }
.option-list { display: flex; align-items: center; flex-wrap: wrap; gap: 2px 6px; padding: 4px 10px; }
.option-item { padding: 3px 9px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: #475569; font: inherit; font-size: 12px; line-height: 20px; cursor: pointer; transition: color var(--lm-transition), background var(--lm-transition), border-color var(--lm-transition), box-shadow var(--lm-transition); }
.option-item:hover { color: var(--lm-primary); background: var(--lm-primary-bg); }
.option-item.active { color: #fff; border-color: #2563eb; background: linear-gradient(135deg, #2563eb, #3b82f6); font-weight: 600; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.22); }
.selected-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; min-height: 42px; margin-top: 10px; padding: 6px 10px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 10px; }
.selected-label { margin-right: 4px; font-size: 12px; font-weight: 700; color: var(--lm-text-secondary); }
.selected-chip, .clear-button { display: inline-flex; align-items: center; gap: 4px; border: 0; cursor: pointer; font: inherit; }
.selected-chip { padding: 4px 10px; border: 1px solid #d8dee8; border-radius: 999px; background: #f1f3f6; color: #475569; font-size: 12px; }
.selected-chip:hover { border-color: #94a3b8; background: #e5e7eb; color: #1e293b; }
.empty-selection { color: #a1a9b5; font-size: 12px; }
.clear-button { margin-left: auto; padding: 5px 8px; background: transparent; color: var(--lm-text-muted); font-size: 12px; }
.clear-button:hover { color: var(--lm-primary); }
@media (max-width: 900px) { .filter-heading { align-items: flex-start; flex-direction: column; } .heading-actions { width: 100%; flex-wrap: wrap; } .keyword-input { flex: 1; min-width: 220px; } }
@media (max-width: 600px) { .filter-heading h1 { font-size: 22px; } .option-row { grid-template-columns: 72px 1fr; } .option-label { padding: 14px 10px; } .option-list { padding: 8px; gap: 3px; } .option-item { padding: 5px 7px; font-size: 12px; } .keyword-input { flex-basis: 100%; } .clear-button { margin-left: 0; } }
</style>
