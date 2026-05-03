<template>
  <div class="contest-page">
    <div class="page-container">
      <!-- Header -->
      <PageHeader title="赛事列表" description="全国各类数学建模竞赛信息汇总，报名参赛一站掌握">
        <template #actions>
          <el-button type="primary" :icon="Bell" round>赛事订阅</el-button>
        </template>
      </PageHeader>

      <!-- Stats Summary -->
      <div class="stats-row">
        <div class="stat-item" v-for="s in summaryStats" :key="s.label">
          <span class="stat-num" :style="{ color: s.color }">{{ s.count }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
      </div>

      <!-- Filters -->
      <div class="filter-bar">
        <div class="filter-tabs">
          <el-radio-group v-model="filterType" size="small">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="国赛">国赛</el-radio-button>
            <el-radio-button value="美赛">美赛</el-radio-button>
            <el-radio-button value="校赛">校赛</el-radio-button>
            <el-radio-button value="国际赛">国际赛</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-right">
          <el-select v-model="filterStatus" placeholder="赛事状态" size="small" clearable style="width: 130px">
            <el-option label="全部状态" value="all" />
            <el-option label="报名中" value="报名中" />
            <el-option label="进行中" value="进行中" />
            <el-option label="即将开始" value="即将开始" />
            <el-option label="未开始" value="未开始" />
          </el-select>
        </div>
      </div>

      <!-- Contest Grid -->
      <div class="contest-grid" v-if="filteredContests.length > 0">
        <div
          class="contest-card card-hover"
          v-for="contest in filteredContests"
          :key="contest.id"
          @click="$router.push(`/contest/${contest.id}`)"
        >
          <div class="card-banner" :style="{ background: gradientForType(contest.type) }">
            <div class="banner-type">{{ contest.shortTitle }}</div>
            <div class="banner-status" :style="{ background: contest.statusColor }">{{ contest.status }}</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ contest.title }}</h3>
            <div class="card-meta">
              <div class="meta-line">
                <el-icon :size="14"><Calendar /></el-icon>
                <span>比赛: {{ formatDate(contest.startTime) }} - {{ formatDate(contest.endTime) }}</span>
              </div>
              <div class="meta-line">
                <el-icon :size="14"><User /></el-icon>
                <span>{{ formatCount(contest.participantCount) }} 人参赛</span>
              </div>
              <div class="meta-line">
                <el-icon :size="14"><OfficeBuilding /></el-icon>
                <span>{{ contest.organizer }}</span>
              </div>
            </div>
            <div class="card-tags">
              <el-tag
                v-for="prize in contest.prizeLevels.slice(0, 3)"
                :key="prize"
                size="small"
                type="info"
              >{{ prize }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <el-empty description="暂无符合条件的赛事" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Bell, Calendar, User, OfficeBuilding } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { mockContests } from '@/mock/data.js'

const contests = ref(mockContests)
const filterType = ref('all')
const filterStatus = ref('all')

const summaryStats = computed(() => [
  { label: '进行中', count: contests.value.filter(c => c.status === '进行中').length, color: '#2563eb' },
  { label: '报名中', count: contests.value.filter(c => c.status === '报名中').length, color: '#16a34a' },
  { label: '即将开始', count: contests.value.filter(c => c.status === '即将开始').length, color: '#d97706' },
  { label: '全部赛事', count: contests.value.length, color: '#64748b' },
])

const filteredContests = computed(() => {
  return contests.value.filter(c => {
    if (filterType.value !== 'all' && c.type !== filterType.value) return false
    if (filterStatus.value !== 'all' && c.status !== filterStatus.value) return false
    return true
  })
})

function formatDate(d) {
  const date = new Date(d)
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`
}

function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function gradientForType(type) {
  const map = {
    '国赛': 'linear-gradient(135deg, #d97706, #f59e0b)',
    '美赛': 'linear-gradient(135deg, #2563eb, #3b82f6)',
    '校赛': 'linear-gradient(135deg, #16a34a, #22c55e)',
    '国际赛': 'linear-gradient(135deg, #0891b2, #06b6d4)',
  }
  return map[type] || 'linear-gradient(135deg, #64748b, #94a3b8)'
}
</script>

<style scoped>
.contest-page {
  min-height: 100vh;
  background: var(--lm-bg);
  padding-bottom: 40px;
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
}

.stat-num {
  display: block;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-top: 4px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.contest-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.contest-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  overflow: hidden;
  cursor: pointer;
}

.card-banner {
  height: 80px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
}

.banner-type {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.banner-status {
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
  padding: 2px 10px;
  border-radius: 12px;
}

.card-body {
  padding: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.meta-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}

.card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

@media (max-width: 1024px) {
  .contest-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .contest-grid { grid-template-columns: 1fr; }
  .stats-row { flex-wrap: wrap; }
  .stat-item { flex: 0 0 calc(50% - 8px); }
}
</style>
