<template>
  <div class="description">
    <h2>{{ title }}</h2>

    <h3>题目描述</h3>

    <el-card>
      <pre>{{ description }}</pre>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  description: String,
});
</script>

<style scoped>
.description {
  margin: 20px 0;
}
</style>
