<template>

<el-table :data="rank">

<el-table-column prop="rank" label="排名"/>

<el-table-column prop="team" label="队伍"/>

<el-table-column prop="score" label="得分"/>

</el-table>

</template>

<script setup>

import { ref } from "vue"

const rank = ref([
{rank:1,team:"清华队",score:95},
{rank:2,team:"北大队",score:90},
{rank:3,team:"复旦队",score:88}
])

</script>