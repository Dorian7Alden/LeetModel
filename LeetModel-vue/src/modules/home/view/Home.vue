<template>
  <div class="home-layout">
    <!-- 左侧内容 -->

    <div class="main-content">
      <ContestSection />

      <ProblemSection />
    </div>

    <!-- 右侧日历 -->

    <div class="sidebar">
      <CalendarSection />
    </div>
  </div>
</template>

<script setup>
import ContestSection from "../components/homepage/ContestSection.vue";
import ProblemSection from "../components/homepage/ProblemSection.vue";
import CalendarSection from "../components/homepage/CalendarSection.vue";
</script>

<style scoped>
.home-layout {
  display: flex;

  gap: 30px;

  padding: 30px 60px;
}

/* 左侧 60% */

.main-content {
  flex: 6;

  display: flex;

  flex-direction: column;

  gap: 40px;
}

/* 右侧 40% */

.sidebar {
  flex: 4;
}
</style>
