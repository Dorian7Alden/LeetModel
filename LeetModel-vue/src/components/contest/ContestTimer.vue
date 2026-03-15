<template>

<div class="timer">

比赛剩余时间：

<span class="time">{{time}}</span>

</div>

</template>

<script setup>

import { ref,onMounted } from "vue"

const time = ref("00:00:00")

let end = new Date().getTime() + 3600*1000

function update(){

let now = new Date().getTime()

let diff = end-now

let h = Math.floor(diff/1000/3600)

let m = Math.floor(diff/1000%3600/60)

let s = Math.floor(diff/1000%60)

time.value = `${h}:${m}:${s}`

}

onMounted(()=>{

setInterval(update,1000)

})

</script>

<style scoped>

.time{
color:red;
font-weight:bold;
}

</style>