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
            <div class="card-section-title">
              <el-icon class="title-icon"><Document /></el-icon>
              <span>题目描述与任务要求</span>
            </div>
            <article v-if="problem.contentMarkdown" class="markdown-body" v-html="renderedMarkdown" />
            <el-empty v-else description="暂无题面描述" />
          </div>
        </main>

        <!-- 右边栏：具体的题目详细、标题卡、操作与附件 (固定 310px，Sticky 吸顶) -->
        <aside class="detail-side-col">
          <!-- 1. 题目标题与题号卡片 -->
          <div class="side-card detail-title-card">
            <div class="title-main-row">
              <span v-if="problem.code" class="problem-code-badge" :title="`题号 ${problem.code}`">
                #{{ String(problem.code).padStart(2, '0') }}
              </span>
              <h1 class="problem-heading" :title="problem.title">{{ problem.title }}</h1>
            </div>
          </div>

          <!-- 2. 核心行动卡片 -->
          <div class="side-card action-hub-card">
            <button type="button" class="primary-action-btn" @click="createProblemTeam">
              <el-icon><User /></el-icon>
              <span>创建实训队伍</span>
            </button>
            <div class="secondary-actions-row">
              <button type="button" class="secondary-action-btn" @click="findProblemTeams">
                <el-icon><Search /></el-icon>
                <span>寻找队伍</span>
              </button>
              <button type="button" class="secondary-action-btn" @click="viewRanking">
                <el-icon><Trophy /></el-icon>
                <span>查看排行</span>
              </button>
            </div>
          </div>

          <!-- 3. 题目详细档案卡片 -->
          <div class="side-card specs-card">
            <div class="side-card-title">题目详细信息</div>

            <!-- 历史平均预评分大看板 -->
            <div class="score-highlight-block">
              <div class="score-num">{{ formatScore(problem.averageScore) }}</div>
              <div class="score-label">历史平均预评分</div>
            </div>

            <div class="specs-list">
              <div class="spec-row">
                <span class="spec-label">所属赛事</span>
                <span class="spec-value text-strong">{{ problem.contestName || '未分类' }}</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">命题年份</span>
                <span class="spec-value">{{ problem.year ? `${problem.year} 年` : '—' }}</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">难度评级</span>
                <div class="spec-value">
                  <span class="difficulty-capsule" :class="`diff-${problem.difficulty}`">
                    {{ difficultyLabel(problem.difficulty) }}
                  </span>
                </div>
              </div>
              <div class="spec-row">
                <span class="spec-label">试卷语言</span>
                <span class="spec-value">{{ problem.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">建议用时</span>
                <span class="spec-value">{{ formatDuration(problem.durationMinutes) }}</span>
              </div>
              <div class="spec-row">
                <span class="spec-label">更新时间</span>
                <span class="spec-value text-muted text-small">{{ formatTime(problem.updateTime) }}</span>
              </div>
            </div>
          </div>

          <!-- 3. 考察题型与算法标签卡片 -->
          <div v-if="problem.tagNames?.length" class="side-card tags-card">
            <div class="side-card-title">考察题型与方法</div>
            <div class="tags-cloud">
              <span v-for="tag in problem.tagNames" :key="tag" class="detail-tag-chip">
                {{ tag }}
              </span>
            </div>
          </div>

          <!-- 5. 赛题附件与数据集下载卡片 -->
          <div class="side-card detail-attachments-card">
            <div class="side-card-title">
              <el-icon class="title-inline-icon"><Download /></el-icon>
              <span>赛题附件与数据集</span>
            </div>

            <div v-if="problem.attachments?.length" class="side-attachments-feed">
              <div v-for="att in problem.attachments" :key="att.id" class="side-attachment-item">
                <div class="side-att-main">
                  <el-icon class="side-att-icon"><Folder /></el-icon>
                  <div class="side-att-texts">
                    <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="side-att-link" :title="att.fileName">
                      {{ att.fileName }}
                    </a>
                    <span v-else class="side-att-title" :title="att.fileName">{{ att.fileName }}</span>
                    <p v-if="att.description" class="side-att-desc">{{ att.description }}</p>
                  </div>
                </div>
                <div class="side-att-meta">
                  <span class="side-att-size">{{ formatFileSize(att.fileSize) }}</span>
                  <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="side-att-dl-btn">
                    <el-icon><Download /></el-icon>
                    <span>下载</span>
                  </a>
                </div>
              </div>
            </div>

            <div v-else class="side-empty-attachment-notice">
              <el-icon class="notice-icon"><InfoFilled /></el-icon>
              <div class="notice-desc">本题暂无可下载附件，请根据题面文字信息作答。</div>
            </div>
          </div>
        </aside>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="题目不存在或尚未发布" />
    <CreateTeamDialog v-model="showCreateDialog" :preset-problem="problem" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import 'github-markdown-css/github-markdown.css'
import {
  ArrowLeft,
  Document,
  Download,
  Folder,
  InfoFilled,
  Search,
  Trophy,
  User
} from '@element-plus/icons-vue'
import { getPublicProblemDetail } from '@/api/problem'
import CreateTeamDialog from '@/views/team/components/CreateTeamDialog.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const showCreateDialog = ref(false)
const problem = ref(null)
const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN') : '-'
const formatDuration = (minutes) => minutes ? `${Math.floor(minutes / 60)} 小时${minutes % 60 ? ` ${minutes % 60} 分钟` : ''}` : '-'
const formatScore = (score) => Number(score) > 0 ? Number(score).toFixed(1) : '暂无评分'
const formatFileSize = (bytes) => {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
const renderedMarkdown = computed(() => {
  if (!problem.value?.contentMarkdown) return ''
  const html = marked.parse(problem.value.contentMarkdown, {
    async: false,
    breaks: true,
    gfm: true,
  })
  return DOMPurify.sanitize(html)
})
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

const fetchDetail = async () => {
  loading.value = true
  problem.value = null
  try {
    const response = await getPublicProblemDetail(route.params.id)
    problem.value = response.data || null
  } catch (error) {
    ElMessage.error(error.message || '获取题目详情失败')
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, fetchDetail)
onMounted(fetchDetail)
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
  grid-template-columns: 1fr 310px;
  gap: 24px;
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

.detail-title-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 16px 18px;
}

.title-main-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.problem-code-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 7px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  border-radius: 6px;
  color: #18181b;
  font-size: 12px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
  flex-shrink: 0;
  margin-top: 1px;
}

.problem-heading {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #18181b;
  line-height: 1.45;
  word-break: break-word;
}

/* Markdown 题面卡片 */
.detail-markdown-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  overflow: hidden;
}

.card-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  font-size: 13px;
  font-weight: 700;
  color: #27272a;
  border-bottom: 1px solid var(--lm-border-light);
  background: #fafafa;
}

