<template>
  <div class="problem-detail-page" v-loading="loading">
    <template v-if="problem">
      <!-- 顶部面包屑与返回栏 -->
      <div class="detail-breadcrumb-bar">
        <button type="button" class="back-link-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </button>
        <span class="nav-sep">/</span>
        <span v-if="problem.contestName" class="contest-crumb-link" @click="goToContest">
          {{ problem.contestName }}
        </span>
        <span v-if="problem.contestName" class="nav-sep">/</span>
        <span class="current-problem-crumb">
          <span v-if="problem.code" class="crumb-code">#{{ String(problem.code).padStart(2, '0') }}</span>
          {{ problem.title }}
        </span>
      </div>

      <!-- 双栏工作台网格：左边题目描述主体，右边具体题目详细 -->
      <div class="problem-detail-grid">
        <!-- 左边栏：只保留详细的 md 题目内容 (占 1fr) -->
        <main class="detail-main-col">
          <!-- 纯净题面 Markdown 卡片 -->
          <div class="detail-markdown-card">

            <article v-if="problem.contentMarkdown" class="markdown-body" v-html="renderedMarkdown" />
            <el-empty v-else description="暂无题面描述" />
          </div>
        </main>

        <!-- 右边栏：单卡片连续呈现题目概览、附件、实训数据和解题提示 -->
        <aside class="detail-side-col">
          <section class="aside-card problem-side-card">
            <div class="overview-kicker">
              <div class="overview-kicker-tags">
                <span v-if="problem.code" class="problem-code-badge">题号 {{ problem.code }}</span>
                <span class="overview-year">{{ problem.year || '—' }}</span>
                <span class="contest-code-badge">{{ problem.contestCode || '赛事' }}</span>
                <span class="contest-number-badge">{{ problem.problemNumber || 'X' }}题</span>
              </div>
              <button
                type="button"
                class="problem-fav-btn"
                :class="{ active: isFavorited }"
                :title="isFavorited ? '已收藏，点击取消' : '收藏此题'"
                :aria-label="isFavorited ? '取消收藏' : '收藏题目'"
                @click="toggleFavorite"
              >
                <el-icon><StarFilled v-if="isFavorited" /><Star v-else /></el-icon>
              </button>
            </div>
            <h1 class="problem-heading">{{ problem.title }}</h1>

            <div class="overview-facts" aria-label="题目投入信息">
              <span class="difficulty-capsule" :class="`diff-${problem.difficulty}`">
                {{ difficultyLabel(problem.difficulty) }}
              </span>
              <span class="difficulty-capsule duration-capsule">{{ formatDuration(problem.durationMinutes) }}</span>
              <span
                v-for="tag in algorithmTags"
                :key="`algorithm-${tag.name}`"
                class="difficulty-capsule algorithm-capsule"
              >{{ tag.name }}</span>
            </div>

            <div class="action-hub-section">
              <button type="button" class="primary-action-btn" @click="createProblemTeam">
                <el-icon><User /></el-icon>
                <span>以此题开始实训</span>
              </button>
              <div class="secondary-actions-row">
                <button type="button" class="secondary-action-btn" @click="findProblemTeams">
                  <el-icon><Search /></el-icon>
                  <span>寻找队伍</span>
                </button>
                <button type="button" class="secondary-action-btn" @click="viewRanking">
                  <el-icon><Trophy /></el-icon>
                  <span>完整榜单</span>
                </button>
              </div>
            </div>
            <div class="detail-attachments-section">
              <div class="side-section-heading attachment-heading">
                <span class="side-section-title"><Paperclip :size="14" aria-hidden="true" />附件</span>
              </div>

              <div v-if="problem.attachments?.length" class="side-attachments-feed">
                <div v-for="att in problem.attachments" :key="att.id" class="side-attachment-item">
                  <component
                    :is="attachmentIcon(att)"
                    class="side-att-icon"
                    :class="`file-kind-${attachmentKind(att)}`"
                    aria-hidden="true"
                  />
                  <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="side-att-link" :title="att.fileName">{{ att.fileName }}</a>
                  <span v-else class="side-att-title" :title="att.fileName">{{ att.fileName }}</span>
                  <span class="side-att-size">{{ formatFileSize(att.fileSize) }}</span>
                  <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="side-att-dl-btn" :aria-label="`下载 ${att.fileName}`">
                    <el-icon><Download /></el-icon>
                  </a>
                  <span v-else class="side-att-unavailable">不可下载</span>
                </div>
              </div>

              <div v-else class="side-empty-attachment-notice">
                <el-icon class="notice-icon"><InfoFilled /></el-icon>
                <div class="notice-desc">暂无可下载的附件</div>
              </div>
            </div>

          <div class="side-card-divider" aria-hidden="true"></div>

          <div v-loading="practiceStatsLoading" class="side-content-section">

            <div class="practice-metrics" aria-label="题目实训数据">
              <div class="metric-item secondary-metric">
                <strong class="metric-value">{{ formatCount(practiceStats.teamCount) }}</strong>
                <span class="metric-label">参赛队伍</span>
              </div>
              <div class="metric-item primary-metric">
                <strong class="metric-value">{{ averageScoreDisplay }}</strong>
                <span class="metric-label">平均得分</span>
              </div>
              <div class="metric-item secondary-metric">
                <strong class="metric-value">{{ formatCount(practiceStats.submissionCount) }}</strong>
                <span class="metric-label">提交总次数</span>
              </div>
            </div>
          </div>

          <div class="side-card-divider" aria-hidden="true"></div>

          <div v-loading="scoreDistributionLoading" class="score-distribution-section">
            <div class="side-section-heading score-distribution-heading">
              <span class="side-section-title">
                <BarChart3 :size="14" class="section-title-icon icon-score-dist" aria-hidden="true" />分数分布
              </span>
              <span class="score-distribution-caption">队伍数</span>
            </div>

            <div
              v-if="scoreDistributionHasData"
              class="score-distribution-chart"
              role="img"
              aria-label="按四舍五入后的最终得分统计队伍数量"
            >
              <div class="score-chart-y-axis" aria-hidden="true">
                <span>{{ scoreDistributionMax }}</span>
                <span>{{ Math.ceil(scoreDistributionMax / 2) }}</span>
                <span>0</span>
              </div>
              <div class="score-chart-main">
                <div class="score-chart-grid" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="score-bars">
                  <div
                    v-for="item in scoreDistribution"
                    :key="item.score"
                    class="score-bar-column"
                    :title="`${item.score} 分：${item.count} 支队伍`"
                  >
                    <span
                      v-if="item.count"
                      class="score-bar"
                      :style="{ height: `${scoreBarHeight(item.count)}%` }"
                    ></span>
                  </div>
                </div>
                <div class="score-axis-labels" aria-hidden="true">
                  <span v-for="score in scoreAxisLabels" :key="score" :style="{ left: `${score}%` }">{{ score }}</span>
                </div>
              </div>
            </div>
            <p v-else class="score-distribution-empty">暂无已完成评分的队伍</p>
          </div>

          <div class="side-card-divider" aria-hidden="true"></div>

          <div class="side-content-section">
            <div class="side-section-heading focus-heading">
              <span class="side-section-title">
                <Lightbulb :size="14" class="section-title-icon icon-solution-hint" aria-hidden="true" />提示
              </span>
            </div>

            <p class="solution-hint">{{ problem.solutionHint || '暂无解题提示' }}</p>
          </div>
          </section>
        </aside>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="题目不存在或尚未发布" />
    <CreateTeamDialog v-model="showCreateDialog" :preset-problem="problem" />
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import 'github-markdown-css/github-markdown.css'
import {
  ArrowLeft,
  Download,
  InfoFilled,
  Search,
  Star,
  StarFilled,
  Trophy,
  User
} from '@element-plus/icons-vue'
import {
  BarChart3,
  File as FileIcon,
  FileArchive,
  FileImage,
  FileSpreadsheet,
  FileText,
  Lightbulb,
  Paperclip
} from '@lucide/vue'
import { getPublicProblemDetail } from '@/api/problem'
import { getProblemParticipationStats } from '@/api/team'
import { getProblemSubmissionStats } from '@/api/submission'
import { getProblemScoreDistribution } from '@/api/ranking'
import CreateTeamDialog from '@/views/team/components/CreateTeamDialog.vue'
import {
  getFavoriteCount,
  isProblemFavorited,
  toggleProblemFavorite
} from '@/utils/favoriteStorage'

