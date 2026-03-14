<template>

<div class="problem-detail">

<h2>{{problem.title}}</h2>

<el-divider />

<ProblemDescription :content="problem.description" />

<DatasetDownload />

<ProblemDiscussion />

</div>

</template>

<script setup>

import { reactive } from 'vue'
import ProblemDescription from '@/components/problem/ProblemDescription.vue'
import DatasetDownload from '@/components/problem/DatasetDownload.vue'
import ProblemDiscussion from './ProblemDiscussion.vue'

const problem = reactive({

title:"物流配送路径优化",

description:`

某物流公司需要为多个客户配送货物。

请建立模型优化配送路径，使总运输距离最小。

要求：

1 建立数学模型  
2 设计求解算法  
3 给出模拟结果

`

})

</script>

<style scoped>

.problem-detail{
  padding:30px;
  max-width:900px;
}

</style>