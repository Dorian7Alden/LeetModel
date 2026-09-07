<template>
  <section class="problem-filter">
    <!-- 1. 赛事卡片单行横向排布（高度135px，一行3个拉满，左右按钮在容器宽度以内且尺寸较小） -->
    <div class="contest-carousel-wrap">
      <button type="button" class="scroll-arrow left" aria-label="向左滚动" @click="scrollCards('left')">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      
      <div ref="cardsScrollRef" class="contest-cards-track">
        <div
          v-for="contest in sortedContestCards"
          :key="contest.id"
          class="contest-card"
          :class="{ active: filters.contestId === contest.id }"
          @click="selectContestCard(contest.id)"
        >
          <div class="contest-card-top">
            <span class="contest-code-badge">{{ contest.code }}</span>
            <span class="contest-heat-tag">{{ contest.heatTag }}</span>
          </div>
          <div class="contest-card-name" :title="contest.name">{{ contest.name }}</div>
          <div class="contest-card-footer">
            <span class="contest-stat-text">{{ contest.statsText }}</span>
            <el-icon class="contest-arrow-icon"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <button type="button" class="scroll-arrow right" aria-label="向右滚动" @click="scrollCards('right')">
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>

    <!-- 2. 全维度胶囊筛选控制栏（搜索框放最前面，随机为图标，全维度均为下拉胶囊） -->
    <div class="filter-control-panel">
      <div class="compact-filter-bar">
        <div class="dropdown-group">
          <!-- 搜索框作为首个胶囊 -->
          <el-input
            v-model="filters.keyword"
            placeholder="搜索题目标题..."
            :prefix-icon="Search"
            clearable
            class="filter-search-pill"
            @keyup.enter="emitChange"
            @clear="emitChange"
          />

          <!-- 年份下拉 -->
          <el-select
            v-model="filters.year"
            placeholder="年份"
            clearable
            class="filter-select select-year"
            @change="emitChange"
          >
            <el-option label="全部年份" :value="null" />
            <el-option v-for="year in recentYears" :key="year" :label="`${year} 年`" :value="year" />
          </el-select>

          <!-- 语言下拉 -->
          <el-select
            v-model="filters.statementLanguage"
            placeholder="语言"
            clearable
            class="filter-select select-lang"
            @change="emitChange"
          >
            <el-option v-for="item in languageOptions" :key="String(item.value)" :label="item.label === '全部' ? '全部语言' : item.label" :value="item.value" />
          </el-select>

          <!-- 难度下拉 -->
          <el-select
            v-model="filters.difficulty"
            placeholder="难度"
            clearable
            class="filter-select select-diff"
            @change="emitChange"
          >
            <el-option v-for="item in difficultyOptions" :key="String(item.value)" :label="item.label === '全部' ? '全部难度' : item.label" :value="item.value" />
          </el-select>

          <!-- 题目类型下拉 -->
          <el-select
            v-model="filters.selectedTags.PROBLEM_TYPE"
            placeholder="题目类型"
            clearable
            class="filter-select select-type"
            @change="emitChange"
          >
            <el-option label="全部题型" :value="null" />
            <el-option v-for="tag in problemTypeTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 背景领域下拉 -->
          <el-select
            v-model="filters.selectedTags.BACKGROUND_DOMAIN"
            placeholder="背景领域"
            clearable
            class="filter-select select-domain"
            @change="emitChange"
          >
            <el-option label="全部领域" :value="null" />
            <el-option v-for="tag in domainTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 模型算法下拉 -->
          <el-select
            v-model="selectedAlgorithmId"
            placeholder="模型算法"
            clearable
            class="filter-select select-algo"
            @change="changeAlgorithm"
          >
            <el-option label="全部模型算法" :value="null" />
            <el-option v-for="tag in algorithmTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 平均分段下拉 -->
          <el-select
            v-model="selectedScoreLabel"
            placeholder="平均分"
            clearable
            class="filter-select select-score"
            @change="changeScoreRange"
          >
            <el-option v-for="item in scoreOptions" :key="item.label" :label="item.label === '全部' ? '全部分数' : item.label" :value="item.label" />
          </el-select>

          <!-- 随机抽题图标按钮 -->
          <el-tooltip content="按当前条件随机抽取一题" placement="top">
            <button
              type="button"
              class="icon-action-btn random-btn"
              :disabled="randomLoading"
              aria-label="随机抽题"
              @click="emit('random', buildParams())"
            >
              <el-icon v-if="!randomLoading"><Compass /></el-icon>
              <el-icon v-else class="is-loading"><Loading /></el-icon>
            </button>
          </el-tooltip>

          <!-- 重置清空图标按钮（圆形图标） -->
          <el-tooltip v-if="selectedConditions.length" content="清空所有筛选条件" placement="top">
            <button
              type="button"
              class="icon-action-btn reset-btn"
              aria-label="清空筛选"
              @click="reset"
            >
              <el-icon><Refresh /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check, Compass, Loading, Refresh, Search } from '@element-plus/icons-vue'

