<template>
  <aside class="problem-right-aside">
    <!-- 1. 最火热备战题单卡片 -->
    <div class="aside-card">
      <div class="aside-card-header">
        <div class="header-title">
          <span class="fire-icon">🔥</span>
          <span>最火热备战题单</span>
        </div>
        <span class="header-sub">赛前必练</span>
      </div>
      <div class="curated-sets-list">
        <div
          v-for="set in curatedProblemsets"
          :key="set.id"
          class="curated-item"
          @click="emit('select-curated', set)"
        >
          <div class="curated-top">
            <span class="curated-tag" :class="set.tagClass">{{ set.badge }}</span>
            <span class="curated-heat">{{ set.heat }}</span>
          </div>
          <div class="curated-title">{{ set.title }}</div>
          <div class="curated-desc">{{ set.description }}</div>
        </div>
      </div>
    </div>

    <!-- 2. 热门考向标签云展示 -->
    <div class="aside-card">
      <div class="aside-card-header">
        <div class="header-title">
          <span class="tag-icon">🏷️</span>
          <span>高频考向热词</span>
        </div>
        <span class="header-sub">点击筛选</span>
      </div>
      <div class="trending-tags-cloud">
        <span
          v-for="tag in trendingTags"
          :key="tag.id"
          class="cloud-tag"
          @click="emit('select-tag', tag)"
        >
          {{ tag.name }}
          <small class="tag-count">{{ tag.count }}</small>
        </span>
      </div>
    </div>

    <!-- 3. 赛前格式避坑小贴士 -->
    <div class="aside-card tip-card">
      <div class="aside-card-header">
        <div class="header-title">
          <span class="pin-icon">📌</span>
          <span>赛前格式排雷贴士</span>
        </div>
      </div>
      <div class="tips-content">
        <div class="tip-line">
          <strong>国赛 AI 新规：</strong>使用 AI 辅助必须在支撑材料附录中提交《AI 工具使用详情》，严禁隐瞒。
        </div>
        <div class="tip-line">
          <strong>摘要定生死：</strong>首段必须包含背景、建模主方法、关键参数与核心数值结论，杜绝泛泛而谈。
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tags: { type: Array, default: () => [] }
})

const emit = defineEmits(['select-curated', 'select-tag'])

// 精选火热题单
const curatedProblemsets = [
  {
    id: 'cumcm-top5',
    badge: '国奖必刷',
    tagClass: 'badge-gold',
    title: '国赛国一必刷经典 Top 5',
    description: '涵盖生产决策、机理分析与定日镜场',
    heat: '🔥 3.2w 练过',
    query: { contestId: 2 }
  },
  {
    id: 'beginner-easy',
    badge: '新手起步',
    tagClass: 'badge-green',
    title: '零基础快速跑通起手题',
    description: '数据规整、模型套路标准，适合首测',
    heat: '🌱 1.8w 练过',
    query: { difficulty: 1 }
  },
  {
    id: 'mcm-intl',
    badge: '美赛精选',
    tagClass: 'badge-purple',
    title: '美赛 O 奖对标经典全英文题',
    description: '网球势头动态与碳交易预测',
    heat: '🌍 2.4w 练过',
    query: { contestId: 1 }
  }
]

// 热门考向标签云 (展示高频算法与领域)
const trendingTags = computed(() => {
  const hotNames = [
    { name: '线性规划', count: '18 题' },
    { name: '层次分析法', count: '14 题' },
    { name: '回归分析', count: '12 题' },
    { name: '蒙特卡洛', count: '10 题' },
    { name: '交通物流', count: '8 题' },
    { name: '公共健康', count: '6 题' },
    { name: '环境生态', count: '5 题' },
    { name: '经济金融', count: '4 题' },
  ]
  return hotNames.map(h => {
    const match = props.tags.find(t => t.name.includes(h.name) || h.name.includes(t.name))
    return {
      id: match?.id || h.name,
      name: h.name,
      count: h.count,
      rawTag: match
    }
  })
})
</script>

<style scoped>
.problem-right-aside {
  width: 270px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.aside-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}
.aside-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.header-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--lm-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.header-sub {
  font-size: 11px;
  color: var(--lm-text-muted);
}

/* 题单列表 */
.curated-sets-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.curated-item {
  padding: 10px 12px;
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius-sm);
  background: #ffffff;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.curated-item:hover {
  background: #f4f4f5;
  border-color: #18181b;
  transform: translateY(-1px);
}
.curated-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.curated-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
}
.badge-gold { background: #18181b; color: #ffffff; }
.badge-green { background: #f4f4f5; color: #18181b; border: 1px solid #e4e4e7; }
.badge-purple { background: #f4f4f5; color: #52525b; border: 1px solid #e4e4e7; }
.curated-heat {
  font-size: 11px;
  color: var(--lm-text-muted);
}
.curated-title {
  font-size: 13px;
  font-weight: 650;
  color: var(--lm-text-primary);
  line-height: 1.4;
}
.curated-desc {
  font-size: 11px;
  color: var(--lm-text-secondary);
  margin-top: 3px;
  line-height: 1.4;
}

/* 热门考向标签云 */
.trending-tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cloud-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #f4f4f5;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--lm-text-secondary);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.cloud-tag:hover {
  color: #ffffff;
  border-color: #18181b;
  background: #18181b;
}
.tag-count {
  font-size: 9px;
  color: var(--lm-text-muted);
}

/* 避坑贴士卡片 */
.tip-card {
  background: #fafafa;
  border-color: var(--lm-border);
}
.tips-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--lm-text-secondary);
}
.tip-line strong {
  color: var(--lm-text-primary);
}
</style>
