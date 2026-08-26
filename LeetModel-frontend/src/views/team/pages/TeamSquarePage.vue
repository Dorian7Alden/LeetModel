<template>
  <div class="team-page square-page">
    <PageHeader title="队伍广场" :description="pageDescription" />

    <el-tabs v-model="mode" class="discovery-tabs" @tab-change="handleModeChange">
      <el-tab-pane label="按职组队" name="roles" />
      <el-tab-pane label="按队组队" name="teams" />
      <el-tab-pane label="按题组队" name="problems" />
    </el-tabs>

    <div class="team-filters">
      <el-input v-model="keyword" :placeholder="keywordPlaceholder" clearable :prefix-icon="Search" @keyup.enter="runSearchNow" />
      <template v-if="mode === 'roles'">
        <el-checkbox v-model="needModeler">建模手</el-checkbox>
        <el-checkbox v-model="needProgrammer">编程手</el-checkbox>
        <el-checkbox v-model="needWriter">论文手</el-checkbox>
      </template>
    </div>

    <template v-if="mode === 'problems'">
      <div class="problem-recommend-heading">
        <div><strong>推荐练习题目</strong><span>选择一道题目，筛选所有准备练习该题的队伍</span></div>
        <el-button text type="primary" :loading="problemLoading" @click="refreshProblemRecommendations">换一批</el-button>
      </div>
      <div v-loading="problemLoading" class="problem-results">
        <button v-for="problem in problems" :key="problem.id" type="button" class="problem-result" :class="{ selected: String(problem.id) === String(problemId) }" @click="selectProblem(problem)">
          <span>题号 {{ problem.code || problem.id }}</span>
          <strong>{{ problem.title }}</strong>
          <small>{{ difficultyLabel(problem.difficulty) }} · {{ formatDuration(problem.durationMinutes) }}</small>
        </button>
      </div>
      <el-empty v-if="!problemLoading && !problemLoadError && problems.length === 0" description="暂无符合条件的练习题目" />
      <div v-if="selectedProblem" class="selected-problem-bar">
        <span>正在查看题目 {{ selectedProblem.id }}</span><strong>{{ selectedProblem.title }}</strong>
      </div>
      <el-result v-if="problemLoadError" icon="error" title="练习题目加载失败" :sub-title="problemLoadError">
        <template #extra><el-button type="primary" @click="loadProblems">重新加载</el-button></template>
      </el-result>
    </template>

    <el-result v-if="teamLoadError" icon="error" title="队伍广场加载失败" :sub-title="teamLoadError">
      <template #extra><el-button type="primary" @click="loadTeams">重新加载</el-button></template>
    </el-result>
    <div v-if="mode === 'roles' && !teamLoadError" v-loading="loading" class="recruitment-workbench">
      <aside class="recruitment-list" aria-label="开放职位列表">
        <button
          v-for="result in recruitmentResults"
          :key="result.key"
          type="button"
          class="recruitment-list-item"
          :class="{ active: selectedRecruitment?.key === result.key }"
          @click="selectedRecruitmentKey = result.key"
        >
          <span class="list-item-top"><strong>{{ recruitmentText(result.recruitment) }}</strong><small>{{ formatDate(result.recruitment.createTime) }}</small></span>
          <span class="list-item-team">{{ result.team.name }}</span>
          <span class="list-item-problem">{{ result.team.problemTitle || `题号 ${result.team.problemCode || result.team.problemId}` }}</span>
          <span v-if="result.recruitment.description" class="list-item-description">{{ result.recruitment.description }}</span>
        </button>
      </aside>
      <section v-if="selectedRecruitment" class="recruitment-detail">
        <div class="detail-heading">
          <div><span class="detail-eyebrow">正在招募</span><h2>{{ recruitmentText(selectedRecruitment.recruitment) }}</h2></div>
          <div class="detail-actions">
            <el-button @click="goTeamDetail(selectedRecruitment.team.id)">查看队伍详情</el-button>
            <el-button type="primary" :disabled="!selectedRecruitment.team.canApply" @click="applyForRecruitment(selectedRecruitment)">{{ applicationActionText(selectedRecruitment.team) }}</el-button>
          </div>
        </div>
        <div class="detail-meta">
          <span>发布于 {{ formatDate(selectedRecruitment.recruitment.createTime) }}</span>
          <span>剩余 {{ selectedRecruitment.team.remainingSlots }} 个名额</span>
          <span>队伍成立于 {{ formatDate(selectedRecruitment.team.createTime) }}</span>
        </div>
        <div class="detail-section"><small>招募说明</small><div v-if="selectedRecruitment.recruitment.description" class="recruitment-markdown" v-html="renderSafeMarkdown(selectedRecruitment.recruitment.description)" /><p v-else>队长暂未填写招募说明。</p></div>
        <div class="detail-section"><small>所属队伍</small><h3>{{ selectedRecruitment.team.name }}</h3><p>{{ selectedRecruitment.team.description || '队伍暂未填写简介。' }}</p></div>
        <div class="detail-section"><small>练习题目</small><h3>{{ selectedRecruitment.team.problemTitle || `题号 ${selectedRecruitment.team.problemCode || selectedRecruitment.team.problemId}` }}</h3><p>题号 {{ selectedRecruitment.team.problemCode || selectedRecruitment.team.problemId }}</p></div>
        <div class="detail-section">
          <small>当前成员</small>
          <div class="detail-members">
            <button v-for="member in selectedRecruitment.team.members" :key="member.userId" type="button" @click="showUserCard(member)">
              <img v-if="member.avatarUrl" :src="member.avatarUrl" /><span v-else>{{ (member.nickname || String(member.userId)).charAt(0) }}</span>{{ member.nickname || `用户 ${member.userId}` }}
            </button>
          </div>
        </div>
      </section>
    </div>
    <div v-if="!teamLoadError && mode !== 'roles' && (mode !== 'problems' || selectedProblem)" v-loading="loading" class="teams-grid">
      <TeamCard v-for="team in teams" :key="team.id" :team="team" :display-mode="mode" detail-route-name="TeamSquareDetail" @show-user="showUserCard" />
    </div>
    <el-empty v-if="(mode !== 'problems' || selectedProblem) && !loading && !teamLoadError && teams.length === 0" :description="emptyDescription" />
    <el-pagination
      v-if="!teamLoadError && (mode !== 'problems' || selectedProblem) && total > pageSize"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="handlePageChange"
    />

    <UserMiniCardDialog v-model="showMiniCard" :member="selectedMember" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getPublicPreparingProblemIds, getPublicTeams } from '@/api/team'
