<template>
  <div class="contest-problem-page" v-loading="loading">
    <!-- 顶部面包屑与返回栏 -->
    <div class="page-top-nav">
      <button type="button" class="back-link-btn" @click="$router.push('/problem')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回全部题库</span>
      </button>
      <span class="nav-sep">/</span>
      <span class="current-contest-crumb">{{ contestInfo.name }} 专属题库</span>
    </div>

    <!-- 双栏主布局 (左边赛事档案与统计 + 右边题目列表) -->
    <div class="contest-two-col-layout">
      <!-- 左边栏 (32% 宽度): 赛事学术档案、规则与数据大屏 -->
      <aside class="contest-left-sidebar">
        <div class="contest-profile-card">
          <div class="profile-header">
            <span class="contest-flag-badge">{{ contestInfo.code }}</span>
            <span class="contest-heat-tag">{{ contestInfo.heatTag }}</span>
          </div>
          <h1 class="contest-name">{{ contestInfo.name }}</h1>
          <p class="contest-description">{{ contestInfo.description }}</p>
          
          <div class="contest-rules-list">
            <div class="rule-item">
              <el-icon><Calendar /></el-icon>
              <span>{{ contestInfo.scheduleTime }}</span>
            </div>
            <div class="rule-item">
              <el-icon><User /></el-icon>
              <span>{{ contestInfo.teamSpec }}</span>
            </div>
            <div class="rule-item">
              <el-icon><Document /></el-icon>
              <span>{{ contestInfo.deliverable }}</span>
            </div>
          </div>

          <!-- 核心实训数据统计 -->
          <div class="contest-stats-grid">
            <div class="stat-cell">
              <div class="stat-num">{{ total }} 道</div>
              <div class="stat-label">收录真题</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num">{{ contestInfo.participantTotal }}</div>
              <div class="stat-label">演练热度</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num">{{ contestInfo.avgScore }} 分</div>
              <div class="stat-label">历史平均分</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num">每年 ~5 题</div>
              <div class="stat-label">更新频次</div>
            </div>
          </div>

          <!-- 历年分数段分布可视化看板 -->
          <div class="score-distribution-panel">
            <div class="dist-title">全真模拟分数段对标</div>
            <div class="dist-bars">
              <div class="dist-bar-row">
                <span class="dist-label">90+ 分 (国一/国二区)</span>
                <div class="dist-track"><div class="dist-fill tier-1" style="width: 15%;"></div></div>
                <span class="dist-val">15%</span>
              </div>
              <div class="dist-bar-row">
                <span class="dist-label">80~89 分 (省一/省二区)</span>
                <div class="dist-track"><div class="dist-fill tier-2" style="width: 42%;"></div></div>
                <span class="dist-val">42%</span>
              </div>
              <div class="dist-bar-row">
                <span class="dist-label">60~79 分 (成功参赛区)</span>
                <div class="dist-track"><div class="dist-fill tier-3" style="width: 35%;"></div></div>
                <span class="dist-val">35%</span>
              </div>
              <div class="dist-bar-row">
                <span class="dist-label">60分以下 (格式需排雷)</span>
                <div class="dist-track"><div class="dist-fill tier-4" style="width: 8%;"></div></div>
                <span class="dist-val">8%</span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右边栏 (68% 宽度): 按最新年份倒序排布的题目列表 -->
      <main class="contest-right-main">
        <div class="contest-main-card">
          <!-- 顶部工具栏: 年份倒序说明 + 紧凑过滤胶囊 -->
          <div class="contest-feed-header">
            <div class="feed-header-left">
              <h2>历年精选题单</h2>
              <span class="feed-subtitle">按最新年份倒序排布 · 近年真题最具参考价值</span>
            </div>
            <div class="feed-header-actions">
              <!-- 难度胶囊筛选 -->
              <el-select
                v-model="filterDifficulty"
                placeholder="难度"
                clearable
                class="feed-select select-diff"
                @change="fetchContestProblems(false)"
              >
                <el-option label="全部难度" :value="null" />
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>

              <!-- 题型胶囊筛选 -->
              <el-select
                v-model="filterProblemType"
                placeholder="题目类型"
                clearable
                class="feed-select select-type"
                @change="fetchContestProblems(false)"
              >
                <el-option label="全部题型" :value="null" />
                <el-option v-for="tag in problemTypeTags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </div>
          </div>

          <!-- 题目数据表格 -->
          <div v-if="problems.length" class="contest-problem-table">
            <div class="table-head">
              <span>题目</span>
              <span>标签 / 模型</span>
              <span class="col-center">年份</span>
              <span class="col-center">语言</span>
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
                  <span class="row-index" :title="`题号 ${item.code ?? (index + 1)}`">{{ String(item.code ?? (index + 1)).padStart(2, '0') }}</span>
                  {{ item.title }}
                </h3>
              </div>
              <div class="problem-tags">
                <span v-for="tag in item.tagNames?.slice(0, 3)" :key="tag" class="problem-tag" :title="tag">{{ tag }}</span>
              </div>
              <span class="col-center text-muted">{{ item.year || '—' }}</span>
              <span class="col-center text-muted">{{ item.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
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
                <span>正在加载更多年份真题...</span>
              </div>
              <div v-else-if="problems.length < total" class="load-more-wrap">
                <button type="button" class="load-more-btn" @click="loadMore">
                  <span>加载更多真题 (已展示 {{ problems.length }} / 共 {{ total }} 题)</span>
                  <el-icon><ArrowDown /></el-icon>
                </button>
              </div>
              <div v-else class="all-loaded">
                <span>— 已展示该赛事全部 {{ total }} 道真题 —</span>
              </div>
            </div>
          </div>

          <el-empty v-else-if="!loading" description="该赛事下暂无符合筛选条件的题目" />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowRight, Calendar, Document, Loading, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getPublicProblemList } from '@/api/problem'

const route = useRoute()
const contestId = computed(() => Number(route.params.contestId))

const loading = ref(false)
const loadingMore = ref(false)
const problems = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filterDifficulty = ref(null)
const filterProblemType = ref(null)
const allContests = ref([])
const allTags = ref([])

const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatScore = (score) => Number(score) > 0 ? Number(score).toFixed(1) : '-'

const problemTypeTags = computed(() => allTags.value.filter(t => t.type === 'PROBLEM_TYPE'))

// 赛事详细档案模型
const contestInfo = computed(() => {
  const raw = allContests.value.find(c => c.id === contestId.value)
  if (!raw) {
    return {
      code: 'CONTEST',
      name: '数学建模竞赛',
      description: '综合性学术数学建模竞赛，三人成团，限时完成假设、建模、求解与长篇论文撰写。',
      heatTag: '🔥 热门备战赛事',
      scheduleTime: '每年定期举行 · 3天3夜全真模拟',
      teamSpec: '三人小队 · 建模/编程/论文三职责',
      deliverable: '20~30 页完整 PDF 学术论文',
      participantTotal: '10w+ 人',
      avgScore: '81.5'
    }
  }
  
  let code = 'CUMCM'
  let description = '全国大学生数学建模竞赛是中国高校规模最大、最具公信力的基础学科赛事。每年命制 5 道经典赛题（A~E 题），重点考察机理推导、运筹优化与学术论文规范性。'
  let heatTag = '🔥 15w+ 人在练 · 顶尖热度'
  let scheduleTime = '每年 9 月上旬 · 72 小时极限实训'
  let teamSpec = '三人小队 · 建模手/编程手/论文手'
  let deliverable = '20~30 页 PDF 论文（含摘要与附录代码）'
  let participantTotal = '15.2w 人'
  let avgScore = '81.2'

  if (raw.name.includes('美国') || raw.name.toLowerCase().includes('mcm')) {
    code = 'MCM/ICM'
    description = '美国大学生数学建模竞赛是全球规模最大的国际性数模赛事。涵盖连续型、离散型、大数据、环境科学等 6 大赛题，全英文命题与英文论文撰写。'
    heatTag = '🔥 8.6w+ 人在练 · 国际赛事'
    scheduleTime = '每年 2 月中旬 · 96 小时跨学科实战'
    deliverable = '全英文学术论文（PDF 格式）'
    participantTotal = '8.6w 人'
    avgScore = '83.4'
  } else if (raw.name.includes('力模')) {
    code = 'LEETMODEL'
    description = 'LeetModel 平台官方常态化实训模拟赛，紧跟命题前沿，支持随时开赛、全自动双阶段 AI 深度体检与改写建议。'
    heatTag = '⭐ 3.4w+ 人在练 · 官方实训'
    scheduleTime = '常态化开放 · 随时自选时限开赛'
    participantTotal = '3.4w 人'
    avgScore = '79.6'
  }

  return {
    ...raw,
    code,
    description,
    heatTag,
    scheduleTime,
    teamSpec,
    deliverable,
    participantTotal,
    avgScore
  }
})

const fetchContestProblems = async (isLoadMore = false) => {
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    page.value = 1
  }

  try {
    const params = {
      contestId: contestId.value,
      page: page.value,
      pageSize: pageSize.value,
      sortBy: 'year',
      sortOrder: 'desc' // 严格最新年份倒序排布
    }
    if (filterDifficulty.value != null && filterDifficulty.value !== '') {
      params.difficulty = filterDifficulty.value
    }
    if (filterProblemType.value != null && filterProblemType.value !== '') {
      params.tagIds = [filterProblemType.value]
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
    ElMessage.error(err.message || '获取赛事题目失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => {
  if (loadingMore.value || loading.value || problems.value.length >= total.value) return
  page.value++
  fetchContestProblems(true)
}

const initData = async () => {
  try {
    const filterRes = await getPublicProblemFilterOptions()
    allContests.value = filterRes.data?.contests || []
    allTags.value = filterRes.data?.tags || []
    fetchContestProblems(false)
  } catch (err) {
    console.warn('加载赛事元数据异常', err)
  }
}

onMounted(initData)
watch(contestId, () => {
  fetchContestProblems(false)
})
</script>

<style scoped>
.contest-problem-page {
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
.current-contest-crumb {
  color: var(--lm-text-muted);
}

/* 双栏网格布局 */
.contest-two-col-layout {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 20px;
  align-items: start;
}

/* 左侧栏: 赛事档案与数据大屏 */
.contest-left-sidebar {
  position: sticky;
  top: 72px;
}
.contest-profile-card {
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
.contest-flag-badge {
  padding: 3px 8px;
  border-radius: var(--lm-radius-sm);
  background: var(--lm-primary);
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  font-family: var(--lm-code-font-family);
}
.contest-heat-tag {
  font-size: 11px;
  font-weight: 600;
  color: #d97706;
}
.contest-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--lm-text-primary);
}
.contest-description {
  margin: 0 0 16px;
  font-size: 12px;
  line-height: 1.65;
  color: var(--lm-text-secondary);
}

.contest-rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #f8fafc;
  border-radius: var(--lm-radius-sm);
  margin-bottom: 16px;
}
.rule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}
.rule-item .el-icon {
  color: var(--lm-primary);
  font-size: 14px;
}

/* 四格数据统计看板 */
.contest-stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 18px;
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

/* 分数段分布看板 */
.score-distribution-panel {
  padding-top: 14px;
  border-top: 1px dashed var(--lm-border);
}
.dist-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin-bottom: 10px;
}
.dist-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dist-bar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--lm-text-secondary);
}
.dist-label {
  width: 140px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dist-track {
  flex: 1;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}
.dist-fill {
  height: 100%;
  border-radius: 3px;
}
.dist-fill.tier-1 { background: #18181b; }
.dist-fill.tier-2 { background: #3f3f46; }
.dist-fill.tier-3 { background: #71717a; }
.dist-fill.tier-4 { background: #a1a1aa; }
.dist-val {
  font-weight: 700;
  font-size: 11px;
  width: 28px;
  text-align: right;
}

/* 右侧主面板: 题目列表 */
.contest-right-main {
  min-width: 0;
}
.contest-main-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}
.contest-feed-header {
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
  width: 104px;
}
.feed-select :deep(.el-select__wrapper) {
  border-radius: 9999px !important;
  padding: 2px 10px;
  min-height: 32px;
}

/* 表格与题目行 */
.contest-problem-table {
  background: #ffffff;
}
.table-head, .problem-row {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) minmax(130px, 0.7fr) 60px 56px 66px 70px 18px;
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
  font-family: var(--lm-code-font-family);
}
.problem-tags {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
}
.problem-tag {
  padding: 2px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--lm-text-secondary);
  font-size: 10px;
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
  .contest-two-col-layout {
    grid-template-columns: 1fr;
  }
  .contest-left-sidebar {
    position: static;
  }
}
</style>
