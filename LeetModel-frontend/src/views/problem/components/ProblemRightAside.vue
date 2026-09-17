<template>
  <aside class="problem-right-aside">
    <!-- 1. 按有效练习队伍数排序的热门题目 -->
    <div class="aside-card popular-card">
      <div class="aside-card-header">
        <div class="header-title">
          <span>热门练习题</span>
        </div>
      </div>
      <div v-if="popularLoading" class="popular-list" aria-label="热门练习题加载中" aria-busy="true">
        <div v-for="index in 3" :key="index" class="popular-skeleton">
          <div class="popular-item-inner">
            <span class="skeleton-rank"></span>
            <span class="skeleton-title"></span>
            <span class="skeleton-count"></span>
          </div>
        </div>
      </div>
      <div v-else-if="popularProblems.length" class="popular-list">
        <button
          v-for="(problem, index) in displayPopularProblems"
          :key="problem.problemId"
          type="button"
          class="popular-item"
          @click="emit('select-popular', problem)"
        >
          <div class="popular-item-inner">
            <span
              class="popular-rank-trophy"
              :class="`trophy-rank-${index + 1}`"
              :title="`最热练习榜第 ${index + 1} 名`"
            >
              <TrophyIcon :size="15" :stroke-width="2" aria-hidden="true" />
            </span>
            <el-tooltip
              :content="problem.problemTitle"
              placement="top"
              effect="light"
              popper-class="problem-tooltip"
              :show-after="200"
            >
              <span class="popular-copy">
                <span class="popular-title">{{ problem.problemTitle }}</span>
              </span>
            </el-tooltip>
            <span class="popular-count" :aria-label="`${formatPracticeCount(problem.practiceCount)} 次练习`">
              <span class="count-num">{{ formatPracticeCount(problem.practiceCount) }}</span>
              <FlameIcon :size="13" :stroke-width="1.9" aria-hidden="true" />
            </span>
          </div>
        </button>
      </div>
      <div v-else class="popular-empty">暂无练习记录</div>
    </div>

    <!-- 2. 热门考向标签云展示 -->
    <div class="aside-card">
      <div class="aside-card-header">
        <div class="header-title">
          <span>热门标签</span>
        </div>
      </div>
      <div class="trending-tags-cloud">
        <span
          v-for="tag in trendingTags"
          :key="tag.id"
          class="cloud-tag"
          @click="emit('select-tag', tag)"
        >
          <span class="cloud-tag-name">{{ tag.name }}</span>
          <small class="tag-count">{{ tag.count }}</small>
        </span>
      </div>
    </div>

    <nav class="problem-aside-footer" aria-label="帮助链接">
      <router-link to="/about">关于我们</router-link>
      <span aria-hidden="true">·</span>
      <router-link to="/help">使用帮助</router-link>
      <span aria-hidden="true">·</span>
      <router-link to="/contact">联系我们</router-link>
    </nav>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Flame as FlameIcon, Trophy as TrophyIcon } from '@lucide/vue'

