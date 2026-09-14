<template>
  <span class="admin-status-badge" :class="`tone-${resolvedTone}`">
    <span class="status-indicator"></span>
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  status: { type: String, default: "UNKNOWN" },
  label: { type: String, default: "" },
  tone: { type: String, default: "" },
});

const toneByStatus = {
  RUNNING: "running",
  LEASED: "running",
  WAITING: "neutral",
  PENDING: "neutral",
  COMPLETED: "success",
  SUCCEEDED: "success",
  HEALTHY: "success",
  WARNING: "warning",
  UNKNOWN: "warning",
  FAILED: "danger",
  ERROR: "danger",
  OFFLINE: "danger",
};

const resolvedTone = computed(() => props.tone || toneByStatus[props.status] || "neutral");
</script>

<style scoped>
.admin-status-badge {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  color: var(--badge-color);
  background: var(--badge-background);
  border: 1px solid var(--badge-border);
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.status-indicator {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  background: currentColor;
  border-radius: 50%;
}

.tone-neutral {
  --badge-color: var(--lm-admin-info);
  --badge-background: #f1f5f9;
  --badge-border: #e2e8f0;
}

.tone-running {
  --badge-color: var(--lm-admin-primary);
  --badge-background: #eff6ff;
  --badge-border: #bfdbfe;
}

.tone-success {
  --badge-color: var(--lm-admin-success);
  --badge-background: #f0fdf4;
  --badge-border: #bbf7d0;
}

.tone-warning {
  --badge-color: var(--lm-admin-warning);
  --badge-background: #fffbeb;
  --badge-border: #fde68a;
}

.tone-danger {
  --badge-color: var(--lm-admin-danger);
  --badge-background: #fef2f2;
  --badge-border: #fecaca;
}
</style>
