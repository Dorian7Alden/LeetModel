<template>

<div class="create-post">

<h2>发布帖子</h2>

<input
v-model="title"
placeholder="帖子标题"
/>

<textarea
v-model="content"
placeholder="帖子内容"
></textarea>

<button>
发布
</button>

</div>

</template>

<script setup>

import { ref } from "vue"

const title = ref("")
const content = ref("")

</script>

<style scoped>

.create-post{
width:800px;
margin:auto;
display:flex;
flex-direction:column;
gap:20px;
}

textarea{
height:200px;
}

button{
width:120px;
background:#409eff;
color:white;
border:none;
padding:10px;
}

</style>