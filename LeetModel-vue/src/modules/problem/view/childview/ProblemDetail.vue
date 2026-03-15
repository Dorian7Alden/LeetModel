<template>
  <ProblemDescription
    :title="problem.title"
    :description="problem.description"
  />

  <DatasetDownload />

  <ProblemDiscussion />
</template>

<script setup>
import { reactive } from "vue";

import ProblemDescription from "../../components/subpage/ProblemDescription.vue";
import DatasetDownload from "../../components/subpage/DatasetDownload.vue";
import ProblemDiscussion from "../../components/subpage/ProblemDiscussion.vue";

const problem = reactive({
  title: "物流配送路径优化",

  description: `

某物流公司需要为多个客户配送货物。

请建立模型优化配送路径，使总运输距离最小。

要求：

1 建立数学模型  
2 设计求解算法  
3 给出模拟结果

`,
});
</script>
