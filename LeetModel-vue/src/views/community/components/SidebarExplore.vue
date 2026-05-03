<template>
  <div class="sidebar-container">
    <div class="search-box">
      <el-icon class="search-icon"><Search /></el-icon>
      <input type="text" placeholder="搜索话题..." class="search-input" v-model="searchText" />
    </div>

    <div class="explore-section">
      <h2 class="section-title">探索</h2>

      <!-- Categories -->
      <div class="category" v-for="cat in categories" :key="cat.name">
        <div class="category-header" @click="cat.expanded = !cat.expanded">
          <span class="category-tag">{{ cat.name }}</span>
          <div class="category-avatars">
            <div class="avatar-dot" v-for="i in 3" :key="i" :style="{ background: cat.avatarColors[i-1] }"></div>
          </div>
        </div>
        <div class="category-posts" v-show="cat.expanded">
          <div class="post-item" v-for="post in cat.posts" :key="post.id" @click="$router.push(`/post/${post.id}`)">
            <span class="post-prefix">{{ post.prefix }}</span>
            <span class="post-title">{{ post.title }}</span>
          </div>
        </div>
      </div>

      <!-- Hot Topics -->
      <div class="hot-topics">
        <h3 class="hot-title">热门标签</h3>
        <div class="topic-cloud">
          <span class="topic-tag" v-for="t in hotTopics" :key="t.name" :style="{ fontSize: t.size + 'px' }">
            {{ t.name }}
          </span>
        </div>
      </div>

      <!-- Active Users -->
      <div class="active-users">
        <h3 class="hot-title">活跃用户</h3>
        <div class="user-list">
          <div class="user-item" v-for="u in activeUsers" :key="u.name">
            <div class="user-dot" :style="{ background: u.color }">{{ u.name.charAt(0) }}</div>
            <span class="user-name">{{ u.name }}</span>
            <span class="user-posts">{{ u.posts }} 帖</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { mockPosts, mockUsers } from '@/mock/data.js'

const searchText = ref('')

const categories = ref([
  {
    name: '#经验分享', expanded: true,
    avatarColors: ['#2563eb', '#16a34a', '#d97706'],
    posts: mockPosts.filter(p => p.type === 'experience').slice(0, 3).map(p => ({ id: p.postId, prefix: '分享 |', title: p.title }))
  },
  {
    name: '#技巧教程', expanded: true,
    avatarColors: ['#8b5cf6', '#0891b2', '#dc2626'],
    posts: mockPosts.filter(p => p.type === 'skill').slice(0, 2).map(p => ({ id: p.postId, prefix: '教程 |', title: p.title }))
  },
  {
    name: '#讨论交流', expanded: false,
    avatarColors: ['#d97706', '#16a34a', '#2563eb'],
    posts: mockPosts.filter(p => p.type === 'discuss').slice(0, 3).map(p => ({ id: p.postId, prefix: '讨论 |', title: p.title }))
  },
])

const hotTopics = ref([
  { name: '数学建模', size: 18 },
  { name: 'Python', size: 16 },
  { name: '深度学习', size: 15 },
  { name: '时间序列', size: 14 },
  { name: '优化算法', size: 13 },
  { name: '国赛', size: 16 },
  { name: '美赛', size: 15 },
  { name: '神经网络', size: 14 },
  { name: '数据可视化', size: 12 },
  { name: '论文写作', size: 13 },
  { name: 'MATLAB', size: 12 },
  { name: '机器学习', size: 14 },
])

const activeUsers = ref(
  mockUsers.slice(0, 5).map((u, i) => ({
    name: u.username,
    color: ['#2563eb', '#16a34a', '#d97706', '#8b5cf6', '#0891b2'][i],
    posts: [45, 38, 32, 28, 25][i]
  }))
)
</script>

<style scoped>
.sidebar-container {
  width: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  background: var(--lm-surface);
  border-radius: var(--lm-radius-lg);
  padding: 16px;
  border: 1px solid var(--lm-border);
}

.search-box {
  display: flex; align-items: center;
  background: var(--lm-bg);
  border-radius: 20px; padding: 10px 16px; margin-bottom: 16px;
}

.search-icon { margin-right: 8px; color: var(--lm-text-muted); }

.search-input {
  border: none; background: transparent; outline: none;
  font-size: 14px; color: var(--lm-text-primary); width: 100%;
}

.search-input::placeholder { color: var(--lm-text-muted); }

.explore-section {
  border: 1px solid var(--lm-border-light); border-radius: var(--lm-radius);
  padding: 16px;
}

.section-title {
  font-size: 22px; font-weight: 700; margin: 0 0 16px; color: var(--lm-text-primary);
}

.category { margin-bottom: 14px; }

.category-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px; cursor: pointer; padding: 4px 0;
}

.category-tag {
  font-size: 14px; color: var(--lm-text-secondary); font-weight: 600;
}

.avatar-dot {
  width: 28px; height: 28px; border-radius: 50%;
  margin-left: -8px; border: 2px solid #fff;
  display: inline-block;
}

.category-posts { padding-left: 4px; }

.post-item {
  font-size: 14px; color: var(--lm-text-primary); margin-bottom: 8px;
  line-height: 1.5; cursor: pointer; padding: 4px 0;
}

.post-item:hover { color: var(--lm-primary); }

.post-prefix { color: var(--lm-text-secondary); font-weight: 500; }

.hot-topics, .active-users { margin-top: 20px; }

.hot-title {
  font-size: 14px; font-weight: 600; color: var(--lm-text-primary);
  margin: 0 0 10px; padding-top: 12px; border-top: 1px solid var(--lm-border-light);
}

.topic-cloud { display: flex; flex-wrap: wrap; gap: 8px; }

.topic-tag {
  color: var(--lm-primary); font-weight: 500; cursor: pointer;
  padding: 2px 4px;
}

.topic-tag:hover { text-decoration: underline; }

.user-list { display: flex; flex-direction: column; gap: 8px; }

.user-item { display: flex; align-items: center; gap: 10px; }

.user-dot {
  width: 28px; height: 28px; border-radius: 50%;
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}

.user-name { font-size: 13px; color: var(--lm-text-primary); flex: 1; font-weight: 500; }
.user-posts { font-size: 12px; color: var(--lm-text-muted); }
</style>
