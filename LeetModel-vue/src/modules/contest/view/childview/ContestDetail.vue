<template>
  <div>
    <h2>{{ contest.title }}</h2>

    <el-tabs>
      <el-tab-pane label="比赛说明">
        <p>{{ contest.description }}</p>
      </el-tab-pane>

      <el-tab-pane label="题目">
        <ul>
          <li v-for="p in problems" :key="p">
            {{ p }}
          </li>
        </ul>
      </el-tab-pane>

      <el-tab-pane label="排行榜">
        <ContestRank />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import ContestRank from "../../components/subpage/ContestRank.vue";

const contest = {
  title: "校内建模赛",

  description: "3人组队建模",
};

const problems = ["A题", "B题", "C题"];
</script>