const route = useRoute()
const router = useRouter()
const workbench = inject('problemWorkbench', null)
const loading = ref(false)
const practiceStatsLoading = ref(false)
const scoreDistributionLoading = ref(false)
const showCreateDialog = ref(false)
const problem = ref(null)
const practiceStats = ref({ teamCount: null, submissionCount: null, participantCount: null })
const scoreDistribution = ref([])
const scoreAxisLabels = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
const favVersion = ref(0)

const isFavorited = computed(() => {
  void favVersion.value
  if (!problem.value?.id) return false
  return isProblemFavorited(problem.value.id)
})

const syncFavoriteState = () => {
  favVersion.value++
  workbench?.updateFavCount?.(getFavoriteCount())
}

const toggleFavorite = () => {
  if (!problem.value?.id) return
  toggleProblemFavorite(problem.value.id)
  syncFavoriteState()
}

const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const formatDuration = (minutes) => {
  if (!minutes) return '-'
  const hours = minutes / 60
  return `${Number.isInteger(hours) ? hours : hours.toFixed(1)}h`
}
const formatScore = (value) => {
  if (value == null || value === '') return '—'
  const score = Number(value)
  return Number.isFinite(score) && score >= 0 ? score.toFixed(1) : '—'
}
const averageScoreDisplay = computed(() => {
  if (problem.value?.averageScore != null && Number(problem.value.averageScore) >= 0) {
    return formatScore(problem.value.averageScore)
  }
  const validBuckets = (scoreDistribution.value || []).filter((item) => item.count > 0)
  if (validBuckets.length > 0) {
    const totalScore = validBuckets.reduce((sum, item) => sum + item.score * item.count, 0)
    const totalTeams = validBuckets.reduce((sum, item) => sum + item.count, 0)
    if (totalTeams > 0) {
      return (totalScore / totalTeams).toFixed(1)
    }
  }
  return '—'
})
const formatCount = (count) => Number.isFinite(Number(count)) ? Number(count).toLocaleString('zh-CN') : '—'
const scoreDistributionMax = computed(() => Math.max(...scoreDistribution.value.map((item) => item.count), 0))
const scoreDistributionHasData = computed(() => scoreDistributionMax.value > 0)
const scoreBarHeight = (count) => {
  if (!scoreDistributionMax.value || !count) return 0
  return Math.max((count / scoreDistributionMax.value) * 100, 4)
}
const formatFileSize = (bytes) => {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
const prepareMarkdownImages = (html) => {
  if (typeof document === 'undefined') return html
  const container = document.createElement('div')
  container.innerHTML = html
  container.querySelectorAll('img').forEach((image) => {
    // Gitee 图床会拒绝带本地 Referer 的嵌入请求；不泄露页面地址即可正常加载。
    image.setAttribute('referrerpolicy', 'no-referrer')
    image.setAttribute('loading', 'lazy')
    image.setAttribute('decoding', 'async')
  })
  return container.innerHTML
}
const renderedMarkdown = computed(() => {
  if (!problem.value?.contentMarkdown) return ''
  const html = marked.parse(problem.value.contentMarkdown, {
    async: false,
    breaks: true,
    gfm: true,
  })
  return prepareMarkdownImages(DOMPurify.sanitize(html))
})

const detailTags = computed(() => problem.value?.tags || [])
const algorithmTags = computed(() => detailTags.value.filter((tag) => tag.type === 'MODEL_ALGORITHM'))

function attachmentKind(attachment) {
  const fileName = String(attachment?.fileName || '').toLowerCase()
  const extension = fileName.includes('.') ? fileName.split('.').pop() : ''
  if (['zip', 'rar', '7z', 'tar', 'gz', 'tgz', 'bz2', 'xz'].includes(extension)) return 'archive'
  if (['csv', 'tsv', 'xls', 'xlsx', 'json'].includes(extension)) return 'sheet'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(extension)) return 'image'
  if (['pdf', 'doc', 'docx', 'txt', 'md', 'rtf'].includes(extension)) return 'document'
  return 'file'
}

