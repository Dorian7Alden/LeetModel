<template>
  <div class="activity-card">
    <div class="card-header">
      <h3>提交记录</h3>
      <div class="header-right">
        <span class="total-label">共 {{ submissions.length }} 次提交</span>
        <router-link to="/profile/history" class="view-all-link">
          查看全部 <el-icon :size="12"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </div>

    <div class="submission-grid" v-if="recentSubmissions.length > 0">
      <div
        v-for="item in recentSubmissions"
        :key="item.submissionId"
        class="submission-item"
        @click="$router.push('/profile/history')"
      >
        <div class="item-top">
          <span class="item-problem">{{ item.problemTitle || '题目 #' + item.problemId }}</span>
          <el-tag
            :type="item.status === 'COMPLETED' ? 'success' : 'warning'"
            size="small"
            effect="plain"
          >
            {{ item.status === 'COMPLETED' ? '已完成' : '评审中' }}
          </el-tag>
        </div>
        <p class="item-title">{{ item.title }}</p>
        <div class="item-footer">
          <span class="item-time">{{ item.submitTime }}</span>
          <span
            v-if="item.totalScore !== null"
            class="item-score"
            :class="scoreClass(item.totalScore)"
          >
            {{ item.totalScore.toFixed(1) }} 分
          </span>
          <span v-else class="item-score evaluating">-- 分</span>
        </div>
      </div>
    </div>

    <div v-else class="empty-hint">暂无提交记录</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { mockSubmissions } from '@/mock/data.js'

const submissions = ref([...mockSubmissions].sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime)))
const recentSubmissions = ref(submissions.value.slice(0, 4))

function scoreClass(score) {
  if (score >= 90) return 'score-high'
  if (score >= 80) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.activity-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: var(--lm-text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.total-label {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.view-all-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--lm-primary);
  text-decoration: none;
  transition: color var(--lm-transition);
}

.view-all-link:hover {
  color: var(--lm-primary-dark);
}

/* ===== Grid ===== */
.submission-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.submission-item {
  padding: 10px 12px;
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius-sm);
  cursor: pointer;
  transition: background var(--lm-transition), box-shadow var(--lm-transition);
}

.submission-item:hover {
  background: var(--lm-bg-secondary);
  box-shadow: var(--lm-shadow-xs);
}

.item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 3px;
}

.item-problem {
  font-size: 11px;
  font-weight: 500;
  color: var(--lm-primary);
}

.item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 6px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-time {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.item-score {
  font-size: 13px;
  font-weight: 700;
}

.score-high { color: #67c23a; }
.score-mid { color: var(--lm-primary, #409eff); }
.score-low { color: #f56c6c; }

.evaluating {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 500;
}

.empty-hint {
  text-align: center;
  color: var(--lm-text-muted);
  font-size: 13px;
  padding: 24px 0;
}

</style>
