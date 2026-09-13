<template>
  <div class="ranking-page">
    <!-- 1. 页面级工作台头部 (Academic Workbench Header) -->
    <header class="ranking-header">
      <div class="header-main">
        <div class="header-badge">
          <el-icon><Trophy /></el-icon>
          <span>成果榜单 · ACADEMIC LEADERBOARD</span>
        </div>
        <h1 class="header-title">题目成果与评审排行</h1>
        <p class="header-desc">
          以同一建模赛题为基准，记录各队伍最终学术作品在统一 AI 评审流水线下的竞技成果、得分梯队与技术分布。
        </p>
      </div>

      <div class="header-rules" aria-label="榜单排榜准则">
        <span class="rule-pill">
          <i class="dot-indicator"></i>
          仅统计最终提交作品
        </span>
        <span class="rule-pill">
          <i class="dot-indicator"></i>
          仅纳入已完成评审
        </span>
        <span class="rule-pill">
          <i class="dot-indicator"></i>
          相同得分按并列计名
        </span>
        <span class="rule-pill tech-pill">
          <i class="dot-indicator blue-dot"></i>
          快照协商缓存 (ETag)
        </span>
      </div>
    </header>

    <!-- 2. 一体化工具栏 (Sticky Filter Toolbar) -->
    <section class="toolbar-card" aria-label="排行榜检索与过滤工具栏">
      <div class="toolbar-row">
        <!-- 题目选择 -->
        <div class="toolbar-item problem-selector">
          <label for="select-problem" class="selector-label">
            <el-icon><Document /></el-icon>
            <span>选择赛题</span>
          </label>
          <el-select
            id="select-problem"
            v-model="selectedProblemId"
            filterable
            :loading="loadingProblems"
            placeholder="输入题号或赛题名称查找"
            class="problem-select"
            @change="handleProblemChange"
          >
            <el-option
              v-for="problem in problems"
              :key="problem.id"
              :label="`题号 ${problem.code || problem.id} · ${problem.title}`"
              :value="problem.id"
            >
              <div class="problem-option-item">
                <span class="option-code">#{{ problem.code || problem.id }}</span>
                <span class="option-title">{{ problem.title }}</span>
                <span v-if="problem.year" class="option-year">{{ problem.year }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- 队伍搜索框 -->
        <div class="toolbar-item search-box">
          <label for="search-team" class="selector-label">
            <el-icon><Search /></el-icon>
            <span>搜索队伍</span>
          </label>
          <el-input
            id="search-team"
            v-model="keyword"
            placeholder="输入队伍名称模糊搜索"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleClearSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 操作区：定位我的队伍 & 刷新 -->
        <div class="toolbar-actions">
          <button
            v-if="myTeamInCurrentProblem"
            type="button"
            class="btn-my-team"
            :class="{ 'is-ranked': myTeamRankingItem }"
            :title="myTeamButtonTitle"
            @click="locateMyTeamAction"
          >
            <el-icon><UserFilled /></el-icon>
            <span class="team-label">我的队伍</span>
            <strong v-if="myTeamRankingItem" class="team-rank-badge">
              #{{ myTeamRankingItem.rank }} ({{ formatScore(myTeamRankingItem.score) }}分)
            </strong>
            <span v-else class="team-status-badge">实训中</span>
          </button>

          <button
            type="button"
            class="btn-refresh"
            :disabled="loading"
            title="刷新榜单数据"
            @click="handleManualRefresh"
          >
            <el-icon :class="{ 'is-spinning': loading }"><Refresh /></el-icon>
            <span class="btn-text">刷新</span>
          </button>
        </div>
      </div>

      <!-- 当前选中题目元信息速览栏 -->
      <div v-if="currentProblem" class="problem-meta-strip">
        <div class="strip-left">
          <span class="problem-code-badge">#{{ currentProblem.code || currentProblem.id }}</span>
          <h2 class="problem-strip-title">{{ currentProblem.title }}</h2>
          <span class="strip-tag contest-tag">{{ currentProblem.contestName || '建模公开赛题' }}</span>
          <span v-if="currentProblem.year" class="strip-tag">{{ currentProblem.year }} 年</span>
          <span class="strip-tag">{{ languageLabel(currentProblem.statementLanguage) }}</span>
          <span class="strip-tag difficulty-tag" :class="`diff-${currentProblem.difficulty}`">
            {{ difficultyLabel(currentProblem.difficulty) }}
          </span>
        </div>
        <router-link :to="`/problem/${currentProblem.id}`" class="problem-detail-link">
          <span>阅读题目详情</span>
          <el-icon><ArrowRight /></el-icon>
        </router-link>
      </div>
    </section>

    <!-- 3. 加载骨架屏 (Skeleton Loading State) -->
    <section v-if="loading && !overview" class="skeleton-wrapper" aria-label="榜单加载中">
      <div class="skeleton-metrics">
        <div v-for="i in 4" :key="`sk-m-${i}`" class="skeleton-card skeleton-pulse"></div>
      </div>
      <div class="skeleton-distribution skeleton-card skeleton-pulse"></div>
      <div class="skeleton-podium">
        <div v-for="i in 3" :key="`sk-p-${i}`" class="skeleton-podium-card skeleton-pulse"></div>
      </div>
      <div class="skeleton-table skeleton-card skeleton-pulse"></div>
    </section>

    <!-- 4. 接口错误状态 (Error State) -->
    <section v-else-if="rankingError" class="error-container">
      <div class="error-card">
        <el-icon class="error-icon"><Warning /></el-icon>
        <div class="error-content">
          <h3>无法获取当前题目排行榜</h3>
          <p>{{ rankingError }}</p>
        </div>
        <button type="button" class="btn-retry" @click="loadData">
          <el-icon><Refresh /></el-icon>
          <span>重新加载</span>
        </button>
      </div>
    </section>

    <!-- 5. 榜单主内容区 (Data Content) -->
    <main v-else-if="overview" class="ranking-main-content">
      <!-- 5.1 数据看板区：核心指标 + 分数分布直方图 -->
      <section class="dashboard-grid">
        <!-- 4 核心指标卡 -->
        <div class="metrics-grid">
          <article class="metric-card">
            <div class="metric-icon-box blue-box">
              <el-icon><UserFilled /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-label">{{ hasActiveKeyword ? '匹配队伍' : '上榜队伍' }}</span>
              <div class="metric-value-row">
                <strong class="metric-number">{{ rankingItems.length }}</strong>
                <span class="metric-unit">支</span>
              </div>
              <p class="metric-sub">{{ hasActiveKeyword ? `筛选关键词 “${appliedKeyword}”` : '已完成最终稿评审' }}</p>
            </div>
          </article>

          <article class="metric-card">
            <div class="metric-icon-box gold-box">
              <el-icon><Trophy /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-label">最高作品得分</span>
              <div class="metric-value-row">
                <strong class="metric-number">{{ highestScore }}</strong>
                <span v-if="highestScore !== '-'" class="metric-unit">分</span>
              </div>
              <p class="metric-sub">{{ rankingItems.length ? '当前全榜最优成绩' : '等待首支队伍上榜' }}</p>
            </div>
          </article>

          <article class="metric-card">
            <div class="metric-icon-box green-box">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-label">平均评审得分</span>
              <div class="metric-value-row">
                <strong class="metric-number">{{ averageScore }}</strong>
                <span v-if="averageScore !== '-'" class="metric-unit">分</span>
              </div>
              <p class="metric-sub">{{ rankingItems.length ? `样本基数 ${rankingItems.length} 支队伍` : '暂无评审样本' }}</p>
            </div>
          </article>

          <article class="metric-card">
            <div class="metric-icon-box slate-box">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="metric-info">
              <span class="metric-label">快照生成时间</span>
              <div class="metric-value-row">
                <strong class="metric-number metric-time">{{ formatSnapshotTime(overview.computedAt) }}</strong>
              </div>
              <p class="metric-sub">权威有效计算批次</p>
            </div>
          </article>
        </div>

        <!-- 分数分布直方图 (Score Distribution Histogram) -->
        <div class="distribution-panel" aria-label="分数梯队分布图">
          <div class="distribution-header">
            <div class="dist-title-box">
              <el-icon><DataAnalysis /></el-icon>
              <h3>分数分布梯队 (0–100 分)</h3>
            </div>
            <span class="dist-desc">
              共统计 {{ totalDistributionTeams }} 支队伍
              <span v-if="myTeamRankingItem" class="my-score-indicator">
                · 我的队伍: {{ formatScore(myTeamRankingItem.score) }}分
              </span>
            </span>
          </div>

          <div v-if="distributionBuckets.length" class="distribution-chart">
            <div class="histogram-track">
              <div
                v-for="(bucket, idx) in distributionBuckets"
                :key="`bucket-${idx}`"
                class="histogram-column"
                :class="{ 'is-my-bucket': isTeamInBucket(bucket) }"
              >
                <!-- 悬停 Tooltip -->
                <div class="histogram-tooltip">
                  <div class="tooltip-range">{{ bucket.label }}</div>
                  <div class="tooltip-count">{{ bucket.count }} 支队伍 ({{ bucket.percentage }}%)</div>
                  <div v-if="isTeamInBucket(bucket)" class="tooltip-my-team">★ 我的队伍在此分数段</div>
                </div>

                <!-- 柱顶数值与柱条 -->
                <div class="bar-container">
                  <span v-if="bucket.count > 0" class="bar-count-label">{{ bucket.count }}</span>
                  <div
                    class="bar-fill"
                    :style="{ height: `${Math.max(bucket.heightRatio * 100, bucket.count > 0 ? 8 : 2)}%` }"
                  >
                    <div v-if="isTeamInBucket(bucket)" class="my-team-dot"></div>
                  </div>
                </div>

                <!-- X 轴刻度标签 -->
                <span class="bar-x-label">{{ bucket.shortLabel }}</span>
              </div>
            </div>
          </div>

          <div v-else class="distribution-empty">
            <span>暂无足够的分数样本生成梯队分布图</span>
          </div>
        </div>
      </section>

      <!-- 5.2 榜单内容：若有上榜数据 -->
      <template v-if="rankingItems.length">
        <!-- 5.2.1 Top 3 荣誉领奖台 (Podium Section) -->
        <section v-if="!hasActiveKeyword && podiumItems.length" class="podium-section" aria-label="荣誉前三名领奖台">
          <div class="section-title-row">
            <div class="title-left">
              <el-icon class="title-icon"><Medal /></el-icon>
              <h2>荣誉领奖台 · Top 3</h2>
              <span class="title-caption">当前赛题评审综合表现最前列的建模队伍</span>
            </div>
          </div>

          <div class="podium-grid" :class="`podium-size-${podiumItems.length}`">
            <article
              v-for="item in podiumItems"
              :key="item.teamId"
              class="podium-card"
              :class="[`podium-rank-${item.rank}`, { 'is-my-team-card': isMyTeam(item.teamId) }]"
              @click="scrollToTeamRow(item.teamId)"
            >
              <div class="podium-header">
                <div class="rank-crown">
                  <span class="crown-badge">{{ getRankLabel(item.rank) }}</span>
                  <span class="rank-number">#{{ item.rank }}</span>
                </div>
                <span v-if="isMyTeam(item.teamId)" class="my-team-pill">我的队伍</span>
              </div>

              <div class="podium-body">
                <div class="podium-avatar">
                  {{ teamInitial(item.teamName) }}
                </div>
                <h3 class="podium-team-name" :title="item.teamName">{{ item.teamName }}</h3>
                <div class="podium-score-row">
                  <strong class="podium-score">{{ formatScore(item.score) }}</strong>
                  <span class="podium-score-unit">分</span>
                </div>
              </div>

              <div class="podium-footer">
                <div class="footer-item">
                  <span class="footer-label">评审流水线</span>
                  <span class="workflow-chip">{{ workflowLabel(item.workflowVersion) }}</span>
                </div>
                <div class="footer-item">
                  <span class="footer-label">最终稿提交</span>
                  <span class="time-text">{{ formatDate(item.submittedAt) }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <!-- 5.2.2 完整排名数据表格 (Full Ranking Table) -->
        <section class="ranking-table-section" aria-label="完整排名数据表">
          <div class="table-card-header">
            <div class="header-info">
              <h2>{{ hasActiveKeyword ? '筛选匹配结果' : '全题成果排名' }}</h2>
              <span class="count-tag">共 {{ rankingItems.length }} 支队伍</span>
              <span v-if="hasActiveKeyword" class="filter-tip">已按队伍名 “{{ appliedKeyword }}” 过滤</span>
            </div>
            <div class="table-actions">
              <span class="table-rule-note">排序依据：最终稿 AI 评审得分（从高到低）</span>
            </div>
          </div>

          <div class="ranking-table-container">
            <div class="custom-table" role="table">
              <!-- 表头 -->
              <div class="table-row table-head-row" role="row">
                <div class="col-cell col-rank" role="columnheader">排名</div>
                <div class="col-cell col-team" role="columnheader">参赛队伍</div>
                <div class="col-cell col-score" role="columnheader">最终得分</div>
                <div class="col-cell col-review" role="columnheader">AI 评审版本</div>
                <div class="col-cell col-time" role="columnheader">最终稿提交时间</div>
                <div class="col-cell col-action" role="columnheader">操作</div>
              </div>

              <!-- 表体数据行 -->
              <div
                v-for="item in rankingItems"
                :id="`team-row-${item.teamId}`"
                :key="item.teamId"
                class="table-row table-body-row"
                :class="{
                  'is-top-three': item.rank <= 3,
                  'is-current-user-team': isMyTeam(item.teamId),
                  'row-highlighted': highlightedTeamId === item.teamId
                }"
                role="row"
              >
                <!-- 排名 -->
                <div class="col-cell col-rank" role="cell">
                  <span class="rank-badge" :class="`rank-badge-${item.rank}`">
                    {{ item.rank }}
                  </span>
                </div>

                <!-- 队伍名称 & 头像 -->
                <div class="col-cell col-team" role="cell">
                  <div class="team-cell-inner">
                    <span class="team-avatar-icon" :class="`avatar-rank-${item.rank <= 3 ? item.rank : 'default'}`">
                      {{ teamInitial(item.teamName) }}
                    </span>
                    <div class="team-names">
                      <div class="name-row">
                        <strong class="team-title" :title="item.teamName">{{ item.teamName }}</strong>
                        <span v-if="isMyTeam(item.teamId)" class="inline-my-team-tag">我的队伍</span>
                      </div>
                      <span class="team-sub-info">已提交最终学术论文成果</span>
                    </div>
                  </div>
                </div>

                <!-- 最终得分 -->
                <div class="col-cell col-score" role="cell">
                  <div class="score-display">
                    <strong class="score-val">{{ formatScore(item.score) }}</strong>
                    <span class="score-unit">分</span>
                  </div>
                </div>

                <!-- 评审版本与完成时间 -->
                <div class="col-cell col-review" role="cell">
                  <div class="review-meta">
                    <span class="workflow-badge" :title="item.workflowVersion">
                      {{ workflowLabel(item.workflowVersion) }}
                    </span>
                    <small class="review-date">完成于 {{ formatCompactDate(item.reviewFinishedAt) }}</small>
                  </div>
                </div>

                <!-- 最终稿提交时间 -->
                <div class="col-cell col-time" role="cell">
                  <div class="time-meta">
                    <span class="submission-time">{{ formatDate(item.submittedAt) }}</span>
                    <small class="time-hint">以最终稿参与排榜</small>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="col-cell col-action" role="cell">
                  <button
                    type="button"
                    class="action-btn copy-btn"
                    title="复制队伍名称"
                    @click.stop="copyTeamName(item.teamName)"
                  >
                    <el-icon><CopyDocument /></el-icon>
                    <span>复制</span>
                  </button>
                  <router-link
                    v-if="item.teamId"
                    :to="`/team/detail/${item.teamId}`"
                    class="action-btn team-link-btn"
                    title="查看队伍详情与成员"
                  >
                    <span>队伍</span>
                    <el-icon><ArrowRight /></el-icon>
                  </router-link>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- 5.3 空状态：搜索无结果 (Search Empty State) -->
      <section v-else-if="hasActiveKeyword && !loading" class="empty-state-panel">
        <div class="empty-icon-circle">
          <el-icon><Search /></el-icon>
        </div>
        <h3>未找到匹配的队伍</h3>
        <p>在当前题目 “{{ currentProblem?.title }}” 的上榜队伍中，没有包含关键词 “{{ appliedKeyword }}” 的结果。</p>
        <button type="button" class="btn-primary-action" @click="handleClearSearch">
          清空搜索关键词
        </button>
      </section>

      <!-- 5.4 空状态：题目暂无队伍上榜 (No Submissions Empty State) -->
      <section v-else-if="!loading" class="empty-state-panel">
        <div class="empty-icon-circle">
          <el-icon><Trophy /></el-icon>
        </div>
        <h3>该题目还在等待首个上榜成果</h3>
        <p>队伍在题目下完成建模论文最终稿提交并通过系统 AI 评审后，榜单将自动生成并展示在此。</p>
        <div class="empty-action-group">
          <router-link :to="`/problem/${selectedProblemId}`" class="btn-primary-action">
            前往题目查看详情与开始建模
          </router-link>
        </div>
      </section>
    </main>

    <!-- 6. 初始引导态 (未选题目) -->
    <section v-else-if="!loading" class="empty-state-panel">
      <div class="empty-icon-circle">
        <el-icon><Document /></el-icon>
      </div>
      <h3>请在上方选择赛题</h3>
      <p>选择一道正在练习或关心的建模题目，即可查看其成果榜单与分数分布梯队。</p>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Clock,
  CopyDocument,
  DataAnalysis,
  Document,
  Medal,
  Refresh,
  Search,
  Trophy,
  UserFilled,
  Warning
} from '@element-plus/icons-vue'
import { getPublicProblemList } from '@/api/problem'
import { getProblemScoreDistribution, getRanking } from '@/api/ranking'
import { getAllMyTeams } from '@/api/team'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 数据加载与状态
const loadingProblems = ref(false)
const loading = ref(false)
const problems = ref([])
const selectedProblemId = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const overview = ref(null)
const distribution = ref(null)
const rankingError = ref('')
const myTeams = ref([])
const highlightedTeamId = ref(null)

