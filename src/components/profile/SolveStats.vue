<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

function goAnalysis(){
  router.push("/profile/analysis")
}

</script>

<template>

<div class="card stats" @click="goAnalysis">

<h3>解题统计</h3>

<p>点击查看技能分析</p>

</div>

</template>

<style scoped>

.card{

background:white;

padding:20px;

border-radius:8px;

box-shadow:0 2px 8px rgba(0,0,0,0.1);

}

.circle{

font-size:28px;

margin:10px 0;

}

.difficulty{

display:flex;

gap:20px;

}

.easy{color:#67c23a}
.medium{color:#e6a23c}
.hard{color:#f56c6c}

</style>