<template>
  <section class="problem-list" v-loading="initialLoading">
    <div v-if="displayedProblems.length" class="list-table">
      <button v-for="(item, index) in displayedProblems" :key="item.id" class="problem-row" @click="navigateToDetail(item.id)">
        <div class="problem-entities">
          <span class="row-index" :title="`题号 ${item.code ?? (index + 1)}`">{{ item.code ?? (index + 1) }}</span>
          <div class="problem-main">
            <el-tooltip :content="item.title" placement="top" effect="light" popper-class="problem-tooltip" :show-after="250">
              <h3>{{ item.title }}</h3>
            </el-tooltip>
          </div>
          <div class="problem-cell contest-cell">
            <el-tooltip :content="item.contestName || '未分类赛事'" placement="top" effect="light" popper-class="problem-tooltip" :show-after="250">
              <span class="contest-name">{{ contestLabel(item.contestName) }}</span>
            </el-tooltip>
          </div>
          <div class="problem-tags">
            <span v-for="tag in visibleTags(item)" :key="`${tag.type}-${tag.name}`" class="problem-tag" :title="tag.name">
              {{ tag.name }}
            </span>
          </div>
        </div>
        <div class="problem-meta">
          <span class="problem-cell year-value">{{ item.year || '—' }}</span>
          <span class="problem-cell difficulty-value" :class="difficultyClass(item.difficulty)">{{ difficultyLabel(item.difficulty) }}</span>
          <el-tooltip content="平均分" placement="top" effect="light" popper-class="problem-tooltip" :show-after="150">
            <div class="problem-cell average-score" aria-label="平均分"><strong>{{ formatScore(item.averageScore) }}</strong></div>
          </el-tooltip>

          <el-tooltip
            :content="userStore.isLogin ? '参与练习人数' : '登录后查看参与练习人数'"
            placement="top"
            effect="light"
            popper-class="problem-tooltip"
            :show-after="150"
          >
            <div class="problem-cell practice-count" aria-label="参与练习人数">
              <template v-if="userStore.isLogin">
                {{ item.practiceCount ?? item.practiceUserCount ?? item.teamsCount ?? 0 }}
              </template>
              <el-icon v-else><Lock /></el-icon>
            </div>
          </el-tooltip>

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
        </div>

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
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Loading, Lock, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemList } from '@/api/problem'
import { useUserStore } from '@/store/user'

