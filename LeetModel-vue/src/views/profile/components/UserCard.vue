<template>
  <div class="user-card">
    <div class="card-banner"></div>
    <div class="card-body">
      <div class="avatar-ring">
        <div class="avatar-inner">{{ profile.username.charAt(0) }}</div>
      </div>
      <h3 class="username">{{ profile.username }}</h3>
      <p class="school">{{ profile.school }}</p>

      <div class="stats-row">
        <div class="stats-item">
          <span class="stats-value">{{ formatNum(profile.ranking) }}</span>
          <span class="stats-label">排名</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ profile.totalSolved }}</span>
          <span class="stats-label">解题</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ profile.followers }}</span>
          <span class="stats-label">粉丝</span>
        </div>
        <div class="stats-item">
          <span class="stats-value">{{ profile.following }}</span>
          <span class="stats-label">关注</span>
        </div>
      </div>

      <div class="progress-section">
        <div class="progress-header">
          <span>完成率</span>
          <span>{{ completionPercent }}%</span>
        </div>
        <el-progress :percentage="completionPercent" :stroke-width="8" :color="'#2563eb'" />
      </div>

      <el-button class="edit-btn" @click="$router.push('/profile/settings')" round>
        <el-icon><EditPen /></el-icon>
        编辑资料
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { EditPen } from '@element-plus/icons-vue'
import { mockUserProfile } from '@/mock/data.js'

const profile = mockUserProfile

const completionPercent = computed(() => {
  const max = 100
  return Math.round((profile.totalSolved / max) * 100)
})

function formatNum(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
</script>

<style scoped>
.user-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  overflow: hidden;
  text-align: center;
}

.card-banner {
  height: 80px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
}

.card-body {
  padding: 0 20px 24px;
  position: relative;
}

.avatar-ring {
  width: 90px; height: 90px;
  border-radius: 50%;
  background: var(--lm-surface);
  border: 4px solid var(--lm-surface);
  box-shadow: var(--lm-shadow);
  margin: -45px auto 12px;
  display: flex; align-items: center; justify-content: center;
}

.avatar-inner {
  width: 76px; height: 76px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-size: 28px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}

.username {
  font-size: 18px; font-weight: 700; color: var(--lm-text-primary); margin: 0;
}

.school {
  font-size: 13px; color: var(--lm-text-secondary); margin: 4px 0 16px;
}

.stats-row {
  display: flex; justify-content: space-around;
  padding: 16px 0; border-top: 1px solid var(--lm-border-light); border-bottom: 1px solid var(--lm-border-light);
  margin-bottom: 16px;
}

.stats-item {
  display: flex; flex-direction: column; align-items: center;
}

.stats-value {
  font-size: 18px; font-weight: 700; color: var(--lm-text-primary);
}

.stats-label {
  font-size: 11px; color: var(--lm-text-muted); margin-top: 2px;
}

.progress-section {
  margin-bottom: 16px;
}

.progress-header {
  display: flex; justify-content: space-between;
  font-size: 12px; color: var(--lm-text-secondary); margin-bottom: 8px;
}

.edit-btn {
  width: 100%;
  font-weight: 500;
}
</style>
