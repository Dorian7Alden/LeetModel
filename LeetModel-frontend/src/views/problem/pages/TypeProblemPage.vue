<template>
  <div class="type-problem-page" v-loading="loading">
    <!-- 顶部面包屑与返回栏 -->
    <div class="page-top-nav">
      <button type="button" class="back-link-btn" @click="$router.push('/problem')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回全部题库</span>
      </button>
      <span class="nav-sep">/</span>
      <span class="current-type-crumb">{{ typeInfo.name }} 题型专区</span>
    </div>

    <!-- 双栏主布局 (左边题型方法档案与工具链 + 右边真题列表) -->
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
              <div class="stat-num">{{ total }} 道</div>
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

      <!-- 右边栏 (68% 宽度): 属于该题型的历年真题列表 -->
      <main class="type-right-main">
        <div class="type-main-card">
          <!-- 顶部工具栏: 赛事/难度过滤 -->
          <div class="type-feed-header">
            <div class="feed-header-left">
              <h2>{{ typeInfo.name }} · 历年真题库</h2>
              <span class="feed-subtitle">按最新年份倒序排布 · 专攻题型核心解题范式</span>
            </div>
            <div class="feed-header-actions">
              <!-- 赛事胶囊筛选 -->
              <el-select
                v-model="filterContestId"
                placeholder="赛事来源"
                clearable
                class="feed-select select-contest"
                @change="fetchTypeProblems(false)"
              >
                <el-option label="全部赛事" :value="null" />
                <el-option v-for="c in allContests" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>

              <!-- 难度胶囊筛选 -->
              <el-select
                v-model="filterDifficulty"
                placeholder="难度"
                clearable
                class="feed-select select-diff"
                @change="fetchTypeProblems(false)"
              >
                <el-option label="全部难度" :value="null" />
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>
            </div>
          </div>

          <!-- 题目数据表格 -->
          <div v-if="problems.length" class="type-problem-table">
            <div class="table-head">
              <span>题目</span>
              <span>赛事</span>
              <span class="col-center">年份</span>
              <span class="col-center">难度</span>
              <span class="col-center">平均分</span>
              <span></span>
            </div>
            <button
              v-for="(item, index) in problems"
              :key="item.id"
              class="problem-row"
              @click="$router.push(`/problem/${item.id}`)"
            >
              <div class="problem-main">
                <h3 :title="item.title">
                  <span class="row-index">{{ String(index + 1).padStart(2, '0') }}</span>
                  {{ item.title }}
                </h3>
              </div>
              <span class="contest-name" :title="item.contestName">{{ item.contestName || '全国大学生数学建模竞赛' }}</span>
              <span class="col-center text-muted">{{ item.year || '—' }}</span>
              <div class="col-center">
                <el-tag :type="difficultyType(item.difficulty)" size="small" effect="plain">{{ difficultyLabel(item.difficulty) }}</el-tag>
              </div>
              <div class="col-center average-score">
                <strong>{{ formatScore(item.averageScore) }}</strong>
              </div>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </button>

            <!-- 底部流式加载状态 -->
            <div class="feed-footer">
              <div v-if="loadingMore" class="loading-more">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>正在加载更多题目...</span>
              </div>
              <div v-else-if="problems.length < total" class="load-more-wrap">
                <button type="button" class="load-more-btn" @click="loadMore">
                  <span>加载更多真题 (已展示 {{ problems.length }} / 共 {{ total }} 题)</span>
                  <el-icon><ArrowDown /></el-icon>
                </button>
              </div>
              <div v-else class="all-loaded">
                <span>— 已展示该题型全部 {{ total }} 道真题 —</span>
              </div>
            </div>
          </div>

          <el-empty v-else-if="!loading" description="该题型下暂无符合筛选条件的题目" />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowRight, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getPublicProblemList } from '@/api/problem'

const route = useRoute()
const typeId = computed(() => Number(route.params.typeId))

const loading = ref(false)
const loadingMore = ref(false)
const problems = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filterDifficulty = ref(null)
const filterContestId = ref(null)
const allContests = ref([])
const allTags = ref([])

const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatScore = (score) => Number(score) > 0 ? Number(score).toFixed(1) : '-'

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