// 计算属性
const currentProblem = computed(() =>
  problems.value.find((item) => String(item.id) === String(selectedProblemId.value))
)

const rankingItems = computed(() => overview.value?.items || [])
const hasActiveKeyword = computed(() => Boolean(appliedKeyword.value))

// 榜单指标
const highestScore = computed(() => {
  if (!rankingItems.value.length) return '-'
  return formatScore(rankingItems.value[0].score)
})

const averageScore = computed(() => {
  if (!rankingItems.value.length) return '-'
  const validScores = rankingItems.value
    .map((item) => Number(item.score))
    .filter((score) => Number.isFinite(score))
  if (!validScores.length) return '-'
  const sum = validScores.reduce((acc, cur) => acc + cur, 0)
  return (sum / validScores.length).toFixed(1)
})

// Top 3 领奖台卡片排布（2-1-3 经典领奖台布局）
const podiumItems = computed(() => {
  const top = rankingItems.value.slice(0, 3)
  if (top.length === 3 && top[0].rank === 1 && top[1].rank === 2 && top[2].rank === 3) {
    return [top[1], top[0], top[2]] // 亚军、冠军、季军
  }
  return top
})

// 当前用户在此题目的队伍
const myTeamInCurrentProblem = computed(() => {
  if (!userStore.isLogin || !selectedProblemId.value || !myTeams.value.length) return null
  return myTeams.value.find((team) => String(team.problemId) === String(selectedProblemId.value))
})

