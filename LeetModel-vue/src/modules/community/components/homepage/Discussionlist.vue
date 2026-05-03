<template>
  <div class="discussion-page">
    <!-- Filter Bar -->
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索帖子..."
        :prefix-icon="Search"
        clearable
        class="filter-input"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.type" placeholder="全部类型" class="filter-select" @change="handleSearch">
        <el-option label="全部类型" value="" />
        <el-option label="经验分享" value="experience" />
        <el-option label="技巧教程" value="skill" />
        <el-option label="讨论交流" value="discuss" />
      </el-select>
      <el-select v-model="query.sortField" placeholder="排序" class="filter-select" @change="handleSearch">
        <el-option label="最新发布" value="createTime" />
        <el-option label="点赞最多" value="likeCnt" />
        <el-option label="浏览最多" value="viewCnt" />
        <el-option label="评论最多" value="commentCnt" />
        <el-option label="最热" value="heat" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <!-- Post Cards -->
    <div v-if="pagedList.length > 0" class="post-cards">
      <div
        class="post-card card-hover"
        v-for="post in pagedList"
        :key="post.postId"
        @click="$router.push(`/post/${post.postId}`)"
      >
        <div class="post-main">
          <div class="post-avatar" :style="{ background: avatarColor(post.publisherName) }">
            {{ post.publisherName.charAt(0) }}
          </div>
          <div class="post-body">
            <div class="post-header">
              <span class="post-author">{{ post.publisherName }}</span>
              <span v-if="post.isTop" class="pin-badge">置顶</span>
              <el-tag size="small" type="info">{{ typeLabel(post.type) }}</el-tag>
              <span class="post-time">{{ post.createTime }}</span>
            </div>
            <h3 class="post-title">{{ post.title }}</h3>
            <p class="post-excerpt">{{ excerpt(post.content) }}</p>
            <div class="post-tags" v-if="post.tags">
              <span class="post-tag" v-for="tag in post.tags" :key="tag">{{ tag }}</span>
            </div>
            <div class="post-stats">
              <span><el-icon :size="14"><StarFilled /></el-icon> {{ post.likeCnt }}</span>
              <span><el-icon :size="14"><ChatLineSquare /></el-icon> {{ post.commentCnt }}</span>
              <span><el-icon :size="14"><View /></el-icon> {{ post.viewCnt }}</span>
              <span class="heat"><el-icon :size="14"><TrendCharts /></el-icon> {{ post.heat }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-empty description="暂无帖子" />
    </div>

    <!-- Pagination -->
    <div class="pagination-wrap" v-if="filteredList.length > 0">
      <el-pagination
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="filteredList.length"
        background
        layout="prev, pager, next, total"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Refresh, StarFilled, ChatLineSquare, View, TrendCharts } from '@element-plus/icons-vue'
import { mockPosts } from '@/mock/data.js'

const allPosts = ref([])
const query = ref({ keyword: '', type: '', sortField: 'createTime', pageNum: 1, pageSize: 5 })

onMounted(() => {
  allPosts.value = [...mockPosts]
})

const filteredList = computed(() => {
  let list = [...allPosts.value]
  if (query.value.keyword) {
    const kw = query.value.keyword.toLowerCase()
    list = list.filter(p => p.title.toLowerCase().includes(kw) || p.content.toLowerCase().includes(kw))
  }
  if (query.value.type) {
    list = list.filter(p => p.type === query.value.type)
  }
  const sortMap = { createTime: 'createTime', likeCnt: 'likeCnt', viewCnt: 'viewCnt', commentCnt: 'commentCnt', heat: 'heat' }
  const field = sortMap[query.value.sortField] || 'createTime'
  list.sort((a, b) => {
    if (field === 'createTime') return new Date(b[field]) - new Date(a[field])
    return b[field] - a[field]
  })
  return list
})

const pagedList = computed(() => {
  const start = (query.value.pageNum - 1) * query.value.pageSize
  return filteredList.value.slice(start, start + query.value.pageSize)
})

function handleSearch() { query.value.pageNum = 1 }
function handleReset() { query.value = { keyword: '', type: '', sortField: 'createTime', pageNum: 1, pageSize: 5 } }
function handlePageChange() { /* pagedList reacts automatically */ }

function typeLabel(t) {
  const map = { experience: '经验分享', skill: '技巧教程', discuss: '讨论交流' }
  return map[t] || t
}

function excerpt(content) {
  return content.replace(/[#>*`\n]/g, ' ').replace(/\s+/g, ' ').slice(0, 120) + '...'
}

function avatarColor(name) {
  const colors = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#8b5cf6', '#0891b2', '#e11d48', '#7c3aed']
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
}
</script>

<style scoped>
.discussion-page {
  max-width: 100%;
}

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}

.filter-input { width: 240px; }
.filter-select { width: 140px; }

.post-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 18px 20px;
  cursor: pointer;
}

.post-main { display: flex; gap: 14px; }

.post-avatar {
  width: 40px; height: 40px;
  border-radius: 50%;
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 600;
  flex-shrink: 0;
}

.post-body { flex: 1; min-width: 0; }

.post-header {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 6px;
}

.post-author { font-size: 13px; font-weight: 600; color: var(--lm-text-primary); }

.pin-badge {
  font-size: 10px; font-weight: 600; color: var(--lm-danger);
  background: var(--lm-danger-bg); padding: 1px 6px; border-radius: 3px;
}

.post-time { font-size: 12px; color: var(--lm-text-muted); margin-left: auto; }

.post-title {
  font-size: 16px; font-weight: 600; color: var(--lm-text-primary);
  margin: 0 0 6px; line-height: 1.4;
}

.post-excerpt {
  font-size: 13px; color: var(--lm-text-secondary); margin: 0 0 8px;
  line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}

.post-tags { display: flex; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }

.post-tag {
  font-size: 11px; color: var(--lm-primary); background: var(--lm-primary-bg);
  padding: 2px 8px; border-radius: 4px;
}

.post-stats {
  display: flex; gap: 16px; font-size: 12px; color: var(--lm-text-muted);
}

.post-stats span { display: inline-flex; align-items: center; gap: 3px; }
.heat { color: var(--lm-warning); }

.pagination-wrap { margin-top: 24px; display: flex; justify-content: center; }
</style>
