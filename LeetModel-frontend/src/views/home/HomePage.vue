<template>
  <div class="home-page" :aria-busy="loading">
    <section class="home-situation" aria-labelledby="home-heading">
      <div>
        <span class="home-eyebrow">LEETMODEL / 赛前作战中枢</span>
        <h1 id="home-heading">{{ greeting }}，{{ displayName }}</h1>
        <p>{{ situationMessage }}</p>
      </div>
      <div class="situation-status" :class="`situation-status--${situationTone}`">
        <span class="status-pulse" aria-hidden="true"></span>
        {{ situationStatus }}
      </div>
    </section>

    <div v-if="warnings.length" class="home-warning" role="status">
      <CircleAlert :size="17" aria-hidden="true" />
      <span>{{ warnings.join('；') }}</span>
      <button type="button" @click="loadHomeData">
        <RefreshCw :size="14" aria-hidden="true" />
        重新同步
      </button>
    </div>

    <div v-if="loading" class="home-grid home-grid--loading" aria-label="正在加载首页数据">
      <div class="home-primary-column">
        <div class="home-skeleton home-skeleton--hero"></div>
        <div class="home-skeleton home-skeleton--report"></div>
        <div class="home-skeleton home-skeleton--problems"></div>
      </div>
      <aside class="home-secondary-column">
        <div class="home-skeleton home-skeleton--profile"></div>
        <div class="home-skeleton home-skeleton--tools"></div>
      </aside>
    </div>

    <div v-else class="home-grid">
      <main class="home-primary-column">
        <section class="home-card practice-focus" :class="{ 'practice-focus--empty': !focusTeam }" aria-labelledby="practice-heading">
          <template v-if="focusTeam">
            <div class="card-heading">
              <div>
                <span class="section-kicker">{{ focusTeam.practiceStatus === 'IN_PROGRESS' ? '当前进行' : '开赛准备' }}</span>
                <h2 id="practice-heading">{{ focusTeam.name }}</h2>
              </div>
              <span class="problem-code">#{{ formatProblemCode(focusTeam.problemCode) }}</span>
            </div>

            <div class="practice-thesis">
              <div>
                <span class="practice-caption">{{ focusTeam.practiceStatus === 'IN_PROGRESS' ? '距离截止' : '当前赛题' }}</span>
                <strong v-if="focusTeam.practiceStatus === 'IN_PROGRESS'" class="countdown">{{ remainingTimeText }}</strong>
                <strong v-else class="problem-title">{{ focusTeam.problemTitle || `题目 ${focusTeam.problemCode || focusTeam.problemId}` }}</strong>
              </div>
              <div class="practice-meta">
                <span>{{ focusTeam.practiceStatus === 'IN_PROGRESS' ? formatDeadline(focusTeam.deadlineAt) : '职责覆盖后即可开赛' }}</span>
                <span>{{ focusTeam.members?.length || 0 }} / {{ focusTeam.maxMembers || 3 }} 人</span>
              </div>
            </div>

            <ol class="practice-track" aria-label="当前实训进度">
              <li v-for="step in practiceSteps" :key="step.label" :class="{ complete: step.complete, current: step.current }">
                <span class="track-marker" aria-hidden="true"></span>
                <div>
                  <small>{{ step.label }}</small>
                  <strong>{{ step.value }}</strong>
                </div>
              </li>
            </ol>

            <div class="role-row" aria-label="队伍职责覆盖">
              <div v-for="role in roleCoverage" :key="role.key" :class="{ covered: role.covered }">
                <component :is="role.icon" :size="16" aria-hidden="true" />
                <span>{{ role.label }}</span>
                <strong>{{ role.members || '待认领' }}</strong>
              </div>
            </div>

            <router-link :to="focusTeamRoute" class="primary-action">
              {{ focusTeamActionLabel }}
              <ArrowUpRight :size="17" aria-hidden="true" />
            </router-link>
          </template>

          <template v-else>
            <div class="empty-illustration" aria-hidden="true">
              <span class="paper-line paper-line--one"></span>
              <span class="paper-line paper-line--two"></span>
              <Flag :size="27" />
            </div>
            <span class="section-kicker">NEXT PRACTICE</span>
            <h2 id="practice-heading">{{ userStore.isLogin ? '建立下一场全真演练' : '登录后接管你的实训进度' }}</h2>
            <p>{{ userStore.isLogin ? '从一道真题开始，组建队伍、提交论文，完成一次 AI 论文评审闭环。' : '当前仍可浏览真题与榜单；登录后可同步队伍、论文版本和 AI 评审结果。' }}</p>
            <div class="empty-actions">
              <router-link :to="userStore.isLogin ? '/problem' : '/login'" class="primary-action primary-action--inline">
                {{ userStore.isLogin ? '去题库选题' : '登录平台' }}
                <ArrowUpRight :size="17" aria-hidden="true" />
              </router-link>
              <router-link :to="userStore.isLogin ? '/team/square' : '/problem'" class="secondary-action">
                {{ userStore.isLogin ? '寻找队伍' : '先看真题' }}
              </router-link>
            </div>
          </template>
        </section>

        <section class="home-card diagnostic-card" aria-labelledby="diagnostic-heading">
          <div class="card-heading">
            <div>
              <span class="section-kicker">LATEST DIAGNOSTIC</span>
              <h2 id="diagnostic-heading">最近 AI 论文评审</h2>
            </div>
            <span v-if="latestDiagnostic" class="report-date">{{ formatShortDate(latestDiagnostic.review.finishedAt) }}</span>
          </div>

          <router-link v-if="activeReview" :to="activeReviewTeamRoute" class="active-review-banner" aria-live="polite">
            <span class="active-review-marker" aria-hidden="true"></span>
            <div>
              <small>AI 论文评审状态</small>
              <strong>{{ reviewStatusLabel(activeReview.review.status) }}</strong>
              <p>{{ activeReviewStatusHint }}</p>
            </div>
            <span>查看进度 <ArrowRight :size="15" aria-hidden="true" /></span>
          </router-link>

          <div v-if="latestDiagnostic" class="diagnostic-content">
            <div class="score-seal" :aria-label="`评审得分 ${formatScore(latestDiagnostic.review.score)} 分`">
              <strong>{{ formatScore(latestDiagnostic.review.score) }}</strong>
              <small>{{ scoreBand(latestDiagnostic.review.score) }}</small>
            </div>

            <div class="diagnostic-main">
              <div class="diagnostic-context">
                <span>#{{ formatProblemCode(latestDiagnostic.team.problemCode) }}</span>
                <strong>{{ latestDiagnostic.team.name }}</strong>
                <small>{{ latestDiagnostic.review.versionName || workflowLabel(latestDiagnostic.review.workflowVersion) }}</small>
              </div>

              <div class="dimension-list">
                <div v-for="dimension in latestDiagnostic.dimensions" :key="dimension.key" class="dimension-row">
                  <div>
                    <span>{{ dimension.label }}</span>
                    <strong>{{ formatScore(dimension.score) }} / {{ formatScore(dimension.maxScore) }}</strong>
                  </div>
                  <div class="dimension-track" aria-hidden="true">
                    <span :style="{ width: `${dimension.percent}%` }"></span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="latestDiagnostic" class="diagnostic-summary">
            <ShieldCheck :size="18" aria-hidden="true" />
            <p>{{ latestDiagnostic.summary }}</p>
            <span v-if="latestDiagnostic.riskCount">{{ latestDiagnostic.riskCount }} 个待优化项</span>
            <span v-else>已生成结构化结论</span>
          </div>

          <div v-else class="section-empty">
            <FileCheck2 :size="28" aria-hidden="true" />
            <div>
              <strong>{{ userStore.isLogin ? '还没有完成的 AI 论文评审' : '登录后查看 AI 论文评审' }}</strong>
              <p>{{ userStore.isLogin ? '队伍提交 PDF 并完成评审后，这里会展示总分、分项量表与改进入口。' : '你的评审分数、规范风险和建模推导结果将集中显示在这里。' }}</p>
            </div>
          </div>

          <router-link v-if="latestDiagnostic" :to="diagnosticTeamRoute" class="text-action">
            查看完整评审记录
            <ArrowRight :size="16" aria-hidden="true" />
          </router-link>
        </section>

        <section class="home-card problem-card" aria-labelledby="problems-heading">
          <div class="card-heading">
            <div>
              <span class="section-kicker">FAST TRACK</span>
              <h2 id="problems-heading">备战真题</h2>
            </div>
            <router-link to="/problem" class="text-action">
              全部真题
              <ArrowRight :size="16" aria-hidden="true" />
            </router-link>
          </div>

          <div v-if="popularProblems.length" class="problem-list">
            <article v-for="problem in popularProblems" :key="problem.problemId">
              <span class="problem-index">#{{ formatProblemCode(problem.problemCode) }}</span>
              <div>
                <strong>{{ problem.problemTitle || `题目 ${problem.problemCode || problem.problemId}` }}</strong>
                <span>{{ formatCount(problem.practiceCount) }} 支队伍已练习</span>
              </div>
              <router-link :to="{ name: 'ProblemDetail', params: { id: String(problem.problemId) } }" :aria-label="`查看${problem.problemTitle || '该题目'}`">
                查看题目
                <ArrowUpRight :size="15" aria-hidden="true" />
              </router-link>
            </article>
          </div>

          <div v-else class="section-empty section-empty--compact">
            <BookOpen :size="25" aria-hidden="true" />
            <div>
              <strong>暂无热门练习数据</strong>
              <p>可以直接进入题库，按赛事、年份与题号选择真题。</p>
            </div>
          </div>
        </section>
      </main>

      <aside class="home-secondary-column" aria-label="个人实训资产与快捷入口">
        <section class="home-card asset-card" aria-labelledby="assets-heading">
          <div class="card-heading">
            <div>
              <span class="section-kicker">PRACTICE ASSETS</span>
              <h2 id="assets-heading">实训资产</h2>
            </div>
          </div>

          <div class="asset-metrics">
            <div>
              <strong>{{ userStore.isLogin ? teamSummary.completed : '—' }}</strong>
              <span>已完成实训</span>
            </div>
            <div>
              <strong>{{ userStore.isLogin ? highestScore : '—' }}</strong>
              <span>历史最高分</span>
            </div>
          </div>

          <HomeSkillRadar :items="skillRadarItems" />

          <div class="asset-footnote">
            <BrainCircuit :size="16" aria-hidden="true" />
            <span>{{ skillRadarItems.length ? '能力轮廓来自最近一次完成的评审量表。' : '完成一次论文评审后，这里将生成真实能力轮廓。' }}</span>
          </div>
        </section>

        <section class="home-card toolkit-card" aria-labelledby="toolkit-heading">
          <div class="card-heading">
            <div>
              <span class="section-kicker">QUICK ACCESS</span>
              <h2 id="toolkit-heading">突击工具箱</h2>
            </div>
          </div>

          <nav class="toolkit-list" aria-label="突击工具箱">
            <router-link :to="userStore.isLogin ? '/team/square' : '/login'">
              <UsersRound :size="18" aria-hidden="true" />
              <span><strong>寻找队伍</strong><small>查看当前角色缺口</small></span>
              <ArrowRight :size="16" aria-hidden="true" />
            </router-link>
            <router-link to="/ranking">
              <Trophy :size="18" aria-hidden="true" />
              <span><strong>赛题榜单</strong><small>查看同题得分与分位</small></span>
              <ArrowRight :size="16" aria-hidden="true" />
            </router-link>
            <router-link :to="userStore.isLogin ? '/assistant' : '/login'">
              <MessageCircleQuestion :size="18" aria-hidden="true" />
              <span><strong>AI 客服</strong><small>咨询选题与平台操作</small></span>
              <ArrowRight :size="16" aria-hidden="true" />
            </router-link>
          </nav>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  BrainCircuit,
  CircleAlert,
  Code2,
  FileCheck2,
  Flag,
  MessageCircleQuestion,
  PenLine,
  RefreshCw,
  ShieldCheck,
  Trophy,
  UsersRound,
} from '@lucide/vue'
import { getAllMyTeams, getPopularPracticeProblems } from '@/api/team'
import { getTeamReviews } from '@/api/review'
import { getTeamSubmissionHistory } from '@/api/submission'
import { useUserStore } from '@/store/user'
import HomeSkillRadar from './components/HomeSkillRadar.vue'

