<template>

<div class="description">

<h3>题目描述</h3>

<el-card>

<pre>{{content}}</pre>

</el-card>

</div>

</template>

<script setup>

defineProps({
content:String
})

</script>

<style scoped>

.description{
margin:20px 0;
}

pre{
white-space:pre-wrap;
line-height:1.6;
}

</style>