const myTeamRankingItem = computed(() => {
  if (!myTeamInCurrentProblem.value || !rankingItems.value.length) return null
  return rankingItems.value.find((item) => String(item.teamId) === String(myTeamInCurrentProblem.value.id))
})

const myTeamButtonTitle = computed(() => {
  if (myTeamRankingItem.value) {
    return `点击在下方榜单中定位我的队伍 (第 #${myTeamRankingItem.value.rank} 名)`
  }
  return '队伍当前正在实训中，尚未完成最终稿评审'
})

// 分数梯队聚合计算 (0-100 分)
const distributionBuckets = computed(() => {
  if (!distribution.value?.buckets || !distribution.value.buckets.length) return []

  const rawBuckets = distribution.value.buckets
  const totalTeams = rawBuckets.reduce((acc, cur) => acc + (Number(cur.teamCount) || 0), 0)
  if (totalTeams === 0) return []

  // 划分 7 个典型建模竞赛分数梯队
  const tiers = [
    { label: '0–59 分 (需提升)', shortLabel: '<60', min: 0, max: 59 },
    { label: '60–69 分 (及格线)', shortLabel: '60-69', min: 60, max: 69 },
    { label: '70–79 分 (良好档)', shortLabel: '70-79', min: 70, max: 79 },
    { label: '80–84 分 (优良档)', shortLabel: '80-84', min: 80, max: 84 },
    { label: '85–89 分 (卓越档)', shortLabel: '85-89', min: 85, max: 89 },
    { label: '90–94 分 (领跑档)', shortLabel: '90-94', min: 90, max: 94 },
    { label: '95–100 分 (顶尖档)', shortLabel: '95-100', min: 95, max: 100 }
  ]

  const bucketStats = tiers.map((tier) => {
    let count = 0
    for (const item of rawBuckets) {
      const score = Number(item.score)
      if (score >= tier.min && score <= tier.max) {
        count += Number(item.teamCount) || 0
      }
    }
    const percentage = totalTeams > 0 ? ((count / totalTeams) * 100).toFixed(1) : '0.0'
    return {
      ...tier,
      count,
      percentage
    }
  })

  // 计算最大桶用于柱高百分比缩放
  const maxCount = Math.max(...bucketStats.map((item) => item.count), 1)

  return bucketStats.map((item) => ({
    ...item,
    heightRatio: item.count / maxCount
  }))
})