const userStore = useUserStore()
const loading = ref(true)
const warnings = ref([])
const teams = ref([])
const popularProblems = ref([])
const reviewRecords = ref([])
const focusSubmissions = ref([])
const now = ref(Date.now())
let clockTimer
let reviewTimer

const routeNameByStatus = {
  PREPARING: 'TeamPreparing',
  IN_PROGRESS: 'TeamPracticing',
  ENDED: 'TeamEnded',
}

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => userStore.isLogin ? (userStore.nickname || userStore.username || '同学') : '同学')
const focusTeam = computed(() => {
  const activeTeams = teams.value.filter((team) => team.practiceStatus === 'IN_PROGRESS')
  if (activeTeams.length) {
    return [...activeTeams].sort((a, b) => dateValue(a.deadlineAt) - dateValue(b.deadlineAt))[0]
  }
  return teams.value.find((team) => team.practiceStatus === 'PREPARING') || null
})
const focusTeamRoute = computed(() => teamRoute(focusTeam.value, {
  panel: focusTeam.value?.practiceStatus === 'IN_PROGRESS' ? 'review' : undefined,
}))
const focusTeamActionLabel = computed(() => {
  if (focusTeam.value?.practiceStatus !== 'IN_PROGRESS') return '继续完成开赛准备'
  if (!focusSubmissions.value.length) return '提交论文并开始 AI 评审'
  return '查看论文草稿并开始评审'
})
const activeReview = computed(() => reviewRecords.value
  .filter(record => ['WAITING', 'LEASED', 'RUNNING', 'FAILED', 'UNKNOWN'].includes(record.review?.status))
  .sort((a, b) => dateValue(b.review.updatedAt || b.review.createTime) - dateValue(a.review.updatedAt || a.review.createTime))[0] || null)
