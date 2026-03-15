<template>

<div class="problem-list">

  <ProblemCard
    v-for="p in problems"
    :key="p.id"
    :problem="p"
  />

</div>

</template>

<script setup>

import { ref } from 'vue'
import ProblemCard from '@/components/problem/ProblemCard.vue'

const problems = ref([
{
  id:1,
  title:"物流配送路径优化",
  difficulty:"中等",
  tags:["VRP","图论"],
  submit:120
},
{
  id:2,
  title:"城市交通流预测",
  difficulty:"困难",
  tags:["时间序列","预测"],
  submit:80
},
{
  id:3,
  title:"仓储选址优化",
  difficulty:"简单",
  tags:["线性规划"],
  submit:56
}
])

</script>

<style scoped>

.problem-list{
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:20px;
}

</style>