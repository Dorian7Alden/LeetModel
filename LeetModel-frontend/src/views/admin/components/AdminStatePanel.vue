<template>
  <section class="admin-state-panel" :class="`state-${type}`" :role="type === 'error' ? 'alert' : 'status'">
    <span class="state-icon">
      <el-icon><component :is="resolvedIcon" /></el-icon>
    </span>
    <div class="state-copy">
      <strong>{{ title }}</strong>
      <p v-if="$slots.default"><slot></slot></p>
    </div>
    <div v-if="actionLabel || $slots.actions" class="state-actions">
      <slot name="actions">
        <el-button @click="$emit('action')">{{ actionLabel }}</el-button>
      </slot>
    </div>
  </section>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  type: { type: String, default: "empty" },
  title: { type: String, required: true },
  actionLabel: { type: String, default: "" },
  icon: { type: String, default: "" },
});

defineEmits(["action"]);

const iconByType = {
  empty: "Box",
  filtered: "Filter",
  offline: "Connection",
  forbidden: "Lock",
  notFound: "DocumentDelete",
  error: "WarningFilled",
};

const resolvedIcon = computed(() => props.icon || iconByType[props.type] || "InfoFilled");
</script>

<style scoped>
.admin-state-panel {
  display: flex;
  min-height: 112px;
  align-items: center;
  gap: var(--lm-admin-space-3);
  padding: var(--lm-admin-space-4);
  color: var(--lm-admin-text-default);
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.state-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  color: var(--lm-admin-info);
  background: var(--lm-admin-surface-subtle);
  border-radius: var(--lm-admin-radius-control);
}

.state-error .state-icon,
.state-offline .state-icon {
  color: var(--lm-admin-danger);
  background: #fef2f2;
}

.state-forbidden .state-icon {
  color: var(--lm-admin-warning);
  background: #fffbeb;
}

.state-copy {
  min-width: 0;
  flex: 1;
}

.state-copy strong {
  color: var(--lm-admin-text-strong);
  font-size: 14px;
}

.state-copy p {
  margin: 4px 0 0;
  color: var(--lm-admin-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.state-actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--lm-admin-space-2);
}

@media (max-width: 767px) {
  .admin-state-panel {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .state-actions {
    width: 100%;
    padding-left: 48px;
  }
}
</style>
