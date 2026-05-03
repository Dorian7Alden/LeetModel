<template>
  <div class="profile-container">
    <!-- 左侧 -->

    <div class="left">
      <UserCard />
    </div>

    <!-- 右侧 -->

    <div class="right">
      <ActivityCalendar />
    </div>
  </div>
</template>

<script setup>
import UserCard from "./components/UserCard.vue";
import ActivityCalendar from "./components/ActivityCalendar.vue";
</script>

<style scoped>
@import './style.css';
</style>
