<template>
  <div class="achievement-page">
    <h2 class="page-title">我的成就</h2>

    <div class="badge-grid">
      <div
        v-for="badge in badges"
        :key="badge.id"
        :class="['badge-card', { locked: !badge.unlocked }]"
      >
        <div class="badge-icon" :style="{ background: badge.unlocked ? badge.color : '#d9dce1' }">
          <el-icon v-if="badge.unlocked" :size="28">
            <component :is="badge.icon" />
          </el-icon>
          <el-icon v-else :size="28"><Lock /></el-icon>
        </div>
        <div class="badge-info">
          <span class="badge-name">{{ badge.name }}</span>
          <span class="badge-desc">{{ badge.description }}</span>
          <div v-if="badge.unlocked && badge.unlockDate" class="unlock-date">
            获得于 {{ badge.unlockDate }}
          </div>
          <div v-else class="unlock-hint">尚未解锁</div>
        </div>
      </div>
    </div>

    <!-- 总体进度 -->
    <div class="progress-section">
      <h3 class="section-subtitle">成就进度</h3>
      <div class="progress-bar-wrap">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <span class="progress-text">{{ unlockedCount }} / {{ badges.length }} 已解锁</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  CircleCheck,
  Calendar,
  Trophy,
  Star,
  ChatLineSquare,
  Medal,
  Lock,
} from '@element-plus/icons-vue'
import { mockUserProfile } from '@/mock/data.js'

const badges = computed(() => mockUserProfile.badges)

const unlockedCount = computed(() => badges.value.filter((b) => b.unlocked).length)
const progressPercent = computed(() =>
  badges.value.length > 0 ? Math.round((unlockedCount.value / badges.value.length) * 100) : 0
)
</script>

<style scoped>
.achievement-page {
  padding: 24px 30px 40px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 24px;
}

/* Badge Grid */
.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.badge-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 22px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.25s;
}

.badge-card:not(.locked):hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
  border-color: var(--lm-primary, #409eff);
}

.badge-card.locked {
  opacity: 0.55;
  background: var(--lm-bg, #f8f9fb);
}

.badge-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.badge-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.badge-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
}

.badge-desc {
  font-size: 13px;
  color: var(--lm-text-secondary, #666);
}

.unlock-date {
  font-size: 11px;
  color: var(--lm-text-muted, #999);
  margin-top: 2px;
}

.unlock-hint {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

/* Progress Section */
.progress-section {
  margin-top: 32px;
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 22px 24px;
}

.section-subtitle {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 14px;
}

.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}

.progress-bar {
  flex: 1;
  height: 10px;
  background: var(--lm-bg, #f0f2f5);
  border-radius: 10px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #67c23a);
  border-radius: 10px;
  transition: width 0.6s ease;
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-secondary, #666);
  white-space: nowrap;
}

@media (max-width: 900px) {
  .badge-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .badge-grid {
    grid-template-columns: 1fr;
  }
}
</style>
