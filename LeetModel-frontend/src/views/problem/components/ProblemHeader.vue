<template>
  <section class="problem-filter">
    <!-- 1. 赛事卡片单行横向排布（高度135px，一行3个拉满，左右按钮在容器宽度以内且尺寸较小） -->
    <div v-if="showContestCards" class="contest-carousel-wrap">
      <button v-if="canScrollLeft" type="button" class="scroll-arrow left" aria-label="向左滚动" @click="scrollCards('left')">
        <ChevronLeft :size="16" :stroke-width="1.8" />
      </button>
      
      <div ref="cardsScrollRef" class="contest-cards-track">
        <div
          v-for="contest in sortedContestCards"
          :key="contest.id"
          class="contest-card"
          :class="[`contest-card--${contest.visualVariant}`, { active: filters.contestId === contest.id }]"
          :style="contest.themeStyle"
          @click="selectContestCard(contest.id)"
        >
          <div class="contest-card-top">
            <span class="contest-code-badge">{{ contest.code }}</span>
            <span class="contest-heat-tag">{{ contest.heatTag }}</span>
          </div>
          <div class="contest-card-name" :title="contest.name">{{ contest.name }}</div>
          <div class="contest-card-footer">
            <span class="contest-stat-text">{{ contest.statsText }}</span>
            <ChevronRight class="contest-arrow-icon" :size="14" :stroke-width="1.8" />
          </div>
        </div>
      </div>

      <button v-if="canScrollRight" type="button" class="scroll-arrow right" aria-label="向右滚动" @click="scrollCards('right')">
        <ChevronRight :size="16" :stroke-width="1.8" />
      </button>
    </div>

    <!-- 2. 两行筛选工具栏：筛选条件独立一行，搜索与列表操作独立一行 -->
    <div class="filter-control-panel" :class="{ 'is-compact': !showAdvancedFilters }">
      <div v-if="showAdvancedFilters" class="filter-row filter-row-primary">
        <div class="dropdown-group">
          <!-- 年份下拉 -->
          <el-select
            v-model="filters.year"
            placeholder="年份"
            clearable
            class="filter-select select-year"
            popper-class="problem-filter-dropdown"
            @change="emitChange"
          >
            <template #prefix><CalendarDays class="filter-prefix-icon filter-icon-year" :size="15" :stroke-width="1.8" /></template>
            <el-option label="全部年份" :value="null" />
            <el-option v-for="year in recentYears" :key="year" :label="`${year} 年`" :value="year" />
          </el-select>

          <!-- 语言下拉 -->
          <el-select
            v-model="filters.statementLanguage"
            placeholder="语言"
            clearable
            class="filter-select select-lang"
            popper-class="problem-filter-dropdown"
            @change="emitChange"
          >
            <template #prefix><Languages class="filter-prefix-icon filter-icon-language" :size="15" :stroke-width="1.8" /></template>
            <el-option v-for="item in languageOptions" :key="String(item.value)" :label="item.label === '全部' ? '全部语言' : item.label" :value="item.value" />
          </el-select>

          <!-- 难度下拉 -->
          <el-select
            v-model="filters.difficulty"
            placeholder="难度"
            clearable
            class="filter-select select-diff"
            popper-class="problem-filter-dropdown"
            @change="emitChange"
          >
            <template #prefix><Gauge class="filter-prefix-icon filter-icon-difficulty" :size="15" :stroke-width="1.8" /></template>
            <el-option v-for="item in difficultyOptions" :key="String(item.value)" :label="item.label === '全部' ? '全部难度' : item.label" :value="item.value" />
          </el-select>

          <!-- 题目类型下拉 -->
          <el-select
            v-model="filters.selectedTags.PROBLEM_TYPE"
            placeholder="题目类型"
            clearable
            class="filter-select select-type"
            popper-class="problem-filter-dropdown"
            @change="emitChange"
          >
            <template #prefix><Tags class="filter-prefix-icon filter-icon-type" :size="15" :stroke-width="1.8" /></template>
            <el-option label="全部题型" :value="null" />
            <el-option v-for="tag in problemTypeTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 背景领域下拉 -->
          <el-select
            v-model="filters.selectedTags.BACKGROUND_DOMAIN"
            placeholder="背景领域"
            clearable
            class="filter-select select-domain"
            popper-class="problem-filter-dropdown"
            @change="emitChange"
          >
            <template #prefix><Layers3 class="filter-prefix-icon filter-icon-domain" :size="15" :stroke-width="1.8" /></template>
            <el-option label="全部领域" :value="null" />
            <el-option v-for="tag in domainTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 模型算法下拉 -->
          <el-select
            v-model="selectedAlgorithmId"
            placeholder="模型算法"
            clearable
            class="filter-select select-algo"
            popper-class="problem-filter-dropdown"
            @change="changeAlgorithm"
          >
            <template #prefix><Workflow class="filter-prefix-icon filter-icon-algorithm" :size="15" :stroke-width="1.8" /></template>
            <el-option label="全部模型算法" :value="null" />
            <el-option v-for="tag in algorithmTags" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>

          <!-- 平均分段下拉 -->
          <el-select
            v-model="selectedScoreLabel"
            placeholder="平均分"
            clearable
            class="filter-select select-score"
            popper-class="problem-filter-dropdown"
            @change="changeScoreRange"
          >
            <template #prefix><ChartNoAxesColumn class="filter-prefix-icon filter-icon-score" :size="15" :stroke-width="1.8" /></template>
            <el-option v-for="item in scoreOptions" :key="item.label" :label="item.label === '全部' ? '全部分数' : item.label" :value="item.label" />
          </el-select>

        </div>
        <button
          type="button"
          class="text-action-btn reset-btn"
          :disabled="!selectedConditions.length"
          aria-label="重置筛选"
          title="重置筛选"
          @click="reset"
        >
          <RotateCcw :size="17" :stroke-width="1.8" />
        </button>
      </div>

      <div class="filter-row filter-row-secondary">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索题目"
          clearable
          class="filter-search-pill"
          @keyup.enter="emitChange"
          @clear="emitChange"
        >
          <template #prefix><SearchIcon class="search-prefix-icon" :size="16" :stroke-width="1.8" /></template>
        </el-input>

        <el-tooltip content="排序题目" placement="top" effect="light" popper-class="problem-tooltip" :show-after="200">
          <el-dropdown
            trigger="click"
            placement="bottom-start"
            popper-class="problem-sort-dropdown"
            :hide-on-click="false"
            @command="handleSortCommand"
          >
            <button
              type="button"
              class="text-action-btn sort-btn"
              :class="{
                'sort-btn-active': sortField,
                'sort-btn-average': sortField === 'averageScore',
              }"
              :aria-label="sortButtonLabel"
            >
              <ArrowDownWideNarrow v-if="sortField && sortDirection === 'desc'" :size="18" :stroke-width="1.8" />
              <ArrowDownNarrowWide v-else-if="sortField && sortDirection === 'asc'" :size="18" :stroke-width="1.8" />
              <ArrowUpDown v-else :size="18" :stroke-width="1.8" />
              <span v-if="sortField" class="sort-button-text">{{ activeSortLabel }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in visibleSortOptions" :key="item.value" :command="item.value">
                  <span class="sort-option-label">{{ item.label }}</span>
                  <span class="sort-option-status" aria-hidden="true">
                    <ArrowDownWideNarrow v-if="sortField === item.value && sortDirection === 'desc'" :size="16" :stroke-width="2" />
                    <ArrowDownNarrowWide v-else-if="sortField === item.value && sortDirection === 'asc'" :size="16" :stroke-width="2" />
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>

        <span class="result-count">共 {{ total }} 题</span>

        <el-tooltip content="随机一题" placement="top" effect="light" popper-class="problem-tooltip" :show-after="200">
          <button
            type="button"
            class="text-action-btn random-btn"
            :disabled="randomLoading"
            aria-label="随机一题"
            @click="emit('random', buildParams())"
          >
            <Shuffle v-if="!randomLoading" :size="16" :stroke-width="1.8" />
            <LoaderCircle v-else class="loading-icon" :size="22" :stroke-width="1.8" />
          </button>
        </el-tooltip>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDownWideNarrow,
  ArrowDownNarrowWide,
  ArrowUpDown,
  CalendarDays,
  ChartNoAxesColumn,
  ChevronLeft,
  ChevronRight,
  Gauge,
  Languages,
  Layers3,
  LoaderCircle,
  RotateCcw,
  Search as SearchIcon,
  Shuffle,
  Tags,
  Workflow,
} from '@lucide/vue'
import cyanBackground from '@/assets/problem/contest-backgrounds/01-cyan.webp'
import orangeBackground from '@/assets/problem/contest-backgrounds/02-orange.webp'
import plumBackground from '@/assets/problem/contest-backgrounds/03-plum.webp'
import violetBackground from '@/assets/problem/contest-backgrounds/04-violet.webp'
import emeraldBackground from '@/assets/problem/contest-backgrounds/05-emerald.webp'
import indigoBackground from '@/assets/problem/contest-backgrounds/06-indigo.webp'
import tealBackground from '@/assets/problem/contest-backgrounds/07-teal.webp'
import amberBackground from '@/assets/problem/contest-backgrounds/08-amber.webp'
import roseBackground from '@/assets/problem/contest-backgrounds/09-rose.webp'
import midnightBackground from '@/assets/problem/contest-backgrounds/10-midnight.webp'

