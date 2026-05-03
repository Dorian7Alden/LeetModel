<template>
  <div class="skill-page">
    <h2 class="page-title">技能分析</h2>

    <el-row :gutter="24">
      <!-- 雷达图 -->
      <el-col :lg="12" :sm="24">
        <div class="chart-card">
          <h3 class="chart-title">五维能力雷达</h3>
          <div ref="radarRef" class="chart-container"></div>
        </div>
      </el-col>

      <!-- 柱状图 -->
      <el-col :lg="12" :sm="24">
        <div class="chart-card">
          <h3 class="chart-title">各维度得分对比</h3>
          <div ref="barRef" class="chart-container"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 技能总结 -->
    <div class="summary-section">
      <h3 class="section-title">技能评估总结</h3>
      <el-row :gutter="20">
        <el-col :md="12" :sm="24" v-for="item in skillSummary" :key="item.label">
          <div class="summary-card" :class="item.level">
            <div class="summary-header">
              <span class="summary-label">{{ item.label }}</span>
              <el-tag :type="item.tagType" size="small">{{ item.levelLabel }}</el-tag>
            </div>
            <div class="summary-bar-wrap">
              <div class="summary-bar">
                <div
                  class="summary-fill"
                  :style="{ width: item.percent + '%', background: item.color }"
                ></div>
              </div>
              <span class="summary-score">{{ item.score }}/100</span>
            </div>
            <p class="summary-advice">{{ item.advice }}</p>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { mockUserProfile } from '@/mock/data.js'
import * as echarts from 'echarts'

const radarRef = ref(null)
const barRef = ref(null)
let radarChart = null
let barChart = null

const skillScores = mockUserProfile.skillScores

const skillSummary = [
  {
    label: '模型设计',
    score: 78,
    percent: 78,
    color: '#409eff',
    tagType: 'primary',
    level: 'medium',
    levelLabel: '良好',
    advice: '模型选择思路清晰，建议多尝试不同模型组合，提升模型创新性。',
  },
  {
    label: '算法实现',
    score: 85,
    percent: 85,
    color: '#67c23a',
    tagType: 'success',
    level: 'good',
    levelLabel: '优秀',
    advice: '代码实现能力强，可进一步学习高级算法优化技巧。',
  },
  {
    label: '数据处理',
    score: 72,
    percent: 72,
    color: '#e6a23c',
    tagType: 'warning',
    level: 'medium',
    levelLabel: '良好',
    advice: '数据处理流程完整，建议加强特征工程和异常值处理的深度。',
  },
  {
    label: '结果分析',
    score: 80,
    percent: 80,
    color: '#8b5cf6',
    tagType: 'primary',
    level: 'good',
    levelLabel: '优秀',
    advice: '可视化效果出色，建议增加更多统计学角度的深入分析。',
  },
  {
    label: '论文写作',
    score: 68,
    percent: 68,
    color: '#f56c6c',
    tagType: 'danger',
    level: 'weak',
    levelLabel: '待提升',
    advice: '论文结构和表达有待加强，建议阅读优秀论文并练习学术写作。',
  },
  {
    label: '综合能力',
    score: 77,
    percent: 77,
    color: '#0891b2',
    tagType: 'info',
    level: 'medium',
    levelLabel: '良好',
    advice: '各维度发展较为均衡，建议保持优势维度并重点补齐论文写作短板。',
  },
]

function renderRadarChart() {
  if (!radarRef.value) return
  radarChart = echarts.init(radarRef.value)
  radarChart.setOption({
    tooltip: {
      trigger: 'item',
    },
    radar: {
      center: ['50%', '50%'],
      radius: '72%',
      indicator: [
        { name: '模型设计', max: 100 },
        { name: '算法实现', max: 100 },
        { name: '数据处理', max: 100 },
        { name: '结果分析', max: 100 },
        { name: '论文写作', max: 100 },
      ],
      axisName: {
        color: '#666',
        fontSize: 12,
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(64, 158, 255, 0.02)', 'rgba(64, 158, 255, 0.02)'],
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: skillScores.map((s) => s.score),
            name: '你的能力值',
            areaStyle: {
              color: 'rgba(64, 158, 255, 0.2)',
            },
            lineStyle: {
              color: '#409eff',
              width: 2,
            },
            itemStyle: {
              color: '#409eff',
            },
            symbol: 'circle',
            symbolSize: 6,
          },
        ],
      },
    ],
  })
}

function renderBarChart() {
  if (!barRef.value) return
  barChart = echarts.init(barRef.value)
  const cats = skillScores.map((s) => s.dimension)
  const scores = skillScores.map((s) => s.score)
  barChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    grid: {
      left: '3%',
      right: '8%',
      bottom: '5%',
      top: '8%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: cats,
      axisLabel: {
        fontSize: 11,
        color: '#666',
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      interval: 20,
      axisLabel: {
        formatter: '{value}',
      },
    },
    series: [
      {
        type: 'bar',
        data: scores.map((v, i) => ({
          value: v,
          itemStyle: {
            color: ['#409eff', '#67c23a', '#e6a23c', '#8b5cf6', '#f56c6c'][i],
            borderRadius: [6, 6, 0, 0],
          },
        })),
        barWidth: '48%',
        label: {
          show: true,
          position: 'top',
          formatter: '{c}',
          fontSize: 12,
          fontWeight: 600,
          color: '#333',
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 8,
            shadowColor: 'rgba(0,0,0,0.15)',
          },
        },
      },
    ],
  })
}

function handleResize() {
  radarChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  renderRadarChart()
  renderBarChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  radarChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.skill-page {
  padding: 24px 30px 40px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 24px;
}

/* Charts */
.chart-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
}

.chart-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 16px;
}

.chart-container {
  width: 100%;
  height: 340px;
}

/* Summary Section */
.summary-section {
  margin-top: 8px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 16px;
}

.summary-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 14px;
  transition: all 0.2s;
}

.summary-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.summary-card.weak {
  border-left: 4px solid #f56c6c;
}

.summary-card.medium {
  border-left: 4px solid #e6a23c;
}

.summary-card.good {
  border-left: 4px solid #67c23a;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.summary-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary, #1a1a2e);
}

.summary-bar-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.summary-bar {
  flex: 1;
  height: 8px;
  background: var(--lm-bg, #f0f2f5);
  border-radius: 8px;
  overflow: hidden;
}

.summary-fill {
  height: 100%;
  border-radius: 8px;
  transition: width 0.6s ease;
}

.summary-score {
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  white-space: nowrap;
}

.summary-advice {
  font-size: 13px;
  color: var(--lm-text-muted, #999);
  margin: 0;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .chart-container {
    height: 260px;
  }
}
</style>
