<template>
  <div class="community">
    <CommunityHeader />

    <PostCard v-for="post in posts" :key="post.id" :post="post" />
  </div>
</template>

<script setup>
import { ref } from "vue";

import PostCard from "../components/homepage/PostCard.vue";
import CommunityHeader from "../components/homepage/CommunityHeader.vue";

const posts = ref([
  {
    id: 1,
    title: "数学建模如何入门？",
    author: "小明",
    replies: 12,
  },
  {
    id: 2,
    title: "推荐几个 Python 建模库",
    author: "小红",
    replies: 6,
  },
]);
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}

button {
  background: #409eff;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
}
</style>
