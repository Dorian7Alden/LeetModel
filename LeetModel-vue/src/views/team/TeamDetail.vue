<template>

<div class="team-detail">

<h2>{{team.name}}</h2>

<p>当前人数：{{team.members}} / 3</p>

<h3>当前缺少</h3>

<div class="roles">

<span
v-for="role in team.missing"
:key="role"
class="tag"
>

{{role}}

</span>

</div>

</div>

</template>

<script setup>

import { useRoute } from "vue-router"

const route = useRoute()

const team = {
name:"数学建模冲刺队",
members:2,
missing:["论文手"]
}

console.log("队伍ID:",route.params.id)

</script>

<style scoped>

.team-detail{
width:800px;
margin:auto;
}

.roles{
margin-top:10px;
display:flex;
gap:10px;
}

.tag{
background:#409eff;
color:white;
padding:6px 12px;
border-radius:4px;
}

</style>