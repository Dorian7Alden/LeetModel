<template>
  <el-drawer
    :model-value="modelValue"
    size="min(980px, 92vw)"
    destroy-on-close
    class="review-result-drawer"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-title">
        <span>AI PAPER REVIEW</span>
        <strong>{{ review?.status === 'COMPLETED' ? '论文 AI 评审结果' : '论文 AI 评审进度' }}</strong>
      </div>
    </template>

    <div v-if="review" class="review-result">
      <header class="review-result__header">
        <div>
          <span class="workflow-label">{{ workflowLabel(review.workflowVersion) }}</span>
          <p>{{ review.processSummary || '系统正在按已锁定的评审版本处理最终论文。' }}</p>
        </div>
        <span class="status-badge" :class="`status-${String(review.status).toLowerCase()}`">
          <i aria-hidden="true"></i>{{ statusLabel(review.status) }}
        </span>
      </header>

      <section v-if="isPending" class="review-progress" role="status" aria-live="polite">
        <span class="progress-orbit" aria-hidden="true"><i></i></span>
        <div>
          <strong>{{ pendingTitle }}</strong>
          <p>页面会自动刷新评审状态，完成后可在这里查看五维评分、问题证据和评审意见。</p>
        </div>
      </section>

      <el-alert
        v-else-if="review.status === 'FAILED'"
        :title="review.errorMessage || '本次评审执行失败'"
        type="error"
        :closable="false"
        show-icon
      >
        <template #default>
          <el-button type="danger" plain @click="$emit('retry', review.taskId)">重新进入评审队列</el-button>
        </template>
      </el-alert>

      <el-alert
        v-else-if="review.status === 'UNKNOWN'"
        title="AI 上游结果暂时无法确认，系统不会自动重复执行"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          <p class="review-alert-copy">请先核对状态；确认需要重新执行时，可手动把同一论文版本送回评审队列。</p>
          <el-button type="warning" plain @click="$emit('retry', review.taskId)">确认重新评审</el-button>
        </template>
      </el-alert>

      <template v-else-if="review.status === 'COMPLETED' && result">
        <section class="review-overview" aria-labelledby="review-overview-heading">
          <div class="score-block">
            <span id="review-overview-heading">平台训练评分</span>
            <strong>{{ formatScore(result.score ?? review.score) }}</strong>
            <small>/ 100</small>
          </div>
          <div class="assessment-block">
            <span>综合评审</span>
            <p>{{ result.overallAssessment || result.summary || '评审已完成，请结合分项评分和问题证据继续修改论文。' }}</p>
          </div>
        </section>

        <section v-if="dimensions.length" class="review-section" aria-labelledby="dimension-heading">
          <div class="section-heading">
            <div><span>SCORING DIMENSIONS</span><h3 id="dimension-heading">论文分项评分</h3></div>
            <small>{{ dimensions.length }} 个评审维度</small>
          </div>
          <div class="dimension-grid">
            <article v-for="item in dimensions" :key="item.key">
              <div><strong>{{ item.label }}</strong><span>{{ formatScore(item.score) }} / {{ formatScore(item.maxScore) }}</span></div>
              <div class="score-track" aria-hidden="true"><i :style="{ width: `${item.percent}%` }"></i></div>
              <p>{{ item.reason || '已按当前评审量表完成评分。' }}</p>
            </article>
          </div>
        </section>

        <div class="review-detail-grid">
          <section class="review-section" aria-labelledby="finding-heading">
            <div class="section-heading">
              <div><span>EVIDENCE FINDINGS</span><h3 id="finding-heading">需要优先处理的问题</h3></div>
              <small>{{ findings.length }} 条</small>
            </div>
            <div v-if="findings.length" class="finding-list">
              <article v-for="item in findings" :key="item.key">
                <div>
                  <span class="severity" :class="`severity-${String(item.severity).toLowerCase()}`">{{ severityLabel(item.severity) }}</span>
                  <strong>{{ item.statement }}</strong>
                </div>
                <p v-if="item.scoreImpact">{{ item.scoreImpact }}</p>
                <small v-if="item.page">论文第 {{ item.page }} 页</small>
              </article>
            </div>
            <p v-else class="section-empty">当前结果没有可展示的结构化问题记录。</p>
          </section>

          <section class="review-section" aria-labelledby="coverage-heading">
            <div class="section-heading">
              <div><span>REQUIREMENT COVERAGE</span><h3 id="coverage-heading">赛题要求覆盖</h3></div>
              <small>{{ requirements.length }} 项</small>
            </div>
            <div v-if="requirements.length" class="coverage-list">
              <article v-for="item in requirements" :key="item.key">
                <span :class="`coverage-${String(item.status).toLowerCase()}`">{{ coverageLabel(item.status) }}</span>
                <div><strong>{{ item.title }}</strong><p>{{ item.explanation }}</p></div>
              </article>
            </div>
            <p v-else class="section-empty">当前评审版本没有单独的题目覆盖记录。</p>
          </section>
        </div>

        <footer class="review-disclaimer">平台训练评分用于论文修改与实训复盘，不代表具体赛事官方评分。</footer>
      </template>

      <el-alert v-else title="评审结果暂时无法解析" type="warning" :closable="false" show-icon />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  review: { type: Object, default: null },
})

