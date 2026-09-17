<template>
  <div class="admin-metric-strip" :aria-busy="loading">
    <component
      v-for="item in items"
      :key="item.key || item.label"
      :is="item.clickable ? 'button' : 'div'"
      class="metric-item"
      :class="{ interactive: item.clickable, unavailable: item.available === false }"
      :type="item.clickable ? 'button' : undefined"
      @click="item.clickable && $emit('select', item)"
    >
      <span class="metric-label">{{ item.label }}</span>
      <strong class="metric-value">
        {{ loading ? "···" : item.available === false ? "—" : formatValue(item.value) }}
      </strong>
      <span v-if="item.meta" class="metric-meta">{{ item.meta }}</span>
    </component>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
});

defineEmits(["select"]);

function formatValue(value) {
  if (typeof value === "number") return value.toLocaleString("zh-CN");
  return value ?? "—";
}
</script>

<style scoped>
.admin-metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  overflow: hidden;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.metric-item {
  display: flex;
  min-width: 0;
  min-height: 76px;
  align-items: flex-start;
  justify-content: center;
  flex-direction: column;
  padding: 12px 16px;
  text-align: left;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--lm-admin-border);
}

.metric-item:last-child {
  border-right: 0;
}

.metric-item.interactive {
  cursor: pointer;
}

.metric-item.interactive:hover {
  background: var(--lm-admin-surface-subtle);
}

.metric-item.interactive:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--lm-admin-primary);
  outline-offset: -2px;
}

.metric-item.unavailable {
  background: var(--lm-admin-surface-subtle);
}

.metric-label,
.metric-meta {
  overflow: hidden;
  max-width: 100%;
  color: var(--lm-admin-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-value {
  overflow: hidden;
  max-width: 100%;
  margin: 2px 0;
  color: var(--lm-admin-text-strong);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .admin-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item {
    border-bottom: 1px solid var(--lm-admin-border);
  }
}
</style>
