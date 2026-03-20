<template>
  <div class="problem-page">
    <!-- 左侧 -->
    <ProblemSidebar />

    <!-- 中间 -->
    <main class="center">
      <router-view />
    </main>

    <!-- 右侧 -->
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
  gap: 20px;
  padding: 20px;
}

.center {
  flex: 1;
}
</style>
