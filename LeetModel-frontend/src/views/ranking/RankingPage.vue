<template>
  <div class="ranking-page">
    <section class="ranking-hero">
      <div class="hero-orb hero-orb-one" aria-hidden="true"></div>
      <div class="hero-orb hero-orb-two" aria-hidden="true"></div>

      <div class="hero-copy">
        <div class="hero-eyebrow">
          <el-icon><Trophy /></el-icon>
          <span>LEETMODEL 成果榜</span>
        </div>
        <h1><span>让每一次认真建模，</span><br /><span>都有清晰的位置</span></h1>
        <p>在同一道题、同一评审口径下比较最终作品，记录团队从提交到完成评审的真实成果。</p>
        <div class="hero-rules" aria-label="排行榜规则">
          <span><i></i>仅统计最终提交</span>
          <span><i></i>仅纳入已完成评审</span>
          <span><i></i>相同得分并列排名</span>
        </div>
      </div>

      <div class="hero-emblem" aria-hidden="true">
        <div class="emblem-ring">
          <el-icon><Trophy /></el-icon>
        </div>
        <span>同题竞技</span>
        <strong>成果可见</strong>
      </div>
    </section>

    <form class="filter-panel" @submit.prevent="loadRanking">
      <div class="filter-field problem-field">
        <label for="ranking-problem">选择建模题目</label>
        <el-select
          id="ranking-problem"
          v-model="selectedProblemId"
          filterable
          :loading="loadingProblems"
          placeholder="输入题号或标题查找题目"
          class="problem-select"
          aria-label="选择建模题目"
          @change="handleProblemChange"
        >
          <el-option
            v-for="problem in problems"
            :key="problem.id"
            :label="`题号 ${problem.code || problem.id} · ${problem.title}`"
            :value="problem.id"
          />
        </el-select>
      </div>

      <div class="filter-field keyword-field">
        <label for="ranking-keyword">查找上榜队伍</label>
        <el-input
          id="ranking-keyword"
          v-model="keyword"
          placeholder="输入队伍名称"
          clearable
          aria-label="按队伍名称筛选"
          @clear="loadRanking"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <el-button class="query-button" type="primary" native-type="submit" :loading="loading" :disabled="!selectedProblemId">
        查看榜单
      </el-button>
    </form>

    <section v-if="currentProblem" class="problem-context" aria-label="当前题目信息">
      <div class="problem-code">
        <span>PROBLEM</span>
        <strong>{{ currentProblem.code || currentProblem.id }}</strong>
      </div>
      <div class="problem-copy">
        <h2>{{ currentProblem.title }}</h2>
        <p>{{ currentProblem.contestName || '未分类赛事' }}</p>
      </div>
      <div class="problem-tags">
        <span v-if="currentProblem.year">{{ currentProblem.year }}</span>
        <span>{{ languageLabel(currentProblem.statementLanguage) }}</span>
        <span>{{ difficultyLabel(currentProblem.difficulty) }}</span>
      </div>
      <router-link :to="`/problem/${currentProblem.id}`" class="problem-link">
        查看题目<el-icon><ArrowRight /></el-icon>
      </router-link>
    </section>

    <div v-loading="loading" class="ranking-content">
      <template v-if="overview">
        <section class="metric-grid" aria-label="榜单概览">
          <article class="metric-card">
            <span class="metric-icon metric-icon-blue"><el-icon><UserFilled /></el-icon></span>
            <div>
              <span class="metric-label">{{ hasActiveKeyword ? '匹配队伍' : '上榜队伍' }}</span>
              <strong>{{ rankingItems.length }}<small> 支</small></strong>
              <p>{{ hasActiveKeyword ? `筛选“${appliedKeyword}”` : '已完成最终稿评审' }}</p>
            </div>
          </article>
          <article class="metric-card">
            <span class="metric-icon metric-icon-gold"><el-icon><Medal /></el-icon></span>
            <div>
              <span class="metric-label">当前最高分</span>
              <strong>{{ highestScore }}<small v-if="highestScore !== '-'"> 分</small></strong>
              <p>{{ rankingItems.length ? '以当前榜单结果计算' : '等待首支队伍上榜' }}</p>
            </div>
          </article>
          <article class="metric-card">
            <span class="metric-icon metric-icon-slate"><el-icon><Clock /></el-icon></span>
            <div>
              <span class="metric-label">榜单更新时间</span>
              <strong class="metric-time">{{ formatMetricDate(overview.computedAt) }}</strong>
              <p>排名来自最新有效快照</p>
            </div>
          </article>
        </section>

        <template v-if="rankingItems.length">
          <section v-if="!hasActiveKeyword && podiumItems.length" class="podium-section">
            <div class="section-heading">
              <div>
                <span class="section-kicker">TOP TEAMS</span>
                <h2>领先队伍</h2>
              </div>
              <p>聚焦当前榜单中排名最前的建模成果</p>
            </div>

            <div class="podium-grid" :class="`podium-count-${podiumItems.length}`">
              <article
                v-for="item in podiumItems"
                :key="item.teamId"
                class="podium-card"
                :class="`podium-rank-${item.rank}`"
              >
                <span class="podium-rank">TOP {{ item.rank }}</span>
                <div class="podium-avatar">{{ teamInitial(item.teamName) }}</div>
                <h3 :title="item.teamName">{{ item.teamName }}</h3>
                <div class="podium-score">
                  <strong>{{ formatScore(item.score) }}</strong><span>分</span>
                </div>
                <div class="podium-meta">
                  <span :title="item.workflowVersion">{{ workflowLabel(item.workflowVersion) }}</span>
                  <span>提交于 {{ formatCompactDate(item.submittedAt) }}</span>
                </div>
              </article>
            </div>
          </section>

          <section class="board-section">
            <div class="board-heading">
              <div>
                <span class="section-kicker">FULL RANKING</span>
                <h2>{{ hasActiveKeyword ? '筛选结果' : '完整排名' }}</h2>
                <p>{{ hasActiveKeyword ? `保留队伍名称中包含“${appliedKeyword}”的结果` : '排名按最终评审得分从高到低排列' }}</p>
              </div>
              <button type="button" class="refresh-button" :disabled="loading" @click="loadRanking">
                <el-icon><Refresh /></el-icon><span>刷新</span>
              </button>
            </div>

            <div class="ranking-board" role="table" aria-label="题目排行榜">
              <div class="ranking-head" role="row">
                <span role="columnheader">排名</span>
                <span role="columnheader">队伍</span>
                <span role="columnheader">得分</span>
                <span role="columnheader">评审信息</span>
                <span role="columnheader">最终稿提交</span>
              </div>
              <div v-for="item in rankingItems" :key="item.teamId" class="ranking-row" role="row">
                <div class="rank-cell" role="cell">
                  <span class="rank-number" :class="`rank-number-${item.rank}`">{{ item.rank }}</span>
                </div>
                <div class="team-cell" role="cell">
                  <span class="team-avatar">{{ teamInitial(item.teamName) }}</span>
                  <div>
                    <strong>{{ item.teamName }}</strong>
                    <span>最终作品</span>
                  </div>
                </div>
                <div class="score-cell" role="cell">
                  <strong>{{ formatScore(item.score) }}</strong><span>分</span>
                </div>
                <div class="review-cell" role="cell">
                  <span class="cell-label">评审信息</span>
                  <span class="version-pill" :title="item.workflowVersion">{{ workflowLabel(item.workflowVersion) }}</span>
                  <small>完成于 {{ formatDate(item.reviewFinishedAt) }}</small>
                </div>
                <div class="submitted-cell" role="cell">
                  <span class="cell-label">最终稿提交</span>
                  <strong>{{ formatDate(item.submittedAt) }}</strong>
                  <small>用于当前排名</small>
                </div>
              </div>
            </div>
          </section>
        </template>

        <section v-else-if="!loading" class="empty-state">
          <span class="empty-icon"><el-icon><Trophy /></el-icon></span>
          <template v-if="hasActiveKeyword">
            <h2>没有找到匹配的队伍</h2>
            <p>当前题目的上榜队伍中，没有名称包含“{{ appliedKeyword }}”的结果。</p>
            <el-button type="primary" plain @click="clearKeyword">清除队伍筛选</el-button>
          </template>
          <template v-else>
            <h2>这道题还在等待首个上榜作品</h2>
            <p>团队完成最终稿提交并通过 AI 评审后，排名会出现在这里。</p>
            <el-button type="primary" plain @click="$router.push(`/problem/${selectedProblemId}`)">查看题目详情</el-button>
          </template>
        </section>
      </template>

      <el-alert v-else-if="rankingError && !loading" :title="rankingError" type="error" :closable="false" show-icon />

      <section v-else-if="!loading" class="empty-state initial-state">
        <span class="empty-icon"><el-icon><Search /></el-icon></span>
        <h2>选择一道题目，查看它的成果榜单</h2>
        <p>你可以通过题号或标题快速定位正在练习的建模题。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Clock, Medal, Refresh, Search, Trophy, UserFilled } from '@element-plus/icons-vue'