const totalDistributionTeams = computed(() => {
  if (!distribution.value?.buckets) return 0
  return distribution.value.buckets.reduce((acc, cur) => acc + (Number(cur.teamCount) || 0), 0)
})

// 辅助方法
function formatScore(val) {
  if (val == null || val === '') return '-'
  const num = Number(val)
  return Number.isFinite(num) ? num.toFixed(1) : '-'
}

function formatDate(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(0, 16)
}

function formatCompactDate(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(5, 16)
}

function formatSnapshotTime(val) {
  if (!val) return '-'
  const str = String(val).replace('T', ' ')
  return `${str.slice(5, 10)} ${str.slice(11, 16)}`
}

function languageLabel(lang) {
  return lang === 'EN' ? '英文题面' : '中文题面'
}

function difficultyLabel(diff) {
  return { 1: '简单', 2: '中等', 3: '困难' }[diff] || '常规难度'
}

function workflowLabel(ver) {
  if (!ver) return '基础评审 V1'
  if (ver === 'BASIC_REVIEW_V1') return '基础评审 V1'
  return String(ver).replaceAll('_', ' ')
}

function teamInitial(name) {
  return String(name || '队').trim().slice(0, 1).toUpperCase()
}

function getRankLabel(rank) {
  if (rank === 1) return 'CHAMPION'
  if (rank === 2) return 'RUNNER-UP'
  if (rank === 3) return 'THIRD'
  return `TOP ${rank}`
}

