<template>
  <div class="activity-card" @click="$router.push('/profile/history')">
    <div class="card-header">
      <h3>提交记录</h3>
      <span class="total-label">{{ totalSubmissions }} 次提交</span>
    </div>

    <div ref="chartRef" class="chart-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { mockUserProfile } from '@/mock/data.js'

const chartRef = ref(null)
let chart = null
const totalSubmissions = mockUserProfile.totalSubmissions

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)

  const data = mockUserProfile.historyData.map(d => [d.date, d.count])

  chart.setOption({
    tooltip: {
      position: 'top',
      formatter: p => `${p.data[0]}: ${p.data[1]} 次提交`
    },
    visualMap: {
      min: 0, max: 4,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#ebedf0', '#c6e48b', '#7bc96f', '#239a3b', '#196127'] },
      show: false,
    },
    calendar: {
      top: 10, left: 30, right: 10,
      range: ['2026-04-26', '2026-05-03'],
      cellSize: ['auto', 14],
      yearLabel: { show: false },
      monthLabel: { show: false },
      dayLabel: {
        firstDay: 1,
        fontSize: 10,
        color: '#94a3b8',
      },
      itemStyle: {
        borderWidth: 3,
        borderColor: '#fff',
        borderRadius: 2,
      },
    },
    series: [{
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: data,
    }],
  })

  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
})
</script>

<style scoped>
.activity-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 20px;
  cursor: pointer;
  transition: box-shadow var(--lm-transition);
}

.activity-card:hover { box-shadow: var(--lm-shadow); }

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}

.card-header h3 { font-size: 16px; font-weight: 700; margin: 0; color: var(--lm-text-primary); }

.total-label { font-size: 12px; color: var(--lm-text-muted); }

.chart-container { width: 100%; height: 160px; }
</style>