const props = defineProps({
  contests: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  optionsLoading: { type: Boolean, default: false },
  randomLoading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  showContestCards: { type: Boolean, default: true },
  showAdvancedFilters: { type: Boolean, default: true },
  fixedContestId: { type: [Number, String], default: null },
  hideYearSort: { type: Boolean, default: false },
})
const emit = defineEmits(['change', 'random', 'sort'])
const sortOptions = [
  { value: 'code', label: '题号' },
  { value: 'year', label: '年份' },
  { value: 'difficulty', label: '难度' },
  { value: 'averageScore', label: '平均分' },
]
const visibleSortOptions = computed(() => props.hideYearSort ? sortOptions.filter(item => item.value !== 'year') : sortOptions)
const sortField = ref('')
const sortDirection = ref('')
const activeSortLabel = computed(() => sortOptions.find(item => item.value === sortField.value)?.label || '')
const sortButtonLabel = computed(() => sortField.value ? `${activeSortLabel.value}${sortDirection.value === 'desc' ? '降序' : '升序'}` : '选择排序方式')
const route = useRoute()
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
const filters = reactive({ keyword: '', contestId: Number(props.fixedContestId) || null, difficulty: null, year: null, statementLanguage: '', minAverageScore: null, maxAverageScore: null, selectedTags: { BACKGROUND_DOMAIN: null, PROBLEM_TYPE: null, MODEL_ALGORITHM: [] } })
const selectedAlgorithmId = ref(null)
const selectedScoreLabel = ref('')
const cardsScrollRef = ref(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)

