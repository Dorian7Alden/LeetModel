<template>
  <div class="type-problem-page">
    <!-- 顶部面包屑与返回栏 -->
    <div class="page-top-nav">
      <button type="button" class="back-link-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <span class="nav-sep">/</span>
      <span class="current-type-crumb">{{ typeInfo.name }} 题型专区</span>
    </div>

    <!-- 双栏主布局：左侧固定题型上下文，右侧复用赛事页题目流 -->
    <div class="type-two-col-layout">
      <!-- 左边栏 (32% 宽度): 题型学术图谱与得分要点 -->
      <aside class="type-left-sidebar">
        <div class="type-profile-card">
          <div class="profile-header">
            <span class="type-flag-badge">PROBLEM TYPE</span>
            <span class="type-heat-tag">{{ typeInfo.heatTag }}</span>
          </div>
          <h1 class="type-name">{{ typeInfo.name }}</h1>
          <p class="type-description">{{ typeInfo.description }}</p>

          <!-- 经典算法与建模方法 -->
          <div class="type-methods-panel">
            <div class="panel-subtitle">常用核心模型 & 算法</div>
            <div class="method-tags">
              <span v-for="m in typeInfo.coreMethods" :key="m" class="method-tag">{{ m }}</span>
            </div>
          </div>

          <!-- 必备工具库 -->
          <div class="type-tools-panel">
            <div class="panel-subtitle">推荐求解工具链</div>
            <div class="tool-list">
              <span v-for="tool in typeInfo.recommendedTools" :key="tool" class="tool-badge">{{ tool }}</span>
            </div>
          </div>

          <!-- 核心统计 -->
          <div class="type-stats-grid">
            <div class="stat-cell">
              <div class="stat-num">{{ problemTotal }} 道</div>
              <div class="stat-label">收录真题</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num">{{ typeInfo.frequency }}</div>
              <div class="stat-label">国赛命题率</div>
            </div>
          </div>

          <!-- 得分排雷锦囊 -->
          <div class="type-tips-panel">
            <div class="panel-subtitle">论文评审核心得分点</div>
            <ul class="tips-list">
              <li v-for="(tip, idx) in typeInfo.tips" :key="idx">{{ tip }}</li>
            </ul>
          </div>
        </div>
      </aside>

      <!-- 右边栏 (68% 宽度): 题型范围内按年份组织的真题流 -->
      <main class="type-right-main">
        <ProblemHeader
          :contests="allContests"
          :tags="allTags"
          :total="problemTotal"
          :random-loading="randomLoading"
          :show-contest-cards="false"
          :show-advanced-filters="false"
          :hide-year-sort="true"
          :fixed-type-id="typeId"
          @change="handleSearch"
          @random="handleRandom"
          @sort="handleSort"
        />
        <ProblemList
          ref="listRef"
          :tags="allTags"
          :initial-query="typeQuery"
          :group-by-year="true"
          :show-contest="true"
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
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import ProblemHeader from '../components/ProblemHeader.vue'
import ProblemList from '../components/ProblemList.vue'

const route = useRoute()
const router = useRouter()
const typeId = computed(() => Number(route.params.typeId))

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

const rawTag = computed(() => allTags.value.find(t => t.id === typeId.value))

