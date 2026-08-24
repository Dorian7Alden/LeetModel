<template>
  <ProblemHeader
    :contests="filterOptions.contests"
    :tags="filterOptions.tags"
    :options-loading="optionsLoading"
    @change="handleSearch"
    @random="handleRandom"
  />
  <ProblemList ref="listRef" />
</template>

<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicProblemFilterOptions, getRandomPublicProblem } from '@/api/problem'
import ProblemHeader from "../components/ProblemHeader.vue";
import ProblemList from "../components/ProblemList.vue";

const listRef = ref();
const router = useRouter()
const route = useRoute()
const optionsLoading = ref(false)
const filterOptions = reactive({ contests: [], tags: [] })

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
  listRef.value.updateQuery(params);
};

const handleRandom = async (params) => {
  try {
    const response = await getRandomPublicProblem(Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null)))
    if (response.data?.id) router.push(`/problem/${response.data.id}`)
  } catch (error) { ElMessage.error(error.message || '暂时没有符合条件的题目') }
}

onMounted(() => {
  fetchFilterOptions()
  if (route.query.tagIds) listRef.value?.updateQuery({ tagIds: [Number(route.query.tagIds)] })
})
watch(
  () => route.query.tagIds,
  (tagId) => {
    if (tagId && listRef.value) listRef.value.updateQuery({ tagIds: [Number(tagId)] })
  },
  { immediate: true },
)
</script>
