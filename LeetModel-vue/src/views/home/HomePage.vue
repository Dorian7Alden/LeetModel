<template>
  <div class="home-page">
    <!-- Main Content -->
    <div class="home-container">
      <div class="home-content">
        <div class="home-layout">
          <div class="main-content">
            <ContestSection />
            <CommunitySection />
          </div>
          <div class="sidebar">
            <CalendarSection />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import ContestSection from "./components/ContestSection.vue";
import CommunitySection from "./components/CommunitySection.vue";
import CalendarSection from "./components/CalendarSection.vue";
</script>

<style scoped>
@import './style.css';
</style>
