<template>
  <div class="post-detail-page" v-if="post">
    <div class="detail-container">
      <!-- Author header -->
      <div class="author-header">
        <div class="author-avatar" :style="{ background: avatarColor }">{{ post.publisherName.charAt(0) }}</div>
        <div class="author-info">
          <span class="author-name">{{ post.publisherName }}</span>
          <span class="post-meta">
            <el-tag size="small" :type="typeColor">{{ typeLabel }}</el-tag>
            <span>{{ post.createTime }}</span>
            <span>{{ post.viewCnt }} 次阅读</span>
          </span>
        </div>
      </div>

      <!-- Title -->
      <h1 class="post-title">{{ post.title }}</h1>

      <!-- Tags -->
      <div class="post-tags" v-if="post.tags">
        <span class="post-tag" v-for="tag in post.tags" :key="tag">{{ tag }}</span>
      </div>

      <!-- Content -->
      <div class="post-content" v-html="renderedContent"></div>

      <!-- Action bar -->
      <div class="action-bar">
        <el-button :icon="StarFilled" :type="liked ? 'warning' : 'default'" @click="liked = !liked" round>
          {{ liked ? '已赞' : '点赞' }} {{ likeCount }}
        </el-button>
        <el-button :icon="ChatLineSquare" round>评论 {{ post.commentCnt }}</el-button>
        <el-button :icon="Share" round>分享</el-button>
        <el-button :icon="Collection" round>收藏</el-button>
      </div>

      <el-divider />

      <!-- Comments -->
      <div class="comments-section">
        <h3 class="comments-title">评论 ({{ comments.length }})</h3>

        <div class="comment-input">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            placeholder="写下你的评论..."
            maxlength="500"
            show-word-limit
          />
          <el-button type="primary" class="submit-btn" @click="addComment" :disabled="!commentText.trim()">
            发表评论
          </el-button>
        </div>

        <div class="comment-list" v-if="topLevelComments.length > 0">
          <div class="comment-item" v-for="comment in topLevelComments" :key="comment.commentId">
            <div class="comment-avatar" :style="{ background: commentAvatarColor(comment.userName) }">
              {{ comment.userName.charAt(0) }}
            </div>
            <div class="comment-body">
              <div class="comment-header">
                <span class="comment-author">{{ comment.userName }}</span>
                <span class="comment-time">{{ comment.createTime }}</span>
              </div>
              <p class="comment-content">{{ comment.content }}</p>
              <div class="comment-actions">
                <span class="comment-like"><el-icon :size="14"><StarFilled /></el-icon> {{ comment.likeCnt }}</span>
                <span class="comment-reply" @click="toggleReply(comment.commentId)">回复</span>
              </div>

              <!-- Replies -->
              <div class="replies" v-if="getReplies(comment.commentId).length > 0">
                <div class="reply-item" v-for="reply in getReplies(comment.commentId)" :key="reply.commentId">
                  <div class="reply-avatar" :style="{ background: commentAvatarColor(reply.userName) }">
                    {{ reply.userName.charAt(0) }}
                  </div>
                  <div class="reply-body">
                    <span class="reply-author">{{ reply.userName }}</span>
                    <span v-if="reply.parentId !== comment.commentId" class="reply-to">回复 {{ getReplyTarget(reply) }}</span>
                    <p class="reply-content">{{ reply.content }}</p>
                    <span class="reply-time">{{ reply.createTime }}</span>
                  </div>
                </div>
              </div>

              <!-- Reply input -->
              <div v-if="replyingTo === comment.commentId" class="reply-input">
                <el-input v-model="replyText" placeholder="写下回复..." size="small" @keyup.enter="addReply(comment.commentId)" />
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-comments">
          <el-empty description="暂无评论，来说两句吧" :image-size="80" />
        </div>
      </div>
    </div>

    <!-- Related posts sidebar -->
    <div class="related-sidebar">
      <DataCard title="相关帖子">
        <div class="related-list">
          <div class="related-item" v-for="rp in relatedPosts" :key="rp.postId" @click="$router.push(`/post/${rp.postId}`)">
            <span class="related-title">{{ rp.title }}</span>
            <span class="related-stats">{{ rp.likeCnt }} 赞 · {{ rp.commentCnt }} 评论</span>
          </div>
        </div>
      </DataCard>
    </div>
  </div>
  <div v-else class="loading" v-loading="true" style="min-height: 400px"></div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { StarFilled, ChatLineSquare, Share, Collection } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DataCard from '@/components/common/DataCard.vue'