// 分类标签提取
const problemTypeTags = computed(() => props.tags.filter(t => t.type === 'PROBLEM_TYPE'))
const domainTags = computed(() => props.tags.filter(t => t.type === 'BACKGROUND_DOMAIN'))
const algorithmTags = computed(() => props.tags.filter(t => t.type === 'MODEL_ALGORITHM'))

const contestVisualVariants = ['halo', 'ribbon', 'orbit', 'prism']
const contestBackgrounds = [
  { src: cyanBackground, ink: '#18181b', muted: 'rgba(24, 24, 27, 0.72)', controlBg: 'rgba(255, 255, 255, 0.68)', controlBorder: 'rgba(255, 255, 255, 0.82)', textShadow: '0 1px 0 rgba(255, 255, 255, 0.58)' },
  { src: orangeBackground, ink: '#18181b', muted: 'rgba(24, 24, 27, 0.72)', controlBg: 'rgba(255, 255, 255, 0.68)', controlBorder: 'rgba(255, 255, 255, 0.82)', textShadow: '0 1px 0 rgba(255, 255, 255, 0.58)' },
  { src: plumBackground, ink: '#ffffff', muted: 'rgba(255, 255, 255, 0.82)', controlBg: 'rgba(36, 12, 34, 0.34)', controlBorder: 'rgba(255, 255, 255, 0.32)', textShadow: '0 1px 2px rgba(0, 0, 0, 0.34)' },
  { src: violetBackground, ink: '#ffffff', muted: 'rgba(255, 255, 255, 0.84)', controlBg: 'rgba(50, 20, 86, 0.28)', controlBorder: 'rgba(255, 255, 255, 0.36)', textShadow: '0 1px 2px rgba(36, 8, 62, 0.38)' },
  { src: emeraldBackground, ink: '#18181b', muted: 'rgba(24, 24, 27, 0.72)', controlBg: 'rgba(255, 255, 255, 0.68)', controlBorder: 'rgba(255, 255, 255, 0.82)', textShadow: '0 1px 0 rgba(255, 255, 255, 0.58)' },
  { src: indigoBackground, ink: '#ffffff', muted: 'rgba(255, 255, 255, 0.84)', controlBg: 'rgba(9, 25, 67, 0.34)', controlBorder: 'rgba(255, 255, 255, 0.34)', textShadow: '0 1px 2px rgba(0, 0, 0, 0.42)' },
  { src: tealBackground, ink: '#18181b', muted: 'rgba(24, 24, 27, 0.72)', controlBg: 'rgba(255, 255, 255, 0.68)', controlBorder: 'rgba(255, 255, 255, 0.82)', textShadow: '0 1px 0 rgba(255, 255, 255, 0.58)' },
  { src: amberBackground, ink: '#18181b', muted: 'rgba(24, 24, 27, 0.72)', controlBg: 'rgba(255, 255, 255, 0.68)', controlBorder: 'rgba(255, 255, 255, 0.82)', textShadow: '0 1px 0 rgba(255, 255, 255, 0.58)' },
  { src: roseBackground, ink: '#ffffff', muted: 'rgba(255, 255, 255, 0.84)', controlBg: 'rgba(74, 12, 38, 0.3)', controlBorder: 'rgba(255, 255, 255, 0.34)', textShadow: '0 1px 2px rgba(0, 0, 0, 0.4)' },
  { src: midnightBackground, ink: '#ffffff', muted: 'rgba(255, 255, 255, 0.86)', controlBg: 'rgba(3, 15, 42, 0.42)', controlBorder: 'rgba(255, 255, 255, 0.34)', textShadow: '0 1px 2px rgba(0, 0, 0, 0.46)' },
]
const goldenAngle = 137.508
const minimumAdjacentHueDistance = 82

