<template>

<div class="layout">

  <Topbar />

  <!-- 搜索栏 -->
  <div class="search-bar">
    <el-input
      placeholder="搜索题目 / 比赛 / 帖子"
      prefix-icon="el-icon-search"
      style="width:400px"
    />
  </div>

  <!-- 横向导航栏 -->
  <Navbar />

  <div class="content">
    <router-view />
  </div>

</div>

</template>

<script setup>

import Topbar from "./Topbar.vue"
import Navbar from "../components/common/Navbar.vue"

</script>

<style scoped>

.layout{
display:flex;
flex-direction:column;
height:100vh;
}

.search-bar{
background:white;
padding:15px 40px;
border-bottom:1px solid #eee;
}

.content{
flex:1;
padding:20px;
overflow:auto;
background:#f5f7fa;
}

</style>