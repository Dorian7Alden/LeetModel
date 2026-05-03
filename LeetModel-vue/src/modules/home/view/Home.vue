<template>
  <div class="home-page">
    <!-- Main Content -->
    <div class="home-container">
      <div class="home-content">
        <div class="home-layout">
          <div class="main-content">
            <ContestSection />
            <communitySection />
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
import ContestSection from "../components/homepage/ContestSection.vue";
import communitySection from "../components/homepage/communitySection.vue";
import CalendarSection from "../components/homepage/CalendarSection.vue";
</script>

<style scoped>
.home-page {
  background: var(--lm-bg);
}

/* Main Layout */
.home-container {
  max-width: 1200px;
  margin: 0 auto;
}

.home-layout {
  display: flex;
  gap: 30px;
  padding: 30px 40px;
}

.main-content {
  flex: 6;
  display: flex;
  flex-direction: column;
  gap: 30px;
  min-width: 0;
}

.sidebar {
  flex: 4;
  max-width: 380px;
}

@media (max-width: 1024px) {
  .home-layout {
    flex-direction: column;
    padding: 20px;
  }
  .sidebar {
    max-width: none;
  }
}
</style>
