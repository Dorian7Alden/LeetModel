<template>
  <div class="contest-list">
    <div
      class="contest-card card-hover"
      v-for="contest in contests"
      :key="contest.id"
      @click="$emit('select', contest.id)"
    >
      <div class="card-banner" :style="{ background: gradientForType(contest.type) }">
        <span class="banner-type">{{ contest.shortTitle }}</span>
        <span class="banner-status" :style="{ background: contest.statusColor }">{{ contest.status }}</span>
      </div>
      <div class="card-body">
        <h3 class="card-title">{{ contest.title }}</h3>
        <div class="card-meta">
          <span class="meta-item">
            <el-icon :size="14"><Calendar /></el-icon>
            {{ formatDate(contest.startTime) }}
          </span>
          <span class="meta-item">
            <el-icon :size="14"><User /></el-icon>
            {{ formatCount(contest.participantCount) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Calendar, User } from '@element-plus/icons-vue'

defineProps({
  contests: { type: Array, default: () => [] }
})

defineEmits(['select'])

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
.contest-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
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
  height: 70px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 14px;
}

.banner-type {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
}

.banner-status {
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
  padding: 2px 8px;
  border-radius: 12px;
}

.card-body {
  padding: 14px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 10px;
  line-height: 1.4;
}

.card-meta {
  display: flex;
  justify-content: space-between;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}
</style>
