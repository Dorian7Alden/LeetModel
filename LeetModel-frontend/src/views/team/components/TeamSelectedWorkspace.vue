<template>
  <section class="selected-team-workspace">
    <header class="workspace-header">
      <div>
        <span class="workspace-kicker">{{ isPreparing ? 'FORMATION DESK' : 'PRACTICE DESK' }}</span>
        <div class="workspace-title-row">
          <h1>{{ team.name }}</h1>
          <span class="workspace-status" :class="`status-${team.practiceStatus.toLowerCase()}`">{{ statusLabel }}</span>
        </div>
        <p>{{ workspaceDescription }}</p>
      </div>
      <div v-if="!isPreparing" class="workspace-clock">
        <span>{{ isInProgress ? '距离截止' : '练习结束于' }}</span>
        <strong>{{ isInProgress ? remainingTimeText : formatDate(team.endedAt) }}</strong>
      </div>
    </header>

    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <template #default><el-button type="danger" plain size="small" @click="loadWorkspaceData">重新加载</el-button></template>
    </el-alert>

    <div v-if="isPreparing" v-loading="loading" class="formation-workspace">
      <section class="formation-hero" :class="{ ready: allRolesCovered }">
        <div class="formation-progress">
          <div class="progress-ring" :style="{ '--progress': `${readinessPercent * 3.6}deg` }">
            <span>{{ readinessPercent }}%</span>
          </div>
          <div>
            <span class="panel-kicker">开赛准备度</span>
            <h2>{{ allRolesCovered ? '队伍已具备开赛条件' : '继续补齐关键职责' }}</h2>
            <p>{{ allRolesCovered ? '建模、编程、论文三类职责已经覆盖，可以开始限时练习。' : `目前已覆盖 ${coveredRoleCount} / 3 类职责，开赛前仍需明确协作分工。` }}</p>
          </div>
        </div>
        <div class="formation-actions">
          <el-button v-if="team.canManage" type="primary" :disabled="!allRolesCovered" :loading="startingPractice" @click="startPractice">开始练习</el-button>
        </div>
      </section>

      <div class="formation-metrics">
        <article>
          <UsersRound :size="18" />
          <div><strong>{{ team.members?.length || 0 }} / {{ team.maxMembers || 3 }}</strong><span>成员容量</span></div>
        </article>
        <article>
          <ShieldCheck :size="18" />
          <div><strong>{{ coveredRoleCount }} / 3</strong><span>职责覆盖</span></div>
        </article>
        <article>
          <UserPlus :size="18" />
          <div><strong>{{ openRecruitments.length }}</strong><span>开放招募</span></div>
        </article>
        <article>
          <Inbox :size="18" />
          <div><strong>{{ team.canManage ? applicationTotal : '—' }}</strong><span>{{ team.canManage ? '待审核申请' : '队长处理申请' }}</span></div>
        </article>
      </div>

      <div class="formation-grid">
        <section class="formation-panel role-panel">
          <div class="panel-heading">
            <div><span class="panel-kicker">ROLE COVERAGE</span><h2>职责配置</h2></div>
            <span>{{ coveredRoleCount }} / 3</span>
          </div>
          <div class="role-board">
            <article v-for="role in roleDefinitions" :key="role.key" :class="{ covered: role.members.length }">
              <div class="role-icon"><component :is="role.icon" :size="18" /></div>
              <div class="role-copy">
                <strong>{{ role.label }}</strong>
                <span>{{ role.description }}</span>
              </div>
              <div class="role-owner">
                <CheckCircle2 v-if="role.members.length" :size="15" />
                <Circle v-else :size="15" />
                <span>{{ role.members.length ? role.members.join('、') : '待认领' }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="formation-panel member-panel">
          <div class="panel-heading">
            <div><span class="panel-kicker">TEAM ROSTER</span><h2>成员协作</h2></div>
            <span>{{ team.members?.length || 0 }} 人</span>
          </div>
          <div class="formation-member-list">
            <article v-for="member in team.members || []" :key="member.userId">
              <span class="member-avatar">
                <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" />
                <template v-else>{{ initial(member.nickname || member.userId) }}</template>
              </span>
              <div class="member-copy">
                <strong>{{ member.nickname || `用户 ${member.userId}` }}</strong>
                <span>{{ member.role === 'leader' ? '队长 · 训练组织者' : '队伍成员' }}</span>
              </div>
              <div class="member-roles">
                <span v-for="role in memberRoles(member)" :key="role">{{ role }}</span>
                <small v-if="memberRoles(member).length === 0">职责待分配</small>
              </div>
            </article>
          </div>
        </section>

        <section class="formation-panel recruitment-panel">
          <div class="panel-heading">
            <div><span class="panel-kicker">OPEN POSITIONS</span><h2>招募与申请</h2></div>
          </div>
          <div v-if="openRecruitments.length" class="recruitment-list">
            <article v-for="item in openRecruitments" :key="item.id">
              <span class="recruitment-mark"><UserPlus :size="17" /></span>
              <div>
                <strong>{{ recruitmentRolesText(item) }}</strong>
                <p>{{ item.description || '等待合适的队友加入，一起完成本次实训。' }}</p>
              </div>
              <span class="open-pill">招募中</span>
            </article>
          </div>
          <div v-else class="compact-empty">
            <CheckCircle2 :size="24" />
            <strong>当前没有开放招募</strong>
            <p>{{ remainingSlots ? '如仍需队友，可在当前组建工作台发布招募信息。' : '成员名额已经用完，请继续完成职责配置。' }}</p>
          </div>
          <div v-if="team.canManage && pendingApplications.length" class="application-preview">
            <span>待处理申请</span>
            <div v-for="application in pendingApplications" :key="application.id">
              <strong>{{ application.nickname || `用户 ${application.applicantId}` }}</strong>
              <small>{{ recruitmentRolesText(application) }}</small>
            </div>
          </div>
        </section>
      </div>
    </div>

    <div v-else v-loading="loading" class="practice-workspace">
      <nav class="practice-tabs" role="tablist" aria-label="队伍练习内容导航">
        <button
          v-for="tab in practiceTabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activePanel === tab.key"
          :class="{ active: activePanel === tab.key }"
          @click="activePanel = tab.key"
        >
          <component :is="tab.icon" :size="16" />
          <span>{{ tab.label }}</span>
          <small>{{ tab.summary }}</small>
        </button>
      </nav>

      <div class="practice-panel-stage" role="tabpanel">
      <section v-if="activePanel === 'problem'" class="practice-column problem-column">
        <div class="column-heading">
          <span class="column-icon blue"><BookOpen :size="17" /></span>
          <div><span>题目区</span><h2>原始赛题</h2></div>
          <router-link v-if="problem" :to="`/problem/${problem.id}`" title="打开完整题目"><ArrowUpRight :size="16" /></router-link>
        </div>
        <div v-if="problem" class="problem-context-bar">
          <span>题号 {{ problem.code }}</span>
          <strong>{{ problem.title }}</strong>
          <small>{{ difficultyLabel(problem.difficulty) }} · {{ formatDuration(problem.durationMinutes) }}</small>
        </div>
        <article v-if="problem?.contentMarkdown" ref="problemBody" class="markdown-body practice-problem-body" v-html="renderedProblem" />
        <el-empty v-else-if="!loading" description="暂无题面内容" :image-size="52" />
        <div v-if="problem?.attachments?.length" class="problem-attachments">
          <span>题目附件</span>
          <a v-for="attachment in problem.attachments" :key="attachment.id" :href="attachment.downloadUrl" target="_blank" rel="noopener noreferrer">
            <Paperclip :size="13" />{{ attachment.fileName }}
          </a>
        </div>
      </section>

      <section v-else-if="activePanel === 'submission'" class="practice-column submission-column">
        <div class="column-heading">
          <span class="column-icon violet"><UploadCloud :size="17" /></span>
          <div><span>提交区</span><h2>{{ isInProgress ? '赛题提交' : '成果版本' }}</h2></div>
          <button type="button" title="刷新提交" @click="loadSubmissions"><RefreshCw :size="15" /></button>
        </div>

        <div v-if="isInProgress" class="submission-callout">
          <template v-if="currentMemberCanSubmit">
            <div class="submission-step"><span>下一版本</span><strong>V{{ nextVersion }}</strong></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="application/pdf,.pdf" :on-change="handlePdfChange">
              <el-button type="primary" plain>选择 PDF</el-button>
            </el-upload>
            <div v-if="selectedPdf" class="selected-file">
              <span>PDF</span>
              <div><strong>{{ selectedPdf.name }}</strong><small>{{ formatFileSize(selectedPdf.size) }}</small></div>
              <button type="button" :disabled="submitting" @click="clearPdf">移除</button>
            </div>
            <el-progress v-if="submitting || uploadProgress" :percentage="uploadProgress" :stroke-width="6" />
            <el-button type="primary" class="submit-pdf-button" :disabled="!selectedPdf" :loading="submitting" @click="submitPdf">提交第 {{ nextVersion }} 版</el-button>
            <p>{{ uploadStage || '仅支持 20MB 以内 PDF；上传中断后可选择同一文件续传。' }}</p>
          </template>
          <el-alert v-else title="队长尚未授予你作品提交权限" type="warning" :closable="false" show-icon />
        </div>

        <div v-if="featuredSubmission" class="featured-submission" :class="{ final: featuredSubmission.finalVersion }">
          <div>
            <span>{{ featuredSubmission.finalVersion ? '最终版本' : '最新版本' }}</span>
            <strong>V{{ featuredSubmission.version }}</strong>
          </div>
          <div class="featured-score"><strong>{{ featuredSubmission.review?.score ?? '—' }}</strong><span>/ 100</span></div>
          <span class="review-pill" :class="`review-${reviewDisplayStatus(featuredSubmission).toLowerCase()}`">{{ reviewStatusLabel(reviewDisplayStatus(featuredSubmission)) }}</span>
        </div>

        <div class="submission-history">
          <div class="subsection-heading"><strong>版本记录</strong><span>{{ submissionRows.length }} 次提交</span></div>
          <article v-for="row in submissionRows" :key="row.id" :class="{ featured: row.id === featuredSubmission?.id }">
            <span class="version-badge">V{{ row.version }}</span>
            <div class="submission-file">
              <strong :title="row.originalFilename">{{ row.originalFilename }}</strong>
              <span>{{ formatDate(row.createTime) }} · {{ formatFileSize(row.fileSize) }}</span>
            </div>
            <div class="submission-actions">
              <span v-if="row.finalVersion" class="final-tag">最终</span>
              <strong v-if="row.review?.score != null">{{ formatScore(row.review.score) }}</strong>
              <a v-if="row.downloadUrl" :href="row.downloadUrl" target="_blank" rel="noopener noreferrer" title="下载"><Download :size="14" /></a>
            </div>
          </article>
          <div v-if="!submissionRows.length && !loading" class="compact-empty submission-empty">
            <FileText :size="24" /><strong>还没有提交版本</strong><p>{{ isInProgress ? '选择 PDF 并提交后，版本与评审状态会显示在这里。' : '本次练习没有可回看的作品版本。' }}</p>
          </div>
        </div>
      </section>

      <section v-else class="practice-column ranking-column">
        <div class="column-heading">
          <span class="column-icon amber"><Trophy :size="17" /></span>
          <div><span>排行区</span><h2>同题排名</h2></div>
          <router-link :to="{ name: 'Ranking', query: { problemId: String(team.problemId) } }" title="打开完整榜单"><ArrowUpRight :size="16" /></router-link>
        </div>

        <div v-if="currentRanking" class="current-rank-card">
          <span>当前队伍名次</span>
          <div><strong>#{{ currentRanking.rank }}</strong><p>{{ formatScore(currentRanking.score) }}<small> 分</small></p></div>
          <em>共 {{ rankingItems.length }} 支队伍上榜</em>
        </div>
        <div v-else class="ranking-pending">
          <Clock3 :size="22" />
          <strong>{{ isInProgress ? '当前队伍尚未上榜' : '暂无有效排名' }}</strong>
          <p>最终版本完成 AI 评审后，会进入当前题目的统一榜单。</p>
        </div>

        <div class="ranking-list">
          <div class="subsection-heading"><strong>同题榜单</strong><span>{{ rankingItems.length }} 支队伍</span></div>
          <article v-for="item in visibleRankings" :key="item.teamId" :class="{ current: String(item.teamId) === String(team.id) }">
            <span class="rank-index" :class="`top-${item.rank}`">{{ item.rank }}</span>
            <span class="rank-avatar">{{ initial(item.teamName) }}</span>
            <div><strong>{{ item.teamName }}</strong><span>{{ workflowLabel(item.workflowVersion) }}</span></div>
            <strong class="rank-score">{{ formatScore(item.score) }}</strong>
          </article>
          <div v-if="!rankingItems.length && !loading" class="compact-empty">
            <Medal :size="24" /><strong>等待首个上榜成果</strong><p>这里将展示同一道题、同一评审口径下的有效成绩。</p>
          </div>
        </div>
      </section>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowUpRight, BookOpen, CheckCircle2, Circle, Clock3, Code2, Download, FileText, Inbox, Medal, Paperclip, PenLine, RefreshCw, ShieldCheck, Trophy, UploadCloud, UserPlus, UsersRound } from '@lucide/vue'