import { getPublicProblemList } from '@/api/problem'
import { getRanking } from '@/api/ranking'

const route = useRoute()
const router = useRouter()
const loadingProblems = ref(false)
const loading = ref(false)
const problems = ref([])
const selectedProblemId = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const overview = ref(null)
const rankingError = ref('')

const currentProblem = computed(() => problems.value.find((item) => String(item.id) === String(selectedProblemId.value)))
const rankingItems = computed(() => overview.value?.items || [])
const hasActiveKeyword = computed(() => Boolean(appliedKeyword.value))
const highestScore = computed(() => rankingItems.value.length ? formatScore(rankingItems.value[0].score) : '-')
const podiumItems = computed(() => {
  const leaders = rankingItems.value.slice(0, 3)
  if (leaders.length === 3 && leaders.map((item) => Number(item.rank)).join(',') === '1,2,3') {
    return [leaders[1], leaders[0], leaders[2]]
  }
  return leaders
})

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

function formatCompactDate(value) {
  return value ? String(value).replace('T', ' ').slice(5, 16) : '-'
}

function formatMetricDate(value) {
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  return `${normalized.slice(5, 10)} ${normalized.slice(11, 16)}`
}

function formatScore(value) {
  if (value == null || value === '') return '-'
  const score = Number(value)
  return Number.isFinite(score) ? score.toFixed(1) : '-'
}

