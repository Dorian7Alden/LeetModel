<template>
  <section class="problem-list" v-loading="initialLoading">
    <div v-if="displayedProblems.length" class="list-table">
      <div class="table-head">
        <span>题目</span><span>赛事</span><span>标签</span>
        <button class="sortable-head" @click="cycleSort('year')">年份<span class="sort-indicator" :class="sortState('year')"><span>▲</span><span>▼</span></span></button>
        <span>语言</span>
        <button class="sortable-head" @click="cycleSort('difficulty')">难度<span class="sort-indicator" :class="sortState('difficulty')"><span>▲</span><span>▼</span></span></button>
        <button class="sortable-head" @click="cycleSort('averageScore')">平均分<span class="sort-indicator" :class="sortState('averageScore')"><span>▲</span><span>▼</span></span></button>
        <span class="col-center">收藏</span>
        <span></span>
      </div>
      <button v-for="(item, index) in displayedProblems" :key="item.id" class="problem-row" @click="$router.push(`/problem/${item.id}`)">
        <div class="problem-main">
          <h3 :title="item.title"><span class="row-index">{{ String(index + 1).padStart(2, '0') }}</span>{{ item.title }}</h3>
        </div>
        <span class="contest-name" :title="item.contestName || '未分类赛事'">{{ item.contestName || '未分类赛事' }}</span>
        <div class="problem-tags"><span v-for="tag in item.tagNames?.slice(0, 3)" :key="tag" class="problem-tag" :title="tag">{{ tag }}</span></div>
        <span class="year-value">{{ item.year || '—' }}</span>
        <span class="language-value">{{ item.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
        <el-tag :type="difficultyType(item.difficulty)" size="small" effect="plain">{{ difficultyLabel(item.difficulty) }}</el-tag>
        <div class="average-score"><strong>{{ formatScore(item.averageScore) }}</strong></div>
        
        <!-- 收藏星星操作 -->
        <button
          type="button"
          class="fav-star-btn"
          :class="{ active: isFavorited(item.id) }"
          :title="isFavorited(item.id) ? '已收藏，点击取消' : '收藏此题'"
          @click.stop="toggleFavorite(item.id)"
        >
          <el-icon><StarFilled v-if="isFavorited(item.id)" /><Star v-else /></el-icon>
        </button>

        <el-icon class="row-arrow"><ArrowRight /></el-icon>
      </button>

      <!-- 底部流式懒加载锚点与状态提示 (彻底废弃分页栏) -->
      <div ref="sentinelRef" class="feed-footer">
        <div v-if="loadingMore" class="loading-more-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在加载更多题目...</span>
        </div>
        <div v-else-if="problems.length < total" class="load-more-action">
          <button type="button" class="load-more-btn" @click="loadMore">
            <span>向下滚动或点击加载更多 (已展示 {{ problems.length }} / 共 {{ total }} 题)</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
        </div>
        <div v-else class="all-loaded-state">
          <span>— 已展示全部 {{ displayedProblems.length }} 道题目 —</span>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!initialLoading" :description="emptyText" />
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowDown, ArrowRight, Loading, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemList } from '@/api/problem'

const problems = ref([])
const emit = defineEmits(['fav-change'])
const initialLoading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const query = ref({})
const sortBy = ref('')
const sortOrder = ref('')
const sentinelRef = ref(null)
let observer = null
const displayMode = ref('all') // 'all' | 'favorite' | 'in_progress' | 'completed'
const specialIds = ref([])

// 本地收藏题目状态
const favoritedIds = ref(JSON.parse(localStorage.getItem('lm_fav_problems') || '[]'))

const displayedProblems = computed(() => {
  if (displayMode.value === 'favorite') {
    return problems.value.filter(p => isFavorited(p.id))
  }
  if (displayMode.value === 'in_progress' || displayMode.value === 'completed') {
    if (!specialIds.value.length) return []
    return problems.value.filter(p => specialIds.value.includes(Number(p.id)) || specialIds.value.includes(String(p.id)))
  }
  return problems.value
})

const emptyText = computed(() => {
  if (displayMode.value === 'favorite') return '暂无收藏题目，可点击题目右侧小星星加入收藏'
  if (displayMode.value === 'in_progress') return '暂无正在实训练习的赛题'
  if (displayMode.value === 'completed') return '暂无已完成实训练习的赛题'
  return '暂无符合条件的题目'
})

const isFavorited = (id) => favoritedIds.value.includes(String(id))
const toggleFavorite = (id) => {
  const sId = String(id)
  if (favoritedIds.value.includes(sId)) {
    favoritedIds.value = favoritedIds.value.filter(i => i !== sId)
    ElMessage.info('已取消收藏')
  } else {
    favoritedIds.value.push(sId)
    ElMessage.success('已加入我的收藏题单')
  }
  localStorage.setItem('lm_fav_problems', JSON.stringify(favoritedIds.value))
  emit('fav-change', favoritedIds.value.length)
}

const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatScore = (score) => Number(score) > 0 ? Number(score).toFixed(1) : '-'

const fetchProblems = async (isLoadMore = false) => {
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    initialLoading.value = true
  }

  try {
    const response = await getPublicProblemList({
      page: page.value,
      pageSize: pageSize.value,
      ...query.value,
      ...(sortBy.value ? { sortBy: sortBy.value, sortOrder: sortOrder.value } : {})
    })
    const rows = response.data?.rows || []
    total.value = response.data?.total || 0
    if (isLoadMore) {
      problems.value.push(...rows)
    } else {
      problems.value = rows
    }
  } catch (error) {
    if (!isLoadMore) {
      problems.value = []
      total.value = 0
    }
    ElMessage.error(error.message || '获取题目列表失败')
  } finally {
    initialLoading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => {
  if (loadingMore.value || initialLoading.value || problems.value.length >= total.value) return
  page.value++
  fetchProblems(true)
}

const updateQuery = (params) => {
  query.value = Object.fromEntries(Object.entries(params || {}).filter(([, value]) => value !== '' && value != null))
  page.value = 1
  displayMode.value = 'all'
  fetchProblems(false)
}

const setDisplayMode = (mode, ids = []) => {
  displayMode.value = mode
  specialIds.value = ids
}

const sortState = (field) => sortBy.value === field ? sortOrder.value : 'none'
const cycleSort = (field) => {
  if (sortBy.value !== field) { sortBy.value = field; sortOrder.value = 'desc' }
  else if (sortOrder.value === 'desc') sortOrder.value = 'asc'
  else { sortBy.value = ''; sortOrder.value = '' }
  page.value = 1
  fetchProblems(false)
}

onMounted(() => {
  fetchProblems(false)
  emit('fav-change', favoritedIds.value.length)
  if (typeof IntersectionObserver !== 'undefined') {
    observer = new IntersectionObserver((entries) => {
      const entry = entries[0]
      if (entry && entry.isIntersecting && problems.value.length < total.value && !loadingMore.value && !initialLoading.value) {
        loadMore()
      }
    }, { rootMargin: '100px' })
    if (sentinelRef.value) observer.observe(sentinelRef.value)
  }
})

onUnmounted(() => {
  if (observer) observer.disconnect()
})

defineExpose({
  updateQuery,
  setDisplayMode,
  getFavoritedIds: () => favoritedIds.value
})
</script>

<style scoped>
.problem-list { min-height: 320px; }
.list-table { overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03); }
.table-head, .problem-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(110px, .55fr) minmax(110px, .65fr) 56px 48px 56px 60px 32px 18px;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}
.table-head > :nth-child(n + 4):nth-child(-n + 7) { text-align: center; }
.col-center { text-align: center; }
.table-head { min-height: 42px; background: var(--lm-bg); border-bottom: 1px solid var(--lm-border); color: var(--lm-text-muted); font-size: 11px; font-weight: 700; }
.sortable-head { display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.sortable-head:hover { color: #18181b; }
.sort-indicator { display: inline-flex; flex-direction: column; align-items: center; justify-content: center; width: 9px; color: #a1a1aa; font-size: 7px; line-height: 6px; }
.sort-indicator.asc span:first-child { color: #18181b; }
.sort-indicator.desc span:last-child { color: #18181b; }
.problem-row { position: relative; width: 100%; min-height: 62px; border: 0; border-bottom: 1px solid var(--lm-border-light); background: transparent; color: inherit; text-align: left; cursor: pointer; transition: background var(--lm-transition), box-shadow var(--lm-transition); }
.problem-row::before { content: ''; position: absolute; inset: 14px auto 14px 0; width: 3px; border-radius: 0 3px 3px 0; background: #18181b; opacity: 0; transition: opacity var(--lm-transition); }
.problem-row:last-child { border-bottom: 0; }
.problem-row:hover { background: #f4f4f5; }
.problem-row:hover::before { opacity: 1; }
.problem-main { min-width: 0; }
.problem-main h3 { margin: 0; overflow: hidden; color: var(--lm-text-primary); font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.row-index { display: inline-block; min-width: 26px; margin-right: 9px; color: #18181b; font-size: 10px; font-weight: 800; letter-spacing: 0.05em; }
.contest-name { overflow: hidden; color: var(--lm-text-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.problem-tags { display: flex; align-items: center; gap: 5px; min-width: 0; overflow: hidden; }
.problem-tag { max-width: 82px; overflow: hidden; padding: 2px 7px; border-radius: 999px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.year-value, .language-value { text-align: center; color: var(--lm-text-secondary); font-size: 12px; }
.problem-row > .el-tag { justify-self: center; }
.average-score { display: flex; align-items: center; justify-content: center; }
.average-score strong { color: var(--lm-text-primary); font-size: 16px; }
.row-arrow { color: var(--lm-text-muted); transition: transform var(--lm-transition), color var(--lm-transition); }
.problem-row:hover .row-arrow { color: #18181b; transform: translateX(3px); }

/* 收藏小星星 */
.fav-star-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
  justify-self: center;
}
.fav-star-btn:hover {
  color: #18181b;
  background: #e4e4e7;
  transform: scale(1.1);
}
.fav-star-btn.active {
  color: #18181b;
}

/* 流式懒加载底栏 */
.feed-footer {
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-top: 1px solid var(--lm-border-light);
}
.loading-more-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--lm-text-muted);
  font-size: 13px;
}
.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  background: #ffffff;
  color: var(--lm-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.load-more-btn:hover {
  color: var(--lm-primary);
  border-color: var(--lm-primary-light);
  background: var(--lm-primary-bg);
}
.all-loaded-state {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.05em;
}
@media (max-width: 1000px) { .table-head { display: none; } .problem-row { grid-template-columns: minmax(0, 1fr) 90px 70px 20px; gap: 12px; padding: 12px 16px; } .problem-row > .el-tag, .year-value, .language-value, .problem-tags { display: none; } }
@media (max-width: 760px) { .pagination-wrap { flex-wrap: wrap; gap: 10px 14px; } }
@media (max-width: 600px) { .problem-row { grid-template-columns: minmax(0, 1fr) 60px 18px; } .contest-name { display: none; } }
</style>
