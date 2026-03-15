<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

function goHistory(){
  router.push("/profile/history")
}

</script>
<template>

<div class="card" @click="goHistory">

<h3>过去一年提交</h3>

<div class="calendar">
<div v-for="i in 365" :key="i" class="day"></div>
</div>

</div>

</template>

<style scoped>

.card{

background:white;

padding:20px;

border-radius:8px;

box-shadow:0 2px 8px rgba(0,0,0,0.1);

}

.calendar{

display:grid;

grid-template-columns:repeat(52,10px);

gap:3px;

}

.day{

width:10px;

height:10px;

background:#eee;

}

</style>