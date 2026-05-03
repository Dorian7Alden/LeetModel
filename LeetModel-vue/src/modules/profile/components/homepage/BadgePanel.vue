<template>
  <div class="badge-panel" @click="$router.push('/profile/achievement')">
    <div class="card-header">
      <h3>勋章成就</h3>
      <span class="view-link">全部成就 <el-icon :size="12"><ArrowRight /></el-icon></span>
    </div>

    <div class="badge-grid">
      <div
        class="badge-item"
        v-for="badge in displayBadges"
        :key="badge.id"
        :class="{ locked: !badge.unlocked }"
      >
        <div class="badge-icon" :style="{ background: badge.unlocked ? badge.color : '#e2e8f0' }">
          <el-icon :size="20" :color="badge.unlocked ? '#fff' : '#94a3b8'">
            <component :is="badge.icon" />
          </el-icon>
        </div>
        <span class="badge-name">{{ badge.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
import { mockUserProfile } from '@/mock/data.js'

const displayBadges = mockUserProfile.badges.slice(0, 6)
</script>

<style scoped>
.badge-panel {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow var(--lm-transition);
}

.badge-panel:hover {
  box-shadow: var(--lm-shadow);
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}

.card-header h3 { font-size: 16px; font-weight: 700; margin: 0; color: var(--lm-text-primary); }

.view-link {
  font-size: 12px; color: var(--lm-primary); display: flex; align-items: center; gap: 4px; font-weight: 500;
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.badge-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}

.badge-item.locked {
  opacity: 0.5;
}

.badge-icon {
  width: 48px; height: 48px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}

.badge-name {
  font-size: 11px; color: var(--lm-text-secondary); text-align: center; line-height: 1.3;
}
</style>
