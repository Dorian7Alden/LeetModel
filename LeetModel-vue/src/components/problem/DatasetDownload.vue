<template>

<div class="dataset">

<h3>数据集下载</h3>

<el-card>

<el-table :data="files">

<el-table-column prop="name" label="文件名" />

<el-table-column prop="size" label="大小" />

<el-table-column label="操作">

<template #default>

<el-button type="primary" size="small">

下载

</el-button>

</template>

</el-table-column>

</el-table>

</el-card>

</div>

</template>

<script setup>

import { ref } from 'vue'

const files = ref([
{
name:"dataset1.csv",
size:"2MB"
},
{
name:"dataset2.csv",
size:"1MB"
}
])

</script>

<style scoped>

.dataset{
margin:20px 0;
}

</style>