// 同一赛事始终得到相同哈希，以此固定颜色、光影和装饰构图。
const createStableHash = (value) => {
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

const normalizeHue = (hue) => ((hue % 360) + 360) % 360

const getHueDistance = (firstHue, secondHue) => {
  const distance = Math.abs(firstHue - secondHue)
  return Math.min(distance, 360 - distance)
}

// 从赛事自身的色相出发，以黄金角寻找与前两张卡片差异足够大的候选色。
const selectDistinctHue = (seedHue, recentHues) => {
  if (!recentHues.length) return seedHue

  let bestCandidate = seedHue
  let bestDistance = -1
  for (let attempt = 0; attempt < 24; attempt += 1) {
    const candidate = normalizeHue(seedHue + (attempt * goldenAngle))
    const nearestDistance = Math.min(...recentHues.map(hue => getHueDistance(candidate, hue)))
    if (nearestDistance >= minimumAdjacentHueDistance) return candidate
    if (nearestDistance > bestDistance) {
      bestCandidate = candidate
      bestDistance = nearestDistance
    }
  }
  return bestCandidate
}

const selectDistinctVariant = (hash, recentVariants) => {
  const seedIndex = (hash >>> 8) % contestVisualVariants.length
  for (let offset = 0; offset < contestVisualVariants.length; offset += 1) {
    const candidate = contestVisualVariants[(seedIndex + offset) % contestVisualVariants.length]
    if (!recentVariants.includes(candidate)) return candidate
  }
  return contestVisualVariants[seedIndex]
}

const selectDistinctBackgroundIndex = (hash, recentBackgroundIndexes) => {
  const seedIndex = hash % contestBackgrounds.length
  for (let offset = 0; offset < contestBackgrounds.length; offset += 1) {
    const candidateIndex = (seedIndex + offset) % contestBackgrounds.length
    if (!recentBackgroundIndexes.includes(candidateIndex)) return candidateIndex
  }
  return seedIndex
}

const createContestTheme = (contest, recentHues, recentVariants, recentBackgroundIndexes) => {
  const signature = `${contest.id ?? ''}|${contest.code}|${contest.name}`
  const hash = createStableHash(signature)
  const backgroundIndex = selectDistinctBackgroundIndex(hash, recentBackgroundIndexes)
  const localBackground = contestBackgrounds[backgroundIndex]
  const primaryHue = selectDistinctHue(hash % 360, recentHues)
  const secondaryDirection = (hash & 1) === 0 ? 1 : -1
  const secondaryOffset = 22 + ((hash >>> 7) % 21)
  const secondaryHue = normalizeHue(primaryHue + (secondaryDirection * secondaryOffset))
  const tertiaryHue = normalizeHue(primaryHue - (secondaryDirection * (12 + ((hash >>> 11) % 20))))
  const primarySaturation = 70 + ((hash >>> 17) % 14)
  const secondarySaturation = 72 + ((hash >>> 21) % 13)
  const primaryLightness = 38 + ((hash >>> 25) % 23)
  const secondaryLightness = Math.max(30, Math.min(68, primaryLightness + (secondaryDirection * 9)))
  const tertiaryLightness = Math.max(32, Math.min(70, primaryLightness + 14))
  const isDarkTheme = localBackground.ink === '#ffffff'
  const angle = 116 + ((hash >>> 12) % 35)
  const orbX = 68 + ((hash >>> 18) % 25)
  const orbY = 12 + ((hash >>> 23) % 22)
  const patternSize = 9 + ((hash >>> 6) % 4)
  const shapeX = 8 + ((hash >>> 9) % 72)
  const shapeY = -24 + ((hash >>> 13) % 88)
  const shapeRotation = -38 + ((hash >>> 19) % 77)
  const shapeScale = 0.82 + (((hash >>> 27) % 32) / 100)

  return {
    primaryHue,
    backgroundIndex,
    visualVariant: selectDistinctVariant(hash, recentVariants),
    themeStyle: {
      '--contest-gradient-angle': `${angle}deg`,
      '--contest-orb-x': `${orbX}%`,
      '--contest-orb-y': `${orbY}%`,
      '--contest-pattern-size': `${patternSize}px`,
      '--contest-bg-image': `url("${contest.backgroundImageUrl || contest.backgroundUrl || localBackground.src}")`,
      '--contest-shape-x': `${shapeX}%`,
      '--contest-shape-y': `${shapeY}%`,
      '--contest-shape-rotation': `${shapeRotation}deg`,
      '--contest-shape-scale': shapeScale,
      '--contest-primary': `hsl(${primaryHue} ${primarySaturation}% ${primaryLightness}%)`,
      '--contest-secondary': `hsl(${secondaryHue} ${secondarySaturation}% ${secondaryLightness}%)`,
      '--contest-tertiary': `hsl(${tertiaryHue} 76% ${tertiaryLightness}%)`,
      '--contest-glow-a': `hsla(${primaryHue}, 86%, 62%, 0.42)`,
      '--contest-glow-b': `hsla(${secondaryHue}, 86%, 61%, 0.36)`,
      '--contest-accent': `hsl(${primaryHue} ${primarySaturation}% ${Math.max(32, primaryLightness - 5)}%)`,
      '--contest-accent-soft': `hsla(${primaryHue}, ${primarySaturation}%, 52%, 0.2)`,
      '--contest-ink': isDarkTheme ? '#ffffff' : '#18181b',
      '--contest-muted': isDarkTheme ? 'rgba(255, 255, 255, 0.82)' : 'rgba(24, 24, 27, 0.72)',
      '--contest-text-shadow': localBackground.textShadow,
      '--contest-control-bg': localBackground.controlBg,
      '--contest-control-border': localBackground.controlBorder,
      '--contest-pattern': isDarkTheme ? 'rgba(255, 255, 255, 0.14)' : 'rgba(24, 24, 27, 0.1)',
    },
  }
}

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
      // 后端接入时直接返回 backgroundImageUrl（或 backgroundUrl）即可替换本地素材。
      backgroundImageUrl: c.backgroundImageUrl || c.backgroundUrl || c.backgroundImage || null,
      participants,
      heatTag,
      statsText,
    })
  }

  // 按参与人数从大到小排序（最活跃排第一）
  list.sort((a, b) => b.participants - a.participants)

  const recentHues = []
  const recentVariants = []
  const recentBackgroundIndexes = []
  return list.map((contest) => {
    const theme = createContestTheme(contest, recentHues, recentVariants, recentBackgroundIndexes)
    recentHues.push(theme.primaryHue)
    recentVariants.push(theme.visualVariant)
    recentBackgroundIndexes.push(theme.backgroundIndex)
    if (recentHues.length > 2) recentHues.shift()
    if (recentVariants.length > 2) recentVariants.shift()
    if (recentBackgroundIndexes.length > 2) recentBackgroundIndexes.shift()

    return {
      ...contest,
      backgroundIndex: theme.backgroundIndex,
      visualVariant: theme.visualVariant,
      themeStyle: theme.themeStyle,
    }
  })
})