const activeReviewTeamRoute = computed(() => teamRoute(activeReview.value?.team, { panel: 'review' }))
const activeReviewStatusHint = computed(() => ({
  WAITING: '任务已进入队列，等待 AI 开始读取最终论文。',
  LEASED: '系统正在准备论文解析与评审上下文。',
  RUNNING: 'AI 正在生成分项评分、问题证据与综合意见。',
  FAILED: '本次执行失败，可进入论文评审工作台重新排队。',
  UNKNOWN: '上游执行结果待核查，请进入工作台查看详情。',
})[activeReview.value?.review?.status] || '评审状态正在同步。')
const latestDiagnostic = computed(() => {
  const completed = reviewRecords.value
    .filter((record) => record.review?.status === 'COMPLETED' && Number.isFinite(Number(record.review?.score)))
    .sort((a, b) => dateValue(b.review.finishedAt) - dateValue(a.review.finishedAt))[0]
  if (!completed) return null
  const result = parseReviewResult(completed.review.resultJson)
  return {
    ...completed,
    dimensions: normalizeDimensions(result),
    summary: result?.overallAssessment || result?.summary || '评审已完成，可进入队伍工作台查看完整量表与证据。',
    riskCount: reviewRiskCount(result),
  }
})
const diagnosticTeamRoute = computed(() => teamRoute(latestDiagnostic.value?.team, { panel: 'review' }))
const skillRadarItems = computed(() => latestDiagnostic.value?.dimensions.slice(0, 5) || [])
const highestScore = computed(() => {
  const scores = reviewRecords.value
    .filter((record) => record.review?.status === 'COMPLETED')
    .map((record) => Number(record.review.score))
    .filter(Number.isFinite)
  if (!scores.length) return '—'
  return formatScore(Math.max(...scores))
})
const teamSummary = computed(() => ({
  completed: teams.value.filter((team) => team.practiceStatus === 'ENDED').length,
  active: teams.value.filter((team) => team.practiceStatus === 'IN_PROGRESS').length,
}))
const remainingTimeText = computed(() => {
  if (!focusTeam.value?.deadlineAt) return '-- : -- : --'
  const remaining = Math.max(0, Math.floor((dateValue(focusTeam.value.deadlineAt) - now.value) / 1000))
  const hours = Math.floor(remaining / 3600)
  const minutes = Math.floor((remaining % 3600) / 60)
  const seconds = remaining % 60
  return `${String(hours).padStart(2, '0')} : ${String(minutes).padStart(2, '0')} : ${String(seconds).padStart(2, '0')}`
})
const roleCoverage = computed(() => {
  const members = focusTeam.value?.members || []
  const definitions = [
    { key: 'modeler', label: '建模', icon: ShieldCheck },
    { key: 'programmer', label: '编程', icon: Code2 },
    { key: 'writer', label: '论文', icon: PenLine },
  ]
  return definitions.map((role) => {
    const names = members.filter((member) => member[role.key]).map((member) => member.nickname || '队员')
    return { ...role, covered: names.length > 0, members: names.join('、') }
  })
})
const focusReviewStatus = computed(() => {
  if (!focusTeam.value) return '待提交'
  const statuses = reviewRecords.value
    .filter((record) => String(record.team.id) === String(focusTeam.value.id))
    .map((record) => record.review.status)
  if (statuses.includes('COMPLETED')) return '已完成'
  if (statuses.some((status) => ['WAITING', 'LEASED', 'RUNNING'].includes(status))) return '评审中'
  if (statuses.includes('FAILED')) return '需重试'
  return '待触发'
})
const practiceSteps = computed(() => {
  const coveredCount = roleCoverage.value.filter((role) => role.covered).length
  const submissionCount = focusSubmissions.value.length
  const inProgress = focusTeam.value?.practiceStatus === 'IN_PROGRESS'
  return [
    { label: '职责就绪', value: `${coveredCount} / 3`, complete: coveredCount === 3, current: coveredCount < 3 },
    { label: '论文版本', value: submissionCount ? `${submissionCount} 版` : '待提交', complete: submissionCount > 0, current: inProgress && submissionCount === 0 },
    { label: 'AI 论文评审', value: focusReviewStatus.value, complete: focusReviewStatus.value === '已完成', current: ['评审中', '需重试'].includes(focusReviewStatus.value) },
  ]
})
const situationStatus = computed(() => {
  if (loading.value) return '正在同步实训数据'
  if (!userStore.isLogin) return '访客模式 · 实训未同步'
  if (focusTeam.value?.practiceStatus === 'IN_PROGRESS') return `${teamSummary.value.active} 场实训进行中`
  if (focusTeam.value?.practiceStatus === 'PREPARING') return '已有队伍等待开赛'
  return '当前无进行中实训'
})
const situationTone = computed(() => focusTeam.value?.practiceStatus === 'IN_PROGRESS' ? 'active' : 'quiet')
const situationMessage = computed(() => {
  if (!userStore.isLogin) return '浏览真题与榜单，登录后从同一处继续你的每一场赛前演练。'
  if (focusTeam.value?.practiceStatus === 'IN_PROGRESS') return '先处理最近截止的队伍，确认论文版本并开始 AI 论文评审。'
  if (focusTeam.value) return '队伍已创建，补齐三项职责后就能启动全真倒计时。'
  return '从真题、论文提交到 AI 评审与改进建议，把下一次模拟完整跑通。'
})

