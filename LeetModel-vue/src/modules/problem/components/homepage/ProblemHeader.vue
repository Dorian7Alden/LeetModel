<template>
  <div class="problem-header">
    <!-- Tags -->
    <div class="tag-section">
      <span class="section-label">标签筛选</span>
      <div class="tag-container">
        <span
          v-for="tag in tagList"
          :key="tag"
          :class="['tag-chip', selectedTags.includes(tag) ? 'active' : '']"
          @click="toggleTag(tag)"
        >{{ tag }}</span>
      </div>
    </div>

    <!-- Filter Row -->
    <div class="filter-row">
      <el-input
        v-model="keyword"
        placeholder="搜索题目..."
        :prefix-icon="Search"
        clearable
        class="filter-search"
        @change="emitChange"
        @clear="emitChange"
      />

      <el-select v-model="difficulty" placeholder="难度" clearable class="filter-select" @change="emitChange">
        <el-option label="入门" value="入门" />
        <el-option label="中等" value="中等" />
        <el-option label="困难" value="困难" />
        <el-option label="挑战" value="挑战" />
      </el-select>

      <el-select v-model="language" placeholder="语言" clearable class="filter-select" @change="emitChange">
        <el-option label="Python" value="Python" />
        <el-option label="MATLAB" value="MATLAB" />
        <el-option label="R" value="R" />
        <el-option label="C++" value="C++" />
      </el-select>

      <el-select v-model="scoreRange" placeholder="评分区间" clearable class="filter-select" @change="handleRangeChange">
        <el-option v-for="r in scoreRanges" :key="r.label" :label="r.label" :value="r.label" />
      </el-select>

      <el-input-number
        v-model="minAveScore"
        :min="0" :max="100"
        placeholder="最低分"
        class="filter-score"
        controls-position="right"
        size="default"
        @change="onScoreInput"
      />
      <span class="score-sep">—</span>
      <el-input-number
        v-model="maxAveScore"
        :min="0" :max="100"
        placeholder="最高分"
        class="filter-score"
        controls-position="right"
        size="default"
        @change="onScoreInput"
      />

      <el-select v-model="sortOrder" class="filter-sort" @change="emitChange">
        <el-option label="默认排序" value="default" />
        <el-option label="评分从高到低" value="desc" />
        <el-option label="评分从低到高" value="asc" />
        <el-option label="最新发布" value="newest" />
      </el-select>

      <el-button :icon="Refresh" @click="reset" text>清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

const tagList = [
  '时间序列', '神经网络', '遗传算法', '动态规划', '线性回归',
  '聚类分析', '图论模型', '微分方程', '蒙特卡洛', '梯度下降',
  '国赛', '美赛', '交通物流', '金融经济', '生物医学',
]

const keyword = ref('')
const difficulty = ref('')
const language = ref('')
const selectedTags = ref([])
const minAveScore = ref(null)
const maxAveScore = ref(null)
const sortOrder = ref('default')
const scoreRange = ref('')

const emit = defineEmits(['change'])

const scoreRanges = [
  { label: '0-20', min: 0, max: 20 },
  { label: '20-40', min: 20, max: 40 },
  { label: '40-60', min: 40, max: 60 },
  { label: '60-80', min: 60, max: 80 },
  { label: '80-100', min: 80, max: 100 },
]

const toggleTag = (tag) => {
  const idx = selectedTags.value.indexOf(tag)
  if (idx > -1) selectedTags.value.splice(idx, 1)
  else selectedTags.value.push(tag)
  emitChange()
}

const handleRangeChange = () => {
  const range = scoreRanges.find(r => r.label === scoreRange.value)
  if (range) { minAveScore.value = range.min; maxAveScore.value = range.max }
  else { minAveScore.value = null; maxAveScore.value = null }
  emitChange()
}

const onScoreInput = () => {
  scoreRange.value = ''
  emitChange()
}

const emitChange = () => {
  emit('change', {
    keyword: keyword.value,
    difficulty: difficulty.value,
    language: language.value,
    tags: [...selectedTags.value],
    minAveScore: minAveScore.value,
    maxAveScore: maxAveScore.value,
    sortOrder: sortOrder.value,
  })
}

const reset = () => {
  keyword.value = ''
  difficulty.value = ''
  language.value = ''
  selectedTags.value = []
  minAveScore.value = null
  maxAveScore.value = null
  sortOrder.value = 'default'
  scoreRange.value = ''
  emitChange()
}
</script>

<style scoped>
.problem-header {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 20px;
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tag-section { display: flex; flex-direction: column; gap: 10px; }

.section-label {
  font-size: 13px; font-weight: 600; color: var(--lm-text-secondary);
}

.tag-container {
  display: flex; flex-wrap: wrap; gap: 8px;
}

.tag-chip {
  padding: 5px 14px;
  border-radius: 20px;
  background: var(--lm-bg-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--lm-transition);
  color: var(--lm-text-secondary);
  font-weight: 500;
  border: 1px solid transparent;
}

.tag-chip:hover {
  background: var(--lm-primary-bg);
  color: var(--lm-primary);
}

.tag-chip.active {
  background: var(--lm-primary);
  color: #ffffff;
  border-color: var(--lm-primary);
}

.filter-row {
  display: flex; align-items: center; gap: 10px;
  flex-wrap: wrap;
}

.filter-search { width: 200px; }
.filter-select { width: 120px; }
.filter-sort { width: 150px; }
.filter-score { width: 120px; }

.score-sep { color: var(--lm-text-muted); font-size: 13px; }
</style>