const selectContestCard = (contestId) => {
  if (contestId) {
    router.push(`/problem/contest/${contestId}`)
  }
}

const scrollCards = (direction) => {
  const track = cardsScrollRef.value
  if (!track) return
  const card = track.querySelector('.contest-card')
  const cardWidth = card ? card.offsetWidth : 300
  const gap = Number.parseFloat(window.getComputedStyle(track).columnGap) || 0
  const step = cardWidth + gap
  track.scrollBy({ left: direction === 'left' ? -step : step, behavior: 'smooth' })
}

const updateScrollButtons = () => {
  const track = cardsScrollRef.value
  if (!track) return
  const maxScrollLeft = Math.max(0, track.scrollWidth - track.clientWidth)
  canScrollLeft.value = track.scrollLeft > 2
  canScrollRight.value = track.scrollLeft < maxScrollLeft - 2
}

const syncFiltersFromQuery = () => {
  const q = route.query
  if (!q) return
  if (q.keyword !== undefined) filters.keyword = String(q.keyword || '')
  if (props.fixedContestId) filters.contestId = Number(props.fixedContestId)
  else if (q.contestId) filters.contestId = Number(q.contestId)
  else if (!q.contestId && filters.contestId) filters.contestId = null
  if (q.year) filters.year = Number(q.year)
  else if (!q.year && filters.year) filters.year = null
  if (q.difficulty) filters.difficulty = Number(q.difficulty)
  else if (!q.difficulty && filters.difficulty) filters.difficulty = null
  if (q.statementLanguage !== undefined) filters.statementLanguage = String(q.statementLanguage || '')
  if (q.minScore !== undefined) filters.minAverageScore = q.minScore ? Number(q.minScore) : null
  if (q.maxScore !== undefined) filters.maxAverageScore = q.maxScore ? Number(q.maxScore) : null

  if (q.tagIds) {
    const ids = String(q.tagIds).split(',').map(Number).filter(Boolean)
    for (const id of ids) {
      const tag = props.tags.find(t => t.id === id)
      if (tag) {
        if (tag.type === 'PROBLEM_TYPE') filters.selectedTags.PROBLEM_TYPE = id
        else if (tag.type === 'BACKGROUND_DOMAIN') filters.selectedTags.BACKGROUND_DOMAIN = id
        else if (tag.type === 'MODEL_ALGORITHM') {
          if (!filters.selectedTags.MODEL_ALGORITHM.includes(id)) {
            filters.selectedTags.MODEL_ALGORITHM = [id]
          }
          selectedAlgorithmId.value = id
        }
      }
    }
  }
}

onMounted(() => {
  syncFiltersFromQuery()
  nextTick(() => {
    updateScrollButtons()
    cardsScrollRef.value?.addEventListener('scroll', updateScrollButtons, { passive: true })
    window.addEventListener('resize', updateScrollButtons)
  })
})

watch(
  () => route.query,
  () => {
    syncFiltersFromQuery()
  },
  { deep: true }
)

watch(
  () => props.tags,
  () => {
    syncFiltersFromQuery()
  },
  { deep: true }
)

watch(
  () => props.fixedContestId,
  (value) => {
    filters.contestId = Number(value) || null
  }
)

watch(sortedContestCards, () => {
  nextTick(updateScrollButtons)
}, { deep: true })

onUnmounted(() => {
  cardsScrollRef.value?.removeEventListener('scroll', updateScrollButtons)
  window.removeEventListener('resize', updateScrollButtons)
})

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
const buildParams = () => ({ keyword: filters.keyword, contestId: Number(props.fixedContestId) || filters.contestId, difficulty: filters.difficulty, year: filters.year, statementLanguage: filters.statementLanguage, minAverageScore: filters.minAverageScore, maxAverageScore: filters.maxAverageScore, tagIds: Object.values(filters.selectedTags).flatMap((value) => Array.isArray(value) ? value : [value]).filter(Boolean) })
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
  Object.assign(filters, { keyword: '', contestId: Number(props.fixedContestId) || null, difficulty: null, year: null, statementLanguage: '', minAverageScore: null, maxAverageScore: null })
  filters.selectedTags.BACKGROUND_DOMAIN = null
  filters.selectedTags.PROBLEM_TYPE = null
  filters.selectedTags.MODEL_ALGORITHM = []
  selectedAlgorithmId.value = null
  selectedScoreLabel.value = ''
  sortField.value = ''
  sortDirection.value = ''
  emit('sort', 'clear')
  emitChange()
}
const handleSortCommand = (field) => {
  if (sortField.value !== field) {
    sortField.value = field
    sortDirection.value = field === 'code' ? 'asc' : 'desc'
  } else if (sortDirection.value === (field === 'code' ? 'asc' : 'desc')) {
    sortDirection.value = field === 'code' ? 'desc' : 'asc'
  } else {
    sortField.value = ''
    sortDirection.value = ''
  }
  emit('sort', field)
}
</script>

