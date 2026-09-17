<template>
  <div class="contest-problem-page">
    <!-- 顶部面包屑与返回栏 -->
    <div class="page-top-nav">
      <button type="button" class="back-link-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <span class="nav-sep">/</span>
      <span class="current-contest-crumb">{{ contestInfo.name }}</span>
    </div>

    <!-- 双栏主布局 (左边赛事档案与统计 + 右边题目列表) -->
    <div class="contest-two-col-layout">
      <!-- 左边栏: 赛事学术档案与官方规约看板 -->
      <aside class="contest-left-sidebar">
        <div class="contest-profile-card">
          <!-- 1. 顶部代码徽标与官方外链 -->
          <div class="profile-header">
            <span class="contest-code-badge">{{ formattedCode }}</span>
            <a
              v-if="contestInfo.officialUrl"
              :href="contestInfo.officialUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="official-portal-link"
              title="前往组委会官方网站"
            >
              <el-icon><TopRight /></el-icon>
            </a>
          </div>

          <!-- 2. 赛事中英文官方全称 -->
          <div class="profile-title-group">
            <h1 class="contest-name">{{ contestInfo.name }}</h1>
            <p v-if="contestInfo.englishName" class="contest-english-name">{{ contestInfo.englishName }}</p>
          </div>

          <!-- 3. 官方赛制核心规约 -->
          <div class="contest-rules-panel">
            <div class="panel-section-title">官方赛制规约</div>
            <div class="rules-spec-list">
              <div class="spec-row">
                <el-icon class="spec-icon"><Calendar /></el-icon>
                <span class="spec-label">赛程时限</span>
                <span class="spec-value">{{ contestInfo.scheduleDesc || '赛前统一公布' }}</span>
              </div>
              <div class="spec-row">
                <el-icon class="spec-icon"><User /></el-icon>
                <span class="spec-label">队伍规程</span>
                <span class="spec-value">{{ contestInfo.teamRules || '以官方报名简章为准' }}</span>
              </div>
              <div class="spec-row">
                <el-icon class="spec-icon"><Document /></el-icon>
                <span class="spec-label">成果交付</span>
                <span class="spec-value">{{ contestInfo.submissionSpec || '学术论文与支撑材料' }}</span>
              </div>
              <div class="spec-row">
                <el-icon class="spec-icon"><Compass /></el-icon>
                <span class="spec-label">赛题范式</span>
                <span class="spec-value">{{ contestInfo.problemSpec || '综合性数学建模赛题' }}</span>
              </div>
            </div>
          </div>

          <!-- 4. 平台真实收录指标 -->
          <div class="contest-metrics-panel">
            <div class="panel-section-title">平台实训真题</div>
            <div class="metrics-grid">
              <div class="metric-card">
                <div class="metric-num">
                  {{ problemTotal }}
                  <span class="metric-unit">道</span>
                </div>
                <div class="metric-label">收录真题</div>
              </div>
              <div class="metric-card">
                <div class="metric-num">{{ yearSpan }}</div>
                <div class="metric-label">真题年份覆盖</div>
              </div>
            </div>
          </div>

          <!-- 5. 常见考查领域与题型 -->
          <div v-if="focusTags.length" class="contest-topics-panel">
            <div class="panel-section-title">常见考查方向</div>
            <div class="topics-tags-cloud">
              <span v-for="tag in focusTags" :key="tag" class="topic-pill">{{ tag }}</span>
            </div>
          </div>

          <!-- 6. 赛事权威客观概况 -->
          <div v-if="contestInfo.description" class="contest-desc-panel">
            <div class="panel-section-title">赛事概况</div>
            <p class="contest-desc-text">{{ contestInfo.description }}</p>
          </div>
        </div>
      </aside>

      <!-- 右边栏 (68% 宽度): 按最新年份倒序排布的题目列表 -->
      <main class="contest-right-main">
        <ProblemHeader
          :contests="allContests"
          :tags="allTags"
          :total="problemTotal"
          :random-loading="randomLoading"
          :show-contest-cards="false"
          :show-advanced-filters="false"
          :hide-year-sort="true"
          :fixed-contest-id="contestId"
          @change="handleSearch"
          @random="handleRandom"
          @sort="handleSort"
        />
        <ProblemList
          ref="listRef"
          :tags="allTags"
          :initial-query="contestQuery"
          :group-by-year="true"
          :show-contest="false"
          @fav-change="handleFavChange"
          @total-change="handleTotalChange"
          @loaded="handleProblemsLoaded"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Calendar,
  Compass,
  Document,
  TopRight,
  User
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import ProblemHeader from '../components/ProblemHeader.vue'
import ProblemList from '../components/ProblemList.vue'

const route = useRoute()
const router = useRouter()
const contestId = computed(() => Number(route.params.contestId))

