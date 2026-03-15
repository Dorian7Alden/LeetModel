<template>
  <div class="training-page">
    <!-- 训练导航组件 -->
    <TrainingNav />

    <!-- 子页面 -->
    <router-view></router-view>
  </div>
</template>

<script setup>
import TrainingNav from "../components/homepage/TrainingNav.vue";
</script>
