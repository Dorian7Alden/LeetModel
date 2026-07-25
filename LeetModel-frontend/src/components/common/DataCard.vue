<template>
  <div class="data-card" :class="{ 'card-hover': hover }">
    <div v-if="title || $slots.header" class="data-card-header">
      <h3 v-if="title" class="data-card-title">{{ title }}</h3>
      <slot name="header" />
    </div>
    <div class="data-card-body">
      <slot />
    </div>
    <div v-if="$slots.footer" class="data-card-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  hover: { type: Boolean, default: false },
})
</script>

<style scoped>
.data-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  overflow: hidden;
}

.data-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--lm-border-light);
}

.data-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0;
}

.data-card-body {
  padding: 20px;
}

.data-card-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--lm-border-light);
  background: var(--lm-bg);
}
</style>
