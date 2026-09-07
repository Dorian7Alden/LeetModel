<template>
  <div class="problem-workbench-shell" :class="{ 'is-sidebar-collapsed': isSidebarCollapsed }">
    <!-- 左侧常驻 Bar (210px): 题目大厅、赛事展开、题型展开、我的实训、我的收藏 -->
    <ProblemSidebar
      :contests="filterOptions.contests"
      :problem-types="problemTypeTags"
      :active-section="activeSection"
      :fav-count="favCount"
      @select-section="handleSelectSection"
      @collapse-change="handleCollapseChange"
    />

    <!-- 右侧复合工作区 (代码左右布局，视觉上划分出 中间正文 + 右侧火热专区) -->
    <div class="problem-composite-area">
      <!-- 中间正文题目流 -->
      <div class="problem-feed-area">
        <ProblemHeader
          :contests="filterOptions.contests"
          :tags="filterOptions.tags"
          :options-loading="optionsLoading"
          :random-loading="randomLoading"
          @change="handleSearch"
          @random="handleRandom"
        />
        <ProblemList ref="listRef" @fav-change="handleFavChange" />
      </div>

      <!-- 右侧火热专区 (270px): 最火热备战题单、高频考向标签云、赛前排雷锦囊 -->
      <ProblemRightAside
        :tags="filterOptions.tags"
        @select-curated="handleSelectCurated"
        @select-tag="handleSelectRightTag"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import { getMyTeams } from '@/api/team'
import ProblemHeader from "../components/ProblemHeader.vue";
import ProblemList from "../components/ProblemList.vue";
import ProblemSidebar from "../components/ProblemSidebar.vue";
import ProblemRightAside from "../components/ProblemRightAside.vue";

const listRef = ref();
const router = useRouter()
const route = useRoute()
const optionsLoading = ref(false)
const randomLoading = ref(false)
const filterOptions = reactive({ contests: [], tags: [] })
const activeSection = ref('all')
const favCount = ref(0)
const isSidebarCollapsed = ref(false)

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

const handleSearch = (params) => {
  activeSection.value = 'all'
  listRef.value.updateQuery(params);
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

// 左侧 Bar 事件响应
const handleSelectSection = async (sectionKey) => {
  activeSection.value = sectionKey
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
      ElMessage.info('已切换至正在实训练习的赛题')
    } catch {
      listRef.value?.setDisplayMode('in_progress', [])
    }
  } else if (sectionKey === 'completed') {
    try {
      const res = await getMyTeams({ practiceStatus: 'ENDED', page: 1, pageSize: 20 })
      const ids = (res.data?.rows || []).map(t => t.problemId).filter(Boolean)
      listRef.value?.setDisplayMode('completed', ids)
      ElMessage.info('已切换至已完成实训练习的赛题')
    } catch {
      listRef.value?.setDisplayMode('completed', [])
    }
  }
}

const handleSelectType = (typeTag) => {
  activeSection.value = `type-${typeTag.id}`
  listRef.value?.updateQuery({ tagIds: [typeTag.id] })
}

const handleCollapseChange = (collapsed) => {
  isSidebarCollapsed.value = collapsed
}

// 右侧专区事件响应
const handleSelectCurated = (curatedSet) => {
  ElMessage.success(`已载入精选题单：${curatedSet.title}`)
  listRef.value?.updateQuery(curatedSet.query)
}

const handleSelectRightTag = (tag) => {
  if (tag.rawTag?.id) {
    ElMessage.info(`按热门标签筛选：${tag.name}`)
    listRef.value?.updateQuery({ tagIds: [tag.rawTag.id] })
  } else {
    listRef.value?.updateQuery({ keyword: tag.name })
  }
}

const handleFavChange = (count) => {
  favCount.value = count
}

onMounted(() => {
  fetchFilterOptions()
  if (route.query.keyword) listRef.value?.updateQuery({ keyword: String(route.query.keyword) })
  if (route.query.tagIds) listRef.value?.updateQuery({ tagIds: [Number(route.query.tagIds)] })
})
watch(
  () => route.query.keyword,
  (keyword) => {
    if (listRef.value) listRef.value.updateQuery({ keyword: keyword || '' })
  },
  { immediate: true },
)
watch(
  () => route.query.tagIds,
  (tagId) => {
    if (tagId && listRef.value) listRef.value.updateQuery({ tagIds: [Number(tagId)] })
  },
  { immediate: true },
)
</script>

<style scoped>
.problem-workbench-shell {
  display: flex;
  gap: 0;
  align-items: flex-start;
  width: 100%;
  min-height: calc(100vh - 56px);
}

.problem-composite-area {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding: 16px 24px 48px;
  transition: padding-left 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 侧边栏完全收起后，主体区域绝不紧贴屏幕最左侧，留出舒适的呼吸边距 */
.problem-workbench-shell.is-sidebar-collapsed .problem-composite-area {
  padding-left: 64px;
}

.problem-feed-area {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1280px) {
  .problem-composite-area {
    flex-direction: column;
  }
}

@media (max-width: 860px) {
  .problem-workbench-shell {
    flex-direction: column;
  }
  .problem-composite-area {
    padding: 12px 14px 32px;
  }
}
</style>
