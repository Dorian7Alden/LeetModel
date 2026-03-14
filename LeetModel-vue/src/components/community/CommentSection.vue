<template>

<div class="comments">

<h3>评论区</h3>

<div
class="comment"
v-for="c in comments"
:key="c.id"
>

<b>{{c.user}}</b>

<p>{{c.content}}</p>

</div>

<textarea
v-model="newComment"
placeholder="写下你的评论"
></textarea>

<button @click="addComment">
发表评论
</button>

</div>

</template>

<script setup>

import { ref } from "vue"

const comments = ref([
  {
    id:1,
    user:"小红",
    content:"推荐看看经典建模教材"
  }
])

const newComment = ref("")

function addComment(){

comments.value.push({
id:Date.now(),
user:"匿名",
content:newComment.value
})

newComment.value=""

}

</script>

<style scoped>

.comments{
margin-top:40px;
}

.comment{
border-bottom:1px solid #eee;
padding:10px 0;
}

textarea{
width:100%;
height:80px;
margin-top:20px;
}

button{
margin-top:10px;
background:#409eff;
color:white;
border:none;
padding:8px 16px;
}

</style>