async function loadHomeData() {
  loading.value = true
  warnings.value = []
  teams.value = []
  reviewRecords.value = []
  focusSubmissions.value = []

  const requests = [getPopularPracticeProblems(3)]
  if (userStore.isLogin) requests.push(getAllMyTeams({ page: 1, pageSize: 100 }))
  const [problemResult, teamResult] = await Promise.allSettled(requests)

  if (problemResult.status === 'fulfilled') {
    popularProblems.value = problemResult.value.data || []
  } else {
    popularProblems.value = []
    warnings.value.push('备战真题加载失败')
  }

  if (userStore.isLogin) {
    if (teamResult?.status === 'fulfilled') {
      teams.value = teamResult.value.rows || []
      await loadTeamRelatedData()
    } else {
      warnings.value.push('你的实训数据加载失败')
    }
  }
  loading.value = false
}

async function loadTeamRelatedData() {
  await refreshReviewRecords({ reportFailure: true })

  if (focusTeam.value?.practiceStatus !== 'IN_PROGRESS') return
  try {
    const result = await getTeamSubmissionHistory(focusTeam.value.id)
    focusSubmissions.value = result.data || []
  } catch {
    warnings.value.push('当前队伍的论文版本未能同步')
  }
}

async function refreshReviewRecords({ reportFailure = false } = {}) {
  const reviewTeams = teams.value.filter((team) => team.practiceStatus !== 'PREPARING')
  const reviewResults = await Promise.allSettled(reviewTeams.map((team) => getTeamReviews(team.id)))
  const nextRecords = []
  reviewResults.forEach((result, index) => {
    if (result.status !== 'fulfilled') return
    const team = reviewTeams[index]
    const reviews = result.value.data || []
    nextRecords.push(...reviews.map((review) => ({ team, review })))
  })
  reviewRecords.value = nextRecords
  if (reportFailure && reviewResults.some((result) => result.status === 'rejected')) {
    warnings.value.push('部分 AI 论文评审记录未能同步')
  }
}

