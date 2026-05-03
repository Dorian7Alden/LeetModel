<template>
  <section class="community-section">
    <div class="section-header">
      <h2 class="section-title">
        <el-icon :size="20"><ChatDotSquare /></el-icon>
        热门讨论
      </h2>
      <router-link class="view-all" to="/community">
        查看全部 <el-icon :size="14"><ArrowRight /></el-icon>
      </router-link>
    </div>

    <div class="post-list" v-if="posts.length > 0">
      <div
        class="post-card"
        v-for="post in posts"
        :key="post.postId"
        @click="$router.push(`/post/${post.postId}`)"
      >
        <div class="post-main">
          <div class="post-author-avatar">
            {{ post.publisherName.charAt(0) }}
          </div>
          <div class="post-body">
            <div class="post-header">
              <span class="post-author">{{ post.publisherName }}</span>
              <span v-if="post.isTop" class="pinned-badge">置顶</span>
              <span class="post-type-tag">{{ typeLabel(post.type) }}</span>
            </div>
            <h4 class="post-title">{{ post.title }}</h4>
            <p class="post-excerpt">{{ excerpt(post.content) }}</p>
            <div class="post-footer">
              <span class="post-stat">
                <el-icon :size="14"><StarFilled /></el-icon>
                {{ post.likeCnt }}
              </span>
              <span class="post-stat">
                <el-icon :size="14"><ChatLineSquare /></el-icon>
                {{ post.commentCnt }}
              </span>
              <span class="post-stat">
                <el-icon :size="14"><View /></el-icon>
                {{ post.viewCnt }}
              </span>
              <span class="post-time">{{ timeAgo(post.createTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-empty description="暂无帖子" />
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ChatDotSquare, ArrowRight, StarFilled, ChatLineSquare, View } from '@element-plus/icons-vue'
import { mockPosts } from '@/mock/data.js'

const posts = ref([])

onMounted(() => {
  posts.value = [...mockPosts].sort((a, b) => b.heat - a.heat).slice(0, 5)
})

function typeLabel(type) {
  const map = { experience: '经验分享', skill: '技巧教程', discuss: '讨论交流' }
  return map[type] || type
}

function excerpt(content) {
  return content.replace(/[#>*`\n]/g, ' ').replace(/\s+/g, ' ').slice(0, 80) + '...'
}

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime()
  const days = Math.floor(diff / 86400000)
  if (days > 30) return Math.floor(days / 30) + '月前'
  if (days > 0) return days + '天前'
  const hours = Math.floor(diff / 3600000)
  if (hours > 0) return hours + '小时前'
  return '刚刚'
}
</script>

<style scoped>
.community-section {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.view-all {
  font-size: 13px;
  color: var(--lm-primary);
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.post-card {
  padding: 14px 16px;
  border-radius: var(--lm-radius);
  cursor: pointer;
  transition: background var(--lm-transition);
}

.post-card:hover {
  background: var(--lm-bg);
}

.post-main {
  display: flex;
  gap: 12px;
}

.post-author-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.post-body {
  flex: 1;
  min-width: 0;
}

.post-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.post-author {
  font-size: 13px;
  font-weight: 500;
  color: var(--lm-text-primary);
}

.pinned-badge {
  font-size: 10px;
  font-weight: 600;
  color: var(--lm-danger);
  background: var(--lm-danger-bg);
  padding: 1px 6px;
  border-radius: 3px;
}

.post-type-tag {
  font-size: 10px;
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}

.post-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin: 0 0 6px;
  line-height: 1.4;
}

.post-excerpt {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin: 0 0 8px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}

.post-stat {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--lm-text-muted);
}

.post-time {
  font-size: 12px;
  color: var(--lm-text-muted);
  margin-left: auto;
}
</style>
