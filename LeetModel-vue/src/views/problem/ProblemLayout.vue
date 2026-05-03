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
import ProblemSidebar from "./components/ProblemSidebar.vue";
import ProblemRightPanel from "./components/ProblemRightPanel.vue";
</script>

<style scoped>
@import './style.css';
</style>
