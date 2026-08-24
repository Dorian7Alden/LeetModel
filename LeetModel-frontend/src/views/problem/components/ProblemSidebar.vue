<template>
  <div class="problem-sidebar" :class="{ collapsed: isCollapsed }">
    <div class="sidebar-header">
      <span v-if="!isCollapsed" class="header-title">题库导航</span>
      <button class="collapse-btn" @click="toggleCollapse">
        <el-icon :size="16"><component :is="isCollapsed ? 'DArrowRight' : 'DArrowLeft'" /></el-icon>
      </button>
    </div>

    <div class="menu-list">
      <template v-for="item in menuList" :key="item.key">
        <div
          class="menu-item"
          :class="{ active: activeKey === item.key }"
          @click="handleMenuClick(item)"
        >
          <span class="menu-icon">
            <el-icon :size="18"><component :is="iconMap[item.key]" /></el-icon>
          </span>
          <span v-if="!isCollapsed" class="menu-label">{{ item.label }}</span>
          <span v-if="item.children && !isCollapsed" class="arrow" :class="{ open: item.open }">
            <el-icon :size="12"><ArrowDown /></el-icon>
          </span>
        </div>

        <div v-if="item.children && item.open && !isCollapsed" class="submenu-list">
          <div
            v-for="sub in item.children" :key="sub.key"
            class="submenu-item"
            :class="{ active: activeKey === sub.key }"
            @click="handleMenuClick(sub)"
          >{{ sub.label }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { List, DArrowLeft, DArrowRight, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapsed = ref(false)
const activeKey = ref('all')

const iconMap = { all: List }

const menuList = ref([
  { key: 'all', label: '全部题目', path: '/problem/problemListPage' },
])

const toggleCollapse = () => { isCollapsed.value = !isCollapsed.value }

const handleMenuClick = (item) => {
  if (item.children) { item.open = !item.open; return }
  activeKey.value = item.key
  if (item.path) router.push(item.path)
}

watch(() => route.path, (path) => {
  menuList.value.forEach(item => {
    if (item.path === path) activeKey.value = item.key
    if (item.children) {
      item.children.forEach(sub => {
        if (sub.path === path) { activeKey.value = sub.key; item.open = true }
      })
    }
  })
}, { immediate: true })
</script>

<style scoped>
.problem-sidebar {
  width: 220px;
  min-height: calc(100vh - 64px);
  background: var(--lm-surface);
  border-right: 1px solid var(--lm-border);
  padding: 16px 0;
  transition: width 0.25s ease;
  position: sticky; top: 64px;
  flex-shrink: 0;
}

.problem-sidebar.collapsed { width: 56px; }

.sidebar-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 14px 14px;
}

.header-title {
  font-size: 15px; font-weight: 700; color: var(--lm-text-primary);
}

.collapse-btn {
  border: none; background: var(--lm-bg-secondary);
  border-radius: var(--lm-radius-sm); cursor: pointer;
  padding: 4px; display: flex; align-items: center;
  color: var(--lm-text-secondary);
  transition: background var(--lm-transition);
}

.collapse-btn:hover { background: var(--lm-border); }

.menu-list { padding: 0 6px; }

.menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  border-radius: var(--lm-radius-sm);
  cursor: pointer;
  transition: all var(--lm-transition);
  margin-bottom: 2px;
  color: var(--lm-text-secondary);
}

.menu-item:hover { background: var(--lm-bg); color: var(--lm-text-primary); }

.menu-item.active {
  background: var(--lm-primary-bg); color: var(--lm-primary); font-weight: 600;
}

.menu-label { font-size: 14px; flex: 1; }

.arrow { transition: transform var(--lm-transition); }
.arrow.open { transform: rotate(180deg); }

.submenu-list { padding-left: 16px; }

.submenu-item {
  padding: 9px 12px; border-radius: var(--lm-radius-sm);
  cursor: pointer; font-size: 13px; color: var(--lm-text-secondary);
  margin-bottom: 2px;
  transition: all var(--lm-transition);
}

.submenu-item:hover { background: var(--lm-bg); color: var(--lm-text-primary); }
.submenu-item.active { background: var(--lm-primary-bg); color: var(--lm-primary); font-weight: 600; }

@media (max-width: 768px) {
  .problem-sidebar { display: none; }
}
</style>
