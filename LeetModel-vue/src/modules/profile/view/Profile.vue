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
import UserCard from "../components/homepage/UserCard.vue";
import SolveStats from "../components/homepage/SolveStats.vue";
import BadgePanel from "../components/homepage/BadgePanel.vue";
import ActivityCalendar from "../components/homepage/ActivityCalendar.vue";
import RecentActivity from "../components/homepage/RecentActivity.vue";
</script>

<style scoped>
.profile-container {
  display: flex;

  gap: 20px;

  padding: 30px;
}

.left {
  width: 280px;
}

.right {
  flex: 1;

  display: flex;

  flex-direction: column;

  gap: 20px;
}
</style>