import { mockPosts, mockComments } from '@/mock/data.js'

const route = useRoute()
const post = ref(null)
const comments = ref([])
const commentText = ref('')
const replyText = ref('')
const replyingTo = ref(null)
const liked = ref(false)

const avatarColor = computed(() => {
  if (!post.value) return '#2563eb'
  const colors = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#8b5cf6', '#0891b2']
  let hash = 0
  const name = post.value.publisherName
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
})

const typeLabel = computed(() => {
  if (!post.value) return ''
  const map = { experience: '经验分享', skill: '技巧教程', discuss: '讨论交流' }
  return map[post.value.type] || post.value.type
})

const typeColor = computed(() => {
  if (!post.value) return 'info'
  const map = { experience: 'success', skill: '', discuss: 'warning' }
  return map[post.value.type] || 'info'
})

const likeCount = computed(() => {
  if (!post.value) return 0
  return liked.value ? post.value.likeCnt + 1 : post.value.likeCnt
})

const renderedContent = computed(() => {
  if (!post.value) return ''
  return marked(post.value.content)
})

const topLevelComments = computed(() => comments.value.filter(c => !c.parentId))

const relatedPosts = computed(() => mockPosts.filter(p => p.postId !== post.value?.postId).slice(0, 4))

function getReplies(parentId) {
  return comments.value.filter(c => c.parentId === parentId)
}

function getReplyTarget(reply) {
  const parent = comments.value.find(c => c.commentId === reply.parentId)
  return parent ? parent.userName : ''
}

function toggleReply(commentId) {
  replyingTo.value = replyingTo.value === commentId ? null : commentId
  replyText.value = ''
}

function addComment() {
  if (!commentText.value.trim()) return
  comments.value.push({
    commentId: Date.now(),
    postId: post.value.postId,
    userId: 0,
    userName: '当前用户',
    parentId: null,
    content: commentText.value,
    likeCnt: 0,
    status: 'normal',
    createTime: new Date().toISOString().split('T')[0],
  })
  commentText.value = ''
}

function addReply(parentId) {
  if (!replyText.value.trim()) return
  comments.value.push({
    commentId: Date.now() + 1,
    postId: post.value.postId,
    userId: 0,
    userName: '当前用户',
    parentId,
    content: replyText.value,
    likeCnt: 0,
    status: 'normal',
    createTime: new Date().toISOString().split('T')[0],
  })
  replyText.value = ''
  replyingTo.value = null
}

function commentAvatarColor(name) {
  const colors = ['#2563eb', '#16a34a', '#d97706', '#8b5cf6', '#0891b2', '#dc2626']
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
}

onMounted(() => {
  const id = Number(route.params.id)
  post.value = mockPosts.find(p => p.postId === id) || mockPosts[0]
  comments.value = mockComments.filter(c => c.postId === id)
})
</script>

<style scoped>
.post-detail-page {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  gap: 24px;
  padding: 24px 20px;
}

.detail-container {
  flex: 1;
  min-width: 0;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 32px;
}

.related-sidebar {
  width: 280px;
  flex-shrink: 0;
}

/* Author */
.author-header {
  display: flex; align-items: center; gap: 14px; margin-bottom: 20px;
}

.author-avatar {
  width: 48px; height: 48px; border-radius: 50%;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700;
}