<style scoped>
.problem-filter { margin-bottom: 20px; margin-top: 0; }

/* 赛事卡片：高度 135px，单行横滑，左右切换按钮在容器宽度以内且尺寸较小 */
.contest-carousel-wrap {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  margin-bottom: 18px;
}
.contest-cards-track {
  --contest-card-gap: 31px;
  display: flex;
  align-items: center;
  gap: var(--contest-card-gap);
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
  /* 卡片缩窄后的空间回流到两段间距，保证任意连续3张卡片刚好占满轨道。 */
  flex: 0 0 calc((100% - var(--contest-card-gap) - var(--contest-card-gap)) / 3);
  width: calc((100% - var(--contest-card-gap) - var(--contest-card-gap)) / 3);
  box-sizing: border-box;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 135px;
  padding: 16px;
  overflow: hidden;
  isolation: isolate;
  background: transparent;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: none;
  transition: transform var(--lm-transition), filter var(--lm-transition);
}
.contest-card::before,
.contest-card::after {
  position: absolute;
  content: '';
  pointer-events: none;
}
.contest-card::after {
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background-image:
    radial-gradient(circle, var(--contest-pattern) 0.8px, transparent 1px),
    radial-gradient(circle at var(--contest-orb-x) var(--contest-orb-y), var(--contest-glow-a) 0, transparent 36%),
    linear-gradient(var(--contest-gradient-angle), var(--contest-primary) 0 36%, transparent 74%);
  background-position: 2px 3px, 0 0, 0 0;
  background-size: var(--contest-pattern-size) var(--contest-pattern-size), auto, auto;
  opacity: 0.96;
}
.contest-card > * {
  position: relative;
  z-index: 1;
}
.contest-card--halo::after {
  background:
    repeating-radial-gradient(circle at 82% 18%, transparent 0 13px, var(--contest-pattern) 14px 15px, transparent 16px 25px),
    radial-gradient(circle at 82% 18%, var(--contest-glow-a) 0 29%, transparent 30%),
    linear-gradient(132deg, transparent 0 43%, var(--contest-secondary) 44% 61%, transparent 62%),
    linear-gradient(18deg, var(--contest-primary) 0 30%, transparent 72%),
    linear-gradient(138deg, var(--contest-primary) 0%, var(--contest-secondary) 100%);
}
.contest-card--ribbon::after {
  background:
    repeating-linear-gradient(150deg, transparent 0 19px, var(--contest-pattern) 20px 21px, transparent 22px 34px),
    linear-gradient(152deg, transparent 0 32%, var(--contest-secondary) 33% 49%, transparent 50% 100%),
    linear-gradient(25deg, var(--contest-primary) 0 25%, transparent 68%),
    radial-gradient(circle at 86% 86%, var(--contest-glow-b) 0 24%, transparent 46%),
    linear-gradient(118deg, var(--contest-primary) 0%, var(--contest-secondary) 100%);
}
.contest-card--orbit::after {
  background:
    repeating-radial-gradient(ellipse at 78% 86%, transparent 0 11px, var(--contest-pattern) 12px 13px, transparent 14px 23px),
    radial-gradient(ellipse at 78% 86%, var(--contest-glow-b) 0 28%, transparent 29%),
    linear-gradient(112deg, var(--contest-primary) 0 28%, transparent 67%),
    linear-gradient(166deg, transparent 0 52%, var(--contest-secondary) 53% 72%, transparent 73%),
    linear-gradient(146deg, var(--contest-primary) 0%, var(--contest-secondary) 100%);
}
.contest-card--prism::after {
  background:
    linear-gradient(128deg, transparent 0 18%, var(--contest-pattern) 19% 20%, transparent 21% 100%),
    linear-gradient(128deg, transparent 0 41%, var(--contest-secondary) 42% 59%, transparent 60% 100%),
    linear-gradient(35deg, transparent 0 48%, var(--contest-tertiary) 49% 72%, transparent 73%),
    linear-gradient(162deg, var(--contest-primary) 0 32%, transparent 70%),
    linear-gradient(126deg, var(--contest-primary) 0%, var(--contest-secondary) 100%);
}
.contest-card::after,
.contest-card--halo::after,
.contest-card--ribbon::after,
.contest-card--orbit::after,
.contest-card--prism::after {
  background: var(--contest-bg-image) center / cover no-repeat;
  mask-image: none;
  opacity: 1;
}
.contest-card::before,
.contest-card--halo::before,
.contest-card--ribbon::before,
.contest-card--orbit::before,
.contest-card--prism::before {
  display: none;
}
.contest-card--halo::before {
  top: var(--contest-shape-y);
  left: var(--contest-shape-x);
  width: 108px;
  height: 108px;
  z-index: 0;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  background: radial-gradient(circle at 35% 38%, rgba(255, 255, 255, 0.62), var(--contest-accent-soft) 70%);
  box-shadow: 0 0 0 14px rgba(255, 255, 255, 0.13);
}
.contest-card--ribbon::before {
  top: var(--contest-shape-y);
  left: var(--contest-shape-x);
  width: 174px;
  height: 38px;
  z-index: 0;
  border: 1px solid rgba(255, 255, 255, 0.64);
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.06), var(--contest-accent-soft));
  transform: rotate(var(--contest-shape-rotation)) scale(var(--contest-shape-scale));
}
.contest-card--orbit::before {
  top: var(--contest-shape-y);
  left: var(--contest-shape-x);
  width: 126px;
  height: 70px;
  z-index: 0;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 50%;
  box-shadow: 0 0 0 11px rgba(255, 255, 255, 0.12), 0 0 0 24px rgba(255, 255, 255, 0.08);
  transform: rotate(var(--contest-shape-rotation)) scale(var(--contest-shape-scale));
}
.contest-card--prism::before {
  top: var(--contest-shape-y);
  left: var(--contest-shape-x);
  width: 104px;
  height: 104px;
  z-index: 0;
  border: 1px solid rgba(255, 255, 255, 0.66);
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.52), var(--contest-accent-soft));
  box-shadow: 0 0 0 12px rgba(255, 255, 255, 0.1);
  transform: rotate(var(--contest-shape-rotation)) scale(var(--contest-shape-scale));
}
.contest-card:hover {
  transform: translateY(-3px);
  filter: saturate(1.1);
}
.contest-card.active {
  filter: saturate(1.12);
}
.contest-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.contest-code-badge {
  padding: 4px 8px;
  border: 1px solid var(--contest-control-border);
  border-radius: 6px;
  background: var(--contest-control-bg);
  backdrop-filter: blur(6px);
  color: var(--contest-ink);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-family: var(--lm-code-font-family);
}
.contest-card.active .contest-code-badge {
  border-color: transparent;
  background: var(--contest-accent);
  color: #ffffff;
}
.contest-heat-tag {
  max-width: 52%;
  overflow: hidden;
  padding: 3px 6px;
  border: 1px solid var(--contest-control-border);
  border-radius: 999px;
  background: var(--contest-control-bg);
  font-size: 12px;
  font-weight: 650;
  color: var(--contest-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.contest-card-name {
  font-size: 15px;
  font-weight: 750;
  color: var(--contest-ink);
  line-height: 1.4;
  margin: 6px 0;
  text-shadow: var(--contest-text-shadow);
}
.contest-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 6px;
}
.contest-stat-text {
  font-size: 12px;
  color: var(--contest-muted);
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
  color: var(--contest-accent);
  opacity: 0.58;
  transition: transform var(--lm-transition), color var(--lm-transition);
}
.contest-card:hover .contest-arrow-icon {
  color: var(--contest-accent);
  opacity: 1;
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
.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 0;
}
.filter-row-primary {
  justify-content: stretch;
  gap: 10px;
}
.filter-row-secondary {
  border-top: 1px solid var(--lm-border-light);
  margin-top: 16px;
  padding-top: 14px;
}
.filter-control-panel.is-compact .filter-row-secondary {
  border-top: 0;
  margin-top: 0;
  padding-top: 0;
}

.dropdown-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  flex-shrink: 0;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.filter-row-primary .dropdown-group {
  flex: 1;
  justify-content: space-between;
  gap: 10px;
}
.dropdown-group::-webkit-scrollbar { display: none; }

/* 搜索框胶囊样式 */
.filter-search-pill {
  width: min(200px, 45%);
  min-width: 180px;
}
.filter-search-pill :deep(.el-input__wrapper) {
  border-radius: 9999px !important;
  background: #f5f5f5;
  border: 1px solid transparent;
  box-shadow: none;
  padding: 2px 12px;
  height: 32px;
  transition: all var(--lm-transition);
}
.filter-search-pill :deep(.el-input__wrapper:hover) {
  border-color: rgba(0, 0, 0, 0.12);
  background: #f5f5f5;
}
.filter-search-pill :deep(.el-input__wrapper.is-focus) {
  border-color: var(--lm-primary) !important;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12) !important;
}

