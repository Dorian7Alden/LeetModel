<template>
  <div class="history-page">
    <div class="page-header-row">
      <h2 class="page-title">提交历史</h2>
      <div class="filter-row">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="default"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          class="date-picker"
        />
        <el-select
          v-model="statusFilter"
          placeholder="状态筛选"
          size="default"
          class="status-select"
          clearable
        >
          <el-option label="全部状态" value="" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="评审中" value="EVALUATING" />
        </el-select>
      </div>
      <router-link to="/profile" class="back-link">
        <el-icon :size="16"><ArrowLeft /></el-icon>
        <span>返回</span>
      </router-link>
    </div>

    <div class="card-grid" v-if="visibleList.length > 0">
      <div
        v-for="item in visibleList"
        :key="item.submissionId"
        class="submission-card"
      >
        <h3 class="card-problem-title">{{ item.problemTitle || '题目 #' + item.problemId }}</h3>
        <p class="card-submission-title">{{ item.title }}</p>

        <div class="card-footer">
          <span class="card-time">{{ item.submitTime }}</span>
          <div class="card-right">
            <el-tag
              :type="item.status === 'COMPLETED' ? 'success' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ item.status === 'COMPLETED' ? '已完成' : '评审中' }}
            </el-tag>
            <span
              v-if="item.totalScore !== null"
              class="card-score"
              :class="scoreClass(item.totalScore)"
            >
              {{ item.totalScore.toFixed(1) }} 分
            </span>
            <span v-else class="card-score evaluating">-- 分</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="filteredAll.length === 0" class="empty-state">
      <el-empty description="暂无提交记录" />
    </div>

    <div
      v-if="hasMore"
      ref="loadMoreRef"
      class="load-more"
      v-loading="loadingMore"
    >
      <span v-if="!loadingMore">下拉加载更多</span>
    </div>

    <div v-else-if="visibleList.length > 0" class="load-more done">
      已加载全部记录
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { mockSubmissions } from '@/mock/data.js'

const BATCH_SIZE = 8

const dateRange = ref(null)
const statusFilter = ref('')
const loadingMore = ref(false)
const loadMoreRef = ref(null)

const submissions = ref([...mockSubmissions].sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime)))

const displayCount = ref(BATCH_SIZE)
let observer = null

const filteredAll = computed(() => {
  let list = submissions.value
  if (statusFilter.value) {
    list = list.filter((s) => s.status === statusFilter.value)
  }
  if (dateRange.value && dateRange.value.length === 2) {
    const [start, end] = dateRange.value
    list = list.filter((s) => s.submitTime >= start && s.submitTime <= end)
  }
  return list
})

const visibleList = computed(() => filteredAll.value.slice(0, displayCount.value))

const hasMore = computed(() => displayCount.value < filteredAll.value.length)

function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  setTimeout(() => {
    displayCount.value = Math.min(displayCount.value + BATCH_SIZE, filteredAll.value.length)
    loadingMore.value = false
  }, 400)
}

watch([dateRange, statusFilter], () => {
  displayCount.value = BATCH_SIZE
})

onMounted(async () => {
  await nextTick()
  if (loadMoreRef.value) {
    observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && hasMore.value) {
        loadMore()
      }
    }, { threshold: 0.1 })
    observer.observe(loadMoreRef.value)
  }
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

function scoreClass(score) {
  if (score >= 90) return 'score-high'
  if (score >= 80) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.history-page {
  padding: 24px 30px 40px;
}

.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 24px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--lm-text-secondary);
  text-decoration: none;
  font-size: 14px;
  padding: 6px 10px;
  border-radius: var(--lm-radius-sm);
  transition: color var(--lm-transition), background var(--lm-transition);
}

.back-link:hover {
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0;
}

.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.date-picker {
  width: 250px;
}

.status-select {
  width: 130px;
}

/* ===== Card Grid ===== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.submission-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: var(--lm-radius-lg);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.submission-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--lm-shadow-lg);
}

.card-problem-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--lm-primary);
  margin: 0;
}

.card-submission-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ===== Card Footer ===== */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--lm-border-light);
}

.card-time {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.card-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-score {
  font-size: 15px;
  font-weight: 700;
}

.score-high { color: #67c23a; }
.score-mid { color: var(--lm-primary, #409eff); }
.score-low { color: #f56c6c; }

.evaluating {
  font-size: 12px;
  color: var(--lm-text-muted);
  font-weight: 500;
}

/* ===== Empty ===== */
.empty-state {
  padding: 60px 0;
}

/* ===== Load More ===== */
.load-more {
  display: flex;
  justify-content: center;
  padding: 28px 0 8px;
  font-size: 13px;
  color: var(--lm-text-muted);
}

.load-more.done {
  color: var(--lm-text-muted);
}

/* ===== Responsive ===== */
@media (max-width: 1400px) {
  .card-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1000px) {
  .card-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .history-page {
    padding: 16px 20px 32px;
  }

  .page-header-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-row {
    flex-direction: column;
    width: 100%;
  }

  .date-picker,
  .status-select {
    width: 100%;
  }

  .card-grid { grid-template-columns: 1fr; }
}
</style>