.author-name { font-size: 16px; font-weight: 600; color: var(--lm-text-primary); display: block; }
.post-meta { display: flex; gap: 8px; align-items: center; font-size: 12px; color: var(--lm-text-muted); margin-top: 4px; }

/* Title */
.post-title {
  font-size: 26px; font-weight: 800; color: var(--lm-text-primary);
  margin: 0 0 12px; line-height: 1.4;
}

/* Tags */
.post-tags { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.post-tag {
  font-size: 12px; color: var(--lm-primary); background: var(--lm-primary-bg);
  padding: 4px 12px; border-radius: 16px; font-weight: 500;
}

/* Content */
.post-content {
  font-size: 15px; line-height: 1.8; color: var(--lm-text-primary);
  margin-bottom: 24px;
}

.post-content :deep(h2) { font-size: 20px; margin: 24px 0 12px; }
.post-content :deep(h3) { font-size: 17px; margin: 20px 0 10px; }
.post-content :deep(p) { margin: 0 0 12px; }
.post-content :deep(code) {
  background: var(--lm-bg-secondary); padding: 2px 6px; border-radius: 4px; font-size: 13px;
}
.post-content :deep(pre) {
  background: #1e293b; color: #e2e8f0; padding: 16px; border-radius: var(--lm-radius);
  overflow-x: auto; font-size: 13px; line-height: 1.6;
}

/* Action bar */
.action-bar { display: flex; gap: 8px; margin-bottom: 8px; }

/* Comments */
.comments-title { font-size: 18px; font-weight: 700; margin: 0 0 16px; }

.comment-input { margin-bottom: 24px; }
.submit-btn { margin-top: 10px; }

.comment-list { display: flex; flex-direction: column; gap: 0; }

.comment-item { display: flex; gap: 12px; padding: 16px 0; border-bottom: 1px solid var(--lm-border-light); }

.comment-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 600;
}

.comment-body { flex: 1; min-width: 0; }
.comment-header { display: flex; gap: 10px; align-items: center; margin-bottom: 4px; }
.comment-author { font-size: 13px; font-weight: 600; color: var(--lm-text-primary); }
.comment-time { font-size: 12px; color: var(--lm-text-muted); }
.comment-content { font-size: 14px; color: var(--lm-text-primary); margin: 0 0 8px; line-height: 1.6; }
.comment-actions { display: flex; gap: 16px; font-size: 12px; color: var(--lm-text-muted); }
.comment-like, .comment-reply { cursor: pointer; display: inline-flex; align-items: center; gap: 3px; }
.comment-reply:hover { color: var(--lm-primary); }

/* Replies */
.replies { margin-top: 10px; padding-left: 16px; border-left: 2px solid var(--lm-border-light); }
.reply-item { display: flex; gap: 10px; padding: 10px 0; }
.reply-avatar {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.reply-body { flex: 1; }
.reply-author { font-size: 13px; font-weight: 600; color: var(--lm-text-primary); }
.reply-to { font-size: 12px; color: var(--lm-text-muted); margin-left: 4px; }
.reply-content { font-size: 13px; color: var(--lm-text-primary); margin: 4px 0; line-height: 1.5; }
.reply-time { font-size: 11px; color: var(--lm-text-muted); }

.reply-input { margin-top: 8px; }

/* Related */
.related-list { display: flex; flex-direction: column; gap: 12px; }
.related-item { cursor: pointer; padding: 8px 0; border-bottom: 1px solid var(--lm-border-light); }
.related-item:last-child { border-bottom: none; }
.related-title { display: block; font-size: 13px; font-weight: 500; color: var(--lm-text-primary); margin-bottom: 4px; line-height: 1.4; }
.related-title:hover { color: var(--lm-primary); }
.related-stats { font-size: 11px; color: var(--lm-text-muted); }

@media (max-width: 900px) {
  .post-detail-page { flex-direction: column; }
  .related-sidebar { width: 100%; }
}
</style>