const props = defineProps({
  tags: { type: Array, default: () => [] },
  popularProblems: { type: Array, default: () => [] },
  popularLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['select-popular', 'select-tag'])

const formatPracticeCount = (value) => `${Number(value) || 0}`

// 固定取热度最高的前 3 道题目
const displayPopularProblems = computed(() => {
  return props.popularProblems.slice(0, 3)
})

// 热门考向标签云 (展示高频算法与领域)
const trendingTags = computed(() => {
  const hotNames = [
    { name: '线性规划', problemCount: 18, practiceCount: 3200 },
    { name: '层次分析法', problemCount: 14, practiceCount: 2800 },
    { name: '回归分析', problemCount: 12, practiceCount: 2400 },
    { name: '蒙特卡洛', problemCount: 10, practiceCount: 1900 },
    { name: '交通物流', problemCount: 8, practiceCount: 1600 },
    { name: '公共健康', problemCount: 6, practiceCount: 1300 },
    { name: '环境生态', problemCount: 5, practiceCount: 1100 },
    { name: '经济金融', problemCount: 4, practiceCount: 900 },
  ]
  return hotNames.map(h => {
    const match = props.tags.find(t => t.name.includes(h.name) || h.name.includes(t.name))
    const problemCount = Number(match?.problemCount ?? match?.usageCount ?? h.problemCount)
    const practiceCount = Number(match?.practiceCount ?? match?.practiceUsers ?? h.practiceCount)
    return {
      id: match?.id || h.name,
      name: h.name,
      count: problemCount,
      practiceCount,
      rawTag: match
    }
  }).sort((a, b) => b.practiceCount - a.practiceCount)
})
</script>

<style scoped>
.problem-right-aside {
  position: sticky;
  top: 72px;
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: visible;
}
.problem-aside-footer {
  padding: 8px 4px;
  color: var(--lm-text-muted);
  font-size: 12px;
  line-height: 1.6;
  text-align: left;
}
.problem-aside-footer a {
  color: inherit;
  text-decoration: none;
  transition: color var(--lm-transition);
}
.problem-aside-footer a:hover {
  color: var(--lm-primary);
}

.aside-card {
  background: #ffffff;
  border: 0;
  border-radius: 8px;
  padding: 16px;
  color: #71717a;
  box-shadow: 0 6px 18px rgba(30, 41, 59, 0.085);
}
.aside-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

/* 热门练习题卡片去除底部内边距，靠内部 item 自身留白保持上下视觉平衡 */
.aside-card.popular-card {
  padding-bottom: 0;
}

/* 热门练习题的 header 去除 bottom 边距 */
.popular-card .aside-card-header {
  margin-bottom: 0;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #5f6068;
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 热门练习题 */
.popular-list {
  min-height: auto;
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
}
.popular-item {
  width: 100%;
  min-height: 52px;
  display: flex;
  align-items: center;
  padding: 6px 4px;
  border: 0;
  border-bottom: 1px solid #f0f0f2;
  background: transparent;
  color: #71717a;
  text-align: left;
  cursor: pointer;
  transition: background var(--lm-transition), color var(--lm-transition);
}
.popular-item:last-child {
  border-bottom: 0;
}
.popular-item:hover {
  background: #fafafa;
  color: #52525b;
}

.popular-item-inner {
  width: 100%;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: flex-end;
}

.popular-rank-trophy {
  display: inline-flex;
  align-items: flex-end;
  justify-content: center;
  width: 24px;
  height: 18px;
  border-radius: 4px;
  flex-shrink: 0;
  padding-bottom: 1px;
}

/* 冠亚季军奖杯颜色区分 */
.popular-rank-trophy.trophy-rank-1 {
  color: #f59e0b; /* 冠军金 */
}

.popular-rank-trophy.trophy-rank-2 {
  color: #94a3b8; /* 亚军银 */
}

.popular-rank-trophy.trophy-rank-3 {
  color: #b45309; /* 季军铜 */
}
.popular-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.popular-title {
  overflow: hidden;
  color: #6b6c74;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.popular-count {
  display: inline-flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 3px;
  color: #ea580c;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--lm-code-font-family);
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
  white-space: nowrap;
  padding-bottom: 1px;
}
.count-num {
  color: inherit;
  line-height: 1;
}
.popular-count svg {
  color: inherit;
  flex-shrink: 0;
  margin-bottom: 1px;
}
.popular-skeleton {
  min-height: 52px;
  display: flex;
  align-items: center;
  padding: 6px 4px;
  border-bottom: 1px solid #f0f0f2;
}
.skeleton-rank,
.skeleton-title,
.skeleton-count {
  height: 8px;
  border-radius: 999px;
  background: #f0f0f2;
}
.skeleton-rank { width: 14px; justify-self: center; }
.skeleton-title { width: 82%; }
.skeleton-count { width: 48px; }
.popular-empty {
  min-height: 162px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a1a1aa;
  font-size: 11px;
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
  padding: 4px 6px 4px 10px;
  background: #f4f4f5;
  border: 1px solid transparent;
  border-radius: 999px;
  color: #71717a;
  font-size: 11px;
  font-weight: 400;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.cloud-tag:hover {
  color: #52525b;
  border-color: #e4e4e7;
  background: #eeeef0;
}
.tag-count {
  display: inline-flex;
  min-width: 20px;
  height: 18px;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border-radius: 999px;
  background: #ffa116;
  color: #ffffff;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

@media (max-width: 1280px) {
  .problem-right-aside {
    position: static;
    width: 100%;
    overflow: visible;
  }
}
</style>