defineEmits(['update:modelValue', 'retry'])

const isPending = computed(() => ['WAITING', 'LEASED', 'RUNNING'].includes(props.review?.status))
const pendingTitle = computed(() => ({
  WAITING: '评审任务已进入队列',
  LEASED: '评审任务正在准备执行',
  RUNNING: 'AI 正在阅读并评审最终论文',
})[props.review?.status] || '评审任务正在处理')

const result = computed(() => {
  if (!props.review?.resultJson) return null
  try {
    return JSON.parse(props.review.resultJson)
  } catch {
    return null
  }
})

const dimensions = computed(() => {
  const source = result.value?.dimensions
  if (!source) return []
  if (Array.isArray(source)) {
    return source.map((item, index) => normalizeDimension(
      item.dimensionCode || item.dimensionId || `dimension-${index}`,
      item.dimensionName || item.name || `分项 ${index + 1}`,
      item,
    ))
  }
  const labels = {
    assumptionRationality: '假设合理性',
    modelCreativity: '建模创造性',
    resultCorrectness: '结果正确性',
    expressionClarity: '表达清晰性',
  }
  return Object.entries(source).map(([key, item]) => normalizeDimension(key, labels[key] || key, item))
})

const findings = computed(() => {
  if (Array.isArray(result.value?.findings)) {
    const evidence = new Map((result.value.evidence || []).map(item => [item.evidenceId, item]))
    return result.value.findings
      .filter(item => item.type !== 'STRENGTH')
      .map((item, index) => ({
        key: item.findingId || `finding-${index}`,
        severity: item.severity || 'MEDIUM',
        statement: item.statement || '发现一项需要进一步核对的问题',
        scoreImpact: item.scoreImpact,
        page: item.physicalPage || (item.evidenceIds || []).map(id => evidence.get(id)?.physicalPage).find(Boolean),
      }))
  }
  return (result.value?.weaknesses || []).map((statement, index) => ({
    key: `weakness-${index}`,
    severity: 'MEDIUM',
    statement,
  }))
})

const requirements = computed(() => (result.value?.requirementCoverage || []).map((item, index) => ({
  key: item.requirementId || `requirement-${index}`,
  title: item.questionTitle || item.requirement || `赛题要求 ${index + 1}`,
  status: item.status || 'UNCERTAIN',
  explanation: item.explanation || '当前结果没有补充说明。',
})))

function normalizeDimension(key, label, item = {}) {
  const score = Number(item?.score) || 0
  const maxScore = Number(item?.maxScore) || 100
  return {
    key,
    label,
    score,
    maxScore,
    percent: Math.min(100, Math.max(0, Math.round((score / maxScore) * 100))),
    reason: item?.reason || item?.comment,
  }
}

function formatScore(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return Number.isInteger(number) ? String(number) : number.toFixed(1)
}

function statusLabel(status) {
  return ({ WAITING: '队列中', LEASED: '准备评审', RUNNING: '评审中', COMPLETED: '已完成', FAILED: '评审失败', UNKNOWN: '结果待核查' })[status] || status || '等待评审'
}

function workflowLabel(value) {
  return ({
    DEEP_EVIDENCE_REVIEW_V3: 'V3 深度证据化评审',
    EVIDENCE_REVIEW_V2: 'V2 证据化评审',
    BASIC_REVIEW_V1: 'V1 基础评审',
  })[value] || value || 'AI 论文评审'
}

