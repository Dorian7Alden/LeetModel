<template>
  <div class="contest-detail-page" v-if="contest">
    <!-- Hero -->
    <div class="detail-hero" :style="{ background: gradientForType(contest.type) }">
      <div class="hero-inner">
        <div class="hero-badge">{{ contest.shortTitle }}</div>
        <h1 class="hero-title">{{ contest.title }}</h1>
        <div class="hero-status" :style="{ background: contest.statusColor }">{{ contest.status }}</div>
      </div>
    </div>

    <div class="detail-container">
      <!-- Info Panels -->
      <div class="info-panels">
        <div class="info-panel">
          <el-icon :size="20"><Calendar /></el-icon>
          <div class="info-content">
            <span class="info-label">比赛时间</span>
            <span class="info-value">{{ contest.startTime }} — {{ contest.endTime }}</span>
          </div>
        </div>
        <div class="info-panel">
          <el-icon :size="20"><EditPen /></el-icon>
          <div class="info-content">
            <span class="info-label">报名时间</span>
            <span class="info-value">{{ contest.signUpStartTime }} — {{ contest.signUpEndTime }}</span>
          </div>
        </div>
        <div class="info-panel">
          <el-icon :size="20"><User /></el-icon>
          <div class="info-content">
            <span class="info-label">参赛人数</span>
            <span class="info-value">{{ formatCount(contest.participantCount) }} 人</span>
          </div>
        </div>
        <div class="info-panel">
          <el-icon :size="20"><OfficeBuilding /></el-icon>
          <div class="info-content">
            <span class="info-label">主办方</span>
            <span class="info-value">{{ contest.organizer }}</span>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="赛事介绍" name="intro">
          <div class="tab-content">
            <h3>赛事简介</h3>
            <p>{{ contest.introduction }}</p>

            <h3>奖项设置</h3>
            <div class="prize-list">
              <el-tag
                v-for="(prize, idx) in contest.prizeLevels"
                :key="prize"
                :type="prizeTypes[idx] || 'info'"
                size="large"
              >{{ prize }}</el-tag>
            </div>

            <h3>赛程安排</h3>
            <el-timeline>
              <el-timeline-item
                v-for="phase in contest.phases"
                :key="phase.name"
                :timestamp="phase.start + ' — ' + phase.end"
                placement="top"
              >
                <strong>{{ phase.name }}</strong>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>

        <el-tab-pane label="排行榜" name="rank">
          <div class="tab-content">
            <ContestRank :contest-id="contest.id" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="作品提交" name="submit">
          <div class="tab-content">
            <FileSubmit :contest-id="contest.id" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- Side Actions -->
      <div class="side-actions">
        <el-button
          v-if="contest.status === '报名中'"
          type="primary"
          size="large"
          style="width: 100%"
        >立即报名</el-button>
        <el-button
          v-else-if="contest.status === '未开始'"
          type="primary"
          size="large"
          disabled
          style="width: 100%"
        >报名未开始</el-button>
        <el-tag v-else size="large" style="width: 100%; text-align: center; justify-content: center">
          {{ contest.status }}
        </el-tag>
      </div>
    </div>
  </div>

  <div v-else class="loading-state" v-loading="true" style="min-height: 400px"></div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Calendar, EditPen, User, OfficeBuilding } from '@element-plus/icons-vue'
import ContestRank from '../components/ContestRank.vue'
import FileSubmit from '../components/FileSubmit.vue'
import { mockContests } from '@/mock/data.js'

const route = useRoute()
const contest = ref(null)
const activeTab = ref('intro')

const prizeTypes = ['danger', 'warning', '', 'info', '']

onMounted(() => {
  const id = Number(route.params.id)
  contest.value = mockContests.find(c => c.id === id) || mockContests[0]
})

function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
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
.contest-detail-page {
  min-height: 100vh;
  background: var(--lm-bg);
}

.detail-hero {
  padding: 48px 0;
  color: #ffffff;
}

.hero-inner {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 20px;
  text-align: center;
}

.hero-badge {
  display: inline-block;
  font-size: 14px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  padding: 4px 16px;
  border-radius: 20px;
  margin-bottom: 16px;
}

.hero-title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 16px;
}

.hero-status {
  display: inline-block;
  font-size: 14px;
  font-weight: 600;
  padding: 4px 20px;
  border-radius: 20px;
}

.detail-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

/* Info Panels */
.info-panels {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.info-panel {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-label {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.info-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--lm-text-primary);
}

/* Tabs */
.detail-tabs {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 0 20px;
}

.tab-content {
  padding: 20px 0;
}

.tab-content h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 24px 0 12px;
}

.tab-content h3:first-child {
  margin-top: 0;
}

.tab-content p {
  font-size: 14px;
  color: var(--lm-text-secondary);
  line-height: 1.8;
}

.prize-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* Side Actions */
.side-actions {
  margin-top: 20px;
}

@media (max-width: 1024px) {
  .info-panels { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .info-panels { grid-template-columns: 1fr; }
  .hero-title { font-size: 22px; }
}
</style>