import 'github-markdown-css/github-markdown.css'
import { getTeamApplications, startTeamPractice } from '@/api/team'
import { getPublicProblemDetail } from '@/api/problem'
import { getTeamSubmissionHistory } from '@/api/submission'
import { getTeamReviews } from '@/api/review'
import { getRanking } from '@/api/ranking'
import { renderSafeMarkdown } from '@/utils/markdown'
import { uploadPdfResumably } from '@/utils/resumablePdfUpload'
import { useUserStore } from '@/store/user'

const props = defineProps({ team: { type: Object, required: true } })
const emit = defineEmits(['transitioned'])
const userStore = useUserStore()
const loading = ref(false)
const loadError = ref('')
const problem = ref(null)
const problemBody = ref(null)
const submissions = ref([])
const reviews = ref([])
const rankingOverview = ref(null)
const pendingApplications = ref([])
const applicationTotal = ref(0)
const selectedPdf = ref(null)
const submitting = ref(false)
const uploadProgress = ref(0)
const uploadStage = ref('')
const startingPractice = ref(false)
const activePanel = ref('problem')
const now = ref(Date.now())
let loadSequence = 0
let clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)

const isPreparing = computed(() => props.team.practiceStatus === 'PREPARING')
const isInProgress = computed(() => props.team.practiceStatus === 'IN_PROGRESS')
const statusLabel = computed(() => ({ PREPARING: '正在组建', IN_PROGRESS: '练习中', ENDED: '已结束' })[props.team.practiceStatus] || props.team.practiceStatus)
const workspaceDescription = computed(() => isPreparing.value
  ? '围绕成员、职责和招募完成开赛前准备。'
  : isInProgress.value ? '围绕当前赛题完成阅读、版本提交和同题对照。' : '回看原始题目、最终成果与同题排名。')
