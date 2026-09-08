<template>
  <div
    class="problem-workbench-shell"
    :class="{
      'is-sidebar-collapsed': isSidebarCollapsed,
      'no-sidebar': hideSidebar
    }"
  >
    <!-- 左侧常驻 Bar (在题库所有子视图间持久存在，不随子路由切换销毁) -->
    <ProblemSidebar
      v-if="!hideSidebar"
      :contests="filterOptions.contests"
      :problem-types="problemTypeTags"
      :active-section="activeSection"
      :fav-count="favCount"
      @select-section="handleSelectSection"
      @collapse-change="handleCollapseChange"
    />

    <!-- 右侧工作区：平滑承载大厅、赛事专属、题型专属等子视图 -->
    <main class="problem-workbench-main" :class="{ 'full-width': hideSidebar }">
      <router-view v-slot="{ Component }">
        <transition name="view-fade" mode="out-in">
          <keep-alive include="ProblemListPage">
            <component :is="Component" />
          </keep-alive>
        </transition>
      </router-view>
    </main>

    <!-- 全局回到顶部按钮，使用 Lucide ArrowUpFromLine 图标 -->
    <el-backtop :right="28" :bottom="96" :visibility-height="320" class="problem-backtop-btn">
      <div class="backtop-inner">
        <ArrowUpFromLine :size="16" :stroke-width="2" />
      </div>
    </el-backtop>
  </div>
</template>

<script setup>
import { computed, provide, reactive, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowUpFromLine } from '@lucide/vue'
import { getPublicProblemFilterOptions } from '@/api/problem'
import ProblemSidebar from './components/ProblemSidebar.vue'

const route = useRoute()
const router = useRouter()

const hideSidebar = computed(() => Boolean(route.meta?.hideSidebar))

const isSidebarCollapsed = ref(false)
const activeSection = ref('all')
const favCount = ref(0)
const optionsLoading = ref(false)
const filterOptions = reactive({ contests: [], tags: [] })
const lobbyBus = ref(null)

const problemTypeTags = computed(() => filterOptions.tags.filter(t => t.type === 'PROBLEM_TYPE'))

const fetchFilterOptions = async () => {
  optionsLoading.value = true
  try {
    const response = await getPublicProblemFilterOptions()
    filterOptions.contests = response.data?.contests || []
    filterOptions.tags = response.data?.tags || []
  } catch (error) {
    ElMessage.error(error.message || '获取筛选项失败')
  } finally {
    optionsLoading.value = false
  }
}

const handleCollapseChange = (collapsed) => {
  isSidebarCollapsed.value = collapsed
}

const handleSelectSection = (sectionKey) => {
  activeSection.value = sectionKey
  if (sectionKey === 'all') {
    if (route.path !== '/problem') {
      router.push('/problem')
    } else {
      lobbyBus.value = { type: 'reset_all', timestamp: Date.now() }
    }
  } else if (['favorite', 'in_progress', 'completed'].includes(sectionKey)) {
    if (route.path !== '/problem') {
      router.push({ path: '/problem', query: { section: sectionKey } })
    } else {
      lobbyBus.value = { type: 'set_section', section: sectionKey, timestamp: Date.now() }
    }
  }
}

const updateFavCount = (count) => {
  favCount.value = Number(count) || 0
}

// 跨层级依赖注入，子视图（大厅、赛事、题型）可直接共享元数据与通信信标
provide('problemWorkbench', {
  filterOptions,
  optionsLoading,
  problemTypeTags,
  favCount,
  updateFavCount,
  activeSection,
  lobbyBus
})

// 智能同步当前路由状态至侧边栏焦点
const syncActiveSectionFromRoute = () => {
  const path = route.path
  if (path.startsWith('/problem/contest')) {
    activeSection.value = 'contest'
  } else if (path.startsWith('/problem/type')) {
    activeSection.value = 'type'
  } else if (path === '/problem' || path === '/problem/') {
    activeSection.value = route.query.section || 'all'
  }
}

watch(() => [route.path, route.query.section], syncActiveSectionFromRoute, { immediate: true })

onMounted(() => {
  fetchFilterOptions()
})
</script>

<style scoped>
.problem-workbench-shell {
  display: flex;
  gap: 0;
  align-items: flex-start;
  width: 100%;
  min-height: calc(100vh - 56px);
  background: #ffffff;
}

.problem-workbench-main {
  flex: 1;
  min-width: 0;
  padding: 16px 24px 64px;
  transition: padding 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 沉浸式做题详情页无侧边栏时全宽展现 */
.problem-workbench-main.full-width {
  padding: 0;
}

/* 侧边栏完全收起后，右侧工作区留出呼吸边距 */
.problem-workbench-shell.is-sidebar-collapsed .problem-workbench-main:not(.full-width) {
  padding-left: 56px;
  padding-right: 76px;
}

/* 子视图淡入淡出平滑过渡 */
.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.view-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.view-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 860px) {
  .problem-workbench-shell {
    flex-direction: column;
  }
  .problem-workbench-main:not(.full-width) {
    padding: 12px 14px 32px;
  }
}

/* 回到顶部按钮定制 */
.backtop-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #52525b;
  transition: color var(--lm-transition), transform var(--lm-transition);
}

.problem-backtop-btn:hover .backtop-inner {
  color: #18181b;
  transform: translateY(-2px);
}
</style>
