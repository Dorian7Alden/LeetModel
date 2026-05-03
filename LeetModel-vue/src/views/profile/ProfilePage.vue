<template>
  <div class="profile-container">
    <!-- 左侧 -->

    <div class="left">
      <UserCard />
    </div>

    <!-- 右侧 -->

    <div class="right">
      <SolveStats />

      <BadgePanel />

      <ActivityCalendar />

      <RecentActivity />
    </div>
  </div>
</template>

<script setup>
import UserCard from "./components/UserCard.vue";
import SolveStats from "./components/SolveStats.vue";
import BadgePanel from "./components/BadgePanel.vue";
import ActivityCalendar from "./components/ActivityCalendar.vue";
import RecentActivity from "./components/RecentActivity.vue";
</script>

<style scoped>
@import './style.css';
</style>
