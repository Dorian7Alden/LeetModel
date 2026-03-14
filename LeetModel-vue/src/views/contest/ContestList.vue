<template>

<div>

<h2>比赛列表</h2>

<el-table :data="contests">

<el-table-column prop="title" label="比赛名称"/>

<el-table-column prop="start" label="开始时间"/>

<el-table-column prop="end" label="结束时间"/>

<el-table-column>

<template #default="scope">

<router-link :to="'/contest/'+scope.row.id">

<el-button type="primary">进入</el-button>

</router-link>

</template>

</el-table-column>

</el-table>

</div>

</template>

<script setup>

import { ref } from 'vue'

const contests = ref([
{
id:1,
title:"校内建模赛",
start:"2026-04-01",
end:"2026-04-03"
},
{
id:2,
title:"数学建模训练赛",
start:"2026-05-01",
end:"2026-05-02"
}
])

</script>