/* 下拉框胶囊样式 */
.filter-select {
  --filter-pill-space: 12px;
  width: max-content;
  min-width: 0;
}

.filter-select :deep(.el-select__wrapper) {
  border-radius: 9999px !important;
  background: #f4f4f5;
  border: 1px solid transparent;
  box-shadow: none;
  /* padding: 2px var(--filter-pill-space) !important; */
  padding: 2px 18px !important;
  min-height: 38px;
  height: 44px;
  transition: all var(--lm-transition);
  justify-content: center;
  gap: 0;
}
.filter-select :deep(.el-select__prefix) {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  margin-right: var(--filter-pill-space);
}
.filter-select :deep(.el-select__selection) {
  flex: 0 0 auto;
  min-width: max-content;
  gap: 0;
}
.filter-select :deep(.el-select__caret) {
  display: none;
}
.filter-select :deep(.filter-prefix-icon) {
  display: block;
  flex-shrink: 0;
}
.filter-select :deep(.filter-icon-year) { color: #2563eb !important; }
.filter-select :deep(.filter-icon-language) { color: #0f766e !important; }
.filter-select :deep(.filter-icon-difficulty) { color: #d97706 !important; }
.filter-select :deep(.filter-icon-type) { color: #7c3aed !important; }
.filter-select :deep(.filter-icon-domain) { color: #0891b2 !important; }
.filter-select :deep(.filter-icon-algorithm) { color: #db2777 !important; }
.filter-select :deep(.filter-icon-score) { color: #16a34a !important; }
.search-prefix-icon {
  display: block;
  color: var(--lm-text-muted);
  flex-shrink: 0;
}
.filter-select :deep(.el-select__wrapper:hover) {
  border-color: rgba(0, 0, 0, 0.12);
  background: #f4f4f5;
}
.filter-select :deep(.el-select__wrapper.is-focused) {
  border-color: transparent !important;
  box-shadow: none !important;
  background: #f4f4f5;
}
.filter-select :deep(.el-select__placeholder) {
  position: static;
  width: auto;
  color: rgb(110, 85, 91);
  font-size: 13px;
  font-weight: 500;
  max-width: none;
  overflow: visible;
  text-overflow: clip;
  white-space: nowrap;
  transform: none;
}
.filter-select :deep(.el-select__selected-item) {
  font-size: 13px;
  font-weight: 600;
  color: rgb(110, 85, 91);
  max-width: none;
  overflow: visible;
  text-overflow: clip;
  white-space: nowrap;
}
.sort-option-label {
  flex: 1;
  min-width: 0;
}
.sort-option-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-left: 12px;
  color: var(--lm-text-secondary);
  flex: 0 0 16px;
}
.sort-button-text { color: inherit; font-size: 12px; font-weight: 600; white-space: nowrap; }
/* 搜索行操作按钮 */
.text-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 9999px;
  border: 1px solid transparent;
  background: #f5f5f5;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
  box-shadow: none;
  flex-shrink: 0;
}
.sort-btn {
  width: 30px;
  height: 30px;
  min-width: 30px;
  min-height: 30px;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  padding: 0;
  overflow: hidden;
  transition:
    width 240ms cubic-bezier(0.22, 1, 0.36, 1),
    gap 240ms cubic-bezier(0.22, 1, 0.36, 1),
    padding 240ms cubic-bezier(0.22, 1, 0.36, 1),
    color var(--lm-transition),
    border-color var(--lm-transition),
    background var(--lm-transition);
}
.sort-btn.sort-btn-active {
  width: auto;
  min-width: 84px;
  gap: 6px;
  padding: 0 12px;
}
.sort-btn.sort-btn-active.sort-btn-average {
  min-width: 96px;
}
.sort-btn > svg {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  margin: 0;
}
.random-btn {
  width: 36px;
  height: 36px;
  min-height: 36px;
  aspect-ratio: 1;
  padding: 0;
}
.random-btn {
  margin-left: 8px;
  border-color: transparent;
  background: transparent;
}
.text-action-btn svg,
.scroll-arrow svg {
  flex-shrink: 0;
}
.loading-icon {
  animation: problem-icon-spin 0.9s linear infinite;
}
.text-action-btn:hover:not(:disabled) {
  color: var(--lm-primary);
  border-color: rgba(0, 0, 0, 0.12);
  background: #eeeeee;
}
.text-action-btn.sort-btn.sort-btn-active,
.text-action-btn.sort-btn.sort-btn-active:hover:not(:disabled) {
  color: rgb(26, 144, 255);
}
.text-action-btn.random-btn:hover:not(:disabled) {
  border-color: transparent;
  background: transparent;
}
.text-action-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.reset-btn {
  width: 38px;
  height: 38px;
  min-height: 38px;
  padding: 0;
  border-radius: 50%;
  color: var(--lm-text-secondary);
  background: transparent;
  flex: 0 0 38px;
}
.reset-btn:hover:not(:disabled) {
  color: var(--lm-text-primary);
  border-color: transparent;
  background: transparent;
}
.filter-row-secondary .result-count {
  margin-left: auto;
}
.result-count {
  color: var(--lm-text-muted);
  font-size: 14px;
  white-space: nowrap;
}
:global(.problem-tooltip.el-popper.is-light) {
  border-color: #e4e4e7;
  background: #ffffff;
  color: #18181b;
  box-shadow: 0 6px 18px rgba(24, 24, 27, 0.1);
}
:global(.problem-sort-dropdown.el-popper) {
  width: 176px !important;
  min-width: 176px !important;
}
:global(.problem-sort-dropdown .el-dropdown-menu) {
  width: 100%;
  box-sizing: border-box;
}
:global(.problem-filter-dropdown .el-select-dropdown__item) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-inline: 12px;
  text-align: center;
}
:global(.problem-tooltip.el-popper.is-light .el-popper__arrow::before) {
  border-color: #e4e4e7;
  background: #ffffff;
}
@keyframes problem-icon-spin {
  to { transform: rotate(360deg); }
}
@media (max-width: 900px) {
  .contest-cards-track { --contest-card-gap: 16px; }
  .contest-card { flex: 0 0 calc(85% - 10px); width: calc(85% - 10px); }
  .scroll-arrow.left { left: 4px; }
  .scroll-arrow.right { right: 4px; }
  .filter-row { flex-wrap: wrap; }
  .filter-row-primary { align-items: stretch; }
  .dropdown-group { width: 100%; justify-content: flex-start; }
  .filter-search-pill { width: 100%; }
}
</style>
