<template>
  <div class="team-list">
    <h2>寻找队伍</h2>

    <div class="teams">
      <TeamCard v-for="team in teams" :key="team.id" :team="team" />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import TeamCard from "../components/homepage/TeamCard.vue";

const teams = ref([
  {
    id: 1,
    name: "数学建模冲刺队",
    members: 2,
    missing: ["论文手"],
  },
  {
    id: 2,
    name: "AI建模小队",
    members: 1,
    missing: ["论文手", "编程手"],
  },
  {
    id: 3,
    name: "工业优化队",
    members: 2,
    missing: ["编程手"],
  },
]);
</script>

<style scoped>
.team-list {
  padding: 30px 60px;
}

.teams {
  display: grid;

  grid-template-columns: repeat(3, 1fr);

  gap: 20px;

  margin-top: 20px;
}
</style>
