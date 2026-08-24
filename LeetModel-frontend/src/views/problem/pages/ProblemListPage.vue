<template>
  <ProblemHeader @change="handleSearch" @random="handleRandom" />
  <ProblemList ref="listRef" />
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRandomPublicProblem } from '@/api/problem'
import ProblemHeader from "../components/ProblemHeader.vue";
import ProblemList from "../components/ProblemList.vue";

const listRef = ref();
const router = useRouter()

const handleSearch = (params) => {
  listRef.value.updateQuery(params);
};

const handleRandom = async (params) => {
  try {
    const response = await getRandomPublicProblem(Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null)))
    if (response.data?.id) router.push(`/problem/${response.data.id}`)
  } catch (error) { ElMessage.error(error.message || '暂时没有符合条件的题目') }
}
</script>