const openRecruitments = computed(() => (props.team.recruitments || []).filter(item => item.status === 'OPEN'))
const remainingSlots = computed(() => Math.max(0, Number(props.team.maxMembers || 3) - Number(props.team.members?.length || 0)))
const roleDefinitions = computed(() => [
  { key: 'modeler', label: '建模', description: '问题分析与模型构建', icon: ShieldCheck, members: roleMembers('modeler') },
  { key: 'programmer', label: '编程', description: '算法实现与数据验证', icon: Code2, members: roleMembers('programmer') },
  { key: 'writer', label: '论文', description: '论证组织与成果表达', icon: PenLine, members: roleMembers('writer') },
])
const coveredRoleCount = computed(() => roleDefinitions.value.filter(role => role.members.length).length)
const allRolesCovered = computed(() => coveredRoleCount.value === 3)
const readinessPercent = computed(() => Math.round((coveredRoleCount.value / 3) * 100))
const renderedProblem = computed(() => prepareProblemMarkdown(problem.value?.contentMarkdown || ''))
const currentMember = computed(() => (props.team.members || []).find(member => String(member.userId) === String(userStore.userId)))
const currentMemberCanSubmit = computed(() => Boolean(props.team.canManage || currentMember.value?.role === 'leader' || currentMember.value?.canSubmit))
const nextVersion = computed(() => Math.max(0, ...submissions.value.map(item => Number(item.version || 0))) + 1)
const reviewBySubmissionId = computed(() => {
  const result = new Map()
  reviews.value.forEach(item => { if (!result.has(String(item.submissionId))) result.set(String(item.submissionId), item) })
  return result
})
const submissionRows = computed(() => submissions.value.map(item => ({ ...item, review: reviewBySubmissionId.value.get(String(item.id)) })))
const featuredSubmission = computed(() => {
  if (!submissionRows.value.length) return null
  return props.team.practiceStatus === 'ENDED'
    ? submissionRows.value.find(item => item.finalVersion) || submissionRows.value[0]
    : submissionRows.value[0]
})
const rankingItems = computed(() => rankingOverview.value?.items || [])
const currentRanking = computed(() => rankingItems.value.find(item => String(item.teamId) === String(props.team.id)) || null)
const visibleRankings = computed(() => {
  const first = rankingItems.value.slice(0, 6)
  if (!currentRanking.value || first.some(item => String(item.teamId) === String(props.team.id))) return first
  return [...first.slice(0, 5), currentRanking.value]
})
const practiceTabs = computed(() => [
  { key: 'problem', label: '题目', icon: BookOpen, summary: problem.value?.code ? `题号 ${problem.value.code}` : '原始赛题' },
  { key: 'submission', label: '提交', icon: UploadCloud, summary: submissionRows.value.length ? `${submissionRows.value.length} 个版本` : '暂无版本' },
  { key: 'ranking', label: '排行', icon: Trophy, summary: currentRanking.value ? `当前第 ${currentRanking.value.rank} 名` : `${rankingItems.value.length} 支上榜` },
])
const remainingTimeText = computed(() => {
  const remaining = Math.max(0, Math.floor((new Date(props.team.deadlineAt || 0).getTime() - now.value) / 1000))
  const hours = Math.floor(remaining / 3600)
  const minutes = Math.floor((remaining % 3600) / 60)
  const seconds = remaining % 60
  return `${String(hours).padStart(2, '0')} : ${String(minutes).padStart(2, '0')} : ${String(seconds).padStart(2, '0')}`
})