// 题型学术档案模型
const typeInfo = computed(() => {
  const name = rawTag.value?.name || '综合题型'
  let heatTag = '🔥 国赛高频考向'
  let frequency = '~40%'
  let description = '数学建模经典题型，注重客观逻辑推导、变量约束界定与求解效率。'
  let coreMethods = ['线性规划', '整数规划', '动态规划', '遗传算法', '模拟退火']
  let recommendedTools = ['Python (Scipy/PuLP)', 'MATLAB Optimization', 'Lingo', 'Gurobi']
  let tips = [
    '变量定义必须附物理单位，约束条件要完整严密；',
    '必须进行灵敏度分析或参数鲁棒性检验；',
    '求解算法要说明收敛性与复杂度，杜绝黑盒一键输出。'
  ]

  if (name.includes('优化')) {
    heatTag = '🔥 国赛年年必考 A/B 题'
    frequency = '80%+'
    description = '以资源分配、调度排班、路径规划或工程设计为背景，建立目标函数并在严密边界约束下求取极值。'
    coreMethods = ['多目标规划', '混合整数线性规划 (MILP)', 'PSO/GA 启发式', '非线性规划']
    recommendedTools = ['Gurobi', 'CPLEX', 'SciPy.optimize', 'Lingo']
    tips = [
      '目标函数需写明量纲对齐原则，避免不同量级指标直接加和；',
      '决策变量下标定义必须清晰一致，杜绝公式符号前后矛盾；',
      '给出求解算法迭代收敛曲线与全局最优性讨论。'
    ]
  } else if (name.includes('预测') || name.includes('评估')) {
    heatTag = '📊 C/D 题核心题型'
    frequency = '60%+'
    description = '针对时空演化、市场行情或质量综合打分，完成指标体系构建、缺失值填补与趋势外推预测。'
    coreMethods = ['ARIMA / SARIMA', 'LSTM / GRU', '灰色预测 GM(1,1)', 'TOPSIS / 熵权法']
    recommendedTools = ['Pandas', 'Statsmodels', 'Scikit-learn', 'PyTorch']
    tips = [
      '预测模型必须做交叉验证与置信区间估计，杜绝纯点预测；',
      '综合评价权重必须结合主客观赋权法，并交代一致性检验。'
    ]
  } else if (name.includes('微分') || name.includes('机理')) {
    heatTag = '⚙️ 物理与生物过程硬核考向'
    frequency = '35%'
    description = '依据热传导、流体动力学、种群竞争等守恒定律推导偏微分方程或常微分系统。'
    coreMethods = ['常微分方程系统 (ODE)', '有限差分法 (FDM)', '传热传质动力学', '四阶龙格-库塔']
    recommendedTools = ['MATLAB ode45', 'SymPy', 'COMSOL', 'Scipy.integrate']
    tips = [
      '必须明确给出初始条件与边界条件推导过程；',
      '守恒定律（质量/能量/动量）是论文评分核心关键。'
    ]
  }

  return {
    name,
    heatTag,
    frequency,
    description,
    coreMethods,
    recommendedTools,
    tips
  }
})

const typeQuery = computed(() => {
  const query = { tagIds: [typeId.value] }
  if (route.query.tagIds) {
    const extraTagIds = String(route.query.tagIds)
      .split(',')
      .map(Number)
      .filter((id) => id && id !== typeId.value)
    query.tagIds.push(...extraTagIds)
  }
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  if (route.query.contestId) query.contestId = Number(route.query.contestId)
  if (route.query.year) query.year = Number(route.query.year)
  if (route.query.difficulty) query.difficulty = Number(route.query.difficulty)
  if (route.query.statementLanguage) query.statementLanguage = String(route.query.statementLanguage)
  if (route.query.minScore) query.minAverageScore = Number(route.query.minScore)
  if (route.query.maxScore) query.maxAverageScore = Number(route.query.maxScore)
  return query
})

const handleTotalChange = (value) => {
  problemTotal.value = Number(value) || 0
}

const handleProblemsLoaded = ({ problems, total }) => {
  loadedProblems.value = Array.isArray(problems) ? problems : []
  if (typeof total === 'number') problemTotal.value = total
}

