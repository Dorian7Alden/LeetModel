<template>
  <div class="stat-card" :class="{ 'card-hover': hover }">
    <div class="stat-icon" :style="{ background: bgColor }">
      <el-icon :size="iconSize" :color="color">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="stat-info">
      <div class="stat-value">{{ value }}</div>
      <div class="stat-title">{{ title }}</div>
      <div v-if="trend" class="stat-trend" :class="{ 'trend-up': trendUp, 'trend-down': !trendUp }">
        <el-icon :size="12"><component :is="trendUp ? 'CaretTop' : 'CaretBottom'" /></el-icon>
        <span>{{ trend }}</span>
        <span v-if="subtitle" class="trend-subtitle">{{ subtitle }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  value: { type: [String, Number], required: true },
  icon: { type: String, required: true },
  color: { type: String, default: '#409eff' },
  bgColor: { type: String, default: '#ecf5ff' },
  iconSize: { type: [String, Number], default: 24 },
  trend: { type: String, default: '' },
  trendUp: { type: Boolean, default: true },
  subtitle: { type: String, default: '' },
  hover: { type: Boolean, default: true },
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--lm-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--lm-text-primary);
  line-height: 1.2;
}

.stat-title {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-top: 2px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 500;
}

.trend-up {
  color: var(--lm-success);
}

.trend-down {
  color: var(--lm-danger);
}

.trend-subtitle {
  color: var(--lm-text-muted);
  font-weight: 400;
  margin-left: 2px;
}
</style>