.title-icon {
  font-size: 15px;
  color: var(--lm-text-muted);
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

/* 附件卡片 */
.side-attachments-feed {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-attachment-item {
  padding: 8px 10px;
  background: #fafafa;
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius-sm);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-att-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.side-att-icon {
  font-size: 15px;
  color: var(--lm-text-muted);
  margin-top: 2px;
  flex-shrink: 0;
}

.side-att-texts {
  flex: 1;
  min-width: 0;
}

.side-att-link {
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
  color: #18181b;
  font-size: 12px;
  font-weight: 600;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-att-desc {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--lm-text-secondary);
}

.side-att-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 4px;
  border-top: 1px dashed var(--lm-border-light);
}

.side-att-size {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-family: var(--lm-code-font-family);
}

.side-att-dl-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--lm-radius-sm);
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: #27272a;
  font-size: 11px;
  font-weight: 600;
  text-decoration: none;
  transition: all var(--lm-transition);
}
.side-att-dl-btn:hover {
  border-color: #18181b;
  background: #18181b;
  color: #ffffff;
}

.side-empty-attachment-notice {
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

/* 右栏：题目详细与行动区 (Sticky 吸顶) */
.detail-side-col {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 88px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 310px;
  padding-right: 2px;
}

.detail-side-col::-webkit-scrollbar {
  width: 4px;
}
.detail-side-col::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 2px;
}
.detail-side-col:hover::-webkit-scrollbar-thumb {
  background: #e4e4e7;
}

.side-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 18px 20px;
}

.side-card-title {
  font-size: 13px;
  font-weight: 700;
  color: #27272a;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-inline-icon {
  font-size: 14px;
  color: var(--lm-text-muted);
}

/* 行动卡片 */
.action-hub-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.primary-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 40px;
  border-radius: var(--lm-radius-sm);
  border: 1px solid #18181b;
  background: #18181b;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.primary-action-btn:hover {
  background: #27272a;
  border-color: #27272a;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
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
  border-radius: var(--lm-radius-sm);
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: #3f3f46;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.secondary-action-btn:hover {
  border-color: #18181b;
  color: #18181b;
  background: #f4f4f5;
}

/* 详细档案卡片 */
.score-highlight-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 14px;
  margin-bottom: 16px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  border-radius: var(--lm-radius-sm);
}

.score-num {
  font-size: 26px;
  font-weight: 800;
  color: #18181b;
  font-family: var(--lm-code-font-family);
}

.score-label {
  margin-top: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-text-secondary);
}

.specs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.spec-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.spec-label {
  color: var(--lm-text-muted);
  flex-shrink: 0;
}

.spec-value {
  color: #27272a;
  font-weight: 500;
  text-align: right;
}

.spec-value.text-strong {
  font-weight: 600;
  color: #18181b;
}

.spec-value.text-small {
  font-size: 11px;
}

.difficulty-capsule {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 600;
}
.diff-1 { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
.diff-2 { background: #fffbeb; color: #b45309; border: 1px solid #fde68a; }
.diff-3 { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }

/* 题型标签卡片 */
.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag-chip {
  padding: 3px 9px;
  border-radius: 9999px;
  background: #f4f4f5;
  border: 1px solid #e4e4e7;
  color: #52525b;
  font-size: 11px;
  font-weight: 600;
}

@media (max-width: 1100px) {
  .problem-detail-grid {
    grid-template-columns: 1fr;
  }
  .detail-side-col {
    position: static;
    width: 100%;
  }
}
</style>
