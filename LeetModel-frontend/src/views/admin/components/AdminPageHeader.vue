<template>
  <header class="admin-page-header">
    <div class="page-heading">
      <h2>{{ title }}</h2>
      <span v-if="updatedAt" class="page-updated">更新于 {{ updatedAt }}</span>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions"></slot>
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  updatedAt: { type: String, default: "" },
});
</script>

<style scoped>
.admin-page-header {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: var(--lm-admin-space-4);
}

.page-heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--lm-admin-space-3);
}

.page-heading h2 {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.2px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-updated {
  flex: 0 0 auto;
  color: var(--lm-admin-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.page-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--lm-admin-space-2);
}

@media (max-width: 767px) {
  .admin-page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .page-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