function languageLabel(value) {
  return value === 'EN' ? '英文题面' : '中文题面'
}

function difficultyLabel(value) {
  return ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '难度未知'
}

function workflowLabel(value) {
  if (!value) return '评审版本未知'
  if (value === 'BASIC_REVIEW_V1') return '基础评审 V1'
  return String(value).replaceAll('_', ' ')
}

function teamInitial(name) {
  return String(name || '队').trim().slice(0, 1).toUpperCase()
}

async function loadProblems() {
  loadingProblems.value = true
  try {
    const response = await getPublicProblemList({ page: 1, pageSize: 100 })
    problems.value = response.data?.rows || []
    const routeProblem = problems.value.find((item) => String(item.id) === String(route.query.problemId || ''))
    selectedProblemId.value = routeProblem?.id || problems.value[0]?.id || null
    if (selectedProblemId.value) await loadRanking()
  } catch (error) {
    rankingError.value = error.message || '题目列表加载失败'
    ElMessage.error(rankingError.value)
  } finally {
    loadingProblems.value = false
  }
}

async function handleProblemChange() {
  keyword.value = ''
  appliedKeyword.value = ''
  await router.replace({ query: { ...route.query, problemId: String(selectedProblemId.value) } })
  await loadRanking()
}

async function loadRanking() {
  if (!selectedProblemId.value) return
  loading.value = true
  rankingError.value = ''
  const normalizedKeyword = keyword.value.trim()
  try {
    const response = await getRanking(selectedProblemId.value, normalizedKeyword)
    overview.value = response.data
    appliedKeyword.value = normalizedKeyword
  } catch (error) {
    rankingError.value = error.message || '排行榜加载失败'
    overview.value = null
    ElMessage.error(rankingError.value)
  } finally {
    loading.value = false
  }
}

async function clearKeyword() {
  keyword.value = ''
  await loadRanking()
}

onMounted(loadProblems)
</script>