function isMyTeam(teamId) {
  if (!myTeamInCurrentProblem.value) return false
  return String(teamId) === String(myTeamInCurrentProblem.value.id)
}

function isTeamInBucket(bucket) {
  if (!myTeamRankingItem.value) return false
  const score = Math.round(Number(myTeamRankingItem.value.score))
  return score >= bucket.min && score <= bucket.max
}

// 复制队伍名称
async function copyTeamName(name) {
  if (!name) return
  try {
    await navigator.clipboard.writeText(name)
    ElMessage.success(`队伍名称 “${name}” 已复制`)
  } catch {
    ElMessage.info(`队伍名称：${name}`)
  }
}

// 滚动定位并高亮指定队伍行
function scrollToTeamRow(teamId) {
  nextTick(() => {
    const el = document.getElementById(`team-row-${teamId}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      highlightedTeamId.value = teamId
      setTimeout(() => {
        if (highlightedTeamId.value === teamId) {
          highlightedTeamId.value = null
        }
      }, 2500)
    }
  })
}

// 定位我的队伍操作
function locateMyTeamAction() {
  if (!myTeamInCurrentProblem.value) return
  if (myTeamRankingItem.value) {
    // 如果存在搜索筛选，先清空搜索以确保在全榜中能定位到
    if (hasActiveKeyword.value) {
      keyword.value = ''
      appliedKeyword.value = ''
      loadRanking().then(() => {
        scrollToTeamRow(myTeamRankingItem.value.teamId)
      })
    } else {
      scrollToTeamRow(myTeamRankingItem.value.teamId)
    }
  } else {
    ElMessage.info(`队伍 “${myTeamInCurrentProblem.value.name}” 正在实训中，提交最终论文后将自动参与排名`)
  }
}

// 数据加载核心
async function loadProblems() {
  loadingProblems.value = true
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 })
    problems.value = res.data?.rows || []
    const queryProblem = problems.value.find(
      (item) => String(item.id) === String(route.query.problemId || '')
    )
    selectedProblemId.value = queryProblem?.id || problems.value[0]?.id || null

    if (selectedProblemId.value) {
      await loadData()
    }
  } catch (error) {
    rankingError.value = error.message || '赛题列表加载失败'
    ElMessage.error(rankingError.value)
  } finally {
    loadingProblems.value = false
  }
}

async function loadMyTeams() {
  if (!userStore.isLogin) return
  try {
    const res = await getAllMyTeams()
    myTeams.value = res.rows || []
  } catch {
    // 静默降级，不阻断主流程
    myTeams.value = []
  }
}

async function loadData() {
  if (!selectedProblemId.value) return
  await Promise.all([loadRanking(), loadDistribution(), loadMyTeams()])
}

async function loadRanking() {
  if (!selectedProblemId.value) return
  loading.value = true
  rankingError.value = ''
  const trimmed = keyword.value.trim()
  try {
    const res = await getRanking(selectedProblemId.value, trimmed)
    overview.value = res.data
    appliedKeyword.value = trimmed
  } catch (error) {
    rankingError.value = error.message || '排行榜数据加载失败'
    overview.value = null
    ElMessage.error(rankingError.value)
  } finally {
    loading.value = false
  }
}

async function loadDistribution() {
  if (!selectedProblemId.value) return
  try {
    const res = await getProblemScoreDistribution(selectedProblemId.value)
    distribution.value = res.data
  } catch {
    distribution.value = null
  }
}

async function handleProblemChange() {
  keyword.value = ''
  appliedKeyword.value = ''
  await router.replace({ query: { ...route.query, problemId: String(selectedProblemId.value) } })
  await loadData()
}

async function handleSearch() {
  await loadRanking()
}

async function handleClearSearch() {
  keyword.value = ''
  await loadRanking()
}

async function handleManualRefresh() {
  await loadData()
  ElMessage.success('榜单与分数分布已更新')
}

// 监听路由变化
watch(
  () => route.query.problemId,
  (newId) => {
    if (newId && String(newId) !== String(selectedProblemId.value)) {
      selectedProblemId.value = Number(newId) || newId
      loadData()
    }
  }
)

onMounted(async () => {
  await loadProblems()
})
</script>

<style scoped>
/* ==========================================================================
   LeetModel 成果榜单样式系统 (Monochrome & Academic Professional)
   ========================================================================== */

.ranking-page {
  width: min(100%, 1180px);
  margin: 0 auto;
  padding: 8px 16px 48px;
  color: var(--lm-text-primary);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 1. 工作台头部 */
.ranking-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 28px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-xs);
}

.header-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.header-badge .el-icon {
  font-size: 14px;
  color: #d97706; /* 琥珀微金 */
}

.header-title {
  margin: 2px 0 0;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.25;
  color: var(--lm-text-primary);
  letter-spacing: -0.02em;
}

.header-desc {
  margin: 6px 0 0;
  max-width: 820px;
  color: var(--lm-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.header-rules {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 4px;
}

.rule-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: 999px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}

.dot-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981; /* 活力绿 */
}

.blue-dot {
  background: #3b82f6; /* ETag 蓝点 */
}

/* 2. 一体化工具栏 */
.toolbar-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-xs);
  overflow: hidden;
}

.toolbar-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  padding: 16px 20px;
  background: var(--lm-surface);
}

.toolbar-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selector-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 650;
  color: var(--lm-text-muted);
}

.problem-selector {
  flex: 1;
  min-width: 280px;
}

.problem-select {
  width: 100%;
}

.problem-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.option-code {
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: var(--lm-text-muted);
  font-size: 11px;
}

.option-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-year {
  font-size: 11px;
  color: var(--lm-text-muted);
  padding: 1px 6px;
  background: var(--lm-bg-secondary);
  border-radius: 4px;
}

.search-box {
  width: 260px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.btn-my-team {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--lm-transition);
  white-space: nowrap;
}

.btn-my-team:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: var(--lm-text-primary);
}

.btn-my-team.is-ranked {
  background: rgba(37, 99, 235, 0.06);
  border-color: rgba(37, 99, 235, 0.3);
  color: #1d4ed8;
}

.team-rank-badge {
  font-weight: 700;
  color: #1d4ed8;
}

.team-status-badge {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 12px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-refresh:hover:not(:disabled) {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
  border-color: #cbd5e1;
}

.btn-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.is-spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 题目元信息速览条 */
.problem-meta-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  background: var(--lm-bg-secondary);
  border-top: 1px solid var(--lm-border);
}

.strip-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.problem-code-badge {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  color: var(--lm-text-muted);
}

.problem-strip-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strip-tag {
  font-size: 11px;
  padding: 2px 7px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  color: var(--lm-text-secondary);
}

.contest-tag {
  font-weight: 600;
  color: var(--lm-text-primary);
}

.diff-1 { color: #059669; }
.diff-2 { color: #d97706; }
.diff-3 { color: #dc2626; }

.problem-detail-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 650;
  color: #2563eb;
  white-space: nowrap;
  transition: color var(--lm-transition);
}

.problem-detail-link:hover {
  color: #1d4ed8;
}

/* 3. 骨架屏 */
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.skeleton-pulse {
  background: linear-gradient(90deg, #f4f4f5 25%, #e4e4e7 37%, #f4f4f5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

.skeleton-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.skeleton-card {
  height: 96px;
  border-radius: var(--lm-radius);
  border: 1px solid var(--lm-border);
}

.skeleton-distribution {
  height: 180px;
}

.skeleton-podium {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.skeleton-podium-card {
  height: 220px;
  border-radius: var(--lm-radius-lg);
  border: 1px solid var(--lm-border);
}

.skeleton-table {
  height: 320px;
}

/* 4. 错误状态 */
.error-container {
  padding: 20px 0;
}

.error-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--lm-radius-lg);
  color: #991b1b;
}

.error-icon {
  font-size: 28px;
  color: #ef4444;
}

.error-content h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.error-content p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #b91c1c;
}

.btn-retry {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: #ef4444;
  color: #fff;
  border: none;
  border-radius: var(--lm-radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--lm-transition);
}

.btn-retry:hover {
  background: #dc2626;
}

/* 5. 榜单主内容区 */
.ranking-main-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 5.1 数据看板区：4 指标卡 + 直方图 */
.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1.25fr);
  gap: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.metric-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: var(--lm-shadow-xs);
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.metric-card:hover {
  box-shadow: var(--lm-shadow-sm);
  transform: translateY(-1px);
}

.metric-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  font-size: 18px;
  flex-shrink: 0;
}

.blue-box { background: #eff6ff; color: #2563eb; }
.gold-box { background: #fefce8; color: #ca8a04; }
.green-box { background: #f0fdf4; color: #16a34a; }
.slate-box { background: #f8fafc; color: #475569; }

.metric-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.metric-label {
  font-size: 11px;
  font-weight: 650;
  color: var(--lm-text-muted);
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
  margin: 2px 0 0;
}

.metric-number {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--lm-text-primary);
  line-height: 1.2;
}

.metric-number.metric-time {
  font-size: 14px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
  margin-top: 5px;
}

.metric-unit {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 500;
}

.metric-sub {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--lm-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分数分布直方图 */
.distribution-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px 20px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: var(--lm-shadow-xs);
}

.distribution-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.dist-title-box {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-text-primary);
}

.dist-title-box .el-icon {
  font-size: 16px;
  color: #2563eb;
}

.dist-title-box h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}

.dist-desc {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.my-score-indicator {
  color: #2563eb;
  font-weight: 700;
}

.distribution-chart {
  position: relative;
  height: 110px;
  display: flex;
  align-items: flex-end;
}

.histogram-track {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  border-bottom: 1px solid var(--lm-border);
  padding-bottom: 4px;
}

.histogram-column {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  cursor: pointer;
}

.bar-container {
  position: relative;
  width: 100%;
  height: calc(100% - 20px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.bar-count-label {
  font-size: 10px;
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: var(--lm-text-muted);
  margin-bottom: 2px;
}

.bar-fill {
  width: 100%;
  max-width: 32px;
  background: #e2e8f0;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s ease, background 0.2s ease;
  position: relative;
}

.histogram-column:hover .bar-fill {
  background: #94a3b8;
}

.histogram-column.is-my-bucket .bar-fill {
  background: #3b82f6;
}

.histogram-column.is-my-bucket:hover .bar-fill {
  background: #2563eb;
}

.my-team-dot {
  position: absolute;
  top: -4px;
  left: 50%;
  transform: translateX(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fbbf24;
  box-shadow: 0 0 0 2px #fff;
}

.bar-x-label {
  font-size: 10px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
  margin-top: 4px;
  white-space: nowrap;
}

/* Tooltip */
.histogram-tooltip {
  position: absolute;
  bottom: calc(100% - 10px);
  left: 50%;
  transform: translateX(-50%);
  background: #09090b;
  color: #fff;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 11px;
  pointer-events: none;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s ease, transform 0.15s ease;
  white-space: nowrap;
  z-index: 10;
  box-shadow: var(--lm-shadow);
}

.histogram-column:hover .histogram-tooltip {
  opacity: 1;
  visibility: visible;
  transform: translateX(-50%) translateY(-4px);
}

.tooltip-range {
  font-weight: 700;
  margin-bottom: 2px;
}

.tooltip-count {
  color: #cbd5e1;
}

.tooltip-my-team {
  margin-top: 3px;
  color: #fde047;
  font-weight: 600;
}

.distribution-empty {
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lm-text-muted);
  font-size: 12px;
  border: 1px dashed var(--lm-border);
  border-radius: var(--lm-radius-sm);
}

/* 5.2.1 Top 3 领奖台 (Podium) */
.podium-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 18px;
  color: #d97706;
}

.title-left h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--lm-text-primary);
}

.title-caption {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.podium-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  align-items: flex-end;
}

.podium-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  cursor: pointer;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition), border-color var(--lm-transition);
  position: relative;
  overflow: hidden;
}

.podium-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--lm-shadow);
}

/* 冠军卡片突出 */
.podium-rank-1 {
  border-color: rgba(217, 119, 6, 0.4);
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.2) 0%, var(--lm-surface) 35%);
  box-shadow: 0 4px 20px rgba(217, 119, 6, 0.08);
  min-height: 240px;
}

.podium-rank-2 {
  border-color: rgba(148, 163, 184, 0.45);
  background: linear-gradient(180deg, rgba(241, 245, 249, 0.5) 0%, var(--lm-surface) 35%);
  min-height: 220px;
}

.podium-rank-3 {
  border-color: rgba(202, 138, 4, 0.3);
  background: linear-gradient(180deg, rgba(254, 249, 195, 0.3) 0%, var(--lm-surface) 35%);
  min-height: 220px;
}

.podium-card.is-my-team-card {
  outline: 2px solid #2563eb;
  outline-offset: -1px;
}

.podium-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rank-crown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.crown-badge {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 2px 6px;
  border-radius: 4px;
}

.podium-rank-1 .crown-badge { background: #fef3c7; color: #b45309; }
.podium-rank-2 .crown-badge { background: #f1f5f9; color: #475569; }
.podium-rank-3 .crown-badge { background: #ffedd5; color: #c2410c; }

.rank-number {
  font-size: 18px;
  font-weight: 800;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-primary);
}

.my-team-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  background: #2563eb;
  color: #fff;
  border-radius: 999px;
}

.podium-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
}

.podium-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: var(--lm-bg-secondary);
  border: 2px solid var(--lm-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 800;
  color: var(--lm-text-primary);
}

.podium-rank-1 .podium-avatar {
  border-color: #f59e0b;
  background: #fef3c7;
  color: #b45309;
}

.podium-team-name {
  margin: 0;
  font-size: 16px;
  font-weight: 750;
  color: var(--lm-text-primary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.podium-score-row {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.podium-score {
  font-size: 30px;
  font-weight: 850;
  letter-spacing: -0.04em;
  color: var(--lm-text-primary);
  line-height: 1;
}

.podium-rank-1 .podium-score { color: #b45309; }

.podium-score-unit {
  font-size: 12px;
  color: var(--lm-text-muted);
  font-weight: 600;
}

.podium-footer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--lm-border-light);
  font-size: 11px;
}

.footer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.footer-label {
  color: var(--lm-text-muted);
}

.workflow-chip {
  font-size: 10px;
  padding: 1px 6px;
  background: var(--lm-bg-secondary);
  border-radius: 4px;
  color: var(--lm-text-secondary);
}

.time-text {
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
}

/* 5.2.2 完整排名数据表格 */
.ranking-table-section {
  display: flex;
  flex-direction: column;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-xs);
  overflow: hidden;
}

.table-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  background: var(--lm-surface);
  border-bottom: 1px solid var(--lm-border);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-info h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 750;
  color: var(--lm-text-primary);
}

.count-tag {
  font-size: 12px;
  color: var(--lm-text-muted);
  padding: 2px 8px;
  background: var(--lm-bg-secondary);
  border-radius: 999px;
}

.filter-tip {
  font-size: 12px;
  color: #2563eb;
}

.table-rule-note {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.ranking-table-container {
  width: 100%;
  overflow-x: auto;
}

.custom-table {
  width: 100%;
  min-width: 820px;
  display: flex;
  flex-direction: column;
}

.table-row {
  display: grid;
  grid-template-columns: 80px minmax(220px, 1.4fr) 110px minmax(160px, 1fr) minmax(180px, 1.1fr) 130px;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--lm-border-light);
  transition: background var(--lm-transition);
}

.table-head-row {
  background: var(--lm-bg-secondary);
  border-bottom: 1px solid var(--lm-border);
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  letter-spacing: 0.04em;
  padding: 10px 20px;
}

.table-body-row:hover {
  background: #f8fafc;
}

.table-body-row.is-current-user-team {
  background: rgba(37, 99, 235, 0.03);
  border-left: 3px solid #2563eb;
}

/* 高亮定位动画 */
.table-body-row.row-highlighted {
  animation: pulse-highlight 2.5s ease-out;
}

@keyframes pulse-highlight {
  0% {
    background: rgba(254, 240, 138, 0.5);
    box-shadow: 0 0 12px rgba(234, 179, 8, 0.4);
  }
  50% {
    background: rgba(254, 240, 138, 0.25);
  }
  100% {
    background: transparent;
  }
}

.col-cell {
  display: flex;
  align-items: center;
}

/* 排名列 */
.col-rank {
  justify-content: flex-start;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border-radius: 6px;
  font-family: var(--lm-code-font-family);
  font-size: 13px;
  font-weight: 800;
  background: var(--lm-bg-secondary);
  color: var(--lm-text-secondary);
}

.rank-badge-1 { background: #fef3c7; color: #b45309; border: 1px solid rgba(217, 119, 6, 0.3); }
.rank-badge-2 { background: #f1f5f9; color: #475569; border: 1px solid rgba(148, 163, 184, 0.4); }
.rank-badge-3 { background: #ffedd5; color: #c2410c; border: 1px solid rgba(194, 65, 12, 0.3); }

/* 队伍列 */
.team-cell-inner {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.team-avatar-icon {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 750;
  color: var(--lm-text-primary);
  flex-shrink: 0;
}

.avatar-rank-1 { background: #fef3c7; color: #b45309; border-color: #fcd34d; }
.avatar-rank-2 { background: #f1f5f9; color: #475569; border-color: #cbd5e1; }
.avatar-rank-3 { background: #ffedd5; color: #c2410c; border-color: #fdba74; }

.team-names {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.team-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-my-team-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  background: #2563eb;
  color: #fff;
  border-radius: 4px;
  flex-shrink: 0;
}

.team-sub-info {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 1px;
}

/* 得分列 */
.score-display {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-val {
  font-size: 19px;
  font-weight: 850;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-primary);
}

.score-unit {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 500;
}

/* 评审列 */
.review-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workflow-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 650;
  color: #1e40af;
  background: #eff6ff;
  padding: 2px 7px;
  border-radius: 4px;
  border: 1px solid #dbeafe;
  width: fit-content;
}

.review-date {
  font-size: 10px;
  color: var(--lm-text-muted);
  font-family: var(--lm-code-font-family);
}

/* 提交时间列 */
.time-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.submission-time {
  font-size: 12px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
}

.time-hint {
  font-size: 10px;
  color: var(--lm-text-muted);
}

/* 操作列 */
.col-action {
  gap: 6px;
  justify-content: flex-start;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--lm-radius-sm);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.copy-btn {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  color: var(--lm-text-secondary);
}

.copy-btn:hover {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
  border-color: #cbd5e1;
}

.team-link-btn {
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  color: var(--lm-text-primary);
}

.team-link-btn:hover {
  background: #e2e8f0;
  color: #0f172a;
}

/* 5.3 & 5.4 空状态通用样式 */
.empty-state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  background: var(--lm-surface);
  border: 1px dashed var(--lm-border);
  border-radius: var(--lm-radius-lg);
  text-align: center;
}

.empty-icon-circle {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: var(--lm-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--lm-text-muted);
  margin-bottom: 14px;
}

.empty-state-panel h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 750;
  color: var(--lm-text-primary);
}

.empty-state-panel p {
  margin: 8px 0 20px;
  max-width: 480px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--lm-text-secondary);
}

.btn-primary-action {
  display: inline-flex;
  align-items: center;
  padding: 8px 18px;
  background: var(--lm-primary);
  color: #fff;
  border: none;
  border-radius: var(--lm-radius-sm);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--lm-transition);
  text-decoration: none;
}

.btn-primary-action:hover {
  background: var(--lm-primary-light);
  color: #fff;
}

/* ==========================================================================
   响应式断点规则 (Responsive Breakpoints)
   ========================================================================== */

@media (max-width: 992px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .podium-grid {
    grid-template-columns: 1fr;
  }

  .podium-card, .podium-rank-1, .podium-rank-2, .podium-rank-3 {
    min-height: auto;
  }

  .podium-rank-1 { order: 1; }
  .podium-rank-2 { order: 2; }
  .podium-rank-3 { order: 3; }
}

@media (max-width: 768px) {
  .toolbar-row {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .problem-selector, .search-box {
    width: 100%;
    min-width: 0;
  }

  .toolbar-actions {
    margin-left: 0;
    justify-content: flex-end;
  }

  .problem-meta-strip {
    flex-direction: column;
    align-items: flex-start;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .table-row {
    grid-template-columns: 50px minmax(160px, 1fr) 80px 100px;
    gap: 8px;
    padding: 12px 14px;
  }

  .col-review, .col-time {
    display: none;
  }
}
</style>
