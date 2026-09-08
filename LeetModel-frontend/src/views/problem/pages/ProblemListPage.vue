<template>
  <div class="problem-lobby-shell">
    <!-- 中间正文题目流 -->
    <div class="problem-feed-area">
      <ProblemHeader
        :contests="effectiveFilterOptions.contests"
        :tags="effectiveFilterOptions.tags"
        :total="problemTotal"
        :options-loading="effectiveOptionsLoading"
        :random-loading="randomLoading"
        @change="handleSearch"
        @random="handleRandom"
        @sort="handleSort"
      />
      <ProblemList
        ref="listRef"
        :tags="effectiveFilterOptions.tags"
        @fav-change="handleFavChange"
        @total-change="handleTotalChange"
      />
    </div>

    <!-- 右侧辅助区 (240px): 热门练习题、考向标签与页面链接 -->
    <ProblemRightAside
      :tags="effectiveFilterOptions.tags"
      :popular-problems="popularProblems"
      :popular-loading="popularLoading"
      @select-popular="handleSelectPopular"
      @select-tag="handleSelectRightTag"
    />
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import { getMyTeams, getPopularPracticeProblems } from '@/api/team'
import ProblemHeader from "../components/ProblemHeader.vue";
import ProblemList from "../components/ProblemList.vue";
import ProblemRightAside from "../components/ProblemRightAside.vue";

const workbench = inject('problemWorkbench', null)

defineOptions({
  name: 'ProblemListPage'
})

const listRef = ref();
const router = useRouter()
const route = useRoute()
const optionsLoading = ref(false)
const randomLoading = ref(false)
const filterOptions = reactive({ contests: [], tags: [] })
const problemTotal = ref(0)
const popularProblems = ref([])
const popularLoading = ref(false)

const effectiveFilterOptions = computed(() => {
  if (workbench?.filterOptions && (workbench.filterOptions.contests?.length || workbench.filterOptions.tags?.length)) {
    return workbench.filterOptions
  }
  return filterOptions
})

const effectiveOptionsLoading = computed(() => {
  if (workbench?.optionsLoading != null) {
    return workbench.optionsLoading.value
  }
  return optionsLoading.value
})

const fetchFilterOptions = async () => {
  if (workbench?.filterOptions && (workbench.filterOptions.contests?.length || workbench.filterOptions.tags?.length)) {
    return
  }
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

const fetchPopularProblems = async () => {
  popularLoading.value = true
  try {
    const response = await getPopularPracticeProblems(3)
    popularProblems.value = Array.isArray(response.data) ? response.data : []
  } catch {
    popularProblems.value = []
  } finally {
    popularLoading.value = false
  }
}

const handleSearch = (params) => {
  listRef.value.updateQuery(params);

  // 使用顶部筛选时回到“题目”主视图，避免筛选结果仍被个人列表状态限制。
  if (route.query.section) {
    workbench?.activeSection && (workbench.activeSection.value = 'all')
  }

  // 将当前筛选条件同步写入 URL query，确保进入详情页或分享后，返回时条件完整还原
  const nextQuery = {}
  if (params.keyword?.trim()) nextQuery.keyword = params.keyword.trim()
  if (params.contestId) nextQuery.contestId = params.contestId
  if (params.year) nextQuery.year = params.year
  if (params.difficulty) nextQuery.difficulty = params.difficulty
  if (params.statementLanguage) nextQuery.statementLanguage = params.statementLanguage
  if (params.minAverageScore != null) nextQuery.minScore = params.minAverageScore
  if (params.maxAverageScore != null) nextQuery.maxScore = params.maxAverageScore
  if (Array.isArray(params.tagIds) && params.tagIds.length) {
    nextQuery.tagIds = params.tagIds.join(',')
  }

  router.replace({ query: nextQuery })
};

const handleRandom = async (params) => {
  randomLoading.value = true
  try {
    const response = await getRandomPublicProblem(Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null)))
    if (response.data?.id) {
      ElMessage.success('已按当前筛选条件为你抽取一题')
      await router.push(`/problem/${response.data.id}`)
    }
  } catch (error) {
    ElMessage.error(error.message || '暂时没有符合条件的题目')
  } finally {
    randomLoading.value = false
  }
}

