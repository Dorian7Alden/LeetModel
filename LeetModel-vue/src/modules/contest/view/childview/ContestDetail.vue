<template>
  <div class="contest-detail">
    <h1>{{ contest.title }}</h1>

    <img v-if="contest.imageUrl" :src="contest.imageUrl" class="cover" />

    <p class="intro">
      {{ contest.introduction }}
    </p>

    <div class="info">
      <p>
        报名时间：{{ contest.signUpStartTime }} - {{ contest.signUpEndTime }}
      </p>
      <p>比赛时间：{{ contest.startTime }} - {{ contest.endTime }}</p>
      <p>状态：{{ contest.status }}</p>
    </div>

    <a :href="contest.officialUrl" target="_blank" class="official">
      官方网站
    </a>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { getContestDetail } from "@/api/contest";

const route = useRoute();

const contest = ref({});

onMounted(async () => {
  const id = route.params.id;

  const res = await getContestDetail(id);

  contest.value = res.data;
});
</script>

<style scoped>
.contest-detail {
  width: 900px;
  margin: auto;
  padding: 40px;
}

.cover {
  width: 100%;
  border-radius: 10px;
  margin: 20px 0;
}

.intro {
  line-height: 1.8;
  color: #555;
}

.info {
  margin-top: 20px;
}

.official {
  display: inline-block;
  margin-top: 20px;
  color: #409eff;
}
</style>
