<template>
  <div class="problem-detail" v-loading="loading">
    <template v-if="problem">
      <div class="detail-header">
        <button class="back-button" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>返回题库
        </button>
        <div class="detail-top">
          <h1 class="detail-title">{{ problem.title }}</h1>
          <div class="detail-badges">
            <el-tag :type="difficultyType(problem.difficulty)" effect="plain">{{ difficultyLabel(problem.difficulty) }}</el-tag>
            <el-tag type="info" effect="plain">{{ problem.contestName || '未分类' }}</el-tag>
          </div>
        </div>
        <div class="detail-footer-row">
          <div class="detail-meta">
            <span>题号：{{ problem.code }}</span>
            <span>平均分：{{ problem.averageScore ?? 0 }}</span>
            <span>年份：{{ problem.year }}</span>
            <span>题面：{{ problem.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
            <span>完成时长：{{ formatDuration(problem.durationMinutes) }}</span>
            <span>更新时间：{{ formatTime(problem.updateTime) }}</span>
          </div>
          <div class="detail-actions">
            <el-button @click="findProblemTeams">寻找队伍</el-button>
            <el-button type="primary" @click="createProblemTeam">创建队伍</el-button>
          </div>
        </div>
      </div>

      <div v-if="problem.tagNames?.length" class="detail-tags">
        <span>标签：</span><el-tag v-for="tag in problem.tagNames" :key="tag" size="small">{{ tag }}</el-tag>
      </div>

      <el-card class="content-card" shadow="never">
        <article v-if="problem.contentMarkdown" class="markdown-body" v-html="renderedMarkdown" />
        <el-empty v-else description="暂无题面描述" />
      </el-card>

      <el-card v-if="problem.attachments?.length" class="resource-card" shadow="never">
        <div class="resource-list">
          <div v-for="attachment in problem.attachments" :key="attachment.id" class="attachment-item">
            <div>
              <a v-if="attachment.downloadUrl" :href="attachment.downloadUrl" target="_blank" rel="noopener noreferrer">{{ attachment.fileName }}</a>
              <span v-else>{{ attachment.fileName }}</span>
              <p v-if="attachment.description">{{ attachment.description }}</p>
            </div>
            <span class="attachment-size">{{ formatFileSize(attachment.fileSize) }}</span>
          </div>
        </div>
      </el-card>

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
import { ArrowLeft } from '@element-plus/icons-vue'
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
const goBack = () => router.push('/problem/problemListPage')

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
.problem-detail { width: 100%; max-width: 960px; min-height: 420px; margin: 0 auto; }
.detail-header { padding: 24px; margin-bottom: 20px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius-lg); }
.back-button { display: inline-flex; align-items: center; gap: 5px; margin: 0 0 18px; padding: 0; border: 0; background: transparent; color: var(--lm-text-secondary); font: inherit; font-size: 13px; cursor: pointer; }
.back-button:hover { color: var(--lm-primary); }
.detail-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.detail-title { margin: 0; font-size: 24px; color: var(--lm-text-primary); }
.detail-badges, .detail-tags, .detail-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.detail-footer-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-top: 18px; }
.detail-actions { display: flex; flex-shrink: 0; gap: 8px; }
.detail-actions .el-button { margin-left: 0; }
.detail-meta { flex: 1; gap: 24px; font-size: 13px; color: var(--lm-text-secondary); }
.detail-tags { margin-bottom: 16px; padding: 12px 16px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius-lg); }
.content-card { border-radius: var(--lm-radius-lg); }
.content-card :deep(.el-card__body), .resource-card :deep(.el-card__body) { padding: 0; }
.resource-card { margin-top: 16px; }
.resource-list { display: flex; flex-direction: column; gap: 8px; padding: 18px 24px; }
.attachment-item { display: flex; justify-content: space-between; gap: 16px; padding: 10px 0; border-bottom: 1px solid var(--lm-border); }
.attachment-item:last-child { border-bottom: 0; }
.attachment-item p { margin: 4px 0 0; color: var(--lm-text-secondary); font-size: 13px; }
.attachment-size { flex-shrink: 0; color: var(--lm-text-muted); font-size: 12px; }
.markdown-body { padding: 24px 32px 32px; background: transparent; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans CJK SC", "Microsoft YaHei", Arial, sans-serif; font-synthesis: weight; overflow-wrap: anywhere; }
.markdown-body :deep(strong), .markdown-body :deep(b) { font-weight: 800; }
@media (max-width: 768px) { .markdown-body { padding: 20px; } .detail-header { padding: 18px; } .detail-top { align-items: flex-start; flex-direction: column; } .detail-footer-row { align-items: flex-end; flex-wrap: wrap; } }
</style>