const props = defineProps({
  contests: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  optionsLoading: { type: Boolean, default: false },
  randomLoading: { type: Boolean, default: false },
})
const emit = defineEmits(['change', 'random'])
const router = useRouter()
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
const selectedAlgorithmId = ref(null)
const selectedScoreLabel = ref('')
const cardsScrollRef = ref(null)

// 分类标签提取
const problemTypeTags = computed(() => props.tags.filter(t => t.type === 'PROBLEM_TYPE'))
const domainTags = computed(() => props.tags.filter(t => t.type === 'BACKGROUND_DOMAIN'))
const algorithmTags = computed(() => props.tags.filter(t => t.type === 'MODEL_ALGORITHM'))

// 赛事专题卡片：底层按照参赛人数降序排列，最活跃排第一
const sortedContestCards = computed(() => {
  const list = []
  for (const c of props.contests) {
    let code = 'CUMCM'
    let participants = 152000 // 默认参赛热度权重
    let heatTag = '🔥 15w+ 人在练'
    let statsText = '国赛 · 每年 5 题 · 全国规模最大'
    if (c.name.includes('美国') || c.name.toLowerCase().includes('mcm')) {
      code = 'MCM/ICM'
      participants = 86000
      heatTag = '🔥 8.6w+ 人在练'
      statsText = '美赛 · 每年 6 题 · 全英文跨学科'
    } else if (c.name.includes('力模')) {
      code = 'LEETMODEL'
      participants = 34000
      heatTag = '⭐ 3.4w+ 人在练'
      statsText = '官方赛 · 全真演练与高保真模拟'
    }
    list.push({
      id: c.id,
      code,
      name: c.name,
      participants,
      heatTag,
      statsText
    })
  }

  // 按参与人数从大到小排序（最活跃排第一）
  list.sort((a, b) => b.participants - a.participants)
  return list
})

const selectContestCard = (contestId) => {
  if (contestId) {
    router.push(`/problem/contest/${contestId}`)
  }
}

const scrollCards = (direction) => {
  if (!cardsScrollRef.value) return
  const card = cardsScrollRef.value.querySelector('.contest-card')
  const cardWidth = card ? card.offsetWidth : 300
  const gap = 16
  const step = cardWidth + gap
  cardsScrollRef.value.scrollBy({ left: direction === 'left' ? -step : step, behavior: 'smooth' })
}

const changeAlgorithm = (val) => {
  filters.selectedTags.MODEL_ALGORITHM = val ? [val] : []
  emitChange()
}

const changeScoreRange = (label) => {
  const opt = scoreOptions.find(s => s.label === label)
  if (opt) {
    filters.minAverageScore = opt.min
    filters.maxAverageScore = opt.max
  } else {
    filters.minAverageScore = null
    filters.maxAverageScore = null
  }
  emitChange()
}

