<template>
  <div class="recent-activity">
    <div class="card-header">
      <h3>最近活动</h3>
      <router-link to="/profile/history" class="view-link">
        全部记录 <el-icon :size="12"><ArrowRight /></el-icon>
      </router-link>
    </div>

    <div class="timeline">
      <div class="timeline-item" v-for="(item, idx) in activities" :key="idx">
        <div class="timeline-dot" :style="{ background: dotColor(item.type) }">
          <el-icon :size="12" color="#fff">
            <component :is="dotIcon(item.type)" />
          </el-icon>
        </div>
        <div class="timeline-content">
          <p class="activity-text">
            <template v-if="item.type === 'submission'">
              提交了 <strong>{{ item.problemTitle }}</strong>
              <el-tag size="small" :type="item.score >= 90 ? 'success' : ''" style="margin-left: 6px">{{ item.score }}分</el-tag>
            </template>
            <template v-else-if="item.type === 'post'">
              发布了帖子 <strong>{{ item.title }}</strong>
            </template>
            <template v-else-if="item.type === 'badge'">
              获得了徽章 <strong>{{ item.badgeName }}</strong>
            </template>
          </p>
          <span class="activity-time">{{ timeAgo(item.time) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight, Check, ChatDotSquare, Medal } from '@element-plus/icons-vue'
import { mockUserProfile } from '@/mock/data.js'

const activities = mockUserProfile.recentActivity.slice(0, 5)

function dotColor(type) {
  const map = { submission: '#2563eb', post: '#16a34a', badge: '#d97706' }
  return map[type] || '#64748b'
}

function dotIcon(type) {
  const map = { submission: Check, post: ChatDotSquare, badge: Medal }
  return map[type] || Check
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
.recent-activity {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 20px;
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}

.card-header h3 { font-size: 16px; font-weight: 700; margin: 0; color: var(--lm-text-primary); }

.view-link {
  font-size: 12px; color: var(--lm-primary); display: flex; align-items: center; gap: 4px;
  text-decoration: none; font-weight: 500;
}

.timeline {
  display: flex; flex-direction: column; gap: 0;
}

.timeline-item {
  display: flex; gap: 14px; padding: 10px 0;
  border-left: 2px solid var(--lm-border-light);
  margin-left: 8px; padding-left: 16px;
  position: relative;
}

.timeline-item:last-child {
  border-left-color: transparent;
}

.timeline-dot {
  position: absolute; left: -9px; top: 12px;
  width: 18px; height: 18px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}

.timeline-content {
  flex: 1;
}

.activity-text {
  font-size: 13px; color: var(--lm-text-primary); margin: 0 0 4px; line-height: 1.5;
}

.activity-time {
  font-size: 11px; color: var(--lm-text-muted);
}
</style>
