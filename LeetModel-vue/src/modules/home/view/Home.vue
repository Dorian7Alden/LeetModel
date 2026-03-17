<template>
  <div class="home-container">
    <div class="home-content">
      <div class="home-layout">
        <!-- 左侧内容 -->

        <div class="main-content">
          <ContestSection />

          <communitySection />
        </div>

        <!-- 右侧日历 -->

        <div class="sidebar">
          <CalendarSection />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import ContestSection from "../components/homepage/ContestSection.vue";
import communitySection from "../components/homepage/communitySection.vue";
import CalendarSection from "../components/homepage/CalendarSection.vue";
</script>

<style scoped>
.home-content {
  max-width: 1200px;
  margin: 0 auto;
}
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
  max-width: 380px;
}
</style>
