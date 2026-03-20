<template>
  <ProblemHeader @change="handleSearch" />
  <ProblemList ref="listRef" />
</template>

<script setup>
import { ref } from "vue";
import ProblemHeader from "../../components/homepage/ProblemHeader.vue";
import ProblemList from "../../components/homepage/ProblemList.vue";

const listRef = ref();

const handleSearch = (params) => {
  listRef.value.updateQuery(params);
};
</script>