const isScoreActive = (item) => filters.minAverageScore === item.min && filters.maxAverageScore === item.max
const selectedConditions = computed(() => {
  const items = []
  if (filters.keyword.trim()) items.push({ key: 'keyword', label: `关键词：${filters.keyword.trim()}`, field: 'keyword', empty: '' })
  const contest = props.contests.find((item) => item.id === filters.contestId)
  if (contest) items.push({ key: 'contestId', label: contest.name, field: 'contestId', empty: null })
  if (filters.year) items.push({ key: 'year', label: `${filters.year} 年`, field: 'year', empty: null })
  if (filters.statementLanguage) items.push({ key: 'statementLanguage', label: filters.statementLanguage === 'EN' ? '英文' : '中文', field: 'statementLanguage', empty: '' })
  if (filters.difficulty != null) items.push({ key: 'difficulty', label: difficultyOptions.find((item) => item.value === filters.difficulty).label, field: 'difficulty', empty: null })
  
  // 标签提取
  if (filters.selectedTags.PROBLEM_TYPE) {
    const tag = props.tags.find(t => t.id === filters.selectedTags.PROBLEM_TYPE)
    if (tag) items.push({ key: `PROBLEM_TYPE-${tag.id}`, label: tag.name, tagType: 'PROBLEM_TYPE', tagId: tag.id })
  }
  if (filters.selectedTags.BACKGROUND_DOMAIN) {
    const tag = props.tags.find(t => t.id === filters.selectedTags.BACKGROUND_DOMAIN)
    if (tag) items.push({ key: `BACKGROUND_DOMAIN-${tag.id}`, label: tag.name, tagType: 'BACKGROUND_DOMAIN', tagId: tag.id })
  }
  if (Array.isArray(filters.selectedTags.MODEL_ALGORITHM)) {
    for (const tagId of filters.selectedTags.MODEL_ALGORITHM) {
      const tag = props.tags.find(t => t.id === tagId)
      if (tag) items.push({ key: `MODEL_ALGORITHM-${tag.id}`, label: tag.name, tagType: 'MODEL_ALGORITHM', tagId: tag.id })
    }
  }

  const score = scoreOptions.find((item) => isScoreActive(item))
  if (score && (score.min != null || score.max != null)) items.push({ key: 'averageScore', label: `平均分：${score.label}`, score: true })
  return items
})
const buildParams = () => ({ keyword: filters.keyword, contestId: filters.contestId, difficulty: filters.difficulty, year: filters.year, statementLanguage: filters.statementLanguage, minAverageScore: filters.minAverageScore, maxAverageScore: filters.maxAverageScore, tagIds: Object.values(filters.selectedTags).flatMap((value) => Array.isArray(value) ? value : [value]).filter(Boolean) })
const emitChange = () => emit('change', buildParams())
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
  selectedAlgorithmId.value = null
  selectedScoreLabel.value = ''
  emitChange()
}
</script>

<style scoped>
.problem-filter { margin-bottom: 18px; margin-top: 6px; }

/* 赛事卡片：高度 135px，单行横滑，左右切换按钮在容器宽度以内且尺寸较小 */
.contest-carousel-wrap {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  margin-bottom: 18px;
}
.contest-cards-track {
  display: flex;
  align-items: center;
  gap: 16px;
  overflow-x: auto;
  scroll-behavior: smooth;
  width: 100%;
  padding: 4px 0 2px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.contest-cards-track::-webkit-scrollbar {
  display: none;
}
.scroll-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 1px solid var(--lm-border);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(6px);
  color: var(--lm-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: opacity var(--lm-transition), transform var(--lm-transition), background var(--lm-transition), box-shadow var(--lm-transition);
  z-index: 10;
  opacity: 0; /* 默认隐藏 */
  pointer-events: none;
}
/* 鼠标放到整个矩形区域行才显现切换按钮 */
.contest-carousel-wrap:hover .scroll-arrow {
  opacity: 1;
  pointer-events: auto;
}
.scroll-arrow:hover {
  color: var(--lm-primary);
  border-color: var(--lm-primary);
  background: #ffffff;
  box-shadow: 0 3px 10px rgba(37, 99, 235, 0.18);
  transform: translateY(-50%) scale(1.05);
}
.scroll-arrow.left { left: 8px; }
.scroll-arrow.right { right: 8px; }

.contest-card {
  /* 1行只展示3个卡片，左中右，间隔16px，宽度拉满，高度 135px */
  flex: 0 0 calc((100% - 32px) / 3);
  width: calc((100% - 32px) / 3);
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 135px;
  padding: 16px 20px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  cursor: pointer;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition), border-color var(--lm-transition);
}
.contest-card:hover {
  transform: translateY(-2px);
  border-color: var(--lm-primary-light);
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.07);
}
.contest-card.active {
  background: var(--lm-primary-bg);
  border-color: var(--lm-primary);
  box-shadow: 0 0 0 1px var(--lm-primary);
}
.contest-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.contest-code-badge {
  padding: 3px 8px;
  border-radius: var(--lm-radius-sm);
  background: #f1f5f9;
  color: var(--lm-text-secondary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-family: ui-monospace, monospace;
}
.contest-card.active .contest-code-badge {
  background: var(--lm-primary);
  color: #ffffff;
}
.contest-heat-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-text-secondary);
}
.contest-card-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--lm-text-primary);
  line-height: 1.4;
  margin: 6px 0;
}
.contest-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 6px;
  border-top: 1px dashed #f1f5f9;
}
.contest-stat-text {
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.contest-check-icon {
  color: var(--lm-primary);
  font-size: 15px;
  font-weight: 700;
}
.contest-arrow-icon {
  color: #cbd5e1;
  font-size: 14px;
  transition: transform var(--lm-transition), color var(--lm-transition);
}
.contest-card:hover .contest-arrow-icon {
  color: var(--lm-primary);
  transform: translateX(3px);
}

/* 全维度胶囊筛选控制面板 */
.filter-control-panel {
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  overflow: visible;
  padding: 0;
}
.compact-filter-bar {
  display: flex;
  align-items: center;
  padding: 4px 0;
  gap: 8px;
  flex-wrap: nowrap; /* 绝对不换行，压缩在单行 */
  overflow-x: auto;
  scrollbar-width: none;
}
.compact-filter-bar::-webkit-scrollbar { display: none; }
.dropdown-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  flex-shrink: 0;
}

