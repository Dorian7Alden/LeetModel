<template>
  <aside
    class="problem-sidebar-bar"
    :class="{ 'is-collapsed': isCollapsed, 'is-resizing': isDragging }"
    :style="{ width: `${sidebarWidth}px` }"
  >
    <div class="sidebar-inner">
      <!-- 1. 题目 (默认真题大厅) -->
      <div
        class="sidebar-nav-item primary-item"
        :class="{ active: activeSection === 'all' && !isContestOrTypeRoute }"
        title="题目"
        @click="selectSection('all')"
      >
        <el-icon class="item-icon"><Document /></el-icon>
        <span class="item-text">题目</span>
      </div>

      <!-- 2. 赛事栏 (默认折叠，展开展示所有赛事，点击跳转具体赛事页面) -->
      <div class="sidebar-group" :class="{ 'group-open': groupExpanded.contest }">
        <div class="group-header" :class="{ active: isContestRoute }" @click="toggleGroup('contest')">
          <div class="header-left">
            <el-icon class="item-icon"><Trophy /></el-icon>
            <span class="header-title">赛事</span>
          </div>
          <el-icon class="arrow-toggle" :class="{ expanded: groupExpanded.contest }"><ArrowRight /></el-icon>
        </div>
        <transition name="collapse">
          <div v-show="groupExpanded.contest" class="group-sub-menu">
            <div
              v-for="contest in contests"
              :key="contest.id"
              class="sub-nav-item"
              :class="{ active: currentContestId === String(contest.id) }"
              @click="navigateToContest(contest.id)"
            >
              <span class="sub-dot"></span>
              <span class="sub-text" :title="contest.name">{{ contest.name }}</span>
            </div>
          </div>
        </transition>
      </div>

      <!-- 3. 题号导航栏：按赛事内 A-F / X 完整赛题聚合 -->
      <div class="sidebar-group" :class="{ 'group-open': groupExpanded.problemNumber }">
        <div class="group-header" :class="{ active: isNumberRoute }" @click="toggleGroup('problemNumber')">
          <div class="header-left">
            <el-icon class="item-icon"><CollectionTag /></el-icon>
            <span class="header-title">题号</span>
          </div>
          <el-icon class="arrow-toggle" :class="{ expanded: groupExpanded.problemNumber }"><ArrowRight /></el-icon>
        </div>
        <transition name="collapse">
          <div v-show="groupExpanded.problemNumber" class="group-sub-menu">
            <div
              v-for="number in problemNumbers"
              :key="number"
              class="sub-nav-item"
              :class="{ active: currentProblemNumber === number }"
              @click="navigateToNumber(number)"
            >
              <span class="sub-dot"></span>
              <span class="sub-text" :title="problemNumberLabel(number)">{{ problemNumberLabel(number) }}</span>
            </div>
          </div>
        </transition>
      </div>

      <div class="sidebar-divider"></div>

      <!-- 4. 关于用户的内容 (练习中、已完成) -->
      <div class="sidebar-user-group">
        <div
          class="sidebar-nav-item"
          :class="{ active: activeSection === 'in_progress' && !isContestOrTypeRoute }"
          @click="selectSection('in_progress')"
        >
          <el-icon class="item-icon text-warning"><Clock /></el-icon>
          <span class="item-text">练习中</span>
          <span v-if="activeTeamCount > 0" class="active-bubble">{{ activeTeamCount }}</span>
        </div>

        <div
          class="sidebar-nav-item"
          :class="{ active: activeSection === 'completed' && !isContestOrTypeRoute }"
          @click="selectSection('completed')"
        >
          <el-icon class="item-icon text-success"><CircleCheck /></el-icon>
          <span class="item-text">已完成</span>
        </div>
      </div>

      <!-- 5. 收藏栏 (用户点击收藏题目将会加入到这里面来) -->
      <div
        class="sidebar-nav-item fav-item"
        :class="{ active: activeSection === 'favorite' && !isContestOrTypeRoute }"
        title="收藏"
        @click="selectSection('favorite')"
      >
        <el-icon class="item-icon text-gold"><StarFilled /></el-icon>
        <span class="item-text">收藏</span>
        <span v-if="displayFavCount > 0" class="fav-badge"><span class="fav-badge-value">{{ displayFavCount }}</span></span>
      </div>

      <!-- <div class="sidebar-divider"></div> -->

    </div>

    <!-- 右侧垂直分割线与悬浮圆形折叠按钮 (悬浮显示小圆钮，一键折叠到最小/恢复最宽) -->
    <div
      class="sidebar-resizer"
      :class="{ 'is-resizing': isDragging }"
      @mousedown.prevent="startResize"
      @dblclick="resetWidth"
    >
      <div class="resizer-line"></div>

      <!-- 悬浮在边界上的圆形折叠/展开按钮 -->
      <button
        type="button"
        class="collapse-circle-btn"
        :title="isCollapsed ? '点击展开侧边栏' : '点击折叠到最小'"
        @click.stop="toggleCollapse"
      >
        <el-icon class="circle-icon">
          <ArrowRight v-if="isCollapsed" />
          <ArrowLeft v-else />
        </el-icon>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  CircleCheck,
  Clock,
  CollectionTag,
  Document,
  StarFilled,
  Trophy
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMyTeams } from '@/api/team'
import { getFavoriteCount } from '@/utils/favoriteStorage'

