<template>
  <div class="contest-list">
    <div
      class="contest-card"
      v-for="contest in contests"
      :key="contest.id"
      @click="goDetail(contest.id)"
    >
      <div class="contest-title">
        {{ contest.title }}
      </div>

      <div class="contest-info">
        <span>{{ contest.type }}</span>
        <span>{{ contest.date }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

const contests = [
  { id: 1, title: "数学建模校赛", type: "建模", date: "2026-04-10" },
  { id: 2, title: "AI算法挑战赛", type: "编程", date: "2026-05-02" },
  { id: 3, title: "论文写作竞赛", type: "论文", date: "2026-06-01" },
];

function goDetail(id) {
  router.push(`/contest/${id}`);
}
</script>

<style scoped>
.contest-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.contest-card {
  background: #fff;

  padding: 18px;

  border-radius: 10px;

  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  transition: all 0.2s;

  cursor: pointer;
}

.contest-card:hover {
  transform: translateY(-4px);

  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.1);
}

.contest-title {
  font-size: 18px;

  font-weight: 600;

  margin-bottom: 10px;
}

.contest-info {
  display: flex;

  justify-content: space-between;

  font-size: 14px;

  color: #666;
}
</style>
