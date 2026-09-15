<template>
  <div class="skill-radar" :class="{ 'skill-radar--empty': !hasData }">
    <svg v-if="hasData" viewBox="0 0 100 100" role="img" :aria-label="radarDescription">
      <g class="radar-grid" aria-hidden="true">
        <polygon v-for="ratio in gridRatios" :key="ratio" :points="polygonPoints(ratio)" />
        <line v-for="point in axisPoints" :key="`${point.x}-${point.y}`" x1="50" y1="50" :x2="point.x" :y2="point.y" />
      </g>
      <polygon class="radar-value" :points="valuePoints" />
      <circle v-for="point in valuePointList" :key="`${point.x}-${point.y}`" :cx="point.x" :cy="point.y" r="1.5" />
    </svg>

    <div v-else class="radar-empty" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </div>

    <div v-if="hasData" class="radar-legend">
      <div v-for="item in items" :key="item.key">
        <span>{{ shortLabel(item.label) }}</span>
        <strong>{{ item.percent }}%</strong>
      </div>
    </div>
    <p v-else>能力图谱待生成</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
})

const gridRatios = [0.25, 0.5, 0.75, 1]
const hasData = computed(() => props.items.length >= 3)
const axisPoints = computed(() => pointsForValues(props.items.map(() => 1)))
const valuePointList = computed(() => pointsForValues(props.items.map((item) => item.percent / 100)))
const valuePoints = computed(() => serializePoints(valuePointList.value))
const radarDescription = computed(() => props.items.map((item) => `${item.label} ${item.percent}%`).join('，'))

function polygonPoints(ratio) {
  return serializePoints(pointsForValues(props.items.map(() => ratio)))
}

function pointsForValues(values) {
  const count = values.length
  return values.map((value, index) => {
    const angle = -Math.PI / 2 + (index * Math.PI * 2) / count
    const radius = 34 * Math.min(1, Math.max(0, value))
    return {
      x: Number((50 + Math.cos(angle) * radius).toFixed(2)),
      y: Number((50 + Math.sin(angle) * radius).toFixed(2)),
    }
  })
}

function serializePoints(points) {
  return points.map((point) => `${point.x},${point.y}`).join(' ')
}

function shortLabel(value) {
  return value
    .replace('与排版可读性', '')
    .replace('与假设符号规范', '')
    .replace('数学形式化', '')
    .replace('求解', '')
    .replace('合理性与', '')
}
</script>

<style scoped>
.skill-radar {
  --radar-size: 12rem;
  --radar-line: #dbeafe;
  --radar-axis: #e4e4e7;
  --radar-fill: rgba(37, 99, 235, 0.16);
  --radar-action: #2563eb;
  --radar-space: 0.75rem;
  display: grid;
  place-items: center;
  gap: var(--radar-space);
  margin-block-start: 1.25rem;
}

.skill-radar svg,
.radar-empty {
  width: min(100%, var(--radar-size));
  aspect-ratio: 1;
}

.radar-grid polygon,
.radar-grid line {
  fill: none;
  stroke: var(--radar-axis);
  stroke-width: 0.65;
}

.radar-grid polygon:last-of-type {
  stroke: var(--radar-line);
  stroke-width: 1;
}

.radar-value {
  fill: var(--radar-fill);
  stroke: var(--radar-action);
  stroke-linejoin: round;
  stroke-width: 1.4;
}

.skill-radar circle {
  fill: var(--radar-action);
  stroke: var(--lm-surface);
  stroke-width: 0.8;
}

.radar-legend {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem 0.75rem;
}

.radar-legend div {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.6875rem;
}

.radar-legend span {
  overflow: hidden;
  color: var(--lm-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.radar-legend strong {
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
  font-variant-numeric: tabular-nums;
}

.skill-radar--empty {
  min-height: var(--radar-size);
  align-content: center;
}

.radar-empty {
  position: relative;
  display: grid;
  place-items: center;
  border: 1px dashed var(--lm-border);
  border-radius: 50%;
  background: var(--lm-bg-secondary);
}

.radar-empty span {
  position: absolute;
  width: 68%;
  height: 1px;
  background: var(--lm-border);
}

.radar-empty span:first-child {
  transform: rotate(60deg);
}

.radar-empty span:last-child {
  transform: rotate(-60deg);
}

.skill-radar p {
  margin: 0;
  color: var(--lm-text-muted);
  font-size: 0.75rem;
}
</style>
