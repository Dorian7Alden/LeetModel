<template>
  <nav class="admin-subnav" :aria-label="ariaLabel">
    <button
      v-for="item in items"
      :key="item.value"
      class="subnav-item"
      :class="{ active: modelValue === item.value }"
      type="button"
      :disabled="item.disabled"
      :aria-current="modelValue === item.value ? 'page' : undefined"
      @click="$emit('update:modelValue', item.value)"
    >
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <span v-if="item.count !== undefined" class="subnav-count">{{ item.count }}</span>
    </button>
  </nav>
</template>

<script setup>
defineProps({
  modelValue: { type: String, required: true },
  items: { type: Array, default: () => [] },
  ariaLabel: { type: String, default: "工作域导航" },
});

defineEmits(["update:modelValue"]);
</script>

<style scoped>
.admin-subnav {
  display: flex;
  min-width: 0;
  gap: var(--lm-admin-space-1);
  overflow-x: auto;
  border-bottom: 1px solid var(--lm-admin-border);
  scrollbar-width: none;
}

.admin-subnav::-webkit-scrollbar {
  display: none;
}

.subnav-item {
  position: relative;
  display: inline-flex;
  min-height: 40px;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  color: var(--lm-admin-text-muted);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.subnav-item::after {
  position: absolute;
  right: 10px;
  bottom: -1px;
  left: 10px;
  height: 2px;
  background: transparent;
  content: "";
}

.subnav-item:hover:not(:disabled) {
  color: var(--lm-admin-text-strong);
  background: var(--lm-admin-surface-subtle);
}

.subnav-item.active {
  color: var(--lm-admin-primary);
}

.subnav-item.active::after {
  background: var(--lm-admin-primary);
}

.subnav-item:focus-visible {
  outline: 2px solid var(--lm-admin-primary);
  outline-offset: -2px;
}

.subnav-item:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.subnav-count {
  min-width: 18px;
  padding: 0 5px;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  border-radius: 9px;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  text-align: center;
}
</style>