async function loadWorkspaceData() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  resetRelatedData()
  try {
    problem.value = (await getPublicProblemDetail(props.team.problemId)).data
    if (isPreparing.value) {
      if (props.team.canManage) {
        const page = (await getTeamApplications(props.team.id, { page: 1, pageSize: 3, status: 'pending' })).data || {}
        if (sequence !== loadSequence) return
        pendingApplications.value = page.rows || []
        applicationTotal.value = Number(page.total || 0)
      }
      return
    }
    const [submissionResult, reviewResult, rankingResult] = await Promise.allSettled([
      getTeamSubmissionHistory(props.team.id),
      getTeamReviews(props.team.id),
      getRanking(props.team.problemId),
    ])
    if (sequence !== loadSequence) return
    submissions.value = submissionResult.status === 'fulfilled' ? submissionResult.value.data || [] : []
    reviews.value = reviewResult.status === 'fulfilled' ? reviewResult.value.data || [] : []
    rankingOverview.value = rankingResult.status === 'fulfilled' ? rankingResult.value.data : null
    const failed = [submissionResult, reviewResult, rankingResult].filter(result => result.status === 'rejected')
    if (failed.length) loadError.value = '部分工作区数据加载失败，可重新加载后继续。'
  } catch (error) {
    if (sequence === loadSequence) loadError.value = error.message || '队伍工作台加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function resetRelatedData() {
  problem.value = null
  submissions.value = []
  reviews.value = []
  rankingOverview.value = null
  pendingApplications.value = []
  applicationTotal.value = 0
  clearPdf()
}

function prepareProblemMarkdown(value) {
  const container = document.createElement('div')
  container.innerHTML = renderSafeMarkdown(value)
  container.querySelectorAll('img').forEach(image => {
    image.setAttribute('referrerpolicy', 'no-referrer')
    image.setAttribute('loading', 'lazy')
    image.setAttribute('decoding', 'async')
  })
  return container.innerHTML
}

async function configureProblemImages() {
  await nextTick()
  problemBody.value?.querySelectorAll('img').forEach(image => {
    const hideBrokenImage = () => image.classList.add('is-unavailable')
    image.addEventListener('error', hideBrokenImage, { once: true })
    if (image.complete && image.naturalWidth === 0) hideBrokenImage()
  })
}

async function loadSubmissions() {
  try {
    const [submissionResult, reviewResult] = await Promise.all([getTeamSubmissionHistory(props.team.id), getTeamReviews(props.team.id)])
    submissions.value = submissionResult.data || []
    reviews.value = reviewResult.data || []
  } catch (error) {
    ElMessage.error(error.message || '提交记录加载失败')
  }
}

async function startPractice() {
  try {
    await ElMessageBox.confirm('开始后将立即按题目时长倒计时，成员和职责不可再修改。确定开始吗？', '开始限时练习', { type: 'warning' })
    startingPractice.value = true
    const result = await startTeamPractice(props.team.id)
    ElMessage.success('限时练习已开始')
    emit('transitioned', result.data)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message || '开始练习失败')
  } finally {
    startingPractice.value = false
  }
}