const attachmentIcons = { archive: FileArchive, sheet: FileSpreadsheet, image: FileImage, document: FileText, file: FileIcon }
const attachmentIcon = (attachment) => attachmentIcons[attachmentKind(attachment)]

const createProblemTeam = () => { showCreateDialog.value = true }
const findProblemTeams = () => router.push({ name: 'TeamSquare', query: { mode: 'problems', problemId: String(problem.value.id) } })
const viewRanking = () => router.push({ name: 'Ranking', query: { problemId: String(problem.value.id) } })
const goBack = () => {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push({
      path: '/problem',
      query: route.query
    })
  }
}
const goToContest = () => {
  if (problem.value?.contestId) {
    router.push(`/problem/contest/${problem.value.contestId}`)
  } else {
    router.push('/problem')
  }
}

const fetchPracticeStats = async () => {
  const problemId = problem.value?.id
  practiceStats.value = { teamCount: null, submissionCount: null, participantCount: null }
  if (!problemId) return

  practiceStatsLoading.value = true
  const [participationResult, submissionResult] = await Promise.allSettled([
    getProblemParticipationStats(problemId),
    getProblemSubmissionStats(problemId),
  ])
  if (participationResult.status === 'fulfilled') {
    practiceStats.value.teamCount = participationResult.value.data?.teamCount ?? 0
    practiceStats.value.participantCount = participationResult.value.data?.participantCount ?? 0
  }
  if (submissionResult.status === 'fulfilled') {
    practiceStats.value.submissionCount = submissionResult.value.data?.submissionCount ?? 0
  }
  practiceStatsLoading.value = false
}

