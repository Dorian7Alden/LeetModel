<template>
  <div class="contest-page">
    <h2 class="page-title">赛事列表</h2>

    <ContestList />
  </div>
</template>

<script setup>
import ContestList from "../components/homepage/ContestList.vue";
</script>

<style scoped>
.page-title {
  font-size: 24px;

  font-weight: 600;

  margin-bottom: 20px;
}
</style>
