<template>
  <ProblemHeader @change="handleSearch" />
  <ProblemList ref="listRef" />
</template>

<script setup>
import { ref } from "vue";
import ProblemHeader from "../components/ProblemHeader.vue";
import ProblemList from "../components/ProblemList.vue";

const listRef = ref();

const handleSearch = (params) => {
  listRef.value.updateQuery(params);
};
</script>
