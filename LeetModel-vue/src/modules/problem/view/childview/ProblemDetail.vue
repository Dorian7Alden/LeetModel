<template>
  <div class="detail">
    <h2>{{ problem.title }}</h2>

    <div class="meta">
      <span class="difficulty">{{ problem.difficulty }}</span>
    </div>

    <div class="content">
      {{ problem.content }}
    </div>
  </div>
</template>

<script setup>
import { useRoute } from "vue-router";
import { ref, onMounted } from "vue";
import { getProblemDetail } from "@/api/problem";

const route = useRoute();
const problem = ref({});

// 获取详情
const fetchDetail = async () => {
  const id = route.params.id;

  const res = await getProblemDetail(id);

  console.log("详情数据:", res);

  if (res.code === 20000) {
    problem.value = res.data;
  }
};

onMounted(() => {
  fetchDetail();
});
</script>

<style scoped>
.detail {
  padding: 20px;
}

.meta {
  margin: 10px 0;
}

.difficulty {
  padding: 4px 10px;
  border-radius: 6px;
  background: #f56c6c;
  color: #fff;
}

.content {
  margin-top: 20px;
  line-height: 1.6;
}
</style>
