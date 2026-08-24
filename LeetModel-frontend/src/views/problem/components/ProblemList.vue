<template>
  <div class="problem-list" v-loading="loading">
    <div class="list-header"><span class="result-count">共 {{ total }} 题</span></div>
    <div v-if="problems.length" class="list-cards">
      <div v-for="(item, index) in problems" :key="item.id" class="problem-card card-hover" @click="$router.push(`/problem/${item.id}`)">
        <span class="card-index">{{ (page - 1) * pageSize + index + 1 }}</span>
        <div class="card-body">
          <div class="card-top">
            <h3 class="card-title">{{ item.title }}</h3>
            <el-tag :type="difficultyType(item.difficulty)" size="small" effect="plain">{{ difficultyLabel(item.difficulty) }}</el-tag>
            <el-tag size="small" effect="plain" type="info">{{ contestTypeLabel(item.contestType) }}</el-tag>
          </div>
          <div v-if="item.tagNames?.length" class="card-tags">
            <span v-for="tag in item.tagNames.slice(0, 4)" :key="tag" class="card-tag">{{ tag }}</span>
          </div>
          <div class="card-meta">
            <span class="meta-item"><el-icon :size="14"><StarFilled /></el-icon>平均分 {{ item.averageScore ?? 0 }}</span>
            <span class="meta-item">发布于 {{ formatDate(item.createTime) }}</span>
            <span class="meta-item">{{ item.year || '-' }} 年 · {{ item.statementLanguage === 'EN' ? '英文' : '中文' }} · {{ formatDuration(item.durationMinutes) }}</span>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无符合条件的题目" />
    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" background layout="prev, pager, next, total" @current-change="fetchProblems" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemList } from '@/api/problem'

const problems = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const query = ref({})
const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const contestTypeLabel = (value) => ({ MCM_ICM: '美赛', CUMCM: '国赛' })[value] || value || '未分类'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '-'
const formatDuration = (minutes) => minutes ? `${Math.floor(minutes / 60)} 小时${minutes % 60 ? ` ${minutes % 60} 分钟` : ''}` : '时长未设置'

const fetchProblems = async () => {
  loading.value = true
  try {
    const response = await getPublicProblemList({ page: page.value, pageSize: pageSize.value, ...query.value })
    problems.value = response.data?.rows || []
    total.value = response.data?.total || 0
  } catch (error) {
    problems.value = []
    total.value = 0
    ElMessage.error(error.message || '获取题目列表失败')
  } finally {
    loading.value = false
  }
}

const updateQuery = (params) => {
  query.value = Object.fromEntries(Object.entries(params || {}).filter(([, value]) => value !== '' && value != null))
  page.value = 1
  fetchProblems()
}

onMounted(fetchProblems)
defineExpose({ updateQuery })
</script>

<style scoped>
.problem-list { min-height: 240px; }
.list-header { margin-bottom: 12px; }
.result-count { font-size: 13px; color: var(--lm-text-secondary); font-weight: 500; }
.list-cards { display: flex; flex-direction: column; gap: 8px; }
.problem-card { display: flex; gap: 16px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius); padding: 16px 18px; cursor: pointer; }
.card-index { width: 28px; padding-top: 2px; text-align: center; font-size: 14px; font-weight: 600; color: var(--lm-text-muted); }
.card-body { flex: 1; min-width: 0; }
.card-top { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.card-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--lm-text-primary); }
.card-tags, .card-meta { display: flex; gap: 8px; flex-wrap: wrap; }
.card-tags { margin-bottom: 8px; }
.card-tag { padding: 2px 8px; border-radius: 4px; background: var(--lm-bg-secondary); font-size: 11px; color: var(--lm-text-secondary); }
.card-meta { gap: 16px; }
.meta-item { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--lm-text-muted); }
.pagination-wrap { display: flex; justify-content: center; margin-top: 24px; }
</style>