function severityLabel(value) {
  return ({ CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低' })[value] || value || '问题'
}

function coverageLabel(value) {
  return ({ COVERED: '已覆盖', PARTIAL: '部分覆盖', MISSING: '未覆盖', UNCERTAIN: '待核对', UNVERIFIABLE: '无法核实' })[value] || value || '待核对'
}
</script>

<style scoped>
.drawer-title { display: flex; flex-direction: column; gap: 3px; }
.drawer-title span,.section-heading span,.workflow-label { color: var(--lm-primary); font-family: var(--lm-code-font-family); font-size: 10px; font-weight: 800; letter-spacing: .08em; }
.drawer-title strong { color: var(--lm-text-primary); font-size: 18px; }
.review-result { display: grid; gap: 18px; padding-bottom: 30px; }
.review-alert-copy { margin: 8px 0 12px; color: var(--lm-text-secondary); font-size: 12px; line-height: 1.6; }
.review-result__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 16px 18px; border: 1px solid var(--lm-border); border-radius: 12px; background: #f8fafc; }
.review-result__header p { max-width: 650px; margin: 7px 0 0; color: var(--lm-text-secondary); font-size: 13px; line-height: 1.6; }
.status-badge { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 7px; padding: 6px 10px; border-radius: 999px; background: #eff6ff; color: #1d4ed8; font-size: 11px; font-weight: 700; }
.status-badge i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.status-badge.status-completed { background: #ecfdf5; color: #047857; }
.status-badge.status-failed { background: #fef2f2; color: #b91c1c; }
.status-badge.status-unknown { background: #fff7ed; color: #b45309; }
.review-progress { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 20px; padding: 28px; border: 1px dashed #bfdbfe; border-radius: 12px; background: #f8fbff; }
.progress-orbit { position: relative; width: 54px; height: 54px; flex: 0 0 auto; border: 2px solid #dbeafe; border-radius: 50%; }
.progress-orbit i { position: absolute; inset: -2px; border: 2px solid transparent; border-top-color: #2563eb; border-radius: 50%; animation: review-spin 1.1s linear infinite; }
.review-progress strong { color: var(--lm-text-primary); font-size: 16px; }
.review-progress p { max-width: 520px; margin: 7px 0 0; color: var(--lm-text-secondary); font-size: 13px; line-height: 1.65; }
.review-overview { display: grid; grid-template-columns: 220px minmax(0,1fr); overflow: hidden; border: 1px solid #bfdbfe; border-radius: 14px; background: #f8fbff; }
.score-block { display: flex; align-items: baseline; justify-content: center; flex-wrap: wrap; padding: 24px; border-right: 1px solid #dbeafe; }
.score-block > span { width: 100%; color: var(--lm-text-muted); font-size: 11px; text-align: center; }
.score-block strong { margin-top: 7px; color: #2563eb; font-family: var(--lm-code-font-family); font-size: 44px; line-height: 1; }
.score-block small { margin-left: 5px; color: var(--lm-text-muted); font-size: 12px; }
.assessment-block { padding: 23px 26px; }
.assessment-block span { color: var(--lm-text-muted); font-size: 11px; font-weight: 700; }
.assessment-block p { margin: 8px 0 0; color: var(--lm-text-primary); font-size: 14px; line-height: 1.75; }
.review-section { min-width: 0; padding: 18px; border: 1px solid var(--lm-border); border-radius: 12px; background: #fff; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.section-heading h3 { margin: 4px 0 0; color: var(--lm-text-primary); font-size: 15px; }
.section-heading small { color: var(--lm-text-muted); font-size: 11px; }
.dimension-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; }
.dimension-grid article { padding: 13px 14px; border-radius: 9px; background: #f8fafc; }
.dimension-grid article > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dimension-grid strong { color: var(--lm-text-primary); font-size: 12px; }
.dimension-grid article > div:first-child span { color: #2563eb; font-family: var(--lm-code-font-family); font-size: 11px; font-weight: 700; }
.score-track { height: 5px; margin-top: 10px; overflow: hidden; border-radius: 999px; background: #e2e8f0; }
.score-track i { display: block; height: 100%; border-radius: inherit; background: #2563eb; }
.dimension-grid p { margin: 8px 0 0; color: var(--lm-text-secondary); font-size: 11px; line-height: 1.6; }
.review-detail-grid { display: grid; grid-template-columns: 1.12fr .88fr; gap: 14px; }
.finding-list,.coverage-list { display: grid; gap: 0; }
.finding-list article,.coverage-list article { padding: 12px 0; border-top: 1px solid var(--lm-border-light); }
.finding-list article:first-child,.coverage-list article:first-child { border-top: 0; }
.finding-list article > div { display: flex; align-items: flex-start; gap: 8px; }
.finding-list strong,.coverage-list strong { color: var(--lm-text-primary); font-size: 12px; line-height: 1.55; }
.finding-list p,.coverage-list p { margin: 6px 0 0; color: var(--lm-text-secondary); font-size: 11px; line-height: 1.6; }
.finding-list small { display: block; margin-top: 6px; color: var(--lm-text-muted); font-size: 10px; }
.severity { flex: 0 0 auto; padding: 2px 6px; border-radius: 999px; background: #fff7ed; color: #b45309; font-size: 9px; font-weight: 800; }
.severity-critical,.severity-high { background: #fef2f2; color: #b91c1c; }
.severity-low { background: #eff6ff; color: #1d4ed8; }
.coverage-list article { display: grid; grid-template-columns: auto minmax(0,1fr); align-items: flex-start; gap: 9px; }
.coverage-list article > span { padding: 3px 6px; border-radius: 5px; background: #f4f4f5; color: #52525b; font-size: 9px; font-weight: 700; }
.coverage-list article > span.coverage-covered { background: #ecfdf5; color: #047857; }
.coverage-list article > span.coverage-missing { background: #fef2f2; color: #b91c1c; }
.coverage-list article > span.coverage-partial { background: #fff7ed; color: #b45309; }
.section-empty { margin: 24px 0; color: var(--lm-text-muted); font-size: 12px; text-align: center; }
.review-disclaimer { padding: 12px 14px; border-left: 3px solid #93c5fd; background: #eff6ff; color: #475569; font-size: 11px; line-height: 1.6; }
@keyframes review-spin { to { transform: rotate(360deg); } }
@media (max-width: 760px) { .review-overview,.review-detail-grid { grid-template-columns: 1fr; }.score-block { border-right: 0; border-bottom: 1px solid #dbeafe; }.dimension-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .progress-orbit i { animation: none; } }
</style>