const fetchTypeProblems = async (isLoadMore = false) => {
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    page.value = 1
  }

  try {
    const params = {
      tagIds: [typeId.value],
      page: page.value,
      pageSize: pageSize.value,
      sortBy: 'year',
      sortOrder: 'desc'
    }
    if (filterDifficulty.value != null && filterDifficulty.value !== '') {
      params.difficulty = filterDifficulty.value
    }
    if (filterContestId.value != null && filterContestId.value !== '') {
      params.contestId = filterContestId.value
    }
    const response = await getPublicProblemList(params)
    const rows = response.data?.rows || []
    total.value = response.data?.total || 0
    if (isLoadMore) {
      problems.value.push(...rows)
    } else {
      problems.value = rows
    }
  } catch (err) {
    ElMessage.error(err.message || '获取题型题目失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => {
  if (loadingMore.value || loading.value || problems.value.length >= total.value) return
  page.value++
  fetchTypeProblems(true)
}

const initData = async () => {
  try {
    const filterRes = await getPublicProblemFilterOptions()
    allContests.value = filterRes.data?.contests || []
    allTags.value = filterRes.data?.tags || []
    fetchTypeProblems(false)
  } catch (err) {
    console.warn('加载题型元数据异常', err)
  }
}

onMounted(initData)
watch(typeId, () => {
  fetchTypeProblems(false)
})
</script>

<style scoped>
.type-problem-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 0 32px;
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
  gap: 20px;
  align-items: start;
}

/* 左侧栏: 题型档案与图谱 */
.type-left-sidebar {
  position: sticky;
  top: 72px;
}
.type-profile-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 22px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}
.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.type-flag-badge {
  padding: 3px 8px;
  border-radius: var(--lm-radius-sm);
  background: var(--lm-primary);
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-family: ui-monospace, monospace;
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

.type-methods-panel, .type-tools-panel {
  margin-bottom: 16px;
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
  font-family: ui-monospace, monospace;
}

/* 核心统计 */
.type-stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
}
stat-cell {
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
  font-family: ui-monospace, monospace;
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

/* 右侧主面板: 题目列表 */
.type-right-main {
  min-width: 0;
}
.type-main-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}
.type-feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--lm-border);
  background: #ffffff;
  gap: 16px;
  flex-wrap: wrap;
}
.feed-header-left h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary);
}
.feed-subtitle {
  font-size: 12px;
  color: var(--lm-text-muted);
  margin-top: 3px;
  display: block;
}
.feed-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.feed-select {
  width: 116px;
}
.feed-select :deep(.el-select__wrapper) {
  border-radius: 9999px !important;
  padding: 2px 10px;
  min-height: 32px;
}

/* 表格与题目行 */
.type-problem-table {
  background: #ffffff;
}
.table-head, .problem-row {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) minmax(130px, 0.7fr) 60px 66px 70px 18px;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
}
.table-head {
  min-height: 40px;
  background: #f8fafc;
  border-bottom: 1px solid var(--lm-border);
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
}
.problem-row {
  width: 100%;
  min-height: 58px;
  border: 0;
  border-bottom: 1px solid var(--lm-border-light);
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: background var(--lm-transition);
}
.problem-row:last-child {
  border-bottom: 0;
}
.problem-row:hover {
  background: #eff6ff;
}
.problem-main h3 {
  margin: 0;
  overflow: hidden;
  color: var(--lm-text-primary);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-index {
  display: inline-block;
  min-width: 24px;
  margin-right: 6px;
  color: var(--lm-primary);
  font-size: 10px;
  font-weight: 800;
  font-family: ui-monospace, monospace;
}
.contest-name {
  color: var(--lm-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.col-center {
  text-align: center;
}
.text-muted {
  color: var(--lm-text-secondary);
  font-size: 12px;
}
.average-score strong {
  color: var(--lm-text-primary);
  font-size: 15px;
}
.row-arrow {
  color: var(--lm-text-muted);
  transition: transform var(--lm-transition), color var(--lm-transition);
}
.problem-row:hover .row-arrow {
  color: var(--lm-primary);
  transform: translateX(3px);
}

/* 流式加载底栏 */
.feed-footer {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-top: 1px solid var(--lm-border-light);
}
.loading-more {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--lm-text-muted);
  font-size: 12px;
}
.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  background: #ffffff;
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.load-more-btn:hover {
  color: var(--lm-primary);
  border-color: var(--lm-primary-light);
  background: #eff6ff;
}
.all-loaded {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.04em;
}

@media (max-width: 960px) {
  .type-two-col-layout {
    grid-template-columns: 1fr;
  }
  .type-left-sidebar {
    position: static;
  }
}
</style>
