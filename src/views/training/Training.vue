<template>

<div class="training-page">

  <!-- 训练导航栏 -->

  <div class="training-nav">

    <router-link to="/training/modeling">
      建模手
    </router-link>

    <router-link to="/training/paper">
      论文手
    </router-link>

    <router-link to="/training/coding">
      编程手
    </router-link>

  </div>

  <!-- 子页面显示区域 -->

  <router-view></router-view>

</div>

</template>

<style scoped>

.training-nav{

display:flex;

gap:30px;

margin-bottom:30px;

font-size:18px;

}

.training-nav a{

text-decoration:none;

color:#333;

}

.training-nav a.router-link-active{

color:#409eff;

font-weight:bold;

}

</style>