const handleSearch = (params) => {
  const selectedTagIds = Array.isArray(params.tagIds) ? params.tagIds : []
  const nextParams = {
    ...params,
    tagIds: [typeId.value, ...selectedTagIds.filter((id) => Number(id) !== typeId.value)]
  }
  listRef.value?.updateQuery(nextParams)

  const nextQuery = {}
  if (params.keyword?.trim()) nextQuery.keyword = params.keyword.trim()
  if (params.contestId) nextQuery.contestId = params.contestId
  if (params.year) nextQuery.year = params.year
  if (params.difficulty) nextQuery.difficulty = params.difficulty
  if (params.statementLanguage) nextQuery.statementLanguage = params.statementLanguage
  if (params.minAverageScore != null) nextQuery.minScore = params.minAverageScore
  if (params.maxAverageScore != null) nextQuery.maxScore = params.maxAverageScore
  const extraTagIds = Array.isArray(params.tagIds)
    ? params.tagIds.filter((id) => Number(id) !== typeId.value)
    : []
  if (extraTagIds.length) nextQuery.tagIds = extraTagIds.join(',')
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
  randomLoading.value = true
  try {
    const activeFilters = Object.fromEntries(
      Object.entries(params).filter(([, value]) => (
        value !== '' && value != null && (!Array.isArray(value) || value.length > 0)
      ))
    )
    const selectedTagIds = Array.isArray(activeFilters.tagIds) ? activeFilters.tagIds : []
    const tagIds = [typeId.value, ...selectedTagIds.filter((id) => Number(id) !== typeId.value)]
    const response = await getRandomPublicProblem({ ...activeFilters, tagIds })
    if (response.data?.id) {
      ElMessage.success('已从当前题型抽取一题')
      await router.push({ path: `/problem/${response.data.id}`, query: route.query })
    }
  } catch (error) {
    ElMessage.error(error.message || '暂时没有符合条件的题目')
  } finally {
    randomLoading.value = false
  }
}

const goBack = () => {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push('/problem')
  }
}

const syncMetadata = () => {
  if (workbench?.filterOptions?.tags?.length) {
    allContests.value = workbench.filterOptions.contests || []
    allTags.value = workbench.filterOptions.tags || []
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
      console.warn('加载题型元数据异常', err)
    }
  }
}

onMounted(initData)
watch(
  () => workbench?.filterOptions?.tags,
  () => {
    syncMetadata()
  },
  { deep: true }
)
watch(typeId, () => {
  listRef.value?.updateQuery({ tagIds: [typeId.value] })
})
</script>

<style scoped>
.type-problem-page {
  width: 100%;
  margin: 0;
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
.current-type-crumb {
  color: var(--lm-text-muted);
}

/* 双栏网格布局 */
.type-two-col-layout {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 32px;
  align-items: start;
}

/* 左侧栏: 题型档案与图谱 */
.type-left-sidebar {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 88px);
  overflow-y: auto;
}
.type-left-sidebar::-webkit-scrollbar { width: 4px; }
.type-left-sidebar::-webkit-scrollbar-thumb { background: transparent; border-radius: 2px; }
.type-left-sidebar:hover::-webkit-scrollbar-thumb { background: #cbd5e1; }
.type-profile-card {
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
.type-flag-badge {
  padding: 3px 8px;
  border-radius: var(--lm-radius-sm);
  background: var(--lm-primary);
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-family: var(--lm-code-font-family);
}
.type-heat-tag {
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-text-secondary);
}
.type-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--lm-text-primary);
}
.type-description {
  margin: 0 0 16px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--lm-text-secondary);
}

.panel-subtitle {
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}

.method-tags, .tool-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.method-tag {
  padding: 3px 8px;
  border-radius: var(--lm-radius-sm);
  background: #f4f4f5;
  color: #18181b;
  border: 1px solid #e4e4e7;
  font-size: 11px;
  font-weight: 500;
}
.tool-badge {
  padding: 2px 7px;
  border-radius: 4px;
  background: #f1f5f9;
  color: var(--lm-text-secondary);
  font-size: 11px;
  font-family: var(--lm-code-font-family);
}

/* 核心统计 */
.type-stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 0;
}
.stat-cell {
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  text-align: center;
}
.stat-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-primary);
  font-family: var(--lm-code-font-family);
}
.stat-label {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 2px;
}

/* 排雷锦囊 */
.type-tips-panel {
  padding-top: 14px;
  border-top: 1px dashed var(--lm-border);
}
.tips-list {
  margin: 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--lm-text-secondary);
}

/* 右侧主面板由 ProblemHeader / ProblemList 提供统一题目流 */
.type-right-main { min-width: 0; }

@media (max-width: 960px) {
  .type-two-col-layout {
    grid-template-columns: 1fr;
  }
  .type-left-sidebar {
    position: static;
    max-height: none;
    overflow-y: visible;
  }
}
</style>
