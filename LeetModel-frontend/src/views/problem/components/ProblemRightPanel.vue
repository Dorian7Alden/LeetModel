<template>
  <aside class="right-panel">
    <CustomCalendar />
    <PopularLabel />
  </aside>
</template>

<script setup>
import CustomCalendar from "./CustomCalendar.vue";
import PopularLabel from "./PopularLabel.vue";
</script>

<style scoped>
.right-panel {
  width: 260px;
  flex-shrink: 0;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky; top: 64px;
  align-self: flex-start;
}

@media (max-width: 1200px) {
  .right-panel { display: none; }
}
</style>
