<template>
  <div class="problem-detail" v-loading="loading">
    <template v-if="problem">
      <div class="detail-header">
        <div class="detail-top">
          <h1 class="detail-title">{{ problem.title }}</h1>
          <div class="detail-badges">
            <el-tag :type="difficultyType(problem.difficulty)" effect="plain">{{ difficultyLabel(problem.difficulty) }}</el-tag>
            <el-tag type="info" effect="plain">{{ contestTypeLabel(problem.contestType) }}</el-tag>
          </div>
        </div>
        <div class="detail-meta">
          <span>平均分：{{ problem.averageScore ?? 0 }}</span>
          <span>年份：{{ problem.year }}</span>
          <span>题面：{{ problem.statementLanguage === 'EN' ? '英文' : '中文' }}</span>
          <span>完成时长：{{ formatDuration(problem.durationMinutes) }}</span>
          <span>更新时间：{{ formatTime(problem.updateTime) }}</span>
        </div>
      </div>

      <div v-if="problem.tagNames?.length" class="detail-tags">
        <span>标签：</span><el-tag v-for="tag in problem.tagNames" :key="tag" size="small">{{ tag }}</el-tag>
      </div>

      <el-card shadow="never">
        <h3>题目内容</h3>
        <el-alert v-if="problem.contentFileId" type="info" :closable="false" show-icon title="题面文件读取能力尚未由后端提供" :description="`题面文件 ID：${problem.contentFileId}`" />
        <el-empty v-else description="暂未上传题面文件" />
      </el-card>

      <el-card v-if="problem.links?.length" class="resource-card" shadow="never">
        <h3>相关资料</h3>
        <div class="resource-list">
          <a v-for="link in problem.links" :key="link.id || link.url" :href="link.url" target="_blank" rel="noopener noreferrer">{{ link.title }}</a>
        </div>
      </el-card>

      <div class="detail-actions">
        <el-button type="primary" size="large" @click="createProblemTeam">围绕此题组队</el-button>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="题目不存在或尚未发布" />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicProblemDetail } from '@/api/problem'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const problem = ref(null)
const difficultyLabel = (value) => ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知'
const contestTypeLabel = (value) => ({ MCM_ICM: '美赛', CUMCM: '国赛' })[value] || value || '未分类'
const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' })[value] || 'info'
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN') : '-'
const formatDuration = (minutes) => minutes ? `${Math.floor(minutes / 60)} 小时${minutes % 60 ? ` ${minutes % 60} 分钟` : ''}` : '-'
const createProblemTeam = () => router.push({ path: '/team', query: { problemId: problem.value.id } })

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
.problem-detail { max-width: 900px; min-height: 420px; }
.detail-header { padding: 24px; margin-bottom: 20px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius-lg); }
.detail-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.detail-title { margin: 0; font-size: 24px; color: var(--lm-text-primary); }
.detail-badges, .detail-tags, .detail-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.detail-meta { gap: 24px; margin-top: 18px; font-size: 13px; color: var(--lm-text-secondary); }
.detail-tags { margin-bottom: 16px; }
h3 { margin-top: 0; }
.resource-card { margin-top: 16px; }
.resource-list { display: flex; flex-direction: column; gap: 8px; }
.detail-actions { margin-top: 24px; }
</style>
