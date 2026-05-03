<template>
  <div class="problem-detail" v-if="problem">
    <!-- Header -->
    <div class="detail-header">
      <div class="detail-top">
        <h1 class="detail-title">{{ problem.problemTitle }}</h1>
        <div class="detail-badges">
          <el-tag :type="difficultyType(problem.difficulty)" effect="plain" size="default">
            {{ problem.difficulty }}
          </el-tag>
          <el-tag type="info" effect="plain" size="default">{{ problem.language }}</el-tag>
        </div>
      </div>

      <div class="detail-stats">
        <div class="stat-item">
          <span class="stat-value">{{ problem.aveScore }}</span>
          <span class="stat-label">平均分</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ problem.submissionCount }}</span>
          <span class="stat-label">提交数</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ (problem.passRate * 100).toFixed(0) }}%</span>
          <span class="stat-label">通过率</span>
        </div>
      </div>
    </div>

    <!-- Tags -->
    <div class="detail-tags" v-if="problem.tags">
      <span class="tag-label">标签：</span>
      <el-tag v-for="t in problem.tags" :key="t" size="small" class="detail-tag">{{ t }}</el-tag>
    </div>

    <el-divider />

    <!-- Content -->
    <div class="detail-content">
      <h3>题目描述</h3>
      <p>{{ problem.description }}</p>

      <div class="info-grid">
        <div class="info-card">
          <el-icon :size="18" color="#2563eb"><Clock /></el-icon>
          <div>
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ problem.createTime }}</span>
          </div>
        </div>
        <div class="info-card">
          <el-icon :size="18" color="#16a34a"><Refresh /></el-icon>
          <div>
            <span class="info-label">最后更新</span>
            <span class="info-value">{{ problem.updateTime }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="detail-actions">
      <el-button type="primary" size="large" :icon="Upload">
        提交作品
      </el-button>
      <el-button size="large" :icon="StarFilled">
        收藏题目
      </el-button>
      <el-button size="large" :icon="Comment">
        查看讨论
      </el-button>
    </div>

    <!-- Related -->
    <div class="related-problems" v-if="relatedProblems.length > 0">
      <h3>相关题目</h3>
      <div class="related-list">
        <div
          class="related-item"
          v-for="rp in relatedProblems"
          :key="rp.problemId"
          @click="$router.push(`/problem/${rp.problemId}`)"
        >
          <span class="related-title">{{ rp.problemTitle }}</span>
          <span class="related-score">{{ rp.aveScore }} 分</span>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="loading-state" v-loading="true" style="min-height: 400px"></div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Clock, Refresh, Upload, StarFilled, Comment } from '@element-plus/icons-vue'
import { mockProblems } from '@/mock/data.js'

const route = useRoute()
const problem = ref(null)

onMounted(() => {
  const id = Number(route.params.id)
  problem.value = mockProblems.find(p => p.problemId === id) || mockProblems[0]
})

const relatedProblems = computed(() => {
  if (!problem.value) return []
  return mockProblems
    .filter(p => p.problemId !== problem.value.problemId)
    .filter(p => p.tags && problem.value.tags && p.tags.some(t => problem.value.tags.includes(t)))
    .slice(0, 3)
})

function difficultyType(d) {
  const map = { '入门': 'success', '中等': 'warning', '困难': 'danger', '挑战': 'danger' }
  return map[d] || 'info'
}
</script>

<style scoped>
.problem-detail {
  max-width: 900px;
}

.detail-header {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 24px;
  margin-bottom: 20px;
}

.detail-top {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 16px; margin-bottom: 20px;
}

.detail-title {
  font-size: 24px; font-weight: 800; color: var(--lm-text-primary);
  margin: 0; line-height: 1.3;
}

.detail-badges { display: flex; gap: 8px; flex-shrink: 0; }

.detail-stats {
  display: flex; gap: 32px;
}

.stat-item {
  display: flex; flex-direction: column;
}

.stat-value {
  font-size: 22px; font-weight: 700; color: var(--lm-text-primary);
}

.stat-label {
  font-size: 12px; color: var(--lm-text-muted); margin-top: 2px;
}

.detail-tags {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-bottom: 16px;
}

.tag-label {
  font-size: 13px; color: var(--lm-text-secondary); font-weight: 500;
}

.detail-tag { }

.detail-content {
  padding: 0;
}

.detail-content h3 {
  font-size: 17px; font-weight: 700; color: var(--lm-text-primary);
  margin: 0 0 12px;
}

.detail-content p {
  font-size: 14px; line-height: 1.8;
  color: var(--lm-text-secondary);
  margin: 0 0 20px;
}

.info-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;
  margin-bottom: 20px;
}

.info-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px;
  background: var(--lm-bg);
  border-radius: var(--lm-radius);
}

.info-label {
  display: block; font-size: 12px; color: var(--lm-text-muted);
}

.info-value {
  font-size: 14px; font-weight: 600; color: var(--lm-text-primary);
}

.detail-actions {
  display: flex; gap: 12px; margin: 24px 0;
}

.related-problems {
  margin-top: 32px;
}

.related-problems h3 {
  font-size: 16px; font-weight: 700; color: var(--lm-text-primary);
  margin: 0 0 12px;
}

.related-list {
  display: flex; flex-direction: column; gap: 8px;
}

.related-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.related-item:hover {
  border-color: var(--lm-primary);
  box-shadow: var(--lm-shadow-sm);
}

.related-title {
  font-size: 14px; font-weight: 500; color: var(--lm-text-primary);
}

.related-score {
  font-size: 13px; color: var(--lm-primary); font-weight: 600;
}
</style>
