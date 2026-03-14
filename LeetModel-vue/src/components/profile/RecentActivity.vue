<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

function goSubmission(){

router.push({
name:"SubmissionDetail",
params:{ id:1 }
})

}

</script>

<template>

<div class="card">

<h3>最近通过</h3>

<ul>

<li @click="goSubmission">

<span>两数之和</span>

<span class="time">1年前</span>

</li>

</ul>

</div>

</template>
<style scoped>

.card{

background:white;

padding:20px;

border-radius:8px;

box-shadow:0 2px 8px rgba(0,0,0,0.1);

}

ul{
list-style:none;
padding:0;
}

li{

display:flex;

justify-content:space-between;

padding:10px 0;

}

.time{
color:#888;
}

</style>