function syncReviewPolling(review) {
  if (reviewTimer) window.clearInterval(reviewTimer)
  reviewTimer = review ? window.setInterval(refreshReviewRecords, 5000) : null
}

function teamRoute(team, { panel } = {}) {
  if (!team) return '/team'
  return {
    name: routeNameByStatus[team.practiceStatus] || 'TeamPreparing',
    params: { teamId: String(team.id) },
    ...(panel ? { query: { panel } } : {}),
  }
}

function reviewStatusLabel(value) {
  return ({ WAITING: '评审任务已进入队列', LEASED: '评审任务正在准备', RUNNING: 'AI 正在评审论文', FAILED: '评审失败，需要重试', UNKNOWN: '评审结果待核查' })[value] || '评审状态待同步'
}

function parseReviewResult(value) {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function normalizeDimensions(result) {
  if (!result?.dimensions) return []
  if (Array.isArray(result.dimensions)) {
    return result.dimensions.map((item, index) => makeDimension(
      item.dimensionCode || item.dimensionId || `dimension-${index}`,
      item.dimensionName || item.name || `分项 ${index + 1}`,
      item.score,
      item.maxScore || 20,
    ))
  }
  const labels = {
    assumptionRationality: '假设合理性',
    modelCreativity: '建模创造性',
    resultCorrectness: '结果正确性',
    expressionClarity: '表达清晰性',
  }
  return Object.entries(result.dimensions).map(([key, item]) => makeDimension(
    key,
    labels[key] || key,
    item?.score,
    item?.maxScore || 100,
  ))
}

function makeDimension(key, label, score, maxScore) {
  const numericScore = Number(score) || 0
  const numericMax = Number(maxScore) || 1
  return {
    key,
    label,
    score: numericScore,
    maxScore: numericMax,
    percent: Math.min(100, Math.max(0, Math.round((numericScore / numericMax) * 100))),
  }
}

function reviewRiskCount(result) {
  if (Array.isArray(result?.findings)) {
    return result.findings.filter((item) => item.type !== 'STRENGTH').length
  }
  return Array.isArray(result?.weaknesses) ? result.weaknesses.length : 0
}

function dateValue(value) {
  const time = value ? new Date(value).getTime() : 0
  return Number.isFinite(time) ? time : 0
}

function formatProblemCode(value) {
  if (value === null || value === undefined || value === '') return '--'
  return String(value).padStart(2, '0')
}

function formatScore(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return Number.isInteger(number) ? String(number) : number.toFixed(1)
}

function formatCount(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0'
  if (number >= 10000) return `${(number / 10000).toFixed(1)} 万`
  return new Intl.NumberFormat('zh-CN').format(number)
}

function formatDeadline(value) {
  if (!value) return '截止时间待定'
  return `${new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))} 截止`
}

function formatShortDate(value) {
  if (!value) return '最近完成'
  const date = new Date(value)
  const options = date.getFullYear() === new Date().getFullYear()
    ? { month: '2-digit', day: '2-digit' }
    : { year: 'numeric', month: '2-digit', day: '2-digit' }
  return new Intl.DateTimeFormat('zh-CN', options).format(date)
}

function scoreBand(value) {
  const score = Number(value)
  if (score >= 90) return '突出'
  if (score >= 80) return '良好'
  if (score >= 70) return '稳定'
  if (score >= 60) return '达标'
  return '待加固'
}

function workflowLabel(value) {
  if (value === 'DEEP_EVIDENCE_REVIEW_V4') return 'V4 专业证据评审'
  if (value === 'DEEP_EVIDENCE_REVIEW_V3') return 'V3 双阶段深度评审'
  if (value === 'EVIDENCE_REVIEW_V2') return 'V2 证据化评审'
  if (value === 'BASIC_REVIEW_V1') return 'V1 基础评审'
  return value || 'AI 论文评审'
}

onMounted(() => {
  loadHomeData()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
})

watch(activeReview, syncReviewPolling)

onBeforeUnmount(() => {
  window.clearInterval(clockTimer)
  if (reviewTimer) window.clearInterval(reviewTimer)
})
</script>

<style scoped>
@import './style.css';
</style>
