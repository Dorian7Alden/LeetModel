<template>
  <div class="solve-stats-card" @click="$router.push('/profile/analysis')">
    <div class="card-header">
      <h3>解题统计</h3>
      <span class="view-link">技能分析 <el-icon :size="12"><ArrowRight /></el-icon></span>
    </div>

    <div class="stats-content">
      <div class="total-solved">
        <span class="total-num">{{ profile.totalSolved }}</span>
        <span class="total-label">总解题数</span>
      </div>

      <div class="difficulty-bars">
        <div class="diff-item">
          <span class="diff-label">入门</span>
          <el-progress :percentage="diffPercent(profile.easySolved)" :stroke-width="8" color="#16a34a" :show-text="false" />
          <span class="diff-count">{{ profile.easySolved }}</span>
        </div>
        <div class="diff-item">
          <span class="diff-label">中等</span>
          <el-progress :percentage="diffPercent(profile.mediumSolved)" :stroke-width="8" color="#d97706" :show-text="false" />
          <span class="diff-count">{{ profile.mediumSolved }}</span>
        </div>
        <div class="diff-item">
          <span class="diff-label">困难</span>
          <el-progress :percentage="diffPercent(profile.hardSolved)" :stroke-width="8" color="#dc2626" :show-text="false" />
          <span class="diff-count">{{ profile.hardSolved }}</span>
        </div>
      </div>

      <div class="weekly-change">
        <el-icon :size="14" color="#16a34a"><CaretTop /></el-icon>
        <span>本周新增 {{ profile.weeklySolved }} 题</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight, CaretTop } from '@element-plus/icons-vue'
import { mockUserProfile } from '@/mock/data.js'

const profile = mockUserProfile

function diffPercent(count) {
  return Math.round((count / profile.totalSolved) * 100)
}
</script>

<style scoped>
.solve-stats-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow var(--lm-transition);
}

.solve-stats-card:hover {
  box-shadow: var(--lm-shadow);
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px;
}

.card-header h3 { font-size: 16px; font-weight: 700; margin: 0; color: var(--lm-text-primary); }

.view-link {
  font-size: 12px; color: var(--lm-primary); display: flex; align-items: center; gap: 4px; font-weight: 500;
}

.stats-content {
  display: flex; align-items: center; gap: 24px;
}

.total-solved {
  display: flex; flex-direction: column; align-items: center;
  min-width: 70px;
}

.total-num {
  font-size: 36px; font-weight: 800; color: var(--lm-primary); line-height: 1.1;
}

.total-label {
  font-size: 12px; color: var(--lm-text-muted); margin-top: 4px;
}

.difficulty-bars {
  flex: 1; display: flex; flex-direction: column; gap: 10px;
}

.diff-item {
  display: flex; align-items: center; gap: 8px;
}

.diff-label {
  font-size: 12px; color: var(--lm-text-secondary); width: 32px;
}

.diff-item :deep(.el-progress) { flex: 1; }

.diff-count {
  font-size: 13px; font-weight: 600; color: var(--lm-text-primary); width: 24px; text-align: right;
}

.weekly-change {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--lm-success); font-weight: 500;
  margin-top: 10px;
}

@media (max-width: 600px) {
  .stats-content { flex-direction: column; }
}
</style>