const props = defineProps({
  contests: { type: Array, default: () => [] },
  problemNumbers: { type: Array, default: () => ['A', 'B', 'C', 'D', 'E', 'F', 'X'] },
  activeSection: { type: String, default: 'all' },
  favCount: { type: Number, default: 0 }
})

const localFavCount = ref(getFavoriteCount())

const displayFavCount = computed(() => {
  if (typeof props.favCount === 'number' && props.favCount > 0) {
    return props.favCount
  }
  return localFavCount.value
})

const syncFavCount = (e) => {
  if (typeof e?.detail?.count === 'number') {
    localFavCount.value = e.detail.count
  } else {
    localFavCount.value = getFavoriteCount()
  }
}

watch(() => props.favCount, (val) => {
  if (typeof val === 'number') {
    localFavCount.value = val
  }
})

const emit = defineEmits(['select-section', 'collapse-change'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 侧边栏宽度与折叠状态控制
const DEFAULT_WIDTH = 240
const COLLAPSED_WIDTH = 0
const MIN_EXPANDED_WIDTH = 140
const COLLAPSE_TRIGGER_WIDTH = 70
const MAX_WIDTH = 280

const sidebarWidth = ref(DEFAULT_WIDTH)
const isCollapsed = ref(false)
const isDragging = ref(false)
const lastExpandedWidth = ref(DEFAULT_WIDTH)

const toggleCollapse = () => {
  if (isCollapsed.value) {
    isCollapsed.value = false
    sidebarWidth.value = lastExpandedWidth.value >= MIN_EXPANDED_WIDTH ? lastExpandedWidth.value : DEFAULT_WIDTH
  } else {
    lastExpandedWidth.value = sidebarWidth.value >= MIN_EXPANDED_WIDTH ? sidebarWidth.value : DEFAULT_WIDTH
    isCollapsed.value = true
    sidebarWidth.value = COLLAPSED_WIDTH
  }
  emit('collapse-change', isCollapsed.value)
}

const startResize = (e) => {
  if (isCollapsed.value) return
  isDragging.value = true
  const startX = e.clientX
  const startWidth = sidebarWidth.value
  const preDragWidth = startWidth >= MIN_EXPANDED_WIDTH ? startWidth : DEFAULT_WIDTH

  const onMouseMove = (moveEvent) => {
    const deltaX = moveEvent.clientX - startX
    const virtualWidth = startWidth + deltaX

    if (virtualWidth < COLLAPSE_TRIGGER_WIDTH) {
      // 达到第二临界值（仅剩图标宽）：彻底折叠归零，不展示残缺半截图标
      sidebarWidth.value = COLLAPSED_WIDTH
      lastExpandedWidth.value = preDragWidth
      if (!isCollapsed.value) {
        isCollapsed.value = true
        emit('collapse-change', true)
      }
    } else if (virtualWidth < MIN_EXPANDED_WIDTH) {
      // 处于第一临界值与第二临界值之间：宽度锁定在文字刚好不被截断的最小宽度，给用户物理阻尼暗示
      sidebarWidth.value = MIN_EXPANDED_WIDTH
      lastExpandedWidth.value = preDragWidth
      if (isCollapsed.value) {
        isCollapsed.value = false
        emit('collapse-change', false)
      }
    } else {
      // 大于第一临界值：正常跟随拖拽连续缩放
      const clampedWidth = Math.min(virtualWidth, MAX_WIDTH)
      sidebarWidth.value = clampedWidth
      lastExpandedWidth.value = clampedWidth
      if (isCollapsed.value) {
        isCollapsed.value = false
        emit('collapse-change', false)
      }
    }
  }

  const onMouseUp = () => {
    isDragging.value = false
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const resetWidth = () => {
  sidebarWidth.value = DEFAULT_WIDTH
  lastExpandedWidth.value = DEFAULT_WIDTH
  isCollapsed.value = false
  emit('collapse-change', false)
}

onUnmounted(() => {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})

// 赛事与题号默认均为折叠状态
const groupExpanded = reactive({
  contest: false,
  problemNumber: false
})

const activeTeamCount = ref(0)

const isContestRoute = computed(() => route.path.startsWith('/problem/contest'))
const currentContestId = computed(() => String(route.params.contestId || ''))
const isNumberRoute = computed(() => route.path.startsWith('/problem/number'))
const currentProblemNumber = computed(() => String(route.params.problemNumber || '').toUpperCase())
const isContestOrTypeRoute = computed(() => isContestRoute.value || isNumberRoute.value || route.path.startsWith('/problem/type'))

// 监听路由自动保持匹配分组展开
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/problem/contest')) {
      groupExpanded.contest = true
    } else if (path.startsWith('/problem/number')) {
      groupExpanded.problemNumber = true
    }
  },
  { immediate: true }
)

const toggleGroup = (key) => {
  groupExpanded[key] = !groupExpanded[key]
}

const selectSection = (sectionKey) => {
  if (sectionKey === 'in_progress' || sectionKey === 'completed') {
    if (!userStore.isLogin) {
      ElMessage.warning('请先登录以查看个人实训练习题目')
      return
    }
  }
  if (route.path !== '/problem' && route.path !== '/problem/problemListPage') {
    router.push({ path: '/problem', query: { section: sectionKey } })
  } else {
    emit('select-section', sectionKey)
  }
}

const navigateToContest = (contestId) => {
  router.push(`/problem/contest/${contestId}`)
}

const problemNumberLabel = (value) => `${value} 题`

const navigateToNumber = (number) => {
  router.push(`/problem/number/${number}`)
}

onMounted(async () => {
  localFavCount.value = getFavoriteCount()
  window.addEventListener('lm-fav-change', syncFavCount)
  window.addEventListener('storage', syncFavCount)
  // 仅在已登录态下才请求进行中的实训队伍数，避免未登录时 401 被拦截器重定向到 /login
  if (!userStore.isLogin) return
  try {
    const res = await getMyTeams({ practiceStatus: 'IN_PROGRESS', page: 1, pageSize: 5 })
    activeTeamCount.value = res.data?.total || 0
  } catch {
    // 静默兜底
  }
})

onUnmounted(() => {
  window.removeEventListener('lm-fav-change', syncFavCount)
  window.removeEventListener('storage', syncFavCount)
})
</script>

<style scoped>
.problem-sidebar-bar {
  position: sticky;
  top: 56px;
  height: calc(100vh - 56px);
  flex-shrink: 0;
  display: flex;
  background: #ffffff;
  user-select: none;
  z-index: 20;
  overflow: visible;
  transition: width 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}
.problem-sidebar-bar.is-resizing,
.problem-sidebar-bar.is-resizing * {
  transition: none !important;
}

.sidebar-inner {
  flex: 1;
  width: 100%;
  min-width: 0;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  background: #ffffff;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 16px 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  opacity: 1;
  transform: translateX(0);
  visibility: visible;
  transition: opacity 0.18s ease, transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 极简精致滚动条 */
.sidebar-inner::-webkit-scrollbar {
  width: 4px;
}
.sidebar-inner::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 2px;
}
.sidebar-inner:hover::-webkit-scrollbar-thumb {
  background: #cbd5e1;
}

/* 右侧拖拽与垂直分割细线 */
.sidebar-resizer {
  position: absolute;
  top: 0;
  bottom: 0;
  right: -6px;
  width: 13px;
  cursor: col-resize;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translateX(0);
  transition: transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}
.resizer-line {
  position: absolute;
  left: 6px;
  top: 0;
  bottom: 0;
  width: 0.5px;
  background: var(--lm-border);
  opacity: 1;
  transition: background var(--lm-transition), width 0.15s ease, opacity 0.18s ease;
}
.sidebar-resizer:hover .resizer-line,
.sidebar-resizer.is-resizing .resizer-line {
  background: var(--lm-primary);
  width: 4px;
  background-color: rgb(38, 150, 255);
}

/* 悬浮在边界上的圆形折叠按钮 (悬浮边界显示，点一下直接折叠/展开) */
.collapse-circle-btn {
  position: absolute;
  top: 110px;
  left: 50%;
  transform: translate(-50%, -50%) scale(0.9);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  color: var(--lm-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  z-index: 50;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.24s cubic-bezier(0.4, 0, 0.2, 1), color 0.15s ease, border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}
.sidebar-resizer:hover .collapse-circle-btn,
.collapse-circle-btn:hover {
  opacity: 1;
  pointer-events: auto;
  transform: translate(-50%, -50%) scale(1);
}
.collapse-circle-btn:hover {
  color: #ffffff;
  border-color: #18181b;
  background: #18181b;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  transform: translate(-50%, -50%) scale(1.12);
}
.circle-icon {
  font-size: 12px;
  font-weight: 700;
}

/* 完全折叠隐藏状态 (宽度为 0，彻底完全隐藏，不占水平空间) */
.problem-sidebar-bar.is-collapsed {
  width: 0 !important;
  min-width: 0 !important;
}
.problem-sidebar-bar.is-collapsed .sidebar-inner {
  opacity: 0;
  pointer-events: none;
  transform: translateX(-12px);
  visibility: hidden;
  overflow: hidden;
  transition: opacity 0.18s ease, transform 0.24s cubic-bezier(0.4, 0, 0.2, 1), visibility 0.24s;
}
.problem-sidebar-bar.is-collapsed .sidebar-resizer {
  transform: translateX(20px);
  cursor: default;
}
.problem-sidebar-bar.is-collapsed .resizer-line {
  opacity: 0;
  pointer-events: none;
}
.problem-sidebar-bar.is-collapsed .collapse-circle-btn {
  opacity: 0.9;
  pointer-events: auto;
  transform: translateY(-50%) scale(1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.problem-sidebar-bar.is-collapsed .collapse-circle-btn:hover {
  opacity: 1;
  color: #ffffff;
  background: #18181b;
  border-color: #18181b;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.22);
  transform: translateY(-50%) scale(1.12);
}

/* 一级菜单通用项 */
.sidebar-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.sidebar-nav-item:hover {
  color: #18181b;
  background: #f4f4f5;
}
.sidebar-nav-item.active {
  color: var(--lm-text-primary);
  background: #f5f5f5;
  font-weight: 600;
}
.item-icon {
  font-size: 16px;
  color: var(--lm-text-muted);
  transition: color var(--lm-transition);
}
.sidebar-nav-item:hover .item-icon,
.sidebar-nav-item.active .item-icon {
  color: var(--lm-text-primary);
}
.item-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 可折叠分组 (赛事 / 题型) */
.sidebar-group {
  display: flex;
  flex-direction: column;
}
.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.group-header:hover {
  color: var(--lm-text-primary);
  background: #f5f5f5;
}
.group-header.active {
  color: var(--lm-text-primary);
  font-weight: 600;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-left .item-icon {
  font-size: 16px;
}
.arrow-toggle {
  font-size: 12px;
  color: var(--lm-text-muted);
  transition: transform 0.2s ease, color var(--lm-transition);
}
.arrow-toggle.expanded {
  transform: rotate(90deg);
  color: var(--lm-primary);
}

/* 展开后的二级项 */
.group-sub-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 3px 0 4px 14px;
}
.sub-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--lm-transition);
}
.sub-nav-item:hover {
  color: var(--lm-text-primary);
  background: #f5f5f5;
}
.sub-nav-item.active {
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
  font-weight: 600;
}
.sub-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #cbd5e1;
  transition: background var(--lm-transition);
}
.sub-nav-item:hover .sub-dot,
.sub-nav-item.active .sub-dot {
  background: #18181b;
}
.sub-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 分割线 */
.sidebar-divider {
  height: 1px;
  background: var(--lm-border-light);
  margin: 4px 6px;
}

/* 用户实战分组 */
.sidebar-user-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.active-bubble {
  padding: 1px 6px;
  border-radius: 10px;
  background: #f4f4f5;
  color: #27272a;
  border: 1px solid #e4e4e7;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
}

.fav-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  line-height: 1;
  text-align: center;
  vertical-align: middle;
  flex-shrink: 0;
  padding: 0 5px;
  border-radius: 999px;
  background: #fef3c7;
  color: #b45309;
  border: 1px solid #fcd34d;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
}
.fav-badge-value {
  display: block;
  transform: translateY(1px);
}

.text-warning { color: #f59e0b !important; }
.text-success { color: #22c55e !important; }
.text-gold { color: #fbbf24 !important; }
.sidebar-nav-item:hover .text-warning,
.sidebar-nav-item.active .text-warning { color: #d97706 !important; }
.sidebar-nav-item:hover .text-success,
.sidebar-nav-item.active .text-success { color: #16a34a !important; }
.sidebar-nav-item:hover .text-gold,
.sidebar-nav-item.active .text-gold { color: #d97706 !important; }

/* 折叠过渡动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