/* 搜索框胶囊样式 */
.filter-search-pill {
  width: 145px;
}
.filter-search-pill :deep(.el-input__wrapper) {
  border-radius: 9999px !important;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  padding: 2px 10px;
  height: 34px;
  transition: all var(--lm-transition);
}
.filter-search-pill :deep(.el-input__wrapper:hover) {
  border-color: var(--lm-primary-light);
  background: #f8fbff;
}
.filter-search-pill :deep(.el-input__wrapper.is-focus) {
  border-color: var(--lm-primary) !important;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12) !important;
}

/* 下拉框胶囊样式 */
.filter-select {
  width: 98px;
}
.filter-select.select-year { width: 94px; }
.filter-select.select-lang { width: 90px; }
.filter-select.select-diff { width: 90px; }
.filter-select.select-type { width: 104px; }
.filter-select.select-domain { width: 104px; }
.filter-select.select-algo { width: 108px; }
.filter-select.select-score { width: 100px; }

.filter-select :deep(.el-select__wrapper) {
  border-radius: 9999px !important;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  padding: 2px 10px !important;
  min-height: 34px;
  transition: all var(--lm-transition);
}
.filter-select :deep(.el-select__wrapper:hover) {
  border-color: var(--lm-primary-light);
  background: #f8fbff;
}
.filter-select :deep(.el-select__wrapper.is-focused) {
  border-color: var(--lm-primary) !important;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12) !important;
  background: #ffffff;
}
.filter-select :deep(.el-select__placeholder) {
  color: var(--lm-text-secondary);
  font-size: 12px;
  font-weight: 500;
}
.filter-select :deep(.el-select__selected-item) {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-primary);
}

/* 圆形图标操作按钮（随机抽题与清空重置） */
.icon-action-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: var(--lm-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--lm-transition);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  flex-shrink: 0;
}
.icon-action-btn:hover {
  color: var(--lm-primary);
  border-color: var(--lm-primary);
  background: var(--lm-primary-bg);
}
.icon-action-btn.random-btn:hover {
  transform: rotate(30deg);
}
.icon-action-btn.reset-btn {
  border-color: #fecaca;
  color: #ef4444;
  background: #fef2f2;
}
.icon-action-btn.reset-btn:hover {
  color: var(--lm-danger);
  border-color: var(--lm-danger);
  background: #fee2e2;
  transform: rotate(-30deg);
}

@media (max-width: 900px) {
  .contest-card { flex: 0 0 85%; width: 85%; }
  .scroll-arrow.left { left: 4px; }
  .scroll-arrow.right { right: 4px; }
  .filter-search-pill { width: 100%; }
}
</style>
