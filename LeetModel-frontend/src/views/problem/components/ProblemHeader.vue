<template>
  <div class="problem-header">
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

      <el-input-number v-model="year" :min="2000" :max="2100" placeholder="年份" class="filter-select" @change="emitChange" />

      <el-select v-model="statementLanguage" placeholder="题面语言" clearable class="filter-select" @change="emitChange">
        <el-option label="中文" value="ZH" /><el-option label="英文" value="EN" />
      </el-select>

      <el-select v-model="difficulty" placeholder="难度" clearable class="filter-select" @change="emitChange">
        <el-option label="简单" :value="1" />
        <el-option label="中等" :value="2" />
        <el-option label="困难" :value="3" />
      </el-select>

      <el-button :icon="Refresh" @click="reset" text>清空</el-button>
      <el-button type="primary" plain :loading="randomLoading" @click="handleRandom">随机一题</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

const keyword = ref('')
const difficulty = ref('')
const year = ref(null)
const statementLanguage = ref('')
const randomLoading = ref(false)

const emit = defineEmits(['change', 'random'])

const emitChange = () => {
  emit('change', {
    keyword: keyword.value,
    difficulty: difficulty.value,
    year: year.value,
    statementLanguage: statementLanguage.value,
  })
}

const reset = () => {
  keyword.value = ''
  difficulty.value = ''
  year.value = null
  statementLanguage.value = ''
  emitChange()
}

const handleRandom = async () => {
  randomLoading.value = true
  try { await emit('random', { keyword: keyword.value, difficulty: difficulty.value, year: year.value, statementLanguage: statementLanguage.value }) }
  finally { randomLoading.value = false }
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