const MAX_PDF_SIZE = 20 * 1024 * 1024
function handlePdfChange(file) {
  const rawFile = file.raw
  const isPdf = rawFile?.name?.toLowerCase().endsWith('.pdf') && (!rawFile.type || rawFile.type === 'application/pdf')
  if (!isPdf) {
    clearPdf()
    ElMessage.warning('请选择 PDF 文件')
    return
  }
  if (rawFile.size > MAX_PDF_SIZE) {
    clearPdf()
    ElMessage.warning('PDF 文件大小不能超过 20MB')
    return
  }
  selectedPdf.value = rawFile
  uploadProgress.value = 0
  uploadStage.value = ''
}

function clearPdf() {
  selectedPdf.value = null
  uploadProgress.value = 0
  uploadStage.value = ''
}

async function submitPdf() {
  if (!selectedPdf.value) return
  submitting.value = true
  uploadProgress.value = 0
  try {
    await uploadPdfResumably({
      teamId: props.team.id,
      file: selectedPdf.value,
      onProgress: value => { uploadProgress.value = value },
      onStage: value => { uploadStage.value = value },
    })
    selectedPdf.value = null
    await loadSubmissions()
    ElMessage.success('PDF 提交成功，AI 评审已进入处理流程')
    uploadProgress.value = 0
    uploadStage.value = ''
  } catch (error) {
    uploadStage.value = '上传已中断，重新选择同一文件可继续上传'
    ElMessage.error(error.message || 'PDF 提交失败')
  } finally {
    submitting.value = false
  }
}

