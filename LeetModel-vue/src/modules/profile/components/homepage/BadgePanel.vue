<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

function goAchievement(){
  router.push("/profile/achievement")
}

</script>

<template>

<div class="card" @click="goAchievement">

<h3>勋章成就</h3>

<p>点击查看全部成就</p>

</div>

</template>
<style scoped>

.card{

background:white;

padding:20px;

border-radius:8px;

box-shadow:0 2px 8px rgba(0,0,0,0.1);

}

.hint{

color:#888;

margin-top:10px;

}

</style>