const allContests = ref([])
const allTags = ref([])
const problemTotal = ref(0)
const loadedProblems = ref([])
const randomLoading = ref(false)
const listRef = ref(null)
const workbench = inject('problemWorkbench', null)

const handleFavChange = (count) => {
  workbench?.updateFavCount?.(count)
}

const contestQuery = computed(() => {
  const query = { contestId: contestId.value }
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  return query
})

// 赛事详细档案模型：直接消费后端扩展字段与真实数据，辅以平滑兜底
const contestInfo = computed(() => {
  const raw = allContests.value.find(c => c.id === contestId.value)
  if (!raw) {
    return {
      code: 'CONTEST',
      name: '数学建模竞赛',
      englishName: '',
      scheduleDesc: '赛前由组委会统一公布',
      teamRules: '以官方报名简章为准',
      submissionSpec: '学术论文与支撑材料',
      problemSpec: '涵盖机理推导、运筹优化与数据分析等大类',
      description: '数学建模竞赛通过三人小队限时协同，完成实际问题的假设、机理建模、数值求解与长篇学术论文撰写。',
      officialUrl: null
    }
  }

  return {
    ...raw,
    englishName: raw.englishName || '',
    scheduleDesc: raw.scheduleDesc || '赛前由组委会统一公布',
    teamRules: raw.teamRules || '以官方报名简章为准',
    submissionSpec: raw.submissionSpec || '学术论文与支撑材料',
    problemSpec: raw.problemSpec || '涵盖机理推导、运筹优化与数据分析等大类',
    description: raw.description || '',
    officialUrl: raw.officialUrl || null
  }
})

// 规范化赛事编码展示
const formattedCode = computed(() => {
  const rawCode = contestInfo.value.code || ''
  if (rawCode === 'MCM_ICM') return 'MCM / ICM'
  if (rawCode === 'LM') return 'LEETMODEL'
  return rawCode.toUpperCase()
})

// 真实年份跨度计算（根据已收录题目动态计算）
const yearSpan = computed(() => {
  const years = loadedProblems.value
    .map(p => Number(p.year))
    .filter(y => Number.isInteger(y) && y > 0)
  if (!years.length) return '—'
  const minYear = Math.min(...years)
  const maxYear = Math.max(...years)
  return minYear === maxYear ? `${minYear} 年` : `${minYear} – ${maxYear}`
})

// 模型与算法类型标签名称集合（严格限制 MODEL_ALGORITHM）
const modelAlgoTagNames = computed(() => {
  return new Set(
    allTags.value
      .filter(t => t.type === 'MODEL_ALGORITHM')
      .map(t => t.name)
  )
})

// 常见考查方向（仅统计 MODEL_ALGORITHM 类型标签，按出现频次降序排列）
const focusTags = computed(() => {
  const algoSet = modelAlgoTagNames.value
  const countMap = {}
  for (const p of loadedProblems.value) {
    if (Array.isArray(p.tagNames)) {
      for (const t of p.tagNames) {
        if (t && typeof t === 'string' && algoSet.has(t.trim())) {
          const name = t.trim()
          countMap[name] = (countMap[name] || 0) + 1
        }
      }
    }
  }
  return Object.entries(countMap)
    .sort((a, b) => b[1] - a[1])
    .map(([name]) => name)
})

const goBack = () => {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push('/problem')
  }
}

const handleTotalChange = (value) => {
  problemTotal.value = Number(value) || 0
}

const handleProblemsLoaded = ({ problems, total }) => {
  loadedProblems.value = Array.isArray(problems) ? problems : []
  if (typeof total === 'number') {
    problemTotal.value = total
  }
}

const handleSearch = (params) => {
  listRef.value?.updateQuery({ ...params, contestId: contestId.value })
  const nextQuery = {}
  if (params.keyword?.trim()) nextQuery.keyword = params.keyword.trim()
  router.replace({ query: nextQuery })
}

const handleSort = (field) => {
  if (field === 'clear') {
    listRef.value?.clearSort()
    return
  }
  listRef.value?.cycleSort(field)
}

const handleRandom = async (params = {}) => {
  const currentContestId = Number(contestId.value)
  if (!Number.isInteger(currentContestId) || currentContestId <= 0) {
    ElMessage.error('当前赛事无效，暂时无法随机抽题')
    return
  }

  randomLoading.value = true
  try {
    // 赛事页的随机题范围由路由赛事 ID 固定，不能被表单或历史 query 覆盖。
    const activeFilters = Object.fromEntries(
      Object.entries(params).filter(([, value]) => (
        value !== '' && value != null && (!Array.isArray(value) || value.length > 0)
      ))
    )
    const response = await getRandomPublicProblem({
      ...activeFilters,
      contestId: currentContestId,
    })
    if (response.data?.id) {
      ElMessage.success('已从当前赛事抽取一题')
      await router.push(`/problem/${response.data.id}`)
    }
  } catch (error) {
    ElMessage.error(error.message || '暂时没有符合条件的题目')
  } finally {
    randomLoading.value = false
  }
}

