<template>
  <div class="post-detail">
    <h2>{{ post.title }}</h2>

    <p class="author">作者：{{ post.author }}</p>

    <p class="content">
      {{ post.content }}
    </p>

    <CommentSection />
  </div>
</template>

<script setup>
import CommentSection from "../../components/subpage/CommentSection.vue";
const post = {
  title: "数学建模如何入门？",

  author: "小明",

  content: "建议先学习 Python + 基础数学模型",
};
</script>

<style scoped>
.post-detail {
  width: 800px;
  margin: auto;
}

.author {
  color: #888;
  margin-bottom: 20px;
}
</style>