const props = defineProps({
  tags: { type: Array, default: () => [] }
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const problems = ref([])
const emit = defineEmits(['fav-change', 'total-change'])
const navigateToDetail = (problemId) => {
  router.push({
    path: `/problem/${problemId}`,
    query: route.query
  })
}

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
const difficultyClass = (value) => ({ 1: 'difficulty-easy', 2: 'difficulty-medium', 3: 'difficulty-hard' })[value] || 'difficulty-unknown'
const formatScore = (score) => {
  const numericScore = Number(score)
  return Number.isFinite(numericScore) && numericScore > 0 ? numericScore.toFixed(1) : '0'
}
const tagTypeByName = computed(() => new Map(props.tags.map(tag => [tag.name, tag.type])))
const visibleTags = (problem) => (problem.tagNames || [])
  .map(name => ({ name, type: tagTypeByName.value.get(name) }))
  .filter(tag => tag.type === 'PROBLEM_TYPE')
const contestLabel = (name) => {
  if (!name) return '未分类'
  if (/力模|leetmodel/i.test(name)) return 'LEETMODEL'
  if (/美国|mcm|icm/i.test(name)) return 'MCM/ICM'
  if (/全国大学生数学建模竞赛|国赛|cumcm/i.test(name)) return 'CUMCM'
  return name.length > 10 ? `${name.slice(0, 10)}…` : name
}

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
    emit('total-change', total.value)
    if (isLoadMore) {
      problems.value.push(...rows)
    } else {
      problems.value = rows
    }
  } catch (error) {
    if (!isLoadMore) {
      problems.value = []
      total.value = 0
      emit('total-change', 0)
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

const cycleSort = (field) => {
  if (sortBy.value !== field) {
    sortBy.value = field
    sortOrder.value = field === 'code' ? 'asc' : 'desc'
  } else if (sortOrder.value === (field === 'code' ? 'asc' : 'desc')) {
    sortOrder.value = field === 'code' ? 'desc' : 'asc'
  } else {
    sortBy.value = ''
    sortOrder.value = ''
  }
  page.value = 1
  fetchProblems(false)
}
const clearSort = () => {
  sortBy.value = ''
  sortOrder.value = ''
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
  cycleSort,
  clearSort,
  getFavoritedIds: () => favoritedIds.value
})
</script>

<style scoped>
.problem-list { min-height: 320px; }
.list-table { overflow: visible; background: transparent; border: 0; border-radius: 0; box-shadow: none; }
.problem-row {
  --problem-row-grid: 16px;
  --problem-row-minor-gap: var(--problem-row-grid);
  --problem-row-micro-gap: 2.6px;
  --problem-row-outer-padding: 24px;
  --problem-row-meta-unit: 52px;
  display: flex;
  align-items: center;
  padding: 0 var(--problem-row-outer-padding);
}
.problem-row { position: relative; width: 100%; min-height: 44px; border: 0; border-radius: 8px; background: transparent; color: inherit; text-align: left; cursor: pointer; transition: background var(--lm-transition), color var(--lm-transition); }
.problem-row:nth-child(even) { background: #fafafa; }
.problem-entities {
  display: grid;
  grid-template-columns: 28px 200px 80px minmax(56px, max-content);
  align-items: center;
  column-gap: var(--problem-row-minor-gap);
  min-width: 0;
  flex: 0 1 auto;
}
.problem-main { min-width: 0; max-width: 200px; }
.problem-main h3 { margin: 0; overflow: hidden; color: var(--lm-text-primary); font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.row-index { display: block; width: 28px; color: #18181b; font-size: 11px; font-weight: 500; letter-spacing: 0; text-align: center; }
.problem-cell { min-width: 0; }
.contest-cell { display: flex; width: 80px; align-items: center; justify-content: center; overflow: hidden; }
.contest-name { display: block; width: 100%; overflow: hidden; color: var(--lm-text-secondary); font-size: 11px; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.problem-tags {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  min-width: 0;
  max-width: 220px;
  overflow: hidden;
  flex-wrap: nowrap;
}
.problem-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 9999px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  color: #52525b;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  flex-shrink: 0;
  box-sizing: border-box;
  transition: background var(--lm-transition), border-color var(--lm-transition), color var(--lm-transition);
}
.problem-row:hover .problem-tag {
  background: #ebebee;
  border-color: #d4d4d8;
}
.problem-meta {
  display: grid;
  grid-template-columns: repeat(5, var(--problem-row-meta-unit));
  align-items: center;
  column-gap: var(--problem-row-micro-gap);
  margin-left: auto;
  flex: 0 0 auto;
}
.year-value { width: 100%; text-align: center; color: var(--lm-text-secondary); font-size: 12px; }
.difficulty-value { width: 100%; font-size: 13px; text-align: center; }
.difficulty-easy { color: #13a8a8; }
.difficulty-medium { color: #d99016; }
.difficulty-hard { color: #d94b4b; }
.difficulty-unknown { color: var(--lm-text-muted); }
.average-score { display: flex; width: 100%; align-items: center; justify-content: center; }
.average-score strong { color: var(--lm-text-secondary); font-size: 14px; font-weight: 500; }
.practice-count { display: flex; width: 100%; align-items: center; justify-content: center; color: var(--lm-text-secondary); font-size: 13px; font-variant-numeric: tabular-nums; }
.practice-count .el-icon { color: var(--lm-text-muted); font-size: 14px; }
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
  color: #d97706;
  background: #fef3c7;
  transform: scale(1.1);
}
.fav-star-btn.active {
  color: #f59e0b;
}

/* 流式懒加载底栏 */
.feed-footer {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border-top: 0;
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
@media (max-width: 1000px) {
  .problem-row { gap: 12px; padding: 12px 16px; }
  .problem-entities { grid-template-columns: 28px minmax(0, 1fr) 90px; gap: 12px; flex: 1 1 auto; }
  .problem-main { max-width: none; }
  .contest-cell { width: 90px; }
  .problem-meta { grid-template-columns: 58px 32px; gap: 12px; margin-left: 0; }
  .difficulty-value, .year-value, .problem-tags { display: none; }
}
@media (max-width: 760px) { .pagination-wrap { flex-wrap: wrap; gap: 10px 14px; } }
@media (max-width: 600px) {
  .problem-entities { grid-template-columns: 28px minmax(0, 1fr); }
  .problem-meta { grid-template-columns: 52px 18px; gap: 4px; }
  .contest-cell { display: none; }
}
</style>