<style scoped>
/* Public ranking page visual system */
.ranking-page { width: 100%; max-width: 1180px; margin: 0 auto; padding: 4px 0 40px; }
.ranking-hero { position: relative; display: flex; min-height: 286px; align-items: center; justify-content: space-between; overflow: hidden; padding: 46px 54px 70px; border: 1px solid rgba(147, 197, 253, 0.22); border-radius: 28px; background: radial-gradient(circle at 82% 14%, rgba(96, 165, 250, 0.32), transparent 27%), linear-gradient(135deg, #0f2b5c 0%, #174ea6 52%, #2563eb 100%); box-shadow: 0 24px 60px rgba(30, 64, 175, 0.16); color: #fff; }
.hero-copy { position: relative; z-index: 2; max-width: 760px; }
.hero-eyebrow { display: inline-flex; align-items: center; gap: 8px; color: #bfdbfe; font-size: 12px; font-weight: 750; letter-spacing: 0.16em; }
.hero-eyebrow .el-icon { font-size: 16px; }
.hero-copy h1 { margin: 16px 0 13px; font-size: clamp(32px, 4.4vw, 50px); font-weight: 780; letter-spacing: -0.035em; line-height: 1.15; }
.hero-copy h1 span { white-space: nowrap; }
.hero-copy > p { max-width: 660px; margin: 0; color: rgba(239, 246, 255, 0.82); font-size: 15px; line-height: 1.8; }
.hero-rules { display: flex; flex-wrap: wrap; gap: 10px 22px; margin-top: 22px; color: rgba(239, 246, 255, 0.88); font-size: 12px; }
.hero-rules span { display: inline-flex; align-items: center; gap: 7px; }
.hero-rules i { width: 6px; height: 6px; border-radius: 50%; background: #93c5fd; box-shadow: 0 0 0 4px rgba(147, 197, 253, 0.12); }
.hero-emblem { position: relative; z-index: 2; display: flex; min-width: 190px; flex-direction: column; align-items: center; margin-left: 36px; color: rgba(255, 255, 255, 0.72); font-size: 11px; letter-spacing: 0.16em; }
.hero-emblem strong { margin-top: 4px; color: #fff; font-size: 17px; letter-spacing: 0.08em; }
.emblem-ring { display: flex; width: 112px; height: 112px; align-items: center; justify-content: center; margin-bottom: 14px; border: 1px solid rgba(255, 255, 255, 0.34); border-radius: 50%; background: rgba(255, 255, 255, 0.1); box-shadow: inset 0 0 0 12px rgba(255, 255, 255, 0.045), 0 18px 40px rgba(4, 18, 48, 0.22); backdrop-filter: blur(6px); }
.emblem-ring .el-icon { font-size: 48px; color: #fde68a; filter: drop-shadow(0 8px 14px rgba(15, 23, 42, 0.28)); }
.hero-orb { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, 0.06); }
.hero-orb-one { right: -76px; bottom: -150px; width: 360px; height: 360px; }
.hero-orb-two { top: -80px; left: 48%; width: 210px; height: 210px; }
.filter-panel { position: relative; z-index: 4; display: grid; width: calc(100% - 56px); grid-template-columns: minmax(0, 1.45fr) minmax(230px, 0.65fr) auto; gap: 16px; align-items: end; margin: -35px auto 24px; padding: 20px; border: 1px solid rgba(219, 228, 241, 0.94); border-radius: 18px; background: rgba(255, 255, 255, 0.97); box-shadow: 0 20px 42px rgba(30, 64, 175, 0.13); backdrop-filter: blur(14px); }
.filter-field { min-width: 0; }
.filter-field label { display: block; margin-bottom: 7px; color: var(--lm-text-secondary); font-size: 12px; font-weight: 650; }
.problem-select { width: 100%; }
.filter-panel :deep(.el-select__wrapper), .filter-panel :deep(.el-input__wrapper) { min-height: 44px; border-radius: 10px; box-shadow: 0 0 0 1px var(--lm-border) inset; }
.filter-panel :deep(.el-select__wrapper.is-focused), .filter-panel :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--lm-primary) inset, 0 0 0 3px rgba(37, 99, 235, 0.1); }
.query-button { min-width: 112px; min-height: 44px; border-radius: 10px; font-weight: 650; box-shadow: 0 8px 18px rgba(37, 99, 235, 0.2); }
.problem-context { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 18px; align-items: center; margin-bottom: 20px; padding: 18px 20px; border: 1px solid var(--lm-border); border-radius: 18px; background: var(--lm-surface); box-shadow: var(--lm-shadow-sm); }
.problem-code { display: flex; width: 72px; height: 62px; flex-direction: column; align-items: center; justify-content: center; border-radius: 13px; background: var(--lm-primary-bg); color: var(--lm-primary); }
.problem-code span { font-size: 8px; font-weight: 750; letter-spacing: 0.12em; }
.problem-code strong { margin-top: 1px; font-size: 21px; line-height: 1.2; }
.problem-copy { min-width: 0; }
.problem-copy h2 { overflow: hidden; margin: 0; color: var(--lm-text-primary); font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.problem-copy p { overflow: hidden; margin: 4px 0 0; color: var(--lm-text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.problem-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.problem-tags span { padding: 5px 9px; border-radius: 999px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-size: 11px; white-space: nowrap; }
.problem-link { display: inline-flex; align-items: center; gap: 4px; padding-left: 16px; border-left: 1px solid var(--lm-border); color: var(--lm-primary); font-size: 12px; font-weight: 650; white-space: nowrap; }
.problem-link:hover .el-icon { transform: translateX(2px); }
.problem-link .el-icon { transition: transform var(--lm-transition); }
.ranking-content { min-height: 300px; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 34px; }
.metric-card { display: flex; min-width: 0; align-items: center; gap: 14px; padding: 18px 20px; border: 1px solid var(--lm-border); border-radius: 17px; background: var(--lm-surface); box-shadow: var(--lm-shadow-xs); }
.metric-icon { display: inline-flex; width: 44px; height: 44px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 13px; font-size: 20px; }
.metric-icon-blue { background: #eff6ff; color: #2563eb; }
.metric-icon-gold { background: #fff7e6; color: #d97706; }
.metric-icon-slate { background: #f1f5f9; color: #475569; }
.metric-card > div { min-width: 0; }
.metric-label { display: block; color: var(--lm-text-secondary); font-size: 11px; font-weight: 600; }
.metric-card strong { display: block; overflow: hidden; margin: 1px 0; color: var(--lm-text-primary); font-size: 25px; font-weight: 760; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.metric-card strong small { color: var(--lm-text-secondary); font-size: 11px; font-weight: 550; }
.metric-card strong.metric-time { font-size: 19px; }
.metric-card p { overflow: hidden; margin: 0; color: var(--lm-text-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.section-heading, .board-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.section-kicker { color: var(--lm-primary); font-size: 10px; font-weight: 800; letter-spacing: 0.14em; }
.section-heading h2, .board-heading h2 { margin: 2px 0 0; color: var(--lm-text-primary); font-size: 22px; letter-spacing: -0.02em; }
.section-heading p { margin: 0 0 3px; color: var(--lm-text-muted); font-size: 12px; }
.podium-section { margin-bottom: 36px; }
.podium-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: end; margin-top: 20px; }
.podium-grid.podium-count-1 { grid-template-columns: minmax(280px, 400px); justify-content: center; }
.podium-grid.podium-count-2 { grid-template-columns: repeat(2, minmax(260px, 400px)); justify-content: center; }
.podium-card { position: relative; overflow: hidden; min-height: 224px; padding: 26px 22px 20px; border: 1px solid var(--lm-border); border-radius: 21px; background: linear-gradient(180deg, #fff, #fbfdff); box-shadow: var(--lm-shadow-sm); text-align: center; transition: transform var(--lm-transition), box-shadow var(--lm-transition); }
.podium-card::after { position: absolute; top: -72px; right: -62px; width: 150px; height: 150px; border-radius: 50%; background: var(--podium-glow, #eff6ff); content: ''; opacity: 0.75; }
.podium-card:hover { transform: translateY(-3px); box-shadow: var(--lm-shadow); }
.podium-card.podium-rank-1 { min-height: 240px; border-color: #f5d889; background: linear-gradient(180deg, #fffdf7, #fff); box-shadow: 0 16px 38px rgba(180, 83, 9, 0.1); --podium-glow: #fef3c7; }
.podium-card.podium-rank-2 { --podium-glow: #e2e8f0; }
.podium-card.podium-rank-3 { --podium-glow: #ffedd5; }
.podium-rank { position: relative; z-index: 1; display: inline-flex; padding: 4px 9px; border-radius: 999px; background: var(--lm-primary-bg); color: var(--lm-primary); font-size: 9px; font-weight: 800; letter-spacing: 0.1em; }
.podium-rank-1 .podium-rank { background: #fef3c7; color: #a16207; }
.podium-rank-2 .podium-rank { background: #f1f5f9; color: #475569; }
.podium-rank-3 .podium-rank { background: #ffedd5; color: #9a3412; }
.podium-avatar { display: flex; width: 50px; height: 50px; align-items: center; justify-content: center; margin: 14px auto 9px; border: 4px solid #fff; border-radius: 16px; background: linear-gradient(145deg, #dbeafe, #eff6ff); box-shadow: 0 7px 16px rgba(37, 99, 235, 0.12); color: var(--lm-primary); font-size: 18px; font-weight: 780; }
.podium-rank-1 .podium-avatar { background: linear-gradient(145deg, #fde68a, #fff7d6); color: #a16207; }
.podium-card h3 { overflow: hidden; margin: 0; color: var(--lm-text-primary); font-size: 15px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.podium-score { display: flex; align-items: baseline; justify-content: center; margin: 7px 0 13px; color: var(--lm-text-muted); }
.podium-score strong { color: var(--lm-text-primary); font-size: 31px; font-weight: 800; letter-spacing: -0.04em; }
.podium-rank-1 .podium-score strong { color: #b45309; }
.podium-score span { margin-left: 3px; font-size: 11px; }
.podium-meta { display: flex; align-items: center; justify-content: center; gap: 10px; padding-top: 12px; border-top: 1px solid var(--lm-border-light); color: var(--lm-text-muted); font-size: 10px; }
.podium-meta span:first-child { overflow: hidden; max-width: 120px; text-overflow: ellipsis; white-space: nowrap; }
.board-section { overflow: hidden; border: 1px solid var(--lm-border); border-radius: 22px; background: var(--lm-surface); box-shadow: var(--lm-shadow-sm); }
.board-heading { align-items: center; padding: 22px 24px 18px; }
.board-heading h2 { font-size: 20px; }
.board-heading p { margin: 3px 0 0; color: var(--lm-text-muted); font-size: 11px; }
.refresh-button { display: inline-flex; align-items: center; gap: 5px; padding: 7px 10px; border: 0; border-radius: 8px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font: inherit; font-size: 11px; cursor: pointer; transition: color var(--lm-transition), background var(--lm-transition); }
.refresh-button:hover:not(:disabled) { background: var(--lm-primary-bg); color: var(--lm-primary); }
.refresh-button:disabled { cursor: not-allowed; opacity: 0.55; }
.ranking-head, .ranking-row { display: grid; grid-template-columns: 76px minmax(200px, 1.25fr) 100px minmax(190px, 0.9fr) minmax(170px, 0.8fr); gap: 14px; align-items: center; }
.ranking-head { min-height: 42px; padding: 0 24px; border-top: 1px solid var(--lm-border-light); border-bottom: 1px solid var(--lm-border); background: var(--lm-bg); color: var(--lm-text-muted); font-size: 10px; font-weight: 700; letter-spacing: 0.03em; }
.ranking-head span:nth-child(1), .ranking-head span:nth-child(3) { text-align: center; }
.ranking-row { min-height: 78px; padding: 12px 24px; border-bottom: 1px solid var(--lm-border-light); transition: background var(--lm-transition); }
.ranking-row:last-child { border-bottom: 0; }
.ranking-row:hover { background: #f8fbff; }
.rank-cell { text-align: center; }
.rank-number { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; border-radius: 11px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-size: 13px; font-weight: 800; }
.rank-number-1 { background: #fef3c7; color: #a16207; }
.rank-number-2 { background: #e9eef5; color: #475569; }
.rank-number-3 { background: #ffedd5; color: #9a3412; }
.team-cell { display: flex; min-width: 0; align-items: center; gap: 11px; }
.team-avatar { display: inline-flex; width: 38px; height: 38px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 12px; background: var(--lm-primary-bg); color: var(--lm-primary); font-size: 13px; font-weight: 750; }
.team-cell > div { min-width: 0; }
.team-cell strong { display: block; overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.team-cell div span { display: block; margin-top: 2px; color: var(--lm-text-muted); font-size: 9px; }
.score-cell { text-align: center; white-space: nowrap; }
.score-cell strong { color: var(--lm-primary); font-size: 20px; font-weight: 800; letter-spacing: -0.02em; }
.score-cell span { margin-left: 2px; color: var(--lm-text-muted); font-size: 9px; }
.review-cell, .submitted-cell { min-width: 0; }
.version-pill { display: inline-block; overflow: hidden; max-width: 100%; padding: 3px 8px; border-radius: 999px; background: var(--lm-primary-bg); color: #1d4ed8; font-size: 9px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.review-cell small, .submitted-cell small { display: block; overflow: hidden; margin-top: 4px; color: var(--lm-text-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.submitted-cell strong { display: block; color: var(--lm-text-secondary); font-size: 11px; font-weight: 650; }
.cell-label { display: none; }
.empty-state { display: flex; min-height: 300px; flex-direction: column; align-items: center; justify-content: center; padding: 44px 20px; border: 1px dashed #cbd5e1; border-radius: 22px; background: rgba(255, 255, 255, 0.7); text-align: center; }
.empty-icon { display: inline-flex; width: 66px; height: 66px; align-items: center; justify-content: center; margin-bottom: 16px; border-radius: 20px; background: var(--lm-primary-bg); color: var(--lm-primary); font-size: 30px; }
.empty-state h2 { margin: 0; color: var(--lm-text-primary); font-size: 18px; }
.empty-state p { max-width: 480px; margin: 8px 0 20px; color: var(--lm-text-secondary); font-size: 12px; }
@media (max-width: 900px) {
  .ranking-hero { padding-right: 38px; padding-left: 38px; }
  .hero-emblem { min-width: 142px; }
  .filter-panel { width: calc(100% - 32px); grid-template-columns: minmax(0, 1fr) minmax(210px, 0.6fr) auto; }
  .problem-context { grid-template-columns: auto minmax(0, 1fr) auto; }
  .problem-tags { display: none; }
  .ranking-head, .ranking-row { grid-template-columns: 62px minmax(170px, 1.2fr) 82px minmax(155px, 0.8fr) minmax(150px, 0.75fr); padding-right: 16px; padding-left: 16px; }
}
@media (max-width: 720px) {
  .ranking-page { padding-bottom: 20px; }
  .ranking-hero { min-height: 278px; padding: 34px 24px 58px; border-radius: 22px; }
  .hero-copy h1 { font-size: clamp(28px, 7.5vw, 32px); }
  .hero-copy > p { font-size: 13px; line-height: 1.7; }
  .hero-rules { gap: 8px 14px; margin-top: 18px; }
  .hero-emblem { display: none; }
  .filter-panel { width: calc(100% - 24px); grid-template-columns: 1fr; gap: 13px; margin-top: -30px; padding: 16px; }
  .query-button { width: 100%; }
  .problem-context { grid-template-columns: auto minmax(0, 1fr); gap: 12px; padding: 14px; }
  .problem-code { width: 62px; height: 58px; }
  .problem-link { grid-column: 1 / 3; justify-content: center; padding: 11px 0 0; border-top: 1px solid var(--lm-border-light); border-left: 0; }
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 28px; }
  .metric-card { flex-direction: column; align-items: flex-start; gap: 9px; padding: 14px; }
  .metric-card:last-child { grid-column: 1 / 3; flex-direction: row; align-items: center; }
  .metric-icon { width: 38px; height: 38px; border-radius: 11px; font-size: 17px; }
  .metric-card strong { font-size: 22px; }
  .metric-card strong.metric-time { font-size: 18px; }
  .section-heading { align-items: flex-start; }
  .section-heading p { display: none; }
  .podium-grid, .podium-grid.podium-count-1, .podium-grid.podium-count-2 { grid-template-columns: 1fr; }
  .podium-card, .podium-card.podium-rank-1 { min-height: 0; }
  .podium-card.podium-rank-1 { order: 1; }
  .podium-card.podium-rank-2 { order: 2; }
  .podium-card.podium-rank-3 { order: 3; }
  .board-heading { padding: 19px 18px 15px; }
  .ranking-head { display: none; }
  .ranking-row { grid-template-columns: 42px minmax(0, 1fr) auto; gap: 10px 12px; min-height: 0; padding: 16px; }
  .rank-cell { grid-column: 1; grid-row: 1; }
  .team-cell { grid-column: 2; grid-row: 1; }
  .score-cell { grid-column: 3; grid-row: 1; text-align: right; }
  .review-cell, .submitted-cell { grid-column: 2 / 4; display: grid; grid-template-columns: 76px minmax(0, 1fr); align-items: center; gap: 4px 8px; padding-top: 9px; border-top: 1px solid var(--lm-border-light); }
  .review-cell { grid-row: 2; }
  .submitted-cell { grid-row: 3; }
  .cell-label { display: inline; grid-row: 1 / 3; color: var(--lm-text-muted); font-size: 9px; }
  .review-cell small, .submitted-cell small { margin-top: 0; }
  .version-pill { justify-self: start; }
}
@media (max-width: 430px) {
  .ranking-hero { padding-right: 20px; padding-left: 20px; }
  .hero-rules span { font-size: 10px; }
  .problem-copy h2 { font-size: 14px; }
  .section-heading h2, .board-heading h2 { font-size: 19px; }
  .refresh-button span { display: none; }
}
</style>