function roleMembers(key) {
  return (props.team.members || []).filter(member => member[key]).map(member => member.nickname || `用户 ${member.userId}`)
}
function memberRoles(member) { return [member.modeler && '建模', member.programmer && '编程', member.writer && '论文'].filter(Boolean) }
function recruitmentRolesText(item) { return [item.needModeler && '建模', item.needProgrammer && '编程', item.needWriter && '论文'].filter(Boolean).join('、') || '综合职责' }
function initial(value) { return String(value || '队').trim().slice(0, 1).toUpperCase() }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '时间待同步' }
function formatDuration(value) { return value ? (value % 60 === 0 ? `${value / 60} 小时` : `${value} 分钟`) : '时长待同步' }
function difficultyLabel(value) { return ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '难度待同步' }
function formatFileSize(value) { return value == null ? '—' : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function formatScore(value) { const score = Number(value); return Number.isFinite(score) ? score.toFixed(1) : '—' }
function reviewDisplayStatus(row) { return row?.review?.status || row?.reviewDispatchStatus || 'NOT_REQUESTED' }
function reviewStatusLabel(value) { return ({ WAITING_DISPATCH: '等待派发', DISPATCHED: '已派发', DISPATCH_BLOCKED: '派发受阻', NOT_REQUESTED: '等待评审', WAITING: '等待评审', LEASED: '准备评审', RUNNING: '评审中', COMPLETED: '已完成', FAILED: '评审失败', UNKNOWN: '结果待确认' })[value] || '等待评审' }
function workflowLabel(value) { return value === 'BASIC_REVIEW_V1' ? '基础评审 V1' : String(value || '评审版本待同步').replaceAll('_', ' ') }
watch(() => props.team.id, () => {
  activePanel.value = 'problem'
  loadWorkspaceData()
}, { immediate: true })
watch(renderedProblem, configureProblemImages, { flush: 'post' })
watch(activePanel, panel => { if (panel === 'problem') configureProblemImages() })
onBeforeUnmount(() => { window.clearInterval(clockTimer); clockTimer = null })
</script>

<style scoped>
.selected-team-workspace { min-width: 0; }
.workspace-header { display: flex; min-height: 82px; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 2px 2px 18px; border-bottom: 1px solid var(--lm-border); }
.workspace-kicker,.panel-kicker,.column-heading > div > span { color: var(--lm-text-muted); font-size: 9px; font-weight: 800; letter-spacing: .12em; }
.workspace-title-row { display: flex; align-items: center; gap: 10px; margin-top: 5px; }
.workspace-title-row h1 { margin: 0; color: var(--lm-text-primary); font-size: 23px; letter-spacing: -.025em; }
.workspace-status { padding: 4px 8px; border-radius: 999px; background: #eff6ff; color: #2563eb; font-size: 10px; font-weight: 700; }
.workspace-status.status-in_progress { background: #fff7ed; color: #b45309; }
.workspace-status.status-ended { background: #f0fdf4; color: #15803d; }
.workspace-header p { margin: 7px 0 0; color: var(--lm-text-secondary); font-size: 12px; }
.workspace-clock { display: flex; min-width: 158px; flex-direction: column; align-items: flex-end; padding-top: 14px; }
.workspace-clock span { color: var(--lm-text-muted); font-size: 10px; }
.workspace-clock strong { margin-top: 3px; color: var(--lm-text-primary); font-family: var(--lm-code-font-family); font-size: 16px; }
.formation-workspace,.practice-workspace { margin-top: 18px; }
.formation-hero { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 22px; border: 1px solid #fed7aa; border-radius: 12px; background: linear-gradient(120deg,#fffaf2,#fff); }
.formation-hero.ready { border-color: #bbf7d0; background: linear-gradient(120deg,#f4fff7,#fff); }
.formation-progress { display: flex; min-width: 0; align-items: center; gap: 17px; }
.progress-ring { display: grid; width: 68px; height: 68px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: conic-gradient(#f59e0b var(--progress),#ffedd5 0); }
.ready .progress-ring { background: conic-gradient(#16a34a var(--progress),#dcfce7 0); }
.progress-ring::before { width: 54px; height: 54px; border-radius: 50%; background: #fff; content: ''; grid-area: 1/1; }
.progress-ring span { position: relative; z-index: 1; color: #b45309; font-family: var(--lm-code-font-family); font-size: 13px; font-weight: 800; grid-area: 1/1; }
.ready .progress-ring span { color: #15803d; }
.formation-progress h2 { margin: 3px 0 0; color: var(--lm-text-primary); font-size: 18px; }
.formation-progress p { margin: 6px 0 0; color: var(--lm-text-secondary); font-size: 11px; line-height: 1.6; }
.formation-actions { display: flex; flex: 0 0 auto; gap: 8px; }
.formation-metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 10px; margin: 12px 0; }
.formation-metrics article { display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid var(--lm-border-light); border-radius: 10px; background: #fff; color: #64748b; }
.formation-metrics article > div { display: flex; min-width: 0; flex-direction: column; }
.formation-metrics strong { color: var(--lm-text-primary); font-family: var(--lm-code-font-family); font-size: 15px; }
.formation-metrics span { color: var(--lm-text-muted); font-size: 9px; }
.formation-grid { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1.08fr); gap: 12px; }
.formation-panel { min-width: 0; padding: 18px; border: 1px solid var(--lm-border); border-radius: 12px; background: #fff; }
.recruitment-panel { grid-column: 1/3; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid var(--lm-border-light); }
.panel-heading h2 { margin: 3px 0 0; color: var(--lm-text-primary); font-size: 15px; }
.panel-heading > span { color: var(--lm-text-muted); font-family: var(--lm-code-font-family); font-size: 11px; }
.panel-heading button { display: inline-flex; align-items: center; gap: 3px; padding: 0; border: 0; background: none; color: var(--lm-primary); font: inherit; font-size: 10px; cursor: pointer; }
.role-board,.formation-member-list,.recruitment-list { display: grid; gap: 0; }
.role-board article { display: grid; grid-template-columns: 34px minmax(0,1fr) minmax(86px,.8fr); align-items: center; gap: 9px; padding: 13px 0; border-bottom: 1px solid var(--lm-border-light); }
.role-board article:last-child,.formation-member-list article:last-child,.recruitment-list article:last-child { border-bottom: 0; }
.role-icon { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 9px; background: #f4f4f5; color: #71717a; }
.role-board article.covered .role-icon { background: #ecfdf5; color: #059669; }
.role-copy,.member-copy,.submission-file,.ranking-list article > div { min-width: 0; }
.role-copy strong,.member-copy strong { display: block; color: var(--lm-text-primary); font-size: 11px; }
.role-copy span,.member-copy span { display: block; margin-top: 2px; color: var(--lm-text-muted); font-size: 9px; }
.role-owner { display: flex; min-width: 0; align-items: center; justify-content: flex-end; gap: 5px; color: #a1a1aa; font-size: 9px; text-align: right; }
.covered .role-owner { color: #059669; }
.role-owner span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.formation-member-list article { display: grid; grid-template-columns: 34px minmax(0,1fr) auto; align-items: center; gap: 9px; padding: 11px 0; border-bottom: 1px solid var(--lm-border-light); }
.member-avatar,.rank-avatar { display: grid; overflow: hidden; width: 32px; height: 32px; place-items: center; border-radius: 9px; background: #eef2ff; color: #4f46e5; font-size: 11px; font-weight: 800; }
.member-avatar img { width: 100%; height: 100%; object-fit: contain; }
.member-roles { display: flex; max-width: 150px; flex-wrap: wrap; justify-content: flex-end; gap: 4px; }
.member-roles span,.final-tag { padding: 3px 6px; border-radius: 999px; background: #eff6ff; color: #2563eb; font-size: 8px; font-weight: 700; }
.member-roles small { color: var(--lm-text-muted); font-size: 9px; }
.recruitment-list { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; margin-top: 12px; }
.recruitment-list article { display: grid; grid-template-columns: 34px minmax(0,1fr) auto; gap: 9px; padding: 12px; border: 1px solid var(--lm-border-light); border-radius: 9px; }
.recruitment-mark { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 8px; background: #fff7ed; color: #c2410c; }
.recruitment-list strong { color: var(--lm-text-primary); font-size: 11px; }
.recruitment-list p { overflow: hidden; margin: 4px 0 0; color: var(--lm-text-muted); font-size: 9px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.open-pill { align-self: flex-start; padding: 3px 6px; border-radius: 999px; background: #ecfdf5; color: #047857; font-size: 8px; font-weight: 700; }
.application-preview { display: grid; grid-template-columns: auto repeat(3,minmax(0,1fr)); gap: 8px; align-items: center; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--lm-border-light); }
.application-preview > span { color: var(--lm-text-muted); font-size: 9px; font-weight: 700; }
.application-preview > div { display: flex; min-width: 0; flex-direction: column; padding: 7px 9px; border-radius: 7px; background: #f8fafc; }
.application-preview strong { overflow: hidden; color: var(--lm-text-primary); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.application-preview small { margin-top: 2px; color: var(--lm-text-muted); font-size: 8px; }
.practice-workspace { overflow: hidden; border: 1px solid var(--lm-border); border-radius: 12px; background: #fff; }
.practice-tabs { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); padding: 0 18px; border-bottom: 1px solid var(--lm-border); background: #fafafa; }
.practice-tabs button { position: relative; display: grid; grid-template-columns: 20px auto; grid-template-rows: auto auto; align-items: center; justify-content: center; column-gap: 7px; min-height: 64px; padding: 10px 18px; border: 0; background: transparent; color: var(--lm-text-muted); font: inherit; cursor: pointer; }
.practice-tabs button::after { position: absolute; right: 14px; bottom: -1px; left: 14px; height: 2px; border-radius: 2px 2px 0 0; background: transparent; content: ''; }
.practice-tabs button:hover { color: var(--lm-text-primary); }
.practice-tabs button.active { color: var(--lm-primary); }
.practice-tabs button.active::after { background: var(--lm-primary); }
.practice-tabs button > svg { grid-row: 1/3; }
.practice-tabs button > span { color: inherit; font-size: 12px; font-weight: 700; text-align: left; }
.practice-tabs button > small { color: var(--lm-text-muted); font-size: 8px; text-align: left; }
.practice-panel-stage { min-height: 520px; }
.practice-column { min-width: 0; background: #fff; }
.column-heading { display: grid; grid-template-columns: 34px minmax(0,1fr) 24px; align-items: center; gap: 9px; padding: 16px 20px; border-bottom: 1px solid var(--lm-border-light); background: #fff; }
.column-icon { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 9px; }
.column-icon.blue { background: #eff6ff; color: #2563eb; }.column-icon.violet { background: #f5f3ff; color: #7c3aed; }.column-icon.amber { background: #fff7ed; color: #d97706; }
.column-heading h2 { margin: 2px 0 0; color: var(--lm-text-primary); font-size: 14px; }
.column-heading > a,.column-heading > button { display: grid; width: 24px; height: 24px; place-items: center; padding: 0; border: 0; border-radius: 6px; background: #f4f4f5; color: var(--lm-text-secondary); cursor: pointer; }
.problem-context-bar { padding: 16px 24px; border-bottom: 1px solid var(--lm-border-light); background: #fafafa; }
.problem-context-bar > span { display: inline-block; padding: 3px 6px; border-radius: 5px; background: #e0edff; color: #1d4ed8; font-size: 8px; font-weight: 800; }
.problem-context-bar strong { display: block; margin-top: 7px; color: var(--lm-text-primary); font-size: 15px; line-height: 1.5; }
.problem-context-bar small { display: block; margin-top: 4px; color: var(--lm-text-muted); font-size: 11px; }
.practice-problem-body { max-width: 900px; margin: 0 auto; padding: 28px 32px 42px; background: #fff; color: var(--lm-text-primary); font-family: var(--lm-font-family); font-size: 14px; line-height: 1.8; }
.practice-problem-body :deep(h1) { font-size: 24px; }.practice-problem-body :deep(h2) { font-size: 19px; }.practice-problem-body :deep(h3) { font-size: 16px; }
.practice-problem-body :deep(img) { max-width: 100%; height: auto; }.practice-problem-body :deep(img.is-unavailable) { display: none; }.practice-problem-body :deep(table) { font-size: 12px; }
.problem-attachments { display: grid; gap: 6px; padding: 13px 14px; border-top: 1px solid var(--lm-border-light); }
.problem-attachments > span { color: var(--lm-text-muted); font-size: 9px; font-weight: 700; }
.problem-attachments a { display: flex; min-width: 0; align-items: center; gap: 5px; overflow: hidden; color: var(--lm-primary); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.submission-callout { max-width: 720px; margin: 24px auto; padding: 20px; border: 1px solid #ddd6fe; border-radius: 10px; background: #faf8ff; }
.submission-step { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; color: var(--lm-text-muted); font-size: 11px; }
.submission-step strong { color: #6d28d9; font-family: var(--lm-code-font-family); font-size: 22px; }
.selected-file { display: grid; grid-template-columns: 32px minmax(0,1fr) auto; align-items: center; gap: 8px; margin-top: 9px; padding: 8px; border-radius: 8px; background: #fff; }
.selected-file > span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 7px; background: #fee2e2; color: #b91c1c; font-size: 8px; font-weight: 800; }
.selected-file div { min-width: 0; }.selected-file strong,.selected-file small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.selected-file strong { font-size: 12px; }.selected-file small { margin-top: 2px; color: var(--lm-text-muted); font-size: 10px; }
.selected-file button { padding: 0; border: 0; background: none; color: var(--lm-text-muted); font-size: 10px; cursor: pointer; }
.submission-callout :deep(.el-progress) { margin-top: 12px; }.submit-pdf-button { width: 100%; margin-top: 12px; }.submission-callout > p { margin: 9px 0 0; color: var(--lm-text-muted); font-size: 11px; line-height: 1.5; }
.featured-submission { display: grid; max-width: 720px; grid-template-columns: minmax(0,1fr) auto; gap: 8px; margin: 24px auto; padding: 18px 20px; border: 1px solid #dbeafe; border-radius: 10px; background: #f8fbff; }
.featured-submission.final { border-color: #bbf7d0; background: #f6fff8; }
.featured-submission > div:first-child span { display: block; color: var(--lm-text-muted); font-size: 10px; }.featured-submission > div:first-child strong { display: block; margin-top: 2px; font-size: 20px; }
.featured-score { display: flex; align-items: baseline; }.featured-score strong { color: #2563eb; font-size: 28px; }.featured-score span { color: var(--lm-text-muted); font-size: 10px; }
.review-pill { grid-column: 1/3; width: fit-content; padding: 4px 8px; border-radius: 999px; background: #f4f4f5; color: #71717a; font-size: 10px; }.review-pill.review-completed { background: #ecfdf5; color: #047857; }.review-pill.review-failed,.review-pill.review-dispatch_blocked { background: #fef2f2; color: #b91c1c; }
.submission-history,.ranking-list { max-width: 900px; margin: 0 auto; padding: 0 24px 28px; }
.subsection-heading { display: flex; align-items: center; justify-content: space-between; padding: 14px 0 10px; }.subsection-heading strong { color: var(--lm-text-primary); font-size: 13px; }.subsection-heading span { color: var(--lm-text-muted); font-size: 10px; }
.submission-history article { display: grid; grid-template-columns: 38px minmax(0,1fr) auto; align-items: center; gap: 11px; min-height: 58px; padding: 9px 8px; border-top: 1px solid var(--lm-border-light); }.submission-history article.featured { background: #fafafa; }
.version-badge { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 8px; background: #f4f4f5; color: var(--lm-text-secondary); font-family: var(--lm-code-font-family); font-size: 10px; font-weight: 800; }
.submission-file strong,.submission-file span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.submission-file strong { color: var(--lm-text-primary); font-size: 12px; }.submission-file span { margin-top: 3px; color: var(--lm-text-muted); font-size: 9px; }
.submission-actions { display: flex; align-items: center; gap: 7px; }.submission-actions > strong { color: #2563eb; font-size: 14px; }.submission-actions a { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 7px; background: #f4f4f5; color: var(--lm-text-secondary); }
.current-rank-card { max-width: 720px; margin: 24px auto; padding: 22px; border-radius: 10px; background: linear-gradient(135deg,#172554,#1d4ed8); color: #fff; }.current-rank-card > span { color: #bfdbfe; font-size: 11px; font-weight: 700; }.current-rank-card > div { display: flex; align-items: baseline; justify-content: space-between; margin-top: 7px; }.current-rank-card > div > strong { font-size: 32px; }.current-rank-card p { margin: 0; font-size: 24px; font-weight: 800; }.current-rank-card p small { font-size: 10px; font-weight: 500; }.current-rank-card em { display: block; margin-top: 9px; color: #bfdbfe; font-size: 10px; font-style: normal; }
.ranking-pending { max-width: 680px; margin: 24px auto; padding: 28px; border: 1px dashed #cbd5e1; border-radius: 10px; background: #fafafa; color: #94a3b8; text-align: center; }.ranking-pending strong { display: block; margin-top: 9px; color: var(--lm-text-primary); font-size: 13px; }.ranking-pending p { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 11px; line-height: 1.5; }
.ranking-list article { display: grid; grid-template-columns: 34px 38px minmax(0,1fr) auto; align-items: center; gap: 10px; min-height: 56px; border-top: 1px solid var(--lm-border-light); }.ranking-list article.current { margin: 0 -7px; padding: 0 7px; border-radius: 7px; background: #eff6ff; }.rank-index { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 7px; background: #f4f4f5; color: #71717a; font-size: 10px; font-weight: 800; }.rank-index.top-1 { background: #fef3c7; color: #a16207; }.rank-index.top-2 { background: #e2e8f0; color: #475569; }.rank-index.top-3 { background: #ffedd5; color: #9a3412; }.rank-avatar { width: 34px; height: 34px; border-radius: 8px; font-size: 11px; }.ranking-list article > div strong,.ranking-list article > div span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.ranking-list article > div strong { color: var(--lm-text-primary); font-size: 12px; }.ranking-list article > div span { margin-top: 2px; color: var(--lm-text-muted); font-size: 9px; }.rank-score { color: #2563eb; font-family: var(--lm-code-font-family); font-size: 14px; }
.compact-empty { display: flex; min-height: 126px; align-items: center; justify-content: center; flex-direction: column; color: #a1a1aa; text-align: center; }.compact-empty strong { margin-top: 7px; color: var(--lm-text-primary); font-size: 10px; }.compact-empty p { max-width: 240px; margin: 5px 0 0; color: var(--lm-text-muted); font-size: 8px; line-height: 1.5; }.submission-empty { min-height: 150px; }
@media (max-width: 1280px) { .formation-metrics { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (max-width: 980px) { .formation-grid { grid-template-columns: 1fr; }.recruitment-panel { grid-column: auto; }.recruitment-list { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .workspace-header,.formation-hero { align-items: stretch; flex-direction: column; }.workspace-clock { align-items: flex-start; padding-top: 0; }.formation-progress { align-items: flex-start; }.formation-actions { flex-direction: column; }.formation-metrics { grid-template-columns: 1fr 1fr; }.application-preview { grid-template-columns: 1fr; }.practice-tabs { padding: 0; }.practice-tabs button { min-height: 54px; padding: 8px; }.practice-tabs button > small { display: none; }.practice-problem-body { padding: 20px 16px 32px; font-size: 13px; }.submission-callout,.featured-submission,.current-rank-card,.ranking-pending { margin: 14px; }.submission-history,.ranking-list { padding-right: 14px; padding-left: 14px; } }
</style>
