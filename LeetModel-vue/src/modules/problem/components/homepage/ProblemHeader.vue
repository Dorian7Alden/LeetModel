<template>
  <div class="problem-bank">
    <h1>数学建模题库</h1>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索题目" style="width: 300px" />

      <el-select v-model="difficulty" placeholder="难度">
        <el-option label="全部" value="" />
        <el-option label="简单" value="easy" />
        <el-option label="中等" value="medium" />
        <el-option label="困难" value="hard" />
      </el-select>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";

const keyword = ref("");
const difficulty = ref("");
</script>

<style scoped>
.problem-bank {
  padding: 30px;
}

.toolbar {
  margin: 20px 0;
  display: flex;
  gap: 20px;
}
</style>