const fetchScoreDistribution = async () => {
  const problemId = problem.value?.id
  scoreDistribution.value = Array.from({ length: 101 }, (_, score) => ({ score, count: 0 }))
  if (!problemId) return

  scoreDistributionLoading.value = true
  try {
    const response = await getProblemScoreDistribution(problemId)
    const distribution = scoreDistribution.value
    for (const item of response.data?.buckets || []) {
      const score = Number(item?.score)
      const teamCount = Number(item?.teamCount)
      if (Number.isInteger(score) && score >= 0 && score <= 100 && Number.isFinite(teamCount)) {
        distribution[score].count = Math.max(0, teamCount)
      }
    }
  } catch {
    // 排行数据不可用时保留空状态，不影响题面、附件和实训数据浏览。
  } finally {
    scoreDistributionLoading.value = false
  }
}

const fetchDetail = async () => {
  loading.value = true
  problem.value = null
  try {
    const response = await getPublicProblemDetail(route.params.id)
    problem.value = response.data || null
    fetchPracticeStats()
    fetchScoreDistribution()
  } catch (error) {
    ElMessage.error(error.message || '获取题目详情失败')
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, fetchDetail)
onMounted(() => {
  syncFavoriteState()
  fetchDetail()
  window.addEventListener('lm-fav-change', syncFavoriteState)
  window.addEventListener('storage', syncFavoriteState)
})

onUnmounted(() => {
  window.removeEventListener('lm-fav-change', syncFavoriteState)
  window.removeEventListener('storage', syncFavoriteState)
})
</script>

<style scoped>
.problem-detail-page {
  width: 100%;
  min-height: calc(100vh - 120px);
  padding: 0 0 48px;
}

/* 面包屑返回栏 */
.detail-breadcrumb-bar {
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

.contest-crumb-link {
  color: var(--lm-text-secondary);
  cursor: pointer;
  font-weight: 500;
  transition: color var(--lm-transition);
}
.contest-crumb-link:hover {
  color: var(--lm-text-primary);
}

.current-problem-crumb {
  color: var(--lm-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 500px;
}

.crumb-code {
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: #27272a;
}

/* 左右双栏网格 */
.problem-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 28px;
  align-items: flex-start;
  width: 100%;
}

/* 左栏：题目描述与附件 */
.detail-main-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.problem-heading {
  margin: 0;
  font-size: 20px;
  font-weight: 750;
  color: #111827;
  line-height: 1.42;
  word-break: break-word;
}

/* Markdown 题面卡片 */
.detail-markdown-card {
  background: #ffffff;
  overflow: hidden;
}

.markdown-body {
  padding: 28px 32px 36px;
  background: transparent;
  font-family: var(--lm-font-family);
  font-synthesis: weight;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 1.75;
}

.markdown-body :deep(strong),
.markdown-body :deep(b) {
  font-weight: 800;
}

.markdown-body :deep(table) {
  margin: 1.5rem auto;
  text-align: center;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  text-align: center;
  vertical-align: middle;
}

/* 附件卡片 */
.side-attachments-feed {
  display: flex;
  flex-direction: column;
}

.side-attachment-item {
  min-height: 46px;
  padding: 8px 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.side-att-icon {
  width: 17px;
  height: 17px;
  color: #64748b;
  flex-shrink: 0;
}

.side-att-link {
  flex: 1;
  min-width: 0;
  color: #18181b;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--lm-transition);
}
.side-att-link:hover {
  color: var(--lm-primary);
  text-decoration: underline;
}

.side-att-title {
  flex: 1;
  min-width: 0;
  color: #18181b;
  font-size: 12px;
  font-weight: 600;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-att-size {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--lm-text-muted);
  font-family: var(--lm-code-font-family);
}

.side-att-dl-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border-radius: 4px;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: #4b5563;
  font-size: 13px;
  text-decoration: none;
  transition: all var(--lm-transition);
  flex-shrink: 0;
}
.side-att-dl-btn:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}

.side-att-unavailable {
  flex-shrink: 0;
  color: #a1a1aa;
  font-size: 10px;
}

.file-kind-archive { color: #c2410c; }
.file-kind-sheet { color: #15803d; }
.file-kind-image { color: #7c3aed; }
.file-kind-document { color: #2563eb; }
.file-kind-file { color: #64748b; }

.side-empty-attachment-notice {
  margin-top: 10px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: var(--lm-radius-sm);
  border: 1px solid var(--lm-border-light);
}

.notice-desc {
  font-size: 11px;
  color: var(--lm-text-secondary);
  line-height: 1.45;
}

/* 右栏：单卡片连续信息流 */
.detail-side-col {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 88px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 360px;
  padding: 12px 14px 26px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.detail-side-col::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.aside-card {
  flex-shrink: 0;
  padding: 16px;
  border: 0;
  border-radius: 8px;
  background: #ffffff;
  color: #71717a;
  box-shadow: 0 6px 18px rgba(30, 41, 59, 0.085);
}

.overview-kicker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  min-height: 22px;
}

.overview-kicker-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.problem-code-badge,
.overview-year,
.contest-code-badge,
.contest-number-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1;
  font-family: var(--lm-font-family);
  background: #f4f4f5;
  box-sizing: border-box;
}

.problem-code-badge {
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 700;
  flex-shrink: 0;
}

.overview-year,
.contest-code-badge,
.contest-number-badge {
  font-weight: 600;
}

.problem-fav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border-radius: 4px;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: #94a3b8;
  cursor: pointer;
  font-size: 13px;
  transition: all var(--lm-transition);
  flex-shrink: 0;
  margin-left: auto;
  box-sizing: border-box;
}

.problem-fav-btn :deep(.el-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  width: 100%;
  height: 100%;
}

.problem-fav-btn:hover {
  background: #fefce8;
  color: #eab308;
  border-color: #fde047;
}

.problem-fav-btn.active {
  color: #eab308;
  background: #fefce8;
  border-color: #fde047;
}

.problem-fav-btn.active:hover {
  color: #ca8a04;
  background: #fef08a;
  border-color: #facc15;
}

.overview-facts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 13px;
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.side-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.side-section-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.side-section-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #5f6068;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.section-title-icon {
  flex-shrink: 0;
}

.icon-score-dist {
  color: #2563eb;
}

.icon-solution-hint {
  color: #f59e0b;
}

.side-section-caption {
  color: #94a3b8;
  font-size: 10px;
  line-height: 1.45;
}

.title-inline-icon {
  font-size: 14px;
  color: var(--lm-text-muted);
}

.text-action-btn {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.text-action-btn:hover {
  color: #1d4ed8;
  text-decoration: underline;
}

/* 行动卡片 */
.action-hub-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
  padding-top: 4px;
}

.primary-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 42px;
  border-radius: 4px;
  border: 1px solid #1d4ed8;
  background: #2563eb;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.primary-action-btn:hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.2);
  transform: translateY(-1px);
}

.secondary-actions-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.secondary-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  border-radius: 4px;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: #3f3f46;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.secondary-action-btn:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
  background: #eff6ff;
}

/* 实训数据 */
.practice-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.metric-value {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 26px;
  line-height: 1;
  font-family: var(--lm-code-font-family);
  font-weight: 700;
}

.primary-metric .metric-value {
  color: #1e3a8a;
  font-size: 20px;
}

.secondary-metric .metric-value {
  color: #334155;
  font-size: 16px;
}

.metric-label {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
  text-align: center;
}

.score-distribution-section {
  margin-top: 0;
  padding-top: 0;
}

.score-distribution-heading {
  align-items: center;
  margin-bottom: 12px;
}

.score-distribution-caption {
  color: #94a3b8;
  font-size: 10px;
}

.score-distribution-chart {
  display: flex;
  min-height: 118px;
  gap: 7px;
}

.score-chart-y-axis {
  display: flex;
  flex: 0 0 18px;
  flex-direction: column;
  justify-content: space-between;
  padding: 1px 0 15px;
  color: #94a3b8;
  font-family: var(--lm-code-font-family);
  font-size: 9px;
  line-height: 1;
  text-align: right;
}

.score-chart-main {
  position: relative;
  flex: 1;
  min-width: 0;
  padding-bottom: 15px;
}

.score-chart-grid,
.score-bars {
  position: absolute;
  inset: 0 0 15px;
}

.score-chart-grid {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.score-chart-grid span {
  display: block;
  border-top: 1px dashed #e2e8f0;
}

.score-bars {
  display: flex;
  align-items: flex-end;
  gap: 1px;
}

.score-bar-column {
  display: flex;
  flex: 1 1 0;
  align-items: flex-end;
  justify-content: center;
  min-width: 0;
  height: 100%;
}

.score-bar {
  display: block;
  width: min(100%, 6px);
  min-height: 3px;
  border-radius: 2px 2px 0 0;
  background: #2563eb;
  transition: height 180ms ease, background-color 180ms ease;
}

.score-bar-column:hover .score-bar {
  background: #1d4ed8;
}

.score-axis-labels {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 11px;
  color: #94a3b8;
  font-family: var(--lm-code-font-family);
  font-size: 9px;
}

.score-axis-labels span {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
}

.score-axis-labels span:first-child {
  transform: translateX(0);
}

.score-axis-labels span:last-child {
  transform: translateX(-100%);
}

.score-distribution-empty {
  margin: 0;
  color: #94a3b8;
  font-size: 11px;
}

.difficulty-capsule {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f1f5f9;
  font-size: 11px;
  font-weight: 600;
}

.diff-1 { color: #047857; background: #ecfdf5; border-color: #bbf7d0; }
.diff-2 { color: #b45309; background: #fffbeb; border-color: #fde68a; }
.diff-3 { color: #b91c1c; background: #fef2f2; border-color: #fecaca; }
.duration-capsule { color: #1d4ed8; background: #eff6ff; border-color: #bfdbfe; }
.context-capsule { color: #7c3aed; background: #f5f3ff; border-color: #ddd6fe; }
.algorithm-capsule { color: #0f766e; background: #f0fdfa; border-color: #99f6e4; }

/* 解题提示 */
.focus-heading {
  margin-bottom: 10px;
}

.solution-hint {
  margin: 0;
  padding-left: 12px;
  border-left: 2px solid #f59e0b;
  color: #422006;
  font-size: 12px;
  font-weight: 550;
  line-height: 1.72;
}

.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 11px;
}

.detail-tag-chip {
  padding: 2px 0;
  border: 0;
  border-bottom: 1px solid #d6d3d1;
  border-radius: 0;
  background: transparent;
  color: #52525b;
  font-size: 11px;
  font-weight: 600;
}

.attachment-heading {
  align-items: center;
}

.detail-attachments-section {
  margin-top: 20px;
  padding-top: 0;
}

.side-card-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 18px 0;
  border: 0;
}

.side-content-section {
  margin-top: 0;
  padding-top: 0;
}

@media (max-width: 1100px) {
  .problem-detail-grid {
    grid-template-columns: 1fr;
  }
  .detail-side-col {
    position: static;
    width: 100%;
    max-height: none;
    overflow: visible;
    padding: 0;
  }
}

@media (max-width: 640px) {
  .detail-breadcrumb-bar {
    align-items: flex-start;
  }

  .current-problem-crumb {
    max-width: 180px;
  }

  .markdown-body {
    padding: 20px 16px 28px;
  }

  .problem-heading {
    font-size: 18px;
  }
}
</style>