const handleSort = (field) => {
  if (field === 'clear') {
    listRef.value?.clearSort()
    return
  }
  listRef.value?.cycleSort(field)
}

const switchSection = async (sectionKey) => {
  if (sectionKey === 'all') {
    listRef.value?.setDisplayMode('all')
  } else if (sectionKey === 'favorite') {
    listRef.value?.setDisplayMode('favorite')
    const favIds = listRef.value?.getFavoritedIds() || []
    if (!favIds.length) {
      ElMessage.info('暂无收藏题目，可点击题目右侧小星星加入收藏')
    }
  } else if (sectionKey === 'in_progress') {
    try {
      const res = await getMyTeams({ practiceStatus: 'IN_PROGRESS', page: 1, pageSize: 20 })
      const ids = (res.data?.rows || []).map(t => t.problemId).filter(Boolean)
      listRef.value?.setDisplayMode('in_progress', ids)
    } catch {
      listRef.value?.setDisplayMode('in_progress', [])
    }
  } else if (sectionKey === 'completed') {
    try {
      const res = await getMyTeams({ practiceStatus: 'ENDED', page: 1, pageSize: 20 })
      const ids = (res.data?.rows || []).map(t => t.problemId).filter(Boolean)
      listRef.value?.setDisplayMode('completed', ids)
    } catch {
      listRef.value?.setDisplayMode('completed', [])
    }
  }
}

// 右侧辅助信息事件响应
const handleSelectPopular = (problem) => {
  if (problem?.problemId) router.push(`/problem/${problem.problemId}`)
}

const handleSelectRightTag = (tag) => {
  if (route.query.section) {
    workbench?.activeSection && (workbench.activeSection.value = 'all')
    router.replace({ query: { ...route.query, section: undefined } })
  }
  if (tag.rawTag?.id) {
    router.replace({ query: { ...route.query, section: undefined, tagIds: String(tag.rawTag.id) } })
    listRef.value?.updateQuery({ tagIds: [tag.rawTag.id] })
  } else {
    router.replace({ query: { ...route.query, section: undefined, keyword: tag.name, tagIds: undefined } })
    listRef.value?.updateQuery({ keyword: tag.name })
  }
}

const handleFavChange = (count) => {
  workbench?.updateFavCount?.(count)
}

const handleTotalChange = (total) => {
  problemTotal.value = Number(total) || 0
}

const applyRouteQueryToList = () => {
  const q = route.query
  if (!q) return
  const queryObj = {}
  if (q.keyword) queryObj.keyword = String(q.keyword)
  if (q.contestId) queryObj.contestId = Number(q.contestId)
  if (q.year) queryObj.year = Number(q.year)
  if (q.difficulty) queryObj.difficulty = Number(q.difficulty)
  if (q.statementLanguage) queryObj.statementLanguage = String(q.statementLanguage)
  if (q.minScore != null) queryObj.minAverageScore = Number(q.minScore)
  if (q.maxScore != null) queryObj.maxAverageScore = Number(q.maxScore)
  if (q.tagIds) queryObj.tagIds = String(q.tagIds).split(',').map(Number).filter(Boolean)
  if (Object.keys(queryObj).length) {
    listRef.value?.updateQuery(queryObj)
  }
}

onMounted(() => {
  fetchFilterOptions()
  fetchPopularProblems()
  if (route.query.section) switchSection(String(route.query.section))
  applyRouteQueryToList()
})

// 响应父级工作台通过 bus 分发的操作指令
watch(
  () => workbench?.lobbyBus?.value,
  (bus) => {
    if (!bus) return
    if (bus.type === 'reset_all') {
      listRef.value?.setDisplayMode('all')
    } else if (bus.type === 'set_section' && bus.section) {
      switchSection(bus.section)
    }
  },
  { deep: true }
)

watch(
  () => route.query.section,
  (section) => {
    if (section) switchSection(String(section))
    else listRef.value?.setDisplayMode('all')
  }
)
watch(
  () => route.query,
  () => {
    applyRouteQueryToList()
  },
  { immediate: true },
)
</script>

<style scoped>
.problem-lobby-shell {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  width: 100%;
}

.problem-feed-area {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1280px) {
  .problem-lobby-shell {
    flex-direction: column;
  }
}
</style>
