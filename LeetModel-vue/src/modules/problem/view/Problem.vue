<template>
  <div class="problem-page">
    <ProblemSidebar />
    <main class="center">
      <router-view />
    </main>
    <ProblemRightPanel />
  </div>
</template>

<script setup>
import ProblemSidebar from "../components/homepage/ProblemSidebar.vue";
import ProblemRightPanel from "../components/homepage/ProblemRightPanel.vue";
</script>

<style scoped>
.problem-page {
  display: flex;
  gap: 0;
  min-height: calc(100vh - 64px);
  background: var(--lm-bg);
}

.center {
  flex: 1;
  min-width: 0;
  padding: 20px 24px;
}

@media (max-width: 1200px) {
  .center { padding: 16px; }
}

@media (max-width: 768px) {
  .problem-page { flex-direction: column; }
}
</style>
