<template>

<div class="post-card" @click="goDetail">

  <h3>{{post.title}}</h3>

  <div class="info">

    <span>作者：{{post.author}}</span>

    <span>评论：{{post.replies}}</span>

  </div>

</div>

</template>

<script setup>

import { useRouter } from "vue-router"

const props = defineProps({
  post:Object
})

const router = useRouter()

function goDetail(){
  router.push("/post/" + props.post.id)
}

</script>

<style scoped>

.post-card{

padding:20px;

border-bottom:1px solid #eee;

cursor:pointer;

}

.post-card:hover{
background:#f7f7f7;
}

.info{
margin-top:8px;
color:#888;
font-size:14px;
}

</style>