import { getPublicProblemDetail, getPublicProblemList } from '@/api/problem'
import PageHeader from '@/components/common/PageHeader.vue'
import TeamCard from '../components/TeamCard.vue'
import UserMiniCardDialog from '../components/UserMiniCardDialog.vue'
import { renderSafeMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const validModes = new Set(['teams', 'problems', 'roles'])
const mode = ref(validModes.has(route.query.mode) ? route.query.mode : 'roles')
const keyword = ref(String(route.query.keyword || ''))
const needModeler = ref(route.query.modeler === '1')
const needProgrammer = ref(route.query.programmer === '1')
const needWriter = ref(route.query.writer === '1')
const page = ref(Math.max(1, Number(route.query.page) || 1))
const pageSize = 9
const problemId = ref(route.query.problemId ? String(route.query.problemId) : '')
const selectedProblem = ref(null)
const teams = ref([])
const total = ref(0)
const loading = ref(false)
const teamLoadError = ref('')
const problems = ref([])
const problemLoading = ref(false)
const problemLoadError = ref('')
const selectedRecruitmentKey = ref('')
const selectedMember = ref(null)
const showMiniCard = ref(false)
let debounceTimer
let requestSequence = 0

const pageDescription = computed(() => ({
  teams: '浏览尚有成员空位的队伍，重点了解队伍方向与成员构成',
  problems: '先选择练习题目，再查看该题目下的缺人队伍与开放职位',
  roles: '按建模、编程或论文职位，寻找正在招募合适角色的队伍',
})[mode.value])
const keywordPlaceholder = computed(() => mode.value === 'problems' ? '输入题目标题或题号' : mode.value === 'roles' ? '搜索职位所属队伍或简介' : '搜索队伍名称或简介')
const emptyDescription = computed(() => mode.value === 'roles' ? '暂无符合条件的招募职位' : mode.value === 'problems' ? '该题目下暂无缺人队伍' : '暂无符合条件的缺人队伍')
const recruitmentResults = computed(() => teams.value.flatMap(team => (team.recruitments || [])
  .filter(item => item.status === 'OPEN')
  .map(recruitment => ({ key: `${team.id}-${recruitment.id}`, team, recruitment }))))
const selectedRecruitment = computed(() => recruitmentResults.value.find(item => item.key === selectedRecruitmentKey.value) || recruitmentResults.value[0] || null)

function routeQuery() {
  const query = { mode: mode.value }
  if (keyword.value.trim()) query.keyword = keyword.value.trim()
  if (mode.value === 'roles' && needModeler.value) query.modeler = '1'
  if (mode.value === 'roles' && needProgrammer.value) query.programmer = '1'
  if (mode.value === 'roles' && needWriter.value) query.writer = '1'
  if (page.value > 1) query.page = String(page.value)
  if (mode.value === 'problems' && problemId.value) query.problemId = String(problemId.value)
  return query
}

async function syncRoute() {
  const next = routeQuery()
  if (JSON.stringify(next) !== JSON.stringify(route.query)) await router.replace({ query: next })
}

async function loadTeams() {
  if (mode.value === 'problems' && !problemId.value) { teams.value = []; total.value = 0; return }
  const sequence = ++requestSequence
  loading.value = true
  teamLoadError.value = ''
  try {
    const params = { page: page.value, pageSize, availableOnly: true }
    if (mode.value !== 'problems' && keyword.value.trim()) params.keyword = keyword.value.trim()
    if (mode.value === 'roles') Object.assign(params, { recruitingOnly: true, excludeJoined: true, needModeler: needModeler.value, needProgrammer: needProgrammer.value, needWriter: needWriter.value })
    if (mode.value === 'problems') params.problemId = problemId.value
    const res = await getPublicTeams(params)
    if (sequence !== requestSequence) return
    teams.value = res.data.rows || []
    total.value = res.data.total || 0
    if (mode.value === 'roles' && !recruitmentResults.value.some(item => item.key === selectedRecruitmentKey.value)) selectedRecruitmentKey.value = recruitmentResults.value[0]?.key || ''
  } catch (error) {
    if (sequence === requestSequence) {
      teamLoadError.value = error.message || '请检查网络连接后重试'
      ElMessage.error(error.message || '队伍广场加载失败')
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function loadProblems() {
  problemLoading.value = true
  problemLoadError.value = ''
  try {
    const eligibleProblemIds = new Set((await getPublicPreparingProblemIds()).data.map(String))
    const numericId = /^\d+$/.test(keyword.value.trim()) ? keyword.value.trim() : null
    const res = numericId
      ? await getPublicProblemList({ page: 1, pageSize: 20 })
      : await getPublicProblemList({ page: 1, pageSize: 20, keyword: keyword.value.trim() || undefined })
    const candidates = (res.data.rows || []).filter(item => eligibleProblemIds.has(String(item.id)) && (!numericId || String(item.id) === numericId))
    problems.value = numericId ? candidates : shuffle(candidates).slice(0, 4)
    if (problemId.value && !selectedProblem.value) {
      selectedProblem.value = problems.value.find(item => String(item.id) === String(problemId.value)) || (await getPublicProblemDetail(problemId.value)).data
    }
  } catch (error) {
    problemLoadError.value = error.message || '请检查网络连接后重试'
    ElMessage.error(error.message || '练习题目加载失败')
  }
  finally { problemLoading.value = false }
}

async function refreshProblemRecommendations() { await loadProblems() }

async function applyFilters() {
  page.value = 1
  if (mode.value === 'problems') {
    problemId.value = ''
    selectedProblem.value = null
    teams.value = []
    total.value = 0
    await loadProblems()
  } else await loadTeams()
  await syncRoute()
}

function scheduleFilters() { window.clearTimeout(debounceTimer); debounceTimer = window.setTimeout(applyFilters, 280) }
function runSearchNow() { window.clearTimeout(debounceTimer); applyFilters() }
async function handleModeChange() { keyword.value = ''; page.value = 1; problemId.value = ''; selectedProblem.value = null; await syncRoute(); if (mode.value === 'problems') await loadProblems(); else await loadTeams() }
async function selectProblem(problem) { selectedProblem.value = problem; problemId.value = String(problem.id); page.value = 1; await syncRoute(); await loadTeams() }
async function handlePageChange(value) { page.value = value; await syncRoute(); await loadTeams() }
function goTeamDetail(id) { router.push({ name: 'TeamSquareDetail', params: { id } }) }
function applyForRecruitment(result) { router.push({ name: 'TeamSquareDetail', params: { id: result.team.id }, query: { apply: '1', recruitmentId: String(result.recruitment.id) } }) }
function applicationActionText(team) {
  if (team.canApply) return '申请该职位'
  if (team.currentUserRelation === 'pending') return '已申请'
  if (team.currentUserRelation === 'member' || team.currentUserRelation === 'leader') return '已在队伍中'
  return '暂不可申请'
}
function showUserCard(member) { selectedMember.value = member; showMiniCard.value = true }
function recruitmentText(item) { return [item.needModeler && '建模手', item.needProgrammer && '编程手', item.needWriter && '论文手'].filter(Boolean).join('、') }
function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '未知时间' }
function shuffle(items) {
  const result = [...items]
  for (let index = result.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1))
    ;[result[index], result[randomIndex]] = [result[randomIndex], result[index]]
  }
  return result
}
function difficultyLabel(value) { return ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '难度未知' }
function formatDuration(minutes) { if (!minutes) return '时长未设置'; return minutes % 60 === 0 ? `${minutes / 60} 小时` : `${minutes} 分钟` }

watch([keyword, needModeler, needProgrammer, needWriter], scheduleFilters)
onMounted(async () => { if (mode.value === 'problems') { await loadProblems(); if (problemId.value) await loadTeams() } else await loadTeams() })
onBeforeUnmount(() => window.clearTimeout(debounceTimer))
</script>

<style scoped>
@import '../style.css';
.square-page { min-height: 650px; }
.discovery-tabs { margin-top: 8px; }
.discovery-tabs :deep(.el-tabs__item) { height: 52px; padding: 0 28px; font-size: 16px; font-weight: 650; }
.problem-recommend-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:18px 0 10px; }
.problem-recommend-heading div { display:flex; flex-direction:column; gap:4px; }
.problem-recommend-heading span { color:var(--lm-text-muted); font-size:12px; }
.problem-results { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 10px; margin-bottom: 18px; }
.problem-result { display: flex; min-width:0; min-height: 104px; align-items: flex-start; flex-direction: column; gap: 6px; padding: 14px; border: 1px solid var(--lm-border); border-radius: 12px; background: #fff; color: var(--lm-text-secondary); cursor: pointer; text-align: left; transition: border-color .2s,box-shadow .2s,transform .2s; }
.problem-result:hover,.problem-result:focus-visible,.problem-result.selected { outline: none; border-color: #3b82f6; box-shadow: 0 8px 24px rgba(37,99,235,.12); transform: translateY(-2px); }
.problem-result span,.problem-result small { color: var(--lm-text-muted); font-size: 12px; }
.problem-result strong { color: var(--lm-text-primary); font-size: 15px; line-height: 1.45; }
.selected-problem-bar { display: flex; align-items: center; gap: 12px; margin: 20px 0 4px; padding: 14px 18px; border-radius: 12px; background: #eff6ff; color: #31558a; }
.recruitment-workbench { display:grid; grid-template-columns:340px minmax(0,1fr); min-height:560px; gap:18px; align-items:start; }
.recruitment-list { display:flex; max-height:680px; overflow:auto; flex-direction:column; gap:10px; }
.recruitment-list-item { display:flex; flex-direction:column; gap:7px; padding:17px 18px; border:1px solid var(--lm-border); border-radius:13px; background:#fff; text-align:left; cursor:pointer; transition:.2s ease; }
.recruitment-list-item:hover,.recruitment-list-item.active { border-color:#2563eb; box-shadow:0 7px 22px rgba(37,99,235,.1); }
.list-item-top { display:flex; justify-content:space-between; gap:10px; }.list-item-top strong{color:var(--lm-text-primary);font-size:16px}.list-item-top small,.list-item-problem{color:var(--lm-text-muted);font-size:11px}.list-item-team{color:var(--lm-text-secondary);font-size:13px}.list-item-description{overflow:hidden;color:var(--lm-text-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}
.recruitment-detail { position:sticky; top:20px; padding:30px; border:1px solid var(--lm-border); border-radius:16px; background:#fff; box-shadow:0 10px 32px rgba(15,23,42,.05); }
.detail-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; padding-bottom:22px; border-bottom:1px solid var(--lm-border); }.detail-heading h2{margin:5px 0 0;font-size:25px}.detail-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.detail-actions .el-button{margin-left:0}.detail-eyebrow,.detail-section>small{color:#2563eb;font-size:11px;font-weight:750;letter-spacing:.08em}.detail-meta{display:flex;flex-wrap:wrap;gap:10px 22px;padding:15px 0;color:var(--lm-text-muted);font-size:12px}.detail-section{padding:20px 0;border-top:1px solid var(--lm-border-light)}.detail-section h3{margin:7px 0;font-size:18px}.detail-section p{margin:0;color:var(--lm-text-secondary);line-height:1.7}.detail-members{display:flex;flex-wrap:wrap;gap:9px;margin-top:12px}.detail-members button{display:flex;align-items:center;gap:7px;padding:6px 10px;border:1px solid var(--lm-border);border-radius:999px;background:#fff;cursor:pointer}.detail-members img,.detail-members button>span{display:flex;width:25px;height:25px;align-items:center;justify-content:center;border-radius:50%;background:#2563eb;color:#fff;object-fit:cover}
.recruitment-markdown{color:var(--lm-text-secondary);font-size:14px;line-height:1.75}.recruitment-markdown :deep(:first-child){margin-top:8px}.recruitment-markdown :deep(:last-child){margin-bottom:0}.recruitment-markdown :deep(a){color:#2563eb}.recruitment-markdown :deep(code){padding:2px 5px;border-radius:4px;background:#f1f5f9}.recruitment-markdown :deep(ul),.recruitment-markdown :deep(ol){padding-left:22px}
@media(max-width:900px){.problem-results{grid-template-columns:repeat(2,minmax(0,1fr))}.recruitment-workbench{grid-template-columns:1fr}.recruitment-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));max-height:none}.recruitment-detail{position:static}}
@media(max-width:768px){ .problem-results,.recruitment-list{grid-template-columns:1fr}.discovery-tabs :deep(.el-tabs__item){padding:0 16px}.detail-heading{flex-direction:column}.recruitment-detail{padding:22px} }
</style>
