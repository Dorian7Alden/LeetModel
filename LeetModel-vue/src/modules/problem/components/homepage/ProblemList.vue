<template>
  <div class="list">
    <!-- 空状态 -->
    <div v-if="list.length === 0">暂无数据</div>

    <!-- 列表 -->
    <div
      class="item"
      v-for="(item, index) in list"
      :key="item.id"
      @click="goDetail(item.id)"
    >
      <!-- 序号 -->
      <span class="index">
        {{ (query.pageNum - 1) * query.pageSize + index + 1 }}
      </span>

      <span class="title">{{ item.title }}</span>
      <span class="score">{{ item.score || "暂无" }}</span>
      <span class="level easy">{{ item.difficulty || "简单" }}</span>
    </div>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="fetchProblems"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { getProblemList } from "@/api/problem";

// 路由
const router = useRouter();

// 数据
const list = ref([]);
const total = ref(0);

// 查询条件
const query = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  difficulty: "",
  language: "",
  tags: [],
  minAveScore: null,
  maxAveScore: null,
  sortOrder: "asc",
});
const fetchProblems = async () => {
  try {
    const res = await getProblemList({
      ...query.value,
      tags: query.value.tags.length ? query.value.tags : undefined,
      minAveScore: query.value.minAveScore || undefined,
      maxAveScore: query.value.maxAveScore || undefined,
    });

    console.log("题目列表:", res);

    if (res && res.code === 200) {
      list.value = res.data.list || [];
      total.value = res.data.total || 0;
    } else {
      list.value = [];
    }
  } catch (err) {
    console.error("接口报错:", err);
    list.value = [];
  }
};

// 首次加载
onMounted(() => {
  fetchProblems();
});

// 跳转详情
const goDetail = (id) => {
  router.push(`/problem/${id}`);
};

const updateQuery = (params) => {
  query.value = {
    ...query.value,
    ...params,
    pageNum: 1,
  };
  fetchProblems();
};
defineExpose({
  updateQuery,
});
</script>

<style scoped>
.index {
  width: 40px;
  display: inline-block;
  color: #999;
}
.item {
  display: flex;
  justify-content: space-between;
  padding: 14px;
  background: #f5f5f5;
  border-radius: 8px;
  margin-bottom: 10px;
  cursor: pointer;
}

.title {
  color: #409eff;
}

.score {
  color: #67c23a;
}

.level.easy {
  color: #999;
}
</style>
