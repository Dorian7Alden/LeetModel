<template>
  <div class="popular-label">
    <div class="label-header">
      <el-icon :size="18"><TrendCharts /></el-icon>
      <span>热门标签</span>
    </div>

    <el-input
      v-model="searchText"
      placeholder="搜索标签..."
      :prefix-icon="Search"
      size="small"
      clearable
    />

    <div class="tags-container">
      <span
        class="tag-badge"
        v-for="tag in filteredTags"
        :key="tag.tagId"
        :style="{ fontSize: tagSize(tag.usageCount) + 'px' }"
        @click="$router.push('/problem/problemListPage')"
      >
        {{ tag.name }}
      </span>
    </div>

    <div class="label-footer">
      <span class="total-label">{{ mockTags.length }} 个标签</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { TrendCharts, Search } from '@element-plus/icons-vue'
import { mockTags } from '@/mock/data.js'

const searchText = ref('')

const filteredTags = computed(() => {
  if (!searchText.value) return [...mockTags].sort((a, b) => b.usageCount - a.usageCount).slice(0, 20)
  const kw = searchText.value.toLowerCase()
  return mockTags.filter(t => t.name.toLowerCase().includes(kw)).sort((a, b) => b.usageCount - a.usageCount)
})

function tagSize(count) {
  if (count > 300) return 15
  if (count > 200) return 14
  if (count > 100) return 13
  return 12
}
</script>

<style scoped>
.popular-label {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 16px;
}

.label-header {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 700;
  color: var(--lm-text-primary);
  margin-bottom: 12px;
}

.tags-container {
  display: flex; flex-wrap: wrap; gap: 8px;
  margin-top: 12px;
}

.tag-badge {
  padding: 4px 10px;
  background: var(--lm-bg-secondary);
  border-radius: 16px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  font-weight: 500;
  transition: all var(--lm-transition);
}

.tag-badge:hover {
  background: var(--lm-primary-bg);
  color: var(--lm-primary);
}

.label-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--lm-border-light);
}

.total-label {
  font-size: 12px; color: var(--lm-text-muted);
}
</style>
