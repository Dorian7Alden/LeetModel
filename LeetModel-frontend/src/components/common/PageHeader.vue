<template>
  <div class="page-header">
    <div class="page-header-content">
      <div class="page-header-left">
        <h1 class="page-title">{{ title }}</h1>
        <p v-if="description" class="page-description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="page-header-actions">
        <slot name="actions" />
      </div>
    </div>
    <slot name="extra" />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin: 0;
  line-height: 1.3;
}

.page-description {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--lm-text-secondary);
  line-height: 1.5;
}

.page-header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
