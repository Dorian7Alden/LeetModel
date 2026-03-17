<template>
  <section class="contest-wrapper">
    <div class="contest-area">
      <div class="contest-header">
        <h2>进行中的竞赛</h2>

        <router-link class="more" :to="'/contest'">查看更多</router-link>
      </div>

      <div class="contest-list">
        <div
          class="contest-card"
          v-for="contest in sortedContests"
          :key="contest.id"
        >
          <h3 class="contest-title">
            {{ contest.title }}
          </h3>

          <p class="contest-desc">
            {{ contest.desc }}
          </p>

          <div class="countdown">
            剩余时间：{{ getRemainingTime(contest.endTime) }}
          </div>

          <button class="enter-btn" @click="goContest(contest.id)">
            进入竞赛
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { getLatestContest } from "@/api/contest";

const router = useRouter();

/* 比赛数据 */
const contests = ref([]);

/* 当前时间 */
const now = ref(Date.now());

/* 页面加载 */
onMounted(() => {
  loadContest();

  setInterval(() => {
    now.value = Date.now();
  }, 1000);
});

/* 请求接口 */

async function loadContest() {
  try {
    const res = await getLatestContest();

    const list = res.data || [];

    contests.value = list.map((item) => ({
      id: item.id,
      title: item.title,
      desc: item.introduction,
      endTime: item.endTime,
    }));
  } catch (e) {
    console.error("加载竞赛失败", e);
  }
}

/* 跳转竞赛 */

function goContest(id) {
  router.push(`/contest/${id}`);
}

/* 剩余时间 */

function getRemainingTime(endTime) {
  const diff = new Date(endTime) - now.value;

  if (diff <= 0) return "已结束";

  const day = Math.floor(diff / 86400000);
  const hour = Math.floor((diff % 86400000) / 3600000);
  const minute = Math.floor((diff % 3600000) / 60000);
  const second = Math.floor((diff % 60000) / 1000);

  return `${day}天 ${hour}:${minute}:${second}`;
}

/* 排序 */

const sortedContests = computed(() => {
  return [...contests.value].sort((a, b) => {
    return new Date(a.endTime) - new Date(b.endTime);
  });
});
</script>

<style scoped>
.contest-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  margin-bottom: 20px;
}

.contest-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.more {
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
  text-decoration: none;
}

.more:hover {
  text-decoration: underline;
}

.contest-wrapper {
  padding: 40px 0;
}

.title {
  font-size: 22px;
  margin-bottom: 20px;
}

.contest-list {
  display: flex;
  gap: 20px;
}

.contest-card {
  flex: 1;
  background: white;
  padding: 20px;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.contest-card:hover {
  transform: translateY(-5px);
}

.contest-title {
  font-size: 18px;
  margin-bottom: 10px;
}

.contest-desc {
  color: #666;
  margin-bottom: 15px;
}

.countdown {
  color: #f56c6c;
  margin-bottom: 15px;
}

.enter-btn {
  padding: 6px 14px;
  background: #409eff;
  border: none;
  color: white;
  border-radius: 6px;
  cursor: pointer;
}

.enter-btn:hover {
  background: #66b1ff;
}
</style>
