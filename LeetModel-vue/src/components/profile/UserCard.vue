
<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

function goSetting(){
  router.push("/profile/settings")
}

</script>

<template>

<div class="card">

<img class="avatar" src="https://via.placeholder.com/80"/>

<h3>Kind EasleyaAJ</h3>

<p>全站排名 100000</p>

<button class="edit-btn" @click="goSetting">
编辑个人资料
</button>

</div>

</template>

<style scoped>

.card{

background:white;

padding:20px;

border-radius:8px;

text-align:center;

box-shadow:0 2px 8px rgba(0,0,0,0.1);

}

.avatar{

width:80px;

border-radius:50%;

margin-bottom:10px;

}

.edit-btn{

margin-top:10px;

background:#67c23a;

color:white;

border:none;

padding:8px 16px;

border-radius:6px;

}

</style>