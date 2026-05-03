<template>
  <section class="contest-section">
    <div class="section-header">
      <div class="section-title-group">
        <h2 class="section-title">
          <el-icon :size="20"><Trophy /></el-icon>
          进行中的竞赛
        </h2>
        <span class="section-badge">{{ activeCount }} 场进行中</span>
      </div>
      <router-link class="view-all" to="/contest">
        查看全部 <el-icon :size="14"><ArrowRight /></el-icon>
      </router-link>
    </div>

    <div class="contest-grid" v-if="displayContests.length > 0">
      <div
        class="contest-card card-hover"
        v-for="contest in displayContests"
        :key="contest.id"
        @click="$router.push(`/contest/${contest.id}`)"
      >
        <div class="card-top">
          <div class="contest-type-badge" :style="{ background: contest.typeColor }">
            {{ contest.shortTitle }}
          </div>
          <div class="contest-status-tag" :style="{ color: contest.statusColor, background: contest.statusColor + '18' }">
            {{ contest.status }}
          </div>
        </div>
        <h3 class="contest-title">{{ contest.title }}</h3>
        <div class="contest-meta">
          <span class="meta-item">
            <el-icon :size="14"><Calendar /></el-icon>
            {{ formatDateRange(contest.startTime, contest.endTime) }}
          </span>
          <span class="meta-item">
            <el-icon :size="14"><User /></el-icon>
            {{ formatCount(contest.participantCount) }} 人报名
          </span>
        </div>
        <div v-if="contest.status === '报名中' || contest.status === '即将开始'" class="countdown-bar">
          <el-icon :size="14"><Clock /></el-icon>
          <span>{{ getCountdownText(contest) }}</span>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-empty description="暂无进行中的竞赛" />
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Trophy, ArrowRight, Calendar, User, Clock } from '@element-plus/icons-vue'
import { mockContests } from '@/mock/data.js'

const contests = ref([])
const now = ref(Date.now())
let timer = null

onMounted(() => {
  contests.value = mockContests
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const activeCount = computed(() => contests.value.filter(c => c.status === '报名中' || c.status === '进行中').length)

const displayContests = computed(() => {
  return [...contests.value]
    .filter(c => c.status !== '未开始' || new Date(c.signUpStartTime) <= new Date(Date.now() + 30 * 86400000))
    .sort((a, b) => {
      const order = { '进行中': 0, '报名中': 1, '即将开始': 2, '未开始': 3 }
      return (order[a.status] ?? 4) - (order[b.status] ?? 4)
    })
    .slice(0, 4)
})

function formatDateRange(start, end) {
  const fmt = (d) => {
    const date = new Date(d)
    return `${date.getMonth() + 1}/${date.getDate()}`
  }
  return `${fmt(start)} - ${fmt(end)}`
}

function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function getCountdownText(contest) {
  const target = contest.status === '报名中'
    ? new Date(contest.signUpEndTime)
    : new Date(contest.startTime)
  const diff = target - now.value
  if (diff <= 0) return '即将开始'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  if (days > 0) return `距${contest.status === '报名中' ? '报名截止' : '开始'}还有 ${days} 天 ${hours} 小时`
  const mins = Math.floor((diff % 3600000) / 60000)
  return `距${contest.status === '报名中' ? '报名截止' : '开始'}还有 ${hours} 小时 ${mins} 分钟`
}
</script>

<style scoped>
.contest-section {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.section-badge {
  font-size: 12px;
  font-weight: 500;
  color: var(--lm-success);
  background: var(--lm-success-bg);
  padding: 2px 10px;
  border-radius: 20px;
}

.view-all {
  font-size: 13px;
  color: var(--lm-primary);
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.view-all:hover {
  color: var(--lm-primary-dark);
}

.contest-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.contest-card {
  background: var(--lm-bg);
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius);
  padding: 18px;
  cursor: pointer;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.contest-type-badge {
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
  padding: 2px 8px;
  border-radius: 4px;
}

.contest-status-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.contest-title {
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

.contest-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}

.countdown-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 10px;
  background: var(--lm-warning-bg);
  border-radius: var(--lm-radius-sm);
  font-size: 12px;
  font-weight: 500;
  color: var(--lm-warning);
}

@media (max-width: 768px) {
  .contest-grid {
    grid-template-columns: 1fr;
  }
}
</style>