const syncMetadata = () => {
  if (workbench?.filterOptions?.contests?.length) {
    allContests.value = workbench.filterOptions.contests
    allTags.value = workbench.filterOptions.tags
    return true
  }
  return false
}

const initData = async () => {
  if (!syncMetadata()) {
    try {
      const filterRes = await getPublicProblemFilterOptions()
      allContests.value = filterRes.data?.contests || []
      allTags.value = filterRes.data?.tags || []
    } catch (err) {
      console.warn('加载赛事元数据异常', err)
    }
  }
}

onMounted(initData)
watch(
  () => workbench?.filterOptions?.contests,
  () => {
    if (syncMetadata()) {
      // 元数据到达后无需强刷题目列表
    }
  },
  { deep: true }
)
watch(contestId, () => {
  listRef.value?.updateQuery({ contestId: contestId.value })
})
</script>

<style scoped>
.contest-problem-page {
  width: auto;
  margin: 0 40px;
  padding: 0 0 32px;
}

/* 面包屑返回栏 */
.page-top-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  font-size: 13px;
}
.back-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--lm-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--lm-radius-sm);
  transition: all var(--lm-transition);
}
.back-link-btn:hover {
  background: var(--lm-primary-bg);
}
.nav-sep {
  color: var(--lm-border);
}
.current-contest-crumb {
  color: var(--lm-text-muted);
}

/* 双栏网格布局 */
.contest-two-col-layout {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 32px;
  align-items: start;
}

/* 左侧栏: 赛事档案与数据大屏 */
.contest-left-sidebar {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 88px);
  overflow-y: auto;
}
/* 极简精致隐形滚动条 */
.contest-left-sidebar::-webkit-scrollbar {
  width: 4px;
}
.contest-left-sidebar::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 2px;
}
.contest-left-sidebar:hover::-webkit-scrollbar-thumb {
  background: #cbd5e1;
}
.contest-profile-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 18px 18px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.contest-code-badge {
  padding: 2px 7px;
  border-radius: var(--lm-radius-sm);
  background: #18181b;
  color: #ffffff;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
  font-family: var(--lm-code-font-family);
}
.official-portal-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--lm-text-muted);
  font-size: 13px;
  text-decoration: none;
  transition: color var(--lm-transition);
}
.official-portal-link .el-icon {
  font-size: 15px;
}
.official-portal-link:hover {
  color: var(--lm-text-primary);
  text-decoration: underline;
}
.profile-title-group {
  margin: 0;
}
.contest-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--lm-text-primary);
}
.contest-english-name {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 1.4;
  color: var(--lm-text-muted);
  font-family: var(--lm-font-family);
}

.panel-section-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--lm-text-muted);
  margin-bottom: 6px;
}

/* 官方规约列表 */
.contest-rules-panel {
  margin: 0;
}
.rules-spec-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px 12px;
  background: #fafafa;
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius-sm);
}
.spec-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11.5px;
  line-height: 1.45;
}
.spec-icon {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-top: 1px;
  flex-shrink: 0;
}
.spec-label {
  width: 52px;
  flex-shrink: 0;
  color: var(--lm-text-muted);
  font-size: 11.5px;
  font-weight: 500;
}
.spec-value {
  flex: 1;
  color: var(--lm-text-primary);
  font-size: 11.5px;
  font-weight: 500;
  word-break: break-word;
}

/* 平台实训真题指标看板 */
.contest-metrics-panel {
  margin: 0;
}
.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}
.metric-card {
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  text-align: center;
}
.metric-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
  line-height: 1.2;
}
.metric-unit {
  font-size: 10.5px;
  font-weight: 400;
  color: var(--lm-text-muted);
  margin-left: 2px;
}
.metric-label {
  font-size: 10.5px;
  color: var(--lm-text-muted);
  margin-top: 3px;
}

/* 考查方向标签云 */
.contest-topics-panel {
  margin: 0;
}
.topics-tags-cloud {
  display: flex;
  flex-wrap: nowrap;
  gap: 5px;
  overflow: hidden;
  white-space: nowrap;
}
.topic-pill {
  flex-shrink: 0;
  display: inline-block;
  padding: 2px 7px;
  border-radius: 9999px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  font-size: 10.5px;
  color: var(--lm-text-secondary);
  line-height: 1.35;
}

/* 赛事概况客观简介 */
.contest-desc-panel {
  margin: 0;
}
.contest-desc-text {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--lm-text-secondary);
}

/* 右侧主面板: 题目列表 */
.contest-right-main {
  min-width: 0;
}

@media (max-width: 960px) {
  .contest-problem-page {
    margin-right: 0;
    margin-left: 0;
  }
  .contest-two-col-layout {
    grid-template-columns: 1fr;
  }
  .contest-left-sidebar {
    position: static;
    max-height: none;
    overflow-y: visible;
  